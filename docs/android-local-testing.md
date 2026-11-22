# Android 测试指南

项目提供核心检查、Host 检查、Android 原生图形检查和完整模拟器检查。日常改动按所涉及模块选择检查，桌面手势与厂商系统行为在手机上手动验收。

## 核心与 Host

```powershell
. ./Enter-Project.ps1
python tools/check_core.py
python tools/check_wallpaper_store.py
python tools/check_content_host.py
python tools/check_download_service_host.py
python tools/check_motion_host.py
```

## Android 原生图形

```powershell
python tools/prepare_android_graphics_tests.py
python tools/build_android.py --compile-only
python tools/check_android_graphics.py dev.workshopvideo.SettingsUiChecks dev.workshopvideo.PreviewLifecycleChecks dev.workshopvideo.PictureSettingsChecks dev.workshopvideo.SceneGraphicsChecks
python tools/check_android_graphics.py dev.workshopvideo.SceneGpuChecks dev.workshopvideo.LauncherSyncChecks
python tools/check_scene_gpu.py
```

Robolectric API 35 的原生图形检查覆盖实际颜色像素、图层、X-ray、设置控件、调色面板和背景翻页。预览生命周期检查运行实际设置页的方法，以延迟释放的显示后端替身检查连接顺序。两个外部样本用例从 `WVB_WORKSHOP_DIR` 读取自行准备的工坊素材，未配置时跳过；样本不随源码分发。

`SceneGpuChecks` 生成 Android 原生 Canvas 对照帧，`check_scene_gpu.py` 在桌面 ANGLE 上执行正式图层着色器，对照翻页、视差、旋转、颜色、透明度与 X-ray。它验证画面，实际帧率通过真机日志中的 `WMRender` 记录；`cap` 是配置上限，`activeFps` 是连续绘制期间的提交速率。`prepareMs`、`submitMs` 和 `swapMs` 用于定位场景重画、提交及交换等待。

真机交互由使用者操作。通过 USB 读取日志、安装更新，不自动滑动桌面或更换壁纸。精确桌面同步的首次系统授权也由使用者完成。

## 完整模拟器

```powershell
python tools/prepare_software_avd.py
./tools/start_android_validation.ps1
python tools/check_emulator.py --wait 900
python tools/build_android.py
python tools/build_device_checks.py
python tools/check_emulator.py --run
```

软件模拟器使用 AOSP Android 11 / API 30、SwiftShader 和项目内 AVD。设备名 `emulator-5556`，ADB 端口 `5041`。检查脚本核对虚拟硬件，只在该模拟器执行自动交互。结果写入 `.cache/emulator-app-validation`，启动日志在 `.cache/emulator-validation`。

设备测试使用生成的渐变视频验证真实纹理、画布、视频解码、壁纸服务与翻页。AOSP API 30 适合基础渲染回归；较新版本的独立桌面/锁屏接口及厂商启动器行为在相应手机验证。

## 外部样本工具

`tools/check_scene_corpus.py` 和 `tools/inspect_scene_corpus.py` 从 `WVB_WORKSHOP_DIR` 读取工坊目录。`tools/check_streaming_host.py` 使用 `WVB_TEST_VIDEO` 和 `WVB_TEST_MANIFEST` 指定对应视频及清单。样本、报告与日志保存在被忽略的缓存目录。
