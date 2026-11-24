# 开发与构建

### 从源码构建

目前提供 **Windows + PowerShell** 构建工具链，无需 Android Studio 或 Gradle。

准备：

- Python 3.10 或更新版本，可使用 `python` 命令（若该命令指向 Microsoft Store 占位程序，请安装 Python 或关闭对应的“应用执行别名”）。
- JDK 21，设置 `JAVA_HOME` 或 `WVB_JDK`，也可将 JDK 放入 `.tools/jdk`。
- 网络可访问 Android 工具下载站和 Maven 依赖仓库。

克隆仓库后，在仓库根目录运行：

```powershell
./Build-App.ps1
```

脚本会准备 Android SDK / Build Tools、恢复锁定的依赖、运行核心检查，并构建签名 APK。首次执行需要下载工具和依赖，后续使用项目内缓存。

生成的安装包位于 `dist/WallpaperMobile.apk` 和 `dist/WallpaperMobile-0.5.10.apk`。构建缓存、SDK、依赖和签名均保存在项目目录内。自行构建会使用自己的本地签名，后续更新请保留 `.tools/signing`。

也可以分步运行：

```powershell
. ./Enter-Project.ps1
python tools/prepare_android.py
python tools/prepare_dependencies.py
python tools/check_core.py
python tools/build_android.py
```

通过 USB 安装：

```powershell
./Install-App.ps1                         # 列出设备
./Install-App.ps1 -Serial <device-serial> # 覆盖安装到指定设备
```

### 开发与测试

`android/src` 包含 Android 界面、播放器、壁纸引擎及平台无关的 Steam 核心；`android/test` 为核心检查，`android/test-host` 为平台替身与 Android 原生图形检查。

```powershell
. ./Enter-Project.ps1
python tools/check_core.py
python tools/check_wallpaper_store.py
python tools/check_download_service_host.py
python tools/check_motion_host.py
```

Android 原生图形检查（需先运行 `Enter-Project.ps1`，以配置 `WVB_JDK`）：

```powershell
python tools/prepare_android_graphics_tests.py
python tools/build_android.py --compile-only
python tools/check_android_graphics.py dev.workshopvideo.AccountAndFrameChecks dev.workshopvideo.GeneralAndMotionChecks dev.workshopvideo.SceneCompositionChecks dev.workshopvideo.SettingsUiChecks dev.workshopvideo.PreviewLifecycleChecks dev.workshopvideo.PictureSettingsChecks dev.workshopvideo.SceneGraphicsChecks dev.workshopvideo.LauncherSyncChecks dev.workshopvideo.SceneGpuChecks
```

界面截图预览（使用示例数据在电脑上渲染主要页面，输出到 `.cache/ui-previews`，不代表真机效果）：

```powershell
python tools/build_android.py --compile-only
python tools/render_ui_previews.py
```

外部工坊样本为可选测试输入，通过 `WVB_WORKSHOP_DIR` 配置；未配置时跳过对应样本用例。其余图形用例使用生成的测试素材。完整 Android 模拟器流程见 [Android 测试指南](docs/android-local-testing.md)。
