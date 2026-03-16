package com.subwayalert.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.subwayalert.R
import com.subwayalert.SubwayAlertApp
import com.subwayalert.data.RouteManager
import com.subwayalert.data.StationDatabase
import com.subwayalert.data.SubwayRoute
import com.subwayalert.ui.MainActivity
import kotlinx.coroutines.*
import java.util.*
import java.util.regex.Pattern

/**
 * 音频监控服务 - 持续监听麦克风并识别报站
 * 使用Android SpeechRecognizer进行语音识别
 */
class AudioMonitorService : Service() {

    companion object {
        private const val TAG = "AudioMonitorService"
        private const val NOTIFICATION_ID = 1001
        
        // 报站关键词模式
        private val STATION_PATTERNS = listOf(
            Pattern.compile("下一站[，,](.+?)(?:站|$)"),
            Pattern.compile("前方到站[，,](.+?)(?:站|$)"),
            Pattern.compile("本次列车到达[，,](.+?)(?:站|$)"),
            Pattern.compile("到达[，,](.+?)(?:站|$)"),
            Pattern.compile("下一站(.+?)(?:站|$)")
        )

        // 跳過词（减少误识别）
        private val SKIP_WORDS = listOf("您好", "谢谢", "再见", "对不起", "请", "让", "让座", "开门", "关门")
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isMonitoring = false
    private lateinit var routeManager: RouteManager
    private var currentRoute: SubwayRoute? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var prefs: SharedPreferences
    
    // 状态追踪
    private var lastRecognizedStation: String? = null
    private var lastRecognitionTime: Long = 0
    
    // 飞书相关
    private var feishuWebhookUrl: String = ""
    private var feishuSecret: String = ""

    override fun onCreate() {
        super.onCreate()
        routeManager = RouteManager(this)
        prefs = getSharedPreferences("subway_prefs", Context.MODE_PRIVATE)
        
        currentRoute = routeManager.getRoute()
        loadFeishuConfig()
        
        startForeground(NOTIFICATION_ID, createNotification("等待中...", "请设置路线"))
        
        initSpeechRecognizer()
    }

    private fun loadFeishuConfig() {
        feishuWebhookUrl = prefs.getString("feishu_webhook", "") ?: ""
        feishuSecret = prefs.getString("feishu_secret", "") ?: ""
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        } else {
            Log.w(TAG, "Speech recognition not available")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startMonitoring()
            "STOP" -> stopMonitoring()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        
        currentRoute = routeManager.getRoute()
        
        val statusText = if (currentRoute != null) {
            "正在监听: ${currentRoute!!.start} → ${currentRoute!!.end}"
        } else {
            "请先设置路线"
        }
        
        updateNotification("🔊 监听中", statusText)
        
        // 开始持续语音识别
        startContinuousRecognition()
    }

    private fun stopMonitoring() {
        isMonitoring = false
        stopSpeechRecognition()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * 开始持续语音识别
     */
    private fun startContinuousRecognition() {
        scope.launch {
            while (isMonitoring) {
                // 刷新路线
                currentRoute = routeManager.getRoute()
                
                // 开始一次语音识别
                if (currentRoute != null && speechRecognizer != null) {
                    startListening()
                }
                
                // 等待一段时间后再次识别
                delay(2000)
            }
        }
    }

    private fun startListening() {
        try {
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // 添加静默检测
                putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recognition", e)
        }
    }

    private fun stopSpeechRecognition() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recognition", e)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                // 识别出错，重新开始
                val errorNames = mapOf(
                    SpeechRecognizer.ERROR_AUDIO to "AUDIO",
                    SpeechRecognizer.ERROR_CLIENT to "CLIENT",
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS to "PERMISSIONS",
                    SpeechRecognizer.ERROR_NETWORK to "NETWORK",
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT to "NETWORK_TIMEOUT",
                    SpeechRecognizer.ERROR_NO_MATCH to "NO_MATCH",
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY to "BUSY",
                    SpeechRecognizer.ERROR_SERVER to "SERVER",
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT to "SPEECH_TIMEOUT"
                )
                Log.d(TAG, "Recognition error: ${errorNames[error] ?: error}")
                
                // 继续尝试识别
                if (isMonitoring) {
                    startListening()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    for (text in matches) {
                        processRecognizedText(text)
                    }
                }
                
                // 继续识别
                if (isMonitoring) {
                    startListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    for (text in matches) {
                        if (isLikelyStationAnnouncement(text)) {
                            processRecognizedText(text)
                        }
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    /**
     * 判断是否是可能的报站语音
     */
    private fun isLikelyStationAnnouncement(text: String): Boolean {
        val lowerText = text.lowercase()
        return lowerText.contains("下一站") || 
               lowerText.contains("前方到站") || 
               lowerText.contains("本次列车") ||
               lowerText.contains("到达")
    }

    /**
     * 处理识别到的文本
     */
    fun processRecognizedText(text: String) {
        Log.d(TAG, "Recognized: $text")
        
        // 提取站名
        val stationName = extractStationName(text) ?: return
        
        // 跳过无关词语
        if (SKIP_WORDS.any { stationName.contains(it) }) return
        
        // 避免重复识别同一站（5秒内）
        val now = System.currentTimeMillis()
        if (stationName == lastRecognizedStation && now - lastRecognitionTime < 5000) {
            return
        }
        
        lastRecognizedStation = stationName
        lastRecognitionTime = now
        
        val route = routeManager.getRoute() ?: return
        
        // 匹配站点
        val matchedStation = StationDatabase.findStation(route.city, route.line, stationName) ?: run {
            Log.d(TAG, "Station not found: $stationName in ${route.city} ${route.line}")
            return
        }
        
        // 计算剩余站数
        val remaining = StationDatabase.calculateRemainingStations(
            route.city, route.line, matchedStation, route.end
        )
        
        if (remaining < 0) {
            Log.d(TAG, "Not on route: $matchedStation")
            return // 不在路线上
        }
        
        // 构建消息
        val isArriving = remaining <= route.alertBeforeStations
        val emoji = if (isArriving) "🚨" else "📍"
        
        val message = buildString {
            append("$emoji 当前站: $matchedStation\n")
            append("🚇 终点: ${route.end}\n")
            append("📊 剩余 $remaining 站")
            
            if (isArriving) {
                append("\n\n🚨 即将到站！准备好下车！")
            }
        }
        
        // 发送到飞书
        sendToFeishu(message)
        
        // 振动提醒
        if (isArriving) {
            triggerVibration()
        }
        
        // 更新通知
        updateNotification(
            if (isArriving) "🚨 即将到站" else "📍 $matchedStation",
            "距离${route.end}还有$remaining站"
        )
        
        Log.d(TAG, message)
    }

    /**
     * 从识别文本中提取站名
     */
    private fun extractStationName(text: String): String? {
        for (pattern in STATION_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.replace("站", "")?.trim()
            }
        }
        return null
    }

    /**
     * 发送消息到飞书
     */
    private fun sendToFeishu(message: String) {
        if (feishuWebhookUrl.isEmpty()) {
            Log.d(TAG, "Feishu webhook not configured")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val jsonBody = """{"msg_type":"text","content":{"text":"$message"}}"""
                
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url(feishuWebhookUrl)
                    .post(jsonBody.toRequestBody())
                    .build()
                
                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "Feishu response: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send to Feishu", e)
            }
        }
    }

    private fun String.toRequestBody(): okhttp3.RequestBody = 
        okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), this)

    /**
     * 触发振动提醒
     */
    private fun triggerVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 震动模式：短-停-长
            val pattern = longArrayOf(0, 300, 200, 500)
            vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 300, 200, 500), -1)
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(title: String, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 停止按钮
        val stopIntent = Intent(this, AudioMonitorService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SubwayAlertApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_subway)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_subway, "停止", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * 更新通知
     */
    private fun updateNotification(title: String, text: String) {
        val notification = createNotification(title, text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        isMonitoring = false
        stopSpeechRecognition()
        speechRecognizer?.destroy()
        scope.cancel()
        super.onDestroy()
    }
}
