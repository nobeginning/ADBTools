# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 文档优先级

**AGENTS.md 是主要参考文档**，CLAUDE.md 作为补充。当两者信息冲突时，以 AGENTS.md 为准。

后续如有内容补充或更新，**优先写入 AGENTS.md**，仅在此处添加 AGENTS.md 中不适合放置的补充信息。

## 补充说明

### 本地 SDK 路径

SDK 路径在 `local.properties` 中指定（已 gitignore）。当前机器上为：

```
sdk.dir=/Users/jieyue/Library/Android/sdk
```

ADB 二进制文件位于 `$ANDROID_HOME/platform-tools/adb`。在 shell 中使用 ADB 前，可通过以下命令定位：

```sh
# 从 local.properties 提取 SDK 路径
export ANDROID_HOME=$(grep sdk.dir local.properties | cut -d= -f2)
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

### 项目状态

此项目目前是一个 **Jetpack Compose 启动模板**。尽管仓库名为 "ADBTools"，但尚未包含任何 ADB 相关代码。它是一个干净的画布——当功能开发开始时，请相应更新 AGENTS.md。

### IDE 配置

项目使用 Android Studio 的标准 `.idea/` 配置。Gradle 设置通过 IDE 进行管理，它们将覆盖 `gradle.properties` 中的默认值。JDK 配置存储在 `.idea/` 中，并已提交到版本控制。
