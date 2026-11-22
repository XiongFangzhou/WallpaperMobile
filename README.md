# WallpaperMobile

在手机上发现、收藏、播放和设置 Wallpaper Engine 创意工坊壁纸。

WallpaperMobile 是一款原生 Android 应用，集工坊检索、资源下载、视频播放器和动态壁纸引擎于一体。手机可独立连接 Steam，下载的内容保存在应用内，随时播放、管理或设为壁纸。

**Android 10+ · 原生界面 · 手机独立运行 · 视频 / 图片 / 场景**

## 功能

| 功能 | 体验 |
| --- | --- |
| 发现壁纸 | 关键词搜索，按作品类型、标签、分辨率、内容分级和日期筛选，支持排序、翻页与页码直达，自动记住筛选设置 |
| 预览与收藏 | 查看标题、预览、介绍和作品信息，收藏喜欢的作品，稍后再下载 |
| 在线播放 | 视频在线播放与拖动进度，使用内存缓存；也可下载后离线观看 |
| 下载管理 | 后台下载、进度展示、暂停、继续与取消，已保存内容支持批量清理 |
| 内置播放器 | 拖动进度、倍速、长按加速、双指缩放，左侧调亮度、右侧调音量，横滑快进快退 |
| 视频壁纸 | 静音循环播放，可调整镜像、速度、亮度、对比度、饱和度、色相、背景色及画面对齐 |
| 运动与交互 | 设备倾斜视差、桌面翻页跟随、图片图层参数，以及兼容场景的触摸 X-ray |
| 桌面滚动 | 自动按画面比例分段，结合启动器报告的页数，也可自定义 1～12 页；拖动跟随、松手平滑收尾 |
| 省电静态 | 可在系统省电模式下暂停为当前静态画面，退出省电后恢复播放与交互 |

下载文件保存在应用私有目录，不进入手机相册。登录会话使用 Android Keystore 加密保存，应用不保存 Steam 密码。

## 安装与开始使用

从 [Releases](https://github.com/XiongFangzhou/WallpaperMobile/releases) 下载 APK 并安装。已有同签名版本时，直接覆盖安装即可保留登录、收藏和已下载内容。

1. 打开应用，连接 Steam。可先使用公开浏览查看作品，下载时登录拥有 Wallpaper Engine 的账号并完成 Steam Guard 验证。
2. 在「发现」搜索、筛选壁纸，收藏喜欢的作品，或直接播放视频、发起下载。
3. 在「已保存」查看下载进度和本地内容，点击视频打开播放器，点击管理进行批量清理。
4. 长按已保存内容，选择「设为壁纸」，调整画面与交互设置，通过全屏预览确认效果后应用。

视频壁纸的运动模式可选择关闭、设备倾斜或桌面滚动。画面对齐提供 Cover（保持比例铺满）、Fill（拉伸填充）和 Free（自由构图）。省电静态开关也位于壁纸设置中。

首次启用动态壁纸时，跟随系统页面完成设置。桌面与锁屏的可选范围随 Android 版本和手机系统而异。使用桌面滚动时，如果启动器没有报告页数，可手动调整背景分段，匹配自己的桌面布局。

### 支持的内容

视频使用 Android 媒体解码；图片支持常见静态格式及 GIF / 动态 WebP。工坊场景会在下载时解析并转换为手机运行版本，支持基础图片图层、姿态视差和特定双图结构的触摸 X-ray。SceneScript、自定义着色器、复杂粒子等场景效果仍在逐步适配。

主线应用自带壁纸引擎。**Hydrogen** 是通过官方 Wallpaper Engine Android 导入和设置壁纸的归档版本，两种版本沿用相同包名。Hydrogen 已保存的官方 MPKG 可在主线重新下载适配。

## 从源码构建

目前提供 **Windows + PowerShell** 构建工具链，无需 Android Studio 或 Gradle。

准备：

- Python 3.10 或更新版本，可使用 `python` 命令。
- JDK 21，设置 `JAVA_HOME` 或 `WVB_JDK`，也可将 JDK 放入 `.tools/jdk`。
- 网络可访问 Android 工具下载站和 Maven 依赖仓库。

克隆仓库后，在仓库根目录运行：

```powershell
./Build-App.ps1
```

脚本会准备 Android SDK / Build Tools、恢复锁定的依赖、运行核心检查，并构建签名 APK。首次执行需要下载工具和依赖，后续使用项目内缓存。

生成的安装包位于 `dist/WallpaperMobile.apk` 和 `dist/WallpaperMobile-0.5.0.apk`。构建缓存、SDK、依赖和签名均保存在项目目录内。自行构建会使用自己的本地签名，后续更新请保留 `.tools/signing`。

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

## 开发与测试

`android/src` 包含 Android 界面、播放器、壁纸引擎及平台无关的 Steam 核心；`android/test` 为核心检查，`android/test-host` 为平台替身与 Android 原生图形检查。

```powershell
. ./Enter-Project.ps1
python tools/check_core.py
python tools/check_wallpaper_store.py
python tools/check_download_service_host.py
python tools/check_motion_host.py
```

Android 原生图形检查：

```powershell
python tools/prepare_android_graphics_tests.py
python tools/check_android_graphics.py dev.workshopvideo.PictureSettingsChecks dev.workshopvideo.SceneGraphicsChecks
```

外部工坊样本为可选测试输入，通过 `WVB_WORKSHOP_DIR` 配置；未配置时跳过对应样本用例。其余图形用例使用生成的测试素材。完整 Android 模拟器流程见 [Android 测试指南](docs/android-local-testing.md)。

## 项目文档

- [架构与目录](docs/architecture-options.md)
- [格式与壁纸效果](docs/multiformat-and-effects.md)
- [在线播放与下载管理](docs/streaming-and-download-controls.md)
- [权限说明](docs/android-permissions.md)
- [版本记录](docs/CHANGELOG.md)
- [贡献与 Git 工作流](docs/git-workflow.md)

第三方依赖与许可保留在 [THIRD_PARTY_NOTICES](android/assets/THIRD_PARTY_NOTICES.txt) 及 `android/assets/licenses`。WallpaperMobile 是独立项目，与 Valve、Steam 或 Wallpaper Engine 官方无隶属关系。
