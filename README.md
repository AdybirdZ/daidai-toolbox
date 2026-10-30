# 呆呆工具箱工作区

今后统一在本目录开发。当前正式版为 **1.2.6 / versionCode 29**，已发布至 GitHub，唯一当前源码为 `source/`。

- [安装 1.2.6 APK](releases/1.2.6/DaidaiToolbox-1.2.6.apk) · [1.2.6 更新说明](releases/1.2.6/DaidaiToolbox-1.2.6-notes.md)
- [本地预览 pre2.0.0-1 APK](releases/pre2.0.0-1/DaidaiToolbox-pre2.0.0-1.apk) · [本地更新说明](releases/pre2.0.0-1/DaidaiToolbox-pre2.0.0-1-notes.md)（未上传 GitHub）
- [GitHub 发布页](https://github.com/AdybirdZ/daidai-toolbox/releases/tag/v1.2.6)
- [所有版本与下载索引](releases/README.md)
- [当前源码说明](source/README.md) · [更新记录](source/CHANGELOG.md) · [数据授权](source/docs/DATA-LICENSE.md)
- [目录与迁移说明](docs/目录与迁移说明.md)
- [GitHub 仓库](https://github.com/AdybirdZ/daidai-toolbox)

| 目录 | 用途 |
| --- | --- |
| `source/` | 唯一当前源码，保留原 Git 历史、远程仓库及全部版本标签 |
| `prototypes/` | 音乐、局域网传话、离线计算器三个独立原型；未合并、未上传，详见其 README |
| `releases/版本号/` | 各版本 APK、源码包、说明、校验文件；未上传版本使用本地交付校验值 |
| `scripts/` | 从本工作区直接运行的构建、测试、发布文件校验与模拟器入口 |
| `tools/` | Android SDK、模拟器数据、测试依赖、SDK 下载包 |
| `references/` | 天气战士、时光课程表参考仓库及数据/许可研究资料 |
| `docs/` | 工作区说明、资源迁移记录与验证结果 |
| `private/` | 本地签名备份，不能公开上传 |
| `work/` | 今后产生的临时 APK、日志和测试截图 |
| `archive/` | 整理前的旧源码、不同内容的同名文件、旧输出、旧发布脚本和测试资料 |

在本目录打开 PowerShell 后：

```powershell
# 运行核心测试
.\scripts\test.ps1

# 构建当前源码，输出到 work/builds/版本号/，不覆盖已发布 APK
.\scripts\build.ps1

# 核验所有归档的正式发布文件
python .\scripts\verify-releases.py

# 启动已有测试模拟器，日志输出到 work/logs/
.\scripts\start-emulator.ps1

# 查看当前源码状态
git -C .\source status
```

构建需要本机已有的 Python 与 JDK 17+。SDK 和测试 jar 已保存在 `tools/`。模拟器工具使用 `Q:` 映射到本工作区的 `tools/`，以避开 Android 模拟器的中文路径问题；启动脚本不会覆盖其他用途的 Q 盘映射。模拟器设备为 `emulator-5580`，结束测试可执行：

```powershell
.\tools\android-sdk\platform-tools\adb.exe -s emulator-5580 emu kill
```

1.2.4 已通过 545 项核心检查与 19 项原生 Android 检查并完成模拟器与原签名构建验证，见 [本版验证记录](source/TESTING.md)。此前文件夹整理的记录见 [迁移验证结果](docs/migration-2026-10-25/validation.json)。

签名继续使用 `source/.signing/development.jks`，本地备份在 `private/signing/`。不要生成新密钥替换它，否则新包不能覆盖已有安装。发布包保持原样；后续版本确认测试通过后另建版本目录。

工作区根目录不是第二个 Git 仓库。提交应用源码时使用 `git -C source …`，避免把 SDK、模拟器、签名和归档一起上传。

新增功能原型的本地入口：[原型说明](prototypes/README.md) · [音乐、计算器 0.1.0](work/prototypes/0.1.0/) · [传话 0.2.0（蓝牙 / 局域网）](work/prototypes/0.2.0/)。构建用 `python prototypes/build.py all`，核心测试用 `python prototypes/test.py`。原型不占用正式版版本号，正式工具箱的英语支持目前仅在规划中。


## 1.2.9 说明

本版新增全局文字与按钮配色、红色实体对准天空十字、无界面相机环境光采样及后台持续读取选项；整理状态页说明并规范甘孜藏族自治州名称与地区去重。正式版仅含中文资源，英文翻译保留在本地 pre2.0.0-1。
