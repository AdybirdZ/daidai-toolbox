# 简天气

原生 Android 简洁天气客户端，面向个人、非商业使用。独立实现界面与代码，参考天气战士的地区天气查询目标，不使用股票风格。

- Android 8.0+，Java，无第三方运行时依赖。
- 43 个内置常用地区；在线检索城市、区县与海外地区；收藏最多 20 个地区。
- 当前天气（模型估算）、未来 24 小时、7 日预报、湿度、风速、风向、紫外线和日出日落。
- 15 分钟缓存；失败时保留并标注旧数据；切换地区时丢弃不对应当前地区的返回结果。
- 仅网络权限，无广告、登录、分析 SDK、定位权限或自建服务器。
- Open-Meteo / GeoNames 数据署名及条款、隐私入口。

详细授权核查见 `docs/DATA-LICENSE.md`。源码采用 MIT 许可；天气和地区数据采用 CC BY 4.0。免费托管 API 的非商业限制仍需单独遵守。

## 构建 APK

安装 JDK 17 或更新版本、Python 3，以及 Google Android SDK 的 `platforms;android-35` 和 `build-tools;35.0.0`。无需 Gradle 即可构建：

```powershell
python build.py --sdk "C:\Android\Sdk" --out "build\SimpleWeather-1.0.0.apk"
```

macOS/Linux 同样可使用该脚本，但需安装对应平台 SDK 工具；本次实际构建环境为 Windows。路径含中文时脚本会向资源工具传递相对路径。

脚本依次执行资源编译、Java 编译、D8、zipalign、签名和签名验证。首次构建会创建 `.signing/development.jks`，密码为 Android 通用开发密码 `android`。**这只是本地侧载的开发签名，需自行妥善保存；应用商店发布请配置自己的发布签名。** 删除或更换密钥后，无法直接覆盖安装使用旧密钥签名的应用。不要公开或提交签名私钥。

也可以用 Android Studio 打开项目，安装 API 35 SDK 后使用项目的 Gradle 配置构建。项目指定 Android Gradle Plugin 8.7.3，适配 Gradle 8.9；未包含 Gradle wrapper。Android Studio/Gradle 构建路线未在本次环境验证，已验证的是上述 `build.py` 路线。

## 核心行为测试

下载测试专用 [JSON-java 20240303](https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar)，然后运行：

```powershell
python tests/run.py --json-jar "C:\path\json-20240303.jar"
```

Android 应用使用系统自带的 `org.json`；该 jar 只用于 JDK 测试，不打入 APK。测试覆盖城市坐标、同名城市校正、跨时区跨日、小时筛选、过期缓存、缺失值和请求节流。实际安装与界面验证见 `TESTING.md`。

## 主要文件

- `MainActivity.java`：天气页、地区页、收藏、请求状态和界面。
- `WeatherApi.java`：官方 HTTPS 请求、突发节流、429 暂停。
- `WeatherData.java`：数据解析、城市当地时间、缺失值及天气代码。
- `WeatherStore.java`：地区、收藏、缓存及持久化调用预算。
- `WeatherIcon.java`：独立绘制的天气图标。
- `app/src/main/assets/cities.json`：GeoNames 地区数据及展示名称整理。

以上 Java 文件均位于 `app/src/main/java/org/simpleweather/app/`。
