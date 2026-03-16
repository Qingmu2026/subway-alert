# 地铁到站提醒 Android App 规格说明

## 1. 项目概述
- **项目名称**: SubwayAlert (地铁到站提醒)
- **包名**: com.subwayalert
- **功能**: 通过手机麦克风监听地铁报站语音，自动识别站名并提醒用户到站
- **目标用户**: 经常乘坐地铁的用户

## 2. 已实现功能

### 2.1 核心功能
- ✅ **后台持续录音** - 前台Service保持后台运行
- ✅ **语音识别** - 使用Android SpeechRecognizer API
- ✅ **站名提取** - 支持多种报站格式
- ✅ **位置追踪** - 计算离终点站剩余站数
- ✅ **推送通知** - 到站前N站发送系统通知
- ✅ **振动提醒** - 配合推送发送振动
- ✅ **飞书推送** - 支持Webhook推送到飞书群

### 2.2 用户交互
- ✅ 路线设置向导（城市→线路→起点→终点）
- ✅ 提前提醒站数设置（1-5站）
- ✅ 飞书Webhook配置
- ✅ 实时状态显示
- ✅ 开始/停止控制

## 3. 技术架构

### 3.1 技术栈
- Kotlin 1.9
- Jetpack Compose + Material3
- MVVM架构
- Android SpeechRecognizer（语音识别）
- OkHttp（飞书推送）
- Coroutines

### 3.2 数据结构
```
SubwayRoute:
  - city: String (城市)
  - line: String (线路)
  - start: String (起点站)
  - end: String (终点站)
  - alertBeforeStations: Int (提前提醒站数，默认2)
```

## 4. 权限列表
- RECORD_AUDIO (录音)
- FOREGROUND_SERVICE (前台服务)
- POST_NOTIFICATIONS (Android 13+)
- VIBRATE (振动)
- INTERNET (网络)

## 5. 文件结构
```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/subwayalert/
│   │   ├── SubwayAlertApp.kt
│   │   ├── data/
│   │   │   ├── RouteManager.kt
│   │   │   └── StationDatabase.kt
│   │   ├── service/
│   │   │   └── AudioMonitorService.kt
│   │   └── ui/
│   │       ├── MainActivity.kt
│   │       └── Theme.kt
│   └── res/
│       ├── drawable/ic_subway.xml
│       └── values/strings.xml, colors.xml
├── build.gradle.kts
└── proguard-rules.pro
```

## 6. 编译命令
```bash
cd /workspace/subway-alert-app
./gradlew assembleDebug
```

## 7. 使用说明

### 首次使用
1. 安装App并打开
2. 点击"设置路线"
3. 选择城市、线路、起点、终点
4. 设置提前提醒站数
5. 点击"保存"

### 配置飞书推送（可选）
1. 点击右上角📲图标
2. 输入飞书机器人Webhook地址
3. 点击"保存"

### 开始使用
1. 确保已设置路线
2. 点击"开始监听"
3. 将手机放在耳边或衣服口袋
4. 听到报站后自动识别并计算位置
5. 快到站时收到通知+振动提醒
