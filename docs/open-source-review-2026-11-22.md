# 第三方依赖与发布

应用使用 JavaSteam、Media3、OkHttp、Kotlin 运行时及相关编解码依赖。构建依赖及校验值记录在 `android/dependencies.lock.json`，安装包中保留 `THIRD_PARTY_NOTICES.txt` 和对应许可文本。

## 仓库内容

公开源码包含应用、通用构建脚本、测试夹具与文档。工具链、缓存、签名、登录信息、设备日志、个人参考图及下载的工坊作品由 `.gitignore` 排除。

## 发布产物

Android APK 通过 Releases 分发，附带版本说明及 SHA-256 摘要。源码快照与安装包使用相同版本号。

## 工坊内容

工坊作品由各自作者提供。应用通过 Steam 会话访问作品，用户使用自己的账号及资源权限下载内容。外部作品不包含在源码和测试素材中。

## 参考资料

- [JavaSteam](https://github.com/Longi94/JavaSteam)
- [Media3](https://github.com/androidx/media)
- [Steam Subscriber Agreement](https://store.steampowered.com/subscriber_agreement/)
- [Steam Workshop](https://steamcommunity.com/workshop/)
