<div align="center">
  
  <h1>Clipypse</h1>
  
  <b>跨平台剪贴板同步工具</b>

  <br>
  <br>

  Clipypse 是一款强大的跨平台剪贴板同步工具，支持 Android 和 Desktop (Windows/Linux/macOS) 之间的剪贴板实时同步。采用 Kotlin Multiplatform 和 Jetpack Compose/Material 3 构建。

</div>

## 主要功能

- **双向同步**：Android ↔ Desktop 实时双向同步
- **多种数据类型**：支持纯文本、图片、文件同步
- **多种连接模式**：支持 Wi-Fi 和 USB (ADB) 连接
- **跨平台支持**：
  - **Android 客户端**：采用现代 Material 3 设计，支持深色与浅色主题
  - **桌面端服务端**：可在 Windows、Linux 和 macOS 上运行
- **历史记录**：保存剪贴板历史，支持重新发送
- **自动同步**：自动检测剪贴板变化并同步

## 使用指南

### 桌面端作为服务端

1. 在桌面端启动 Clipypse
2. 选择 "Server" 模式
3. 点击 "Start" 开始监听
4. 记下显示的 IP 地址和端口

### Android 端作为客户端

1. 在 Android 端启动 Clipypse
2. 选择 "Client" 模式
3. 输入桌面端显示的 IP 地址和端口
4. 点击 "Start" 连接

### USB 连接

1. 使用 USB 线连接 Android 设备到电脑
2. 确保已启用 ADB 调试
3. 在 Android 端选择 "USB" 模式
4. IP 地址会自动设置为 127.0.0.1

## 构建

```bash
# 构建所有平台
./gradlew build

# Android APK
./gradlew :composeApp:assembleDebug

# 桌面端运行
./gradlew :composeApp:jvmRun

# 桌面端打包
./gradlew :composeApp:packageExe        # Windows
./gradlew :composeApp:packageDmg        # macOS
./gradlew :composeApp:packageDeb        # Linux
```

## 技术栈

- **Kotlin Multiplatform**：跨平台共享代码
- **Jetpack Compose**：声明式 UI 框架
- **Material 3**：现代设计系统
- **Ktor**：网络通信框架
- **Kotlinx Serialization**：数据序列化
- **Protocol Buffers**：高效的二进制序列化格式

## 协议

Clipypse 使用自定义的二进制协议进行通信：

- **TCP**：控制消息和剪贴板数据传输
- **Protocol Buffers**：数据序列化格式
- **握手协议**：确保连接双方都是 Clipypse 客户端

## 许可证

MIT License
