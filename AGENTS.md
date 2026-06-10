# AGENTS.md — 主要文档

> **此文件是本项目的主要参考文档。** CLAUDE.md 作为补充。当两者信息冲突时，以 AGENTS.md 为准。后续内容更新优先写入此处。

# ADBTools

Android Jetpack Compose sample project. Single module, minimal dependencies.

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

Gradle wrapper at `gradlew`. Version catalog at `gradle/libs.versions.toml`.

## Key commands

```sh
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device
./gradlew test                   # run unit tests (JVM)
./gradlew connectedAndroidTest   # run instrumented tests (device/emulator)
./gradlew lint                   # static analysis
./gradlew :app:dependencies      # inspect dependency tree
```

Run a single unit test:
```sh
./gradlew test --tests "com.young.sample.adbtools.ExampleUnitTest"
```

Run a single instrumented test:
```sh
./gradlew connectedAndroidTest --tests "com.young.sample.adbtools.ExampleInstrumentedTest"
```

## Project structure

```
ADBTools/
├── app/
│   ├── src/main/java/com/young/sample/adbtools/
│   │   ├── MainActivity.kt          # single entry point
│   │   └── ui/theme/                # Color.kt, Type.kt, Theme.kt
│   ├── src/test/java/.../            # JVM unit tests
│   └── src/androidTest/java/.../     # instrumented tests
├── gradle/libs.versions.toml        # version catalog
├── build.gradle.kts                  # root (plugin declarations only)
└── settings.gradle.kts               # single :app module
```

## Architecture

- Single `ComponentActivity` (`MainActivity`) with `enableEdgeToEdge()` + `setContent {}`
- Jetpack Compose with Material 3
- Dynamic color on Android 12+ (`Build.VERSION_CODES.S`), fallback to static purple/pink palette
- No navigation library, no DI framework, no ViewModels yet
- No ADB-specific code yet — it's a starter template despite the repo name

## Dependencies

All via version catalog. Compose dependencies use the BOM for version alignment.

**Runtime**: core-ktx 1.10.1, lifecycle-runtime-ktx 2.6.1, activity-compose 1.8.0, Compose UI + Material3.

**Test**: JUnit 4.13.2, AndroidX Test JUnit 1.3.0, Espresso 3.7.0, Compose UI Test (via BOM).

## Conventions

- `android.useAndroidX=true`, `android.nonTransitiveRClass=true`
- Kotlin code style: `official`
- Java 11 source/target compatibility
- Release build has ProGuard (`proguard-android-optimize.txt`) but minification is disabled
- SDK at `/Users/jieyue/Library/Android/sdk` (local.properties, not committed)

## SDK / ADB

SDK path is `~/Library/Android/sdk`. ADB binary is at `$ANDROID_HOME/platform-tools/adb` or the SDK's `platform-tools/` directory.

## Tests

- Unit tests (JVM): `src/test/` — standard JUnit 4
- Instrumented tests: `src/androidTest/` — run on device/emulator with `AndroidJUnit4`
- Tests are currently the default template examples only
