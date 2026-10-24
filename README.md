# 呆呆工具箱 1.0.0

原生 Android 个人非商业工具箱，Android 8.0+，Java，无第三方运行时依赖。页脚：呆呆鸟使用Codex制作。

- 天气：地区搜索、43 个常用地区、收藏、当前模型估算、24 小时与 7 日预报、15 分钟缓存及失败回退。
- 官方天气图：全国降水、气温、全国及七个区域雷达，通过应用内完整官方网页查看，保留原站版权、图例、发布时间，不下载重发图片。
- 天文：观星天气条件、亮星示意、日月位置、月相、升落与曙暮光、流星雨常年参考、NOAA 极光原页入口。本地天文计算为近似结果。
- 课程表：手动新增、修改、删除，课程名、地点、老师，学期、周次、单双周、自定义上下课时间、周课表与今日课程。支持冲突检查，不含教务导入或桌面小组件。
- GPS：前台读取 WGS84 经纬度、水平精度和海拔，切页或进入后台停止更新，不上传坐标。
- 手机图片背景及清晰度遮罩，固定天气 / 课程表 / GPS 导航，每版本更新记录。

首次使用 GPS 才申请定位权限。图片使用系统文件选择器，无存储读取权限。课程、背景、收藏和缓存保存在手机内部，无账号、广告、统计 SDK 或自建服务器。官方网页适用各自隐私规则。天气查询向 Open-Meteo 发送所选城市坐标，GPS 不自动更改天气城市。

授权与隐私见 `docs/DATA-LICENSE.md`，版本内容见 `CHANGELOG.md`，验证见 `TESTING.md`。新写客户端代码为 MIT，数据许可单独保留。

## 安装与升级

安装 `DaidaiToolbox-1.0.0.apk`。包名保持 `org.simpleweather.app`，versionCode 从 1 增至 2，沿用原开发签名，支持覆盖旧包并保留地区与收藏。旧包原标 1.0.0，现统一归为 `0.1.0-test` 测试版；重新归档的测试 APK 已修正版本及名称。

使用本机开发签名，适合个人侧载。保存签名私钥才能继续覆盖升级；源码压缩包不含私钥，重新构建生成的不同密钥不能覆盖本次 APK。不是已提交商店的发行包。

## 构建

JDK 17+、Python 3、Google Android SDK `platforms;android-35` 和 `build-tools;35.0.0`：

```powershell
python build.py --sdk C:/Android/Sdk --out build/DaidaiToolbox-1.0.0.apk
```

脚本执行 aapt2、javac、D8、zipalign、apksigner。首次创建 `.signing/development.jks`，开发密码为 `android`，勿公开私钥。Windows 含中文路径已验证。macOS/Linux 需对应平台 SDK，未实测。Android Studio 可打开 Gradle 配置（AGP 8.7.3 / Gradle 8.9，未附 wrapper），本次只验证脚本构建。

## 核心测试

测试专用 [JSON-java 20240303](https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar)，不打入 APK：

```powershell
python tests/run.py --json-jar C:/path/json-20240303.jar
```

天气、地区、时区、缓存和节流 41 项，课程模型及天文 32 项，共 73 项。Java 源码在 `app/src/main/java/org/simpleweather/app/`，主要模块为 MainActivity、WeatherApi / Data / Store、ScheduleModel / Screen、GpsScreen、Astronomy / Screen、OfficialPageActivity。
