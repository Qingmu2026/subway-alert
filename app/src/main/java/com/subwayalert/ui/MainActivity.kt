package com.subwayalert.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.subwayalert.data.RouteManager
import com.subwayalert.data.StationDatabase
import com.subwayalert.data.SubwayRoute
import com.subwayalert.service.AudioMonitorService
import com.subwayalert.ui.theme.SubwayAlertTheme

class MainActivity : ComponentActivity() {
    companion object { private const val PERMISSION_REQUEST_CODE = 1001 }
    private lateinit var routeManager: RouteManager
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeManager = RouteManager(this)
        prefs = getSharedPreferences("subway_prefs", Context.MODE_PRIVATE)
        requestPermissions()
        
        setContent { 
            SubwayAlertTheme { 
                MainScreen(
                    onStartMonitoring = { startMonitoring() },
                    onStopMonitoring = { stopMonitoring() },
                    onSetRoute = { route -> saveRoute(route) },
                    onClearRoute = { clearRoute() },
                    onSaveFeishu = { url, secret -> saveFeishuConfig(url, secret) }
                ) 
            } 
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { 
            permissions.add(Manifest.permission.POST_NOTIFICATIONS) 
        }
        val needPermissions = permissions.filter { 
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED 
        }
        if (needPermissions.isNotEmpty()) { 
            ActivityCompat.requestPermissions(this, needPermissions.toTypedArray(), PERMISSION_REQUEST_CODE) 
        }
    }

    private fun startMonitoring() {
        if (routeManager.getRoute() == null) {
            Toast.makeText(this, "请先设置路线", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, AudioMonitorService::class.java).apply { action = "START" }
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "已开始监听", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        val intent = Intent(this, AudioMonitorService::class.java).apply { action = "STOP" }
        startService(intent)
        Toast.makeText(this, "已停止监听", Toast.LENGTH_SHORT).show()
    }

    private fun saveRoute(route: SubwayRoute) { 
        routeManager.saveRoute(route)
        Toast.makeText(this, "路线已保存", Toast.LENGTH_SHORT).show() 
    }
    
    private fun clearRoute() { 
        routeManager.clearRoute()
        Toast.makeText(this, "路线已清除", Toast.LENGTH_SHORT).show() 
    }
    
    private fun saveFeishuConfig(url: String, secret: String) {
        prefs.edit()
            .putString("feishu_webhook", url)
            .putString("feishu_secret", secret)
            .apply()
        Toast.makeText(this, "飞书配置已保存", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onSetRoute: (SubwayRoute) -> Unit,
    onClearRoute: () -> Unit,
    onSaveFeishu: (String, String) -> Unit
) {
    val context = LocalContext.current
    val routeManager = remember { RouteManager(context) }
    var currentRoute by remember { mutableStateOf(routeManager.getRoute()) }
    var showSetupDialog by remember { mutableStateOf(false) }
    var showFeishuDialog by remember { mutableStateOf(false) }
    var isMonitoring by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("地铁到站提醒") },
                actions = {
                    IconButton(onClick = { showFeishuDialog = true }) {
                        Text("📲")
                    }
                }
            ) 
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 状态卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMonitoring) 
                        MaterialTheme.colors.primary.copy(alpha = 0.1f)
                    else 
                        MaterialTheme.colors.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isMonitoring) "🔊 正在监听" else "⏸️ 已停止",
                            style = MaterialTheme.typography.h5
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (currentRoute != null) {
                        Text(text = "🚇 ${currentRoute!!.city} ${currentRoute!!.line}", style = MaterialTheme.typography.body1)
                        Text(text = "${currentRoute!!.start} → ${currentRoute!!.end}", style = MaterialTheme.typography.body2)
                        Text(text = "提前${currentRoute!!.alertBeforeStations}站提醒", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    } else {
                        Text(text = "⚠️ 未设置路线", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 控制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { 
                        if (!isMonitoring) {
                            isMonitoring = true
                            onStartMonitoring()
                        } else {
                            isMonitoring = false
                            onStopMonitoring()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isMonitoring) 
                            MaterialTheme.colors.error 
                        else 
                            MaterialTheme.colors.primary
                    ),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text(if (isMonitoring) "停止监听" else "开始监听")
                }
                
                Button(
                    onClick = { showSetupDialog = true },
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Text("设置路线")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 清除路线按钮
            if (currentRoute != null) {
                TextButton(onClick = { 
                    onClearRoute()
                    currentRoute = null
                }) {
                    Text("清除路线", color = MaterialTheme.colors.error)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 帮助信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colors.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📖 使用说明", style = MaterialTheme.typography.subtitle1)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. 点击"设置路线"选择您的行程", style = MaterialTheme.typography.body2)
                    Text("2. 点击"开始监听"启动语音识别", style = MaterialTheme.typography.body2)
                    Text("3. 保持App在后台运行", style = MaterialTheme.typography.body2)
                    Text("4. 地铁报站后自动识别并提醒", style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("💡 提示：可以配置飞书推送", style = MaterialTheme.typography.caption)
                }
            }
        }
    }

    // 设置路线对话框
    if (showSetupDialog) {
        RouteSetupDialog(
            currentRoute = currentRoute,
            onDismiss = { showSetupDialog = false },
            onConfirm = { route ->
                onSetRoute(route)
                currentRoute = route
                showSetupDialog = false
            }
        )
    }
    
    // 飞书配置对话框
    if (showFeishuDialog) {
        FeishuConfigDialog(
            onDismiss = { showFeishuDialog = false },
            onConfirm = { url, secret ->
                onSaveFeishu(url, secret)
                showFeishuDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RouteSetupDialog(
    currentRoute: SubwayRoute?,
    onDismiss: () -> Unit,
    onConfirm: (SubwayRoute) -> Unit
) {
    var selectedCity by remember { mutableStateOf(currentRoute?.city ?: "南京") }
    var selectedLine by remember { mutableStateOf(currentRoute?.line ?: "1号线") }
    var selectedStart by remember { mutableStateOf(currentRoute?.start ?: "") }
    var selectedEnd by remember { mutableStateOf(currentRoute?.end ?: "") }
    var alertBefore by remember { mutableStateOf(currentRoute?.alertBeforeStations ?: 2) }
    
    var lineExpanded by remember { mutableStateOf(false) }
    var startExpanded by remember { mutableStateOf(false) }
    var endExpanded by remember { mutableStateOf(false) }
    
    val cities = StationDatabase.cities.keys.toList()
    val lines = StationDatabase.getLines(selectedCity)
    val stations = StationDatabase.getStations(selectedCity, selectedLine)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置地铁路线") },
        text = {
            LazyColumn {
                item {
                    // 城市选择
                    Text("选择城市", style = MaterialTheme.typography.caption)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        cities.forEachIndexed { index, city ->
                            SegmentedButton(
                                selected = selectedCity == city,
                                onClick = { 
                                    selectedCity = city
                                    selectedLine = StationDatabase.getLines(city).first()
                                    selectedStart = ""
                                    selectedEnd = ""
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, cities.size)
                            ) {
                                Text(city, style = MaterialTheme.typography.body2)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 线路选择
                    Text("选择线路", style = MaterialTheme.typography.caption)
                    ExposedDropdownMenuBox(
                        expanded = lineExpanded,
                        onExpandedChange = { lineExpanded = !lineExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedLine,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lineExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = lineExpanded,
                            onDismissRequest = { lineExpanded = false }
                        ) {
                            StationDatabase.getLines(selectedCity).forEach { line ->
                                DropdownMenuItem(
                                    text = { Text(line) },
                                    onClick = { 
                                        selectedLine = line
                                        selectedStart = ""
                                        selectedEnd = ""
                                        lineExpanded = false 
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 起点选择
                    Text("起点站", style = MaterialTheme.typography.caption)
                    ExposedDropdownMenuBox(
                        expanded = startExpanded,
                        onExpandedChange = { startExpanded = !startExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedStart,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            placeholder = { Text("选择上车站") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = startExpanded,
                            onDismissRequest = { startExpanded = false }
                        ) {
                            stations.forEach { station ->
                                DropdownMenuItem(
                                    text = { Text(station) },
                                    onClick = { 
                                        selectedStart = station
                                        startExpanded = false 
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 终点选择
                    Text("终点站", style = MaterialTheme.typography.caption)
                    ExposedDropdownMenuBox(
                        expanded = endExpanded,
                        onExpandedChange = { endExpanded = !endExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedEnd,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            placeholder = { Text("选择终点站") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = endExpanded,
                            onDismissRequest = { endExpanded = false }
                        ) {
                            stations.forEach { station ->
                                DropdownMenuItem(
                                    text = { Text(station) },
                                    onClick = { 
                                        selectedEnd = station
                                        endExpanded = false 
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 提醒设置
                    Text("提前提醒站数", style = MaterialTheme.typography.caption)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = alertBefore.toFloat(),
                            onValueChange = { alertBefore = it.toInt() },
                            valueRange = 1f..5f,
                            steps = 3,
                            modifier = Modifier.weight(1f)
                        )
                        Text("$alertBefore 站", style = MaterialTheme.typography.body2)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedStart.isNotEmpty() && selectedEnd.isNotEmpty()) {
                        onConfirm(SubwayRoute(
                            city = selectedCity,
                            line = selectedLine,
                            start = selectedStart,
                            end = selectedEnd,
                            alertBeforeStations = alertBefore
                        ))
                    }
                },
                enabled = selectedStart.isNotEmpty() && selectedEnd.isNotEmpty()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun FeishuConfigDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("subway_prefs", Context.MODE_PRIVATE) }
    
    var webhookUrl by remember { mutableStateOf(prefs.getString("feishu_webhook", "") ?: "") }
    var secret by remember { mutableStateOf(prefs.getString("feishu_secret", "") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("飞书推送配置") },
        text = {
            Column {
                Text("配置后，到站提醒会发送到飞书群", style = MaterialTheme.typography.body2)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = webhookUrl,
                    onValueChange = { webhookUrl = it },
                    label = { Text("Webhook地址") },
                    placeholder = { Text("https://open.feishu.cn/...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text("签名密钥(可选)") },
                    placeholder = { Text("可选") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "如何获取Webhook？\n飞书群 → 设置 → 群机器人 → 添加机器人 → 自定义机器人",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(webhookUrl, secret) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
