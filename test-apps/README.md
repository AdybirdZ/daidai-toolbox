# 独立测试应用源码

本目录是本地工作区 `prototypes/` 生成的公开源码快照，包含以下三个独立应用。开发时先修改 `prototypes/`，验证后重新生成本快照，避免两处代码分别演变。来源文件和校验值记录在 `snapshot.json`。

| 应用 | 版本 | 包名 | 说明 |
| --- | --- | --- | --- |
| 呆呆音乐 | 0.7.0 | `org.daidai.prototype.music` | [音乐功能与限制](MUSIC-0.7.0.md) |
| 呆呆传话 | 0.2.0 | `org.daidai.prototype.messaging` | [传话功能与限制](MESSENGER-0.2.0.md) |
| 呆呆色觉模拟 | 0.2.0 | `org.daidai.prototype.colorvision` | [色觉模拟功能与限制](COLORVISION-0.2.0.md) |

三个应用要求 Android 8.0 及以上，可与正式工具箱同时安装。测试 APK 位于仓库根目录的 [`test-apk/`](../test-apk/)，与主程序分开。本快照包含应用所需的模块、启动入口、共享代码和资源，不依赖正式工具箱源码。它们尚未合并为工具箱内的软件安装功能。

音乐 0.7.0 增加四句同步歌词与字号、LRC 文件夹复制、定时停止、多项标签，并调整人员信息及曲库操作布局。播放、曲库、分析等内部类随同一个音乐应用交付，不是独立安装模块。未来安装契约见 [音乐模块架构](docs/MUSIC-ARCHITECTURE.md)；正式 3.0.0 宿主尚未发布。

## 本地构建

需要 Python 3.9 及以上、JDK 17 及以上，以及已安装 Android SDK Platform 35 和 Build-Tools 35.0.0 的本地 SDK。将 `javac` 加入 `PATH`；脚本不下载依赖。

在本目录执行，例如 Windows PowerShell：

```powershell
python build.py all --sdk C:\Android\Sdk --out-dir C:\Temp\daidai-test-apps
python build.py music --sdk C:\Android\Sdk --out-dir C:\Temp\daidai-test-apps
```

可单独选择 `music`、`messaging` 或 `colorvision`。`--sdk` 也可通过 `ANDROID_SDK_ROOT` 或 `ANDROID_HOME` 指定。默认输出目录为本目录下被 Git 忽略的 `build/`；APK 及各应用的构建校验 JSON 写入该目录。

未传入签名时，脚本仅在输出目录的 `.signing/development.jks` 生成开发签名，不读取或改写工具箱签名。开发签名不能覆盖签名不同的已安装应用。使用已有签名可传入：

```powershell
python build.py all --sdk C:\Android\Sdk --out-dir C:\Temp\daidai-test-apps --keystore C:\Keys\test-apps.jks --ks-alias test-apps
```

签名密码通过环境变量 `DAIDAI_TEST_APPS_STORE_PASSWORD` 和 `DAIDAI_TEST_APPS_KEY_PASSWORD` 提供；开发默认密码为 `android`，未单独设置密钥密码时使用存储密码。请保留自己的签名以便后续覆盖安装，勿提交私钥或密码。

## 核心测试

```powershell
python test.py --out-dir C:\Temp\daidai-test-apps-tests
```

核心测试只需 JDK，不使用 Android SDK、模拟器或手机。覆盖播放队列、消息加密和本机 TCP 通信、色彩变换、分屏几何、音频剪辑、WAV 倍速、内嵌标签解析、频谱历史时间轴及人员信息解析。这些检查不能替代真实设备上的相机、蓝牙、文件授权、系统解码器和后台播放验证。

## 许可与资料

新写应用代码和文档使用 [MIT 许可](LICENSE)。色觉模型所参考实现的 MIT 声明随源码与 APK 保留；音乐默认曲绘单独说明授权范围，不能将其视为采用 MIT 的通用素材。详见 [来源声明](NOTICE.md)、[隐私与许可](PRIVACY-LICENSE.md) 和 [技术边界](TECHNICAL-NOTES.md)。

[音乐更新记录](MUSIC-CHANGELOG.md)
