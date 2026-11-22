# WallpaperMobile 手机壁纸调研（2026-11-22）

目标设备：Xiaomi 15 Pro / HyperOS。已核对 Android 15 AOSP 源码与本地 Android 35 SDK 的公开接口；未连接该手机，HyperOS 的屏幕选择选项与桌面偏移仍需真机确认。

| 问题 | 已核实的 Android 行为 | 0.2.0 实现 |
| --- | --- | --- |
| 视频格式能否直接作为壁纸文件 | 标准静态壁纸入口接收位图／图片流；视频需由 WallpaperService 绘制，不是把 MP4 文件交给 setBitmap | 视频静音循环动态壁纸；另可提取首帧设置静态壁纸 |
| MP4、WebM、MKV、MOV、AVI | 扩展名不能证明可播放；容器、轨道编码、profile、分辨率及手机解码器共同决定 | 检查 MediaExtractor 视频轨道、MediaCodecList decoder、MediaPlayer.prepare 与实际首帧解码，再允许设置。MP4/H.264 为优先选项；其他格式逐文件检查，不保证所有文件 |
| GIF／动态 WebP | 属于动画图片，不是当前的视频解码输入；工坊预览也不等同于作品源文件 | 继续在浏览页播放动画预览；不把预览 GIF 当作下载的视频壁纸 |
| WE Scene／Web／Application | 需要 WE 或网页／应用渲染运行时，不能视为现成视频 | 不转换，不提供直接视频壁纸设置 |
| 锁屏和桌面分别设置静态图 | WallpaperManager.setBitmap(bitmap, crop, allowBackup, which) 接受 FLAG_SYSTEM、FLAG_LOCK 或二者组合 | 静态首帧提供仅桌面、仅锁屏和两者。可给两个屏幕先后设置不同静态首帧 |
| 锁屏和桌面分别设置动态视频 | 按目标屏幕直接设置动态组件的接口在 AOSP 中标记为 SystemApi / hide，需要 SET_WALLPAPER_COMPONENT，普通 App 不应调用；普通 App 使用公开系统预览 Intent | 进入 ACTION_CHANGE_LIVE_WALLPAPER，由系统预览页确认范围。不能保证 HyperOS 支持“仅锁屏”或两屏不同视频，本版使用一个视频配置，未实现分别绑定两个不同动态视频 |
| 桌面滑动效果 | Engine.onOffsetsChanged 来自启动器的 setWallpaperOffsets，启动器不发送偏移时 App 不能凭空获得翻页位置 | 铺满模式下对横向多余区域左右取景；完整显示不滑动，比例相同时不虚构多余画面。HyperOS 默认桌面是否发送偏移待验证 |
| 画面适配 | 系统 Surface 比例可能与视频不同 | EGL / 外部纹理按比例绘制；铺满居中裁剪，或完整显示黑边补齐，响应显示区域变化 |
| 声音、后台和耗电 | 系统管理动态壁纸 Engine 可见性；多个 Engine 可同时存在，如预览、桌面、锁屏 | 静音，不抢音频焦点；不可见暂停；Surface 销毁释放解码器和 EGL。高分辨率视频仍可能增加耗电 |
| 删除源视频后壁纸怎么办 | WallpaperService 长期需要可读取的视频；库删除不能使已应用壁纸失效 | 使用独立私有副本，取消预览不替换原活动配置；库删除不删壁纸副本；两屏均不使用本服务时可在壁纸设置页清理。清除数据／卸载仍会删除副本 |

## 核查依据

- [Android 15 AOSP WallpaperManager.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/app/WallpaperManager.java)：ACTION_CHANGE_LIVE_WALLPAPER 与组件 extra；setBitmap 的 which；setWallpaperComponentWithFlags 的 SystemApi 与系统权限。
- [Android 15 AOSP WallpaperService.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/service/wallpaper/WallpaperService.java)：Engine.onOffsetsChanged、可见性、Surface 生命周期及 getWallpaperFlags。
- 本地 Android API 35 android.jar：使用 javap 核对公开 WallpaperManager / WallpaperService.Engine 方法；getWallpaperInfo(which) 在 API 34+ 可用于检查锁屏，清理时必须同时检查桌面和锁屏。
- developer.android.com 的格式与参考网页本次请求超时，因此没有把未读网页当作核实证据。所获取的小米支持链接未返回可核对的相关正文，也未用来断言 HyperOS 已支持某项功能。

## 验证范围

核心检查覆盖收藏按工坊 ID 的独立性、增删、无符号编号、壁纸文件副本及取消预览清理、随机比例裁剪／适配。它们不能代替 Android MediaPlayer/EGL 的真实解码、系统设置确认结果与 HyperOS 桌面/锁屏行为。0.2.0 必须在目标手机复测系统预览、静态目标屏幕、同组件更换视频、旋转、息屏恢复及桌面翻页。


## 0.2.2 恢复原壁纸的边界

Android 15 AOSP WallpaperManager.java 中 THROW_ON_SECURITY_EXCEPTION（target API 34 起）及 getCurrentWallpaperLocked 的权限处理明确限制读取当前壁纸位图／文件；普通 App 没有 READ_WALLPAPER_INTERNAL。不能把 API 返回的系统默认图当成用户的原图备份。本版不申请全文件访问权限，也不把 clear() 当成“恢复原壁纸”（clear 会改成系统默认）。

应用内自有视频保留一个 previous 配置及独立文件，回退前重新验证媒体及绘制，并在当前系统选用本服务时恢复。其他动态壁纸只记录组件，恢复操作交给其系统预览；原静态图因无法合法读取而需用户在系统重新选择。

取消系统预览与撤销已经应用的壁纸是两件事。前者不提交新 active 配置；后者只能对已保留的原视频提供回退。对缺失或含糊的 OEM 返回码，保留 pending 副本并核对当前组件与壁纸编号，避免因误判取消而删除已被系统使用的文件。


## 0.2.3 系统回执与诊断边界

用户设备报告 Xiaomi 2410DPN6CC / Android 17 API 37。0.2.2 提交 ACTION_CHANGE_LIVE_WALLPAPER 后，结果码为 RESULT_CANCELED、系统未选用本组件且没有服务阶段证据。记录差 729 ms 包含固定的 700 ms 延迟核对；它不足以区分系统入口不支持、代理转发／独立任务提前取消回执、服务发现失败和用户未确认，也不能说明编码失败。本次尝试补取 AOSP LivePicker 的 LiveWallpaperChange/Preview 实现超时，未将未读源码当作验证依据。

0.2.3 使用公开 ACTION_LIVE_WALLPAPER_CHOOSER 作为显式诊断入口，用户在系统列表选择本服务并确认；不调用隐藏的 setWallpaperComponent 接口，不要求 root。系统直达入口和列表入口的存在、服务元数据解析、入口权限及 launchMode 都在诊断中记录；系统回执后等待应用恢复前台焦点再核对选用组件和真实服务首帧。USB 诊断脚本读取实际系统日志与 dumpsys，用以定位 OEM 拒绝、任务路由或服务启动异常。仍需真实设备证据才能确认根因。
