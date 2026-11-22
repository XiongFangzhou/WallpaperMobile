# WallpaperMobile：GitHub 现有实现与复用建议

核对日期：2026-11-22（Asia/Shanghai）。对象：当前 WallpaperMobile 0.3.8 的实际需求——WE 式工坊查询、手机独立下载/流式播放、私有保存/收藏/管理、视频动态壁纸、WE 场景/MPKG、X-ray、作者参数、陀螺仪/手势/桌面翻页、锁屏/桌面独立配置。

**结论：已有大量可复用工作，应优先评估现成场景引擎和 Android 视频壁纸实现。此次没有找到能直接替换整个项目、同时已经证明兼容 Xiaomi 15 Pro/HyperOS 上全部功能的单个开源项目。** 工坊下载、文件提取和场景渲染是三个不同层次，不能把“支持 PKG/MPKG”视作“完整运行原场景”。

本次实际检索 GitHub 仓库，阅读 README、许可证、仓库目录及选定核心源码；未编译运行这些第三方项目，未安装第三方 APK，未修改或部署 WallpaperMobile。下述“已实现”指上游文档或可见源码证据，不代表已在本机或用户手机实测。仓库是滚动开发状态，引用是此次读取版本；后续集成需固定版本。

## 1. 最接近完整产品的现有 Android 应用

### WallHub for Android

- 仓库：https://github.com/ChEnLeo-7/WallHub2.0-For-Android
- 主仓库许可证：MIT；本次 metadata 记录最后推送 2026-11-20。
- 相近功能：工坊搜索/筛选、预览/详情、个人订阅与收藏、下载管理、视频 Range 流式播放、PKG→MPKG 转换。
- 关键边界：README 明确写明转换后导入**官方 Wallpaper Engine Android 客户端**。它不是已提供完整自有 Android WE 场景运行时的证据，不直接解决我们“App 自己播放并设置交互场景”的目标。
- 已检查源码：`WorkshopQuerySemantics.kt`、`CachedSteamContentSdkRangeStream.kt`、`WorkshopConverter.kt`、`TexMobileConverter.kt`、AndroidManifest、Gradle 和 SDK 锁文件。转换器与纹理处理是可以继续逐文件审查的现成工作，远不止更换扩展名。
- 不能直接假定整仓库可按 README 一步重建：`app/build.gradle.kts` 依赖 `io.steamcontent.sdk:sc-content-android-preview:0.1.162`，注释称它是本地且 gitignored 的预览 AAR。该 SDK 独立仓库 README 明确说明尚未发布正式包，且最终 LICENSE/NOTICE 尚未提供，Apache-2.0 只是许可意向，不构成授权。
- SDK：https://github.com/ChEnLeo-7/Steam-Content-SDK
- 建议：参考查询、缓存/流读取、转换器的结构和测试；主仓库 MIT 文件与 SDK 分开处理。当前没有必要替换我们已工作的 JavaSteam 登录下载链路去追这个尚未定版的 SDK。

### WorkshopAndroidDownloader

- 仓库：https://github.com/Apricityx/WorkshopAndroidDownloader
- 许可证：Apache-2.0；最后推送 2026-11-10。
- Android 原生通用工坊下载器，分为 `steam-protocol`、`workshop-core`、`app`，包含账号、下载队列、暂停/继续/删除、Manifest/Chunk 组装和验证。
- 浏览部分 README 明确写的是 Steam Community HTML 抓取；不能据此保证与 PC WE 内置 QueryFiles 的筛选和顺序一致。
- 建议：适合借鉴下载任务状态机、重试/续传和模块划分；没有 WE 场景渲染引擎，也不是直接替换我们 WE 查询行为的选择。

### 其他高度相似项目

- https://github.com/weilund/we-workshop ：QueryFiles 查询、筛选记忆、GIF 预览、JavaSteam/CDN 下载；主要是单 Activity + WebView。GitHub 未识别到许可证，README 也含“WE 没有手机端”的错误描述。可比较功能和接口，不作为直接复制源码的优先候选。
- https://github.com/o12r/wallpaper-engine-android ：QueryFiles、Guard、队列、视频动态壁纸和裁剪编辑。README 明确承认 scene.pkg 只下载不渲染；GitHub 未识别许可证。与当前产品前半部分很接近，尚不能替代复杂场景能力。

## 2. 最值得复用的 WE 场景引擎

### WebWallGL：适合优先做小范围兼容性验证

- 仓库：https://github.com/oneincase/webwallgl
- 许可证：根 LICENSE 为 MIT；本次同时检查 `renderer/vendor/we-scene/LICENSE`，该子模块文件也为 MIT。第三方依赖/素材仍保留各自声明。
- WebGL2 场景内核；README 声明支持 scene.pkg、图层、效果链、粒子、模型、SceneScript、用户属性、音频以及指针输入。
- 本次可见源码并非只有展示页：含 `pkg/container.js`、`pkg/texture.js`、`scene/parse.js`、`render/renderer.js`、`render/effects.js`、`system-textures.js` 和 `scene-mount.ts`。
- 与当前问题直接相关：`bench/xray-shot.html` 有真实场景 X-ray 开/关与指针位置测试入口；`scene-mount.ts` 处理 `_rt_` 合成、halo_6 遮罩环绕等问题，比我们只保留两张图片的圆形揭示方案完整。
- 为什么值得考虑：有机会保留作者的实际效果链和参数，减少逐个硬编码特效、降级成静态图片图层的重复工作。已有解析和着色器工作也适合作为手机准入检查参考。
- 不是现成 Android 壁纸 SDK：此次目录未发现 AndroidManifest/Gradle 工程；公开包和示例主要面向浏览器/桌面。Android WebView 能运行 WebGL2，并不等于可直接把它绑到 WallpaperService.Surface；真实桌面输出、触摸/传感器桥接、后台生命周期、GPU 纹理/内存和耗电都需要宿主适配。
- 建议：先用现有真实 scene.pkg 样本做并排像素/交互验证，再验证手机预览与服务输出，避免直接重写整个 App。只有兼容性与移动端成本通过，才替换当前场景内核。未实测前不接受 README “全支持”作为准入结论。

### Open Wallpaper Engine：原生渲染长期候选

- 当前仓库：https://github.com/waywallen/open-wallpaper-engine
- 明确许可证：GPL-2.0-only；最后推送 2026-11-22。
- Vulkan 1.1 + 渲染图。功能清单包含图层/合成/文字、效果、视差、粒子、SceneScript、用户属性、骨骼/Puppet 与 3D；骨骼模拟、Morph 等仍有未完成项。
- `SceneViewer` 直接读取 scene.pkg，依赖 Wallpaper Engine 的 assets 目录。BUILD.md 要求 C++20 modules/Clang22、lito、Vulkan、GLFW 等，主要是 Linux 工程；不是放进 APK 就能用的 AAR。
- Android 移植需处理 NDK 工具链、ANativeWindow/Vulkan Surface、线程/资源与平台依赖，且复用 GPL-2.0-only 内核后的发布许可证/依赖组合要单独核对。
- 重要迁移信息：旧 https://github.com/catsout/wallpaper-scene-renderer 已归档，其 README 指向这个仓库。旧 KDE 插件 https://github.com/catsout/wallpaper-engine-kde-plugin 同样已迁移到 https://github.com/waywallen/waywallen 。Waywallen 管理器为 MIT，但场景插件 OWE 是 GPL-2.0-only，不能混为同一个许可证。
- 建议：若决定做长期原生 WE 兼容运行时，这是比从零实现所有效果更合适的候选；工程量明显大于解析器复用。

### linux-wallpaperengine：另一个原生参考

- 仓库：https://github.com/Almamu/linux-wallpaperengine
- 许可证：GPL-3.0；最后推送 2026-10-30。
- 包含 PKG/TEX、材质/效果、SceneScript/属性、图像/视频/网页等多类支持；用户属性和鼠标/视差是现成结构。
- 依赖 Linux 的 OpenGL、X11/Wayland、GLFW、libmpv、CEF、PulseAudio 等。CMake 和文档都表明不是 Android 工程，移动平台需要替换多处后端。
- 建议：作为解析器/效果行为对照或第二原生候选，不把 Linux 能播放当作 HyperOS 已兼容。

## 3. 视频动态壁纸：已有实现可减少生命周期试错

### UndeadWallpaper

- 仓库：https://github.com/maocide/UndeadWallpaper
- 许可证：GPL-3.0；最后推送 2026-11-17。
- ExoPlayer + OpenGL 视频壁纸，支持裁剪/缩放/偏移/旋转、色彩、桌面视差、独立媒体参数与播放列表。
- 源码直接对应我们遇到的稳定性问题：`EngineFSM.kt` 管理未初始化/初始化/就绪/释放等状态；`WallpaperPlayer.kt` 使用专用 HandlerThread、Media3 ExoPlayer、解码器 fallback、禁用音轨和异步释放；Service 处理 Surface 与可见性、首帧和恢复路径。
- 需分清：`onOffsetsChanged` 仍依赖启动器给出的 xOffset；没有从所查源码证明它能突破 HyperOS 私有页数或保证两屏独立壁纸。不能仅靠换到该项目就承诺我们的两个 OEM 问题消失。
- 建议：优先比较状态机/播放器架构。若实际复制或集成 GPL 代码并分发，需满足其许可证；如果暂不采用 GPL 组件，则保留需求和行为测试，选择宽松许可实现或自主实现接口。

### Alynx Live Wallpaper

- 仓库：https://github.com/AlynxZhou/alynx-live-wallpaper
- 许可证：Apache-2.0；最后推送 2023-07-03，维护较旧。
- 已检查 `GLWallpaperService.java`，确为 ExoPlayer + OpenGL、Surface/可见性生命周期、滑动与裁剪代码，README 解释了直接 MediaPlayer 播放的限制。
- 源码使用旧 `com.google.android.exoplayer2` 和 support API，不适合不经迁移就整套复制；应迁移到当前 AndroidX Media3，并验证新 Android/OEM 行为。
- 建议：宽松许可的原生视频壁纸参考。我们现有依赖已经包含 `androidx.media3:media3-exoplayer:1.5.1`，普通播放器也已在使用，桌面视频后端却仍是 MediaPlayer。因此可优先复用已引入的 Media3，再用成熟项目的架构作对照，避免同时维护两套播放器语义。

## 4. PKG / MPKG 解析：不要再从零扩大格式支持

### mpkg-convert

- 仓库：https://github.com/piaomiaozhishang/mpkg-convert
- 许可证：MIT；最后推送 2026-10-19。
- 原生 Kotlin 工具；核心目录包含 PkgReader/BinaryIO、TexAssetDecoder、LZ4、DXT、ETC2 CPU、视频提取及图片选择。
- README 说明支持 PKGV/PKGM、包内 MP4、TEX 视频以及 RGBA/R8/RG88、DXT、ETC2 等纹理。已检查 PkgReader 实现确认是目录偏移/条目读取结构，不只是按扩展名识别。
- 建议：优先逐模块评估 PKGM/TEX 和 ETC2 解码，沿用我们从发现下载后直接解析、私有保存的流程；无需把它的文件导入/相册导出 UI 一并搬来。
- 边界：提取现成 MP4/图片不等于把任意 PC 场景渲染成视频，更不会保留脚本/X-ray/陀螺仪交互。其 README 的广泛“全部支持”表述不能泛化到所有场景。

### RePKG 与 unmpkg

- https://github.com/notscuffed/repkg ：MIT，C#；成熟 PKG/TEX 解包和格式参考，适合离线生成验证样本。
- https://github.com/aqnya/unmpkg ：GPL-3.0，C++；另一个 MPKG 解码参考。
- 优先用 Android/Kotlin 的已有解码模块和 RePKG 做格式对照，不需要并行引入多个重复容器读取器。

## 5. 我建议的复用顺序

| 优先级 | 当前能力 | 建议 | 复用收益与剩余工作 |
|---|---|---|---|
| 1 | 视频桌面稳定性 | 以现有 Media3 为基础，比较 Alynx/Undead 的状态机、Surface 和异步释放 | 少维护一套播放器语义；HyperOS 必须继续真机验收 |
| 2 | WE 复杂场景 | 固定版本评估 WebWallGL；将 OWE 作为原生备选 | 有机会保留作者效果、脚本、材质；先验证样本和手机输出，不能仅凭网页演示决定 |
| 3 | PKGM/TEX/ETC2 | 评估 mpkg-convert，RePKG 作为离线对照 | 扩展格式准入，替换不必要的手写解码；仍不等于完整场景引擎 |
| 4 | 工坊流式/下载 | 保留 JavaSteam，借鉴 WallHub/WorkshopAndroidDownloader 已核对模块 | 登录下载已可用，无需为替换而改写；WallHub 的 SDK 许可/制品问题要先解决 |
| 保留 | 收藏/已保存/筛选记忆/私有文件、两屏业务配置、手机交互设置 | 保留当前产品层与验收用例 | 这些是用户已有的定制需求；不必为换底层重做全部 UI |

现有项目已经复用了 JavaSteam（MIT）和 AndroidX Media3（Apache-2.0）；不是整个系统都从零实现。最应该调整的是“继续扩充自制 WE 场景效果”的路线，以及“普通播放器与桌面播放器两套后端”的维护方式。

不建议只为了避免重复造轮子就整仓库换成 WallHub：它主要解决工坊客户端与导入官方播放器，而我们追求自有交互场景运行时。也不建议先花大量时间移植完整 Linux 桌面管理器；需要的是可嵌入渲染核心。

## 6. 后续验证应如何决定采用哪个引擎

当前调研不执行实现；后续应固定候选版本，使用同一组原始 scene.pkg（当前六份图片场景、三份 X-ray，加粒子、音频响应、用户属性、Puppet/脚本样本）对比：默认画面、参数生效、触摸像素差、纹理格式、缺失资源报错、切视频恢复、5 页动态偏移、锁屏/亮屏、帧时间和峰值内存。首先完整 AOSP 虚拟机，再由用户手动 HyperOS 测试；现成项目的 README 或星数不替代验收结果。

若采用网页渲染内核，预览成功只是第一步，真正 WallpaperService 输出及电量/温度更关键。若采用 OWE，先验证纯场景内核能在 Android 构建并接收 Surface，不把 Linux 窗口/CEF/音频栈整体塞入手机。

## 7. 本地证据

- 仓库搜索与 metadata：`docs/research/github-reuse-2026-11-22/`。
- `reviewed-source-index.json`：读取到的仓库信息、tree 标识和源码 SHA256。tree SHA 是树对象标识，未冒充 commit SHA。
- `source-manifest.json`：首批固定树对象读取的原始 URL 与校验和。
- 选定上游源码与许可证只保存在 `.cache/github-reuse/`；未加入 `android/src` 或发布 APK。
- GitHub 未认证搜索多次返回 403 限流，部分宽泛检索未完成；上述候选均有实际读取到的仓库/文档或源码，不宣称穷尽 GitHub 全部项目。
