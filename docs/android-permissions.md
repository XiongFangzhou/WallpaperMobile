# Android 权限核对（WallpaperMobile 0.5.0）

| 功能 | 权限/系统要求 | 申请方式 |
| --- | --- | --- |
| 游戏旋转向量、旋转向量、加速度计 | 标准运动传感器；当前注册间隔 20,000 微秒，约 50 Hz | 无需 Android 运行时授权；无需 BODY_SENSORS / ACTIVITY_RECOGNITION |
| 高频运动传感器 | 超过 200 Hz 才需要声明 HIGH_SAMPLING_RATE_SENSORS | 本应用未使用超过 200 Hz 采样或直接通道，不声明该权限 |
| 触摸/桌面翻页事件 | 壁纸服务开启触摸与 offsets 接收 | 无额外运行时权限；是否传递事件由桌面/锁屏决定 |
| 壁纸服务绑定 | BIND_WALLPAPER 为服务的系统绑定保护权限 | 由系统壁纸管理器绑定，不能向用户申请成普通运行时权限 |
| 设置静态壁纸 | SET_WALLPAPER 为普通清单权限 | 安装时授予；系统是否允许设置仍需实时检查 |
| HyperOS 设置壁纸限制 | 厂商设置壁纸授权/系统确认 | 设置时发现缺少授权或系统拒绝入口后引导权限页面；不添加独立授权按钮，也不使用隐藏的直接设置 API |
| 后台下载 | INTERNET、ACCESS_NETWORK_STATE、FOREGROUND_SERVICE、FOREGROUND_SERVICE_DATA_SYNC | 清单声明，启动下载时使用前台数据同步服务 |
| 下载通知 | Android 13+ POST_NOTIFICATIONS | 下载时按需请求；拒绝不影响姿态传感器；进度仍可在已保存查看 |
| 本地保存 | 应用私有目录 | 不需要相册、全部文件访问、存储读写运行时权限 |
| 播放器屏幕亮度 | 只调整当前窗口亮度 | 不需要 WRITE_SETTINGS |
| 播放器音量 | MODIFY_AUDIO_SETTINGS 普通权限 | 清单声明，不弹运行时申请 |
| 省电模式静态画面 | PowerManager 状态及 ACTION_POWER_SAVE_MODE_CHANGED 广播 | 不需要新增权限；只暂停本应用渲染，不更换系统壁纸 |

运动输入优先选择 GAME_ROTATION_VECTOR，回退 ROTATION_VECTOR，再回退加速度计。清单把陀螺仪和加速度计声明为可选硬件，不强制没有这些硬件的设备无法安装。只有对应壁纸/预览可见且倾斜效果启用时才注册，隐藏后注销、清除等待回调及残留偏移。

0.5.0 启用“省电模式下暂停为静态画面”时，实际壁纸与预览都会注销姿态采样，退出省电后按可见性和倾斜开关恢复。视频运动模式互斥，不会因隐藏的旧交互配置继续注册传感器。没有可用姿态传感器时，视频菜单不提供设备倾斜选项。

捕获 registerListener 返回 false、厂商 SecurityException 等异常；成功注册后 2.5 秒仍未收到有效数据会提示检查系统传感器/隐私开关。不会为此申请身体传感器、位置或活动识别权限；这些权限无法解锁标准旋转向量传感器。系统如果仍发送零值等替代数据，本应用不能仅凭注册返回值证明物理传感器未被系统屏蔽。

当前 17 项主机检查直接运行正式 MotionInput，验证采样率、注册拒绝、SecurityException、无数据、传感器回退、重复注册、角度过界及隐藏注销等行为。主机中使用传感器替身，不代表在 Xiaomi 15 Pro / HyperOS 上采集并验证了真实数据。本轮 adb 未检测到连接设备，未修改手机权限或操作界面。

依据：

- Android 官方 SensorManager 源码：https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/hardware/SensorManager.java 。本轮已读取并留存 `research/mobile-capabilities/SensorManager-permissions.java`，类注释明确说明大于 200 Hz 的高频权限要求。
- Android 运动传感器文档：https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion 。本轮该网页连接超时；不声称已成功读取最新网页。
- Android 传感器总览：https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview 。普通运动与步数/身体传感器权限需区分。
