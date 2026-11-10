# 呆呆音乐应用模块契约

本目录公开音乐模块的打包器 `module_tools.py`、异常容器测试 `test_module_package.py` 和契约说明，不包含测试宿主、模块安装器或适配器界面。正式工具箱 2.2.0 没有模块安装功能，正式 3.0.0 宿主尚未发布。

## 可执行模块与独立 APK

[音乐 0.5.0 模块目录](../../modules/packages/external-android-apps/music/0.5.0/)提供 `music-0.5.0.ddapp`、清单、配置契约、索引和校验结果。`.ddapp` 是 ZIP 容器，包含四项：`module.json`、`configuration-contract.json`、`README.md`、`payload/music.apk`。载荷是音乐应用本身，不是完整工具箱 APK。

音乐模块 ID 固定为 `music`，包名为 `org.daidai.prototype.music`，公开入口为 `org.daidai.prototype.music.MainActivity`，当前版本为 0.5.0 / versionCode 5，最低 Android 版本为 8.0（API 26）。`standaloneInstallable: true` 表示其中的 APK 可以由 Android 单独安装，不表示当前工具箱可以安装 `.ddapp`。当前单独使用请安装[音乐测试 APK](../../test-apk/DaidaiMusic-0.5.0.apk)。

未来宿主采用 `daidai-android-app/1` 后，可负责校验、请求系统安装、展示入口、打开和卸载。音乐在独立 Android 包与进程中运行，由系统切换到音乐界面；本契约没有实现向工具箱进程动态加载代码或嵌入音乐界面。

## 功能与资料边界

0.5.0 支持播放、我的歌曲和设置三个栏目，以及曲库、歌单、收藏、分类搜索、歌词、BPM、多段剪辑、倍速与音调、应用内音量上限、时域波形、X-Y 示波图和频谱分析。曲绘可预览、缩放和保存。详情见[音乐架构说明](../docs/MUSIC-ARCHITECTURE.md)。

歌曲导入只复制到用户选择的存储文件夹；文件已经在该文件夹内时不重复复制。音频文件内实际存在的曲绘、BPM、作曲、编曲和艺术家信息会被读取，常见艺术家字段映射到“演唱者”。电脑播放器私有数据库中的字段不会随单独复制音频自动迁移。

音乐自行管理设置、曲库记录、播放列表、歌单、收藏、歌词和曲绘。模块包不包含用户资料，完整音乐资料导入导出桥接尚未实现。Android 文件夹 URI 授权和私有文件路径不能通过复制字符串跨包转移；未来换包名或合并进宿主时，需要用户重新授权或另外实现经校验的资料转移流程。

相同包名和签名升级通常保留应用资料；撤销授权、文件提供方变化或系统清理仍可能导致文件不可访问。卸载音乐清除其私有资料与授权，宿主不应随隐藏入口或卸载主动删除用户存储文件夹中的歌曲。

音乐仅声明唤醒锁、媒体播放前台服务和通知权限，通过系统文件选择器获得指定文件访问权，无网络、录音或全盘存储权限。Android 10 及以上通过 MediaStore 将曲绘保存到 `Pictures/呆呆音乐`；Android 8～9 通过系统文件选择器选定保存位置。

## 校验与打包

需要 Python 3、JDK，以及含 Build Tools 35.0.0 的 Windows Android SDK。以下命令从仓库根目录运行，SDK 使用自己的安装位置：

```powershell
$env:ANDROID_SDK_ROOT = 'C:\Android\Sdk'
python test-apps/module-packages/module_tools.py verify modules/packages/external-android-apps/music/0.5.0/music-0.5.0.ddapp
python test-apps/module-packages/test_module_package.py
python test-apps/module-packages/module_tools.py pack --apk test-apk/DaidaiMusic-0.5.0.apk --output C:\Temp\music-0.5.0.ddapp
```

工具从相邻 `launchers/music/AndroidManifest.xml` 读取版本，优先使用本地构建，找不到时使用仓库 `test-apk/` 的音乐 APK。公开模块包位于 `modules/packages/external-android-apps/music/0.5.0/`，无需原开发工作区的私有资料或 SDK 目录。`pack` 未指定 `--output` 时写入仓库下的 `work/prototypes/music-0.5.0/`，不会覆盖公开模块目录。

校验核对容器路径、条目、大小、清单、配置边界、APK 身份、实际权限、SHA-256 和签名。证书指纹固定为已发布音乐测试应用的证书，自行更换签名的 APK 不属于该信任链；未来宿主也必须从自身可信目录取得指纹，不能信任包内自报值。

离线校验结果不表示 GitHub 上传成功，也不能代替尚未发布的正式 3.0.0 宿主安装验收。包名、签名、载荷、校验结果与实际资产应在发布时继续复核。
