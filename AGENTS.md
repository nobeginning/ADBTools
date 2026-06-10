# AGENTS.md — 主要文档

> **此文件是本项目的主要参考文档**。CLAUDE.md 作为补充。当两者信息冲突时，以 AGENTS.md 为准。后续内容更新优先写入此处。

# ADBTools

Android Jetpack Compose 项目 + 纯 Kotlin/JVM ADB 客户端库 (`:adb-lib`)。

## Build system

| Tool | Version |
|---|---|
| Gradle | 8.11.1 |
| AGP | 8.9.2 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.09.00 |
| compileSdk / targetSdk | 35 |
| minSdk | 24 |
| Java target | 11 |
| coroutines | 1.8.1 |

Gradle wrapper at `gradlew`. Version catalog at `gradle/libs.versions.toml`.

## Modules

```
ADBTools/
├── adb-lib/                    # 独立 Android Library 模块 (:adb-lib)
│   └── src/main/kotlin/com/young/sample/adblib/
│       ├── AdbClient.kt        # 公开主入口
│       ├── AdbConfig.kt        # 连接配置
│       ├── AdbCommand.kt       # 协议命令常量
│       ├── transport/          # 传输层 (Transport, TcpTransport, AuthHandler, AdbSession, AdbStream)
│       ├── protocol/           # 协议层 (AdbMessage, AdbPacket, ShellProtocol)
│       ├── service/            # 服务层 (HostServices, ShellService, SyncService, FramebufferService)
│       └── model/              # 数据模型 (AdbDevice, AdbException, SyncEntry, ForwardEntry 等)
├── app/                        # Android 应用模块 (:app)
│   └── src/main/java/com/young/sample/adbtools/
│       ├── MainActivity.kt     # 入口，Compose Navigation
│       └── ui/
│           ├── navigation/     # NavRoutes + AppNavigation
│           ├── screens/        # DeviceListScreen, ShellScreen
│           ├── viewmodel/      # DeviceViewModel, ShellViewModel
│           └── theme/          # Color.kt, Type.kt, Theme.kt
└── docs/                       # 设计文档
```

## Architecture

### 分层架构

```
App / UI Layer (Compose + ViewModel)
    └── adb-lib API (AdbClient)
         ├── Service Layer (HostServices, ShellService, SyncService)
         ├── Session & Stream Layer (AdbSession, AdbStream)
         ├── Protocol Layer (AdbMessage, AdbPacket, ShellProtocol)
         └── Transport Layer (TcpTransport, AuthHandler)
```

### 连接模型

- **AdbClient**: 管理 Host 级操作（getDevices, trackDevices, forward 等），每个方法使用短连接
- **AdbSession**: 每个目标设备一条独立 TCP 连接，通过 `host:transport:<serial>` 切换
- **AdbStream**: 设备上的一个服务流（shell, sync, framebuffer 等）

关键设计约束：Smart Socket 协议中 `host:transport` 后该连接不能再发 host 命令，因此 host 级操作和设备级操作使用不同的 TCP 连接。

## Key commands

```sh
./gradlew :adb-lib:assembleDebug     # build adb-lib AAR
./gradlew :app:assembleDebug         # build + package APK
./gradlew :adb-lib:test              # adb-lib unit tests
./gradlew :app:test                  # app unit tests
./gradlew test                       # all unit tests
./gradlew lint                       # static analysis
```

## Dependencies

**adb-lib**: `kotlinx-coroutines-core`, Java stdlib (零外部第三方依赖)

**app**: `core-ktx`, `lifecycle-runtime-ktx`, `activity-compose`, Compose UI + Material3 + Material Icons Extended, `lifecycle-viewmodel-compose`, `navigation-compose`, `:adb-lib`

## Conventions

- Kotlin 代码风格: `official`
- Java 11 source/target compatibility
- 协程友好：所有 IO 方法为 `suspend` 函数
- UI 层使用 ViewModel + StateFlow，无 DI 框架
