# HyperOS 桌面滚动分析

## 结论

桌面没有发送壁纸偏移，不能直接推断为第三方应用无法获得真实翻页信息。所检查的 HyperOS 桌面包含原生壁纸偏移发送路径，但发送前会检查系统滚动开关。应先验证这条路径，再考虑复制手势规则或增加运行依赖。

这次分析使用 USB 只读调试、已安装桌面程序的符号及指令，并与公开实现交叉核对。没有接入 Shizuku SDK，没有运行时辅助程序，也未自动操作手机界面。

## 原生偏移路径

检查的桌面版本为 `RELEASE-8.01.02.8604-260918-10282146-R`，Flutter 标记为 `3.38.3-mi-1.17.24-beta1`。安装包采用 ARM64 编译实现，其压缩调试符号保留了相关函数名称。

已定位以下路径：

| 函数 | 指令分析结果 |
| --- | --- |
| `WallpaperGetxController._registerWallpaperScrollableObserver` | 注册系统设置观察者，并先读取滚动状态 |
| `WallpaperGetxController._updateWallpaperScrollable` | 读取整数设置，以值 `1` 表示允许滚动 |
| `WorkspaceGetxController._canWallpaperScrolled` | 获取上述状态，供偏移发送路径检查 |
| `WorkspaceGetxController._updateWallpaperOffsetSteps` | 多页时计算 `1 / (screenCount - 1)`，调用原生壁纸分段接口 |
| `WorkspaceGetxController._updateWallpaperOffset` | 计算 `page / (screenCount - 1)`，限制到 `[0, 1]` 后调用原生壁纸偏移接口 |

对应设置项为：

```text
namespace: secure
key: pref_key_wallpaper_screen_scrolled_span
enabled: 1
```

只读检查时该项为 `0`，与应用接收到固定偏移且没有有效页数步长的现象一致。

## 真机验证结果

2026-11-23，经设备使用者明确允许，仅将上述设置由 `0` 改为 `1`，原值保存在本地还原记录中。

- 桌面日志报告 `rawValue=1, scrollable=true`。
- 应用收到变化的原生偏移，包括页间中间值；无需辅助无障碍事件。
- 手势兼容保持关闭，辅助服务没有收到有效位置，确认跟随来自原生壁纸回调。
- 使用者手动测试普通翻页、短滑和回拖，反馈背景能够较好跟随。
- 数分钟测试及当前壁纸配置更新期间，系统开关保持为 `1`；未强制停止任何进程，也未自动操作桌面。

原生步长在这次切换后仍可能为 `-1`，但规范化偏移本身已经包含桌面总页数的换算，可以直接驱动全幅背景。程序同时修正了小于 `0.0005` 的逐帧变化被丢弃的问题，避免高刷新率回弹末段停住。

当前结果覆盖本次桌面版本及测试会话。系统重启、系统升级或其他壁纸软件是否重置开关，应另行验证。

## 手势规则

符号中存在标准 `PageScrollPhysics`，也存在 `NoSwipeRightAtFirstPagePhysics` 的目标选择、反向甩动、单页运动、回弹及边界处理函数。标准分页函数会按速度方向调整半页后取整，但不能把这一条通用规则等同于桌面全部行为；自定义路径还需要核对生效条件和参数。

即使复制目标选择与动画规则，应用未收到的手势、桌面内部状态和程序触发的切页仍会影响结果。若原生偏移路径可用，应优先使用系统报告的位置；手势估算保留为兼容方式。

## 验证工具

以下命令只读取设置：

```powershell
python tools/probe_launcher_scroll.py --serial <device-serial>
```

在设备使用者明确允许后，可单独开启这一项：

```powershell
python tools/probe_launcher_scroll.py --serial <device-serial> --enable
```

工具在修改前将原值保存到被忽略的缓存目录。随后由使用者滑动桌面，通过应用的 `WMWallpaper` 日志检查偏移是否连续变化、分段步长是否有效。工具不会重启进程、更换壁纸或发送触摸。

还原本次修改：

```powershell
python tools/probe_launcher_scroll.py --serial <device-serial> --restore .cache/launcher-scroll-probe/receipt.json
```

开启的是系统设置，不是向普通应用授予可弹窗申请的权限。若系统保留该值，应用之后接收标准壁纸偏移不需要 Shizuku；若系统重置，应另外评估设置入口或一次性授权方案。

## 交叉核对来源

- [HyperOS3ScrollSetter 对相同设置项的使用](https://github.com/BlizzardAn225/HyperOS3ScrollSetter/blob/a2ac07937270f8f64403f341e1825575c734ff3a/app/src/main/java/com/blizzard225/wallpapersetter/WallpaperCommands.kt)。该项目包含反复写入和进程处理；本次验证工具只修改单个设置项，不执行这些额外操作。
- [MIUI 壁纸控制实现中的设置项与启用值](https://github.com/chrmhoffmann/miui-services/blob/fba6b27f43ee39c1a5d5a62d1dcfb45246eddb27/sources/com/android/server/wm/WallpaperControllerImpl.java)。这是其他版本的公开实现，仅作为交叉证据，当前桌面路径以本次符号和指令分析为依据。
- [Android 无障碍事件分发实现](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/services/accessibility/java/com/android/server/accessibility/AbstractAccessibilityServiceConnection.java)。敏感事件的分发限制与标准壁纸偏移是两条不同路径。

原始安装包、完整符号、反汇编和设备记录保存在被忽略的本地研究目录，不随源码或发布文件分发。
