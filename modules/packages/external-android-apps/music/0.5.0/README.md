# 呆呆音乐 0.5.0 可执行模块

此目录提供 `music-0.5.0.ddapp`、模块清单、配置契约和校验结果。容器包含独立音乐 APK，包名为 `org.daidai.prototype.music`，版本为 0.5.0 / versionCode 5，最低 Android 版本为 8.0。

当前工具箱 2.2.0 没有模块安装功能，正式 3.0.0 宿主尚未发布。未来宿主采用 `daidai-android-app/1` 后，可校验容器并通过 Android 系统请求安装、打开或卸载。当前单独使用请安装[独立测试 APK](../../../../../test-apk/DaidaiMusic-0.5.0.apk)，不能将 `.ddapp` 当作 APK 直接安装。

音乐在独立 Android 进程中运行，拥有自己的私有资料、通知权限与歌曲文件夹授权。容器不包含用户资料，跨包文件夹 URI 授权不能自动转移，完整音乐资料导入导出桥接尚未实现。安装模块不代表音乐界面已经嵌入工具箱。

`module.json` 与 `configuration-contract.json` 是容器内对应文件的原样副本；`index.json` 列出本版本容器和载荷身份；`verification.json` 记录实际签名及容器校验，`SHA256SUMS.txt` 用于文件完整性核对。容器中的 APK 与本版本独立测试 APK 字节一致，未使用完整工具箱 APK 充作音乐模块。

详细说明见[模块契约](../../../../../test-apps/module-packages/README.md)和[音乐架构](../../../../../test-apps/docs/MUSIC-ARCHITECTURE.md)。校验源码与异常容器测试在公开测试源码的 `module-packages/` 目录。离线校验不证明 GitHub 已完成上传，也不替代正式宿主安装验收。
