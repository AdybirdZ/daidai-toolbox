# 状态页 · 1.2.0

2026-10-28。定位栏目沿用 ID 2，改名“状态”，保留原栏目顺序、显隐、定位方式、请求间隔与持续定位设置。

## 范围与实现

全部界面、方向与距离逻辑独立实现，不使用闭源 GPS Status 的源码、资源、品牌图标或接口，没有加入其 SDK 或第三方运行时依赖。新增信息均由用户自己的 Android 设备通过官方 API 返回；不接入卫星目录、星历、轨道、地图瓦片或远程传感器服务，不增加应用网络请求或权限。原天气、地震、官方网页与系统定位服务的联网规则不变。

这是通用个人设备状态显示：显示本机民用接收信息，收藏个人地点并在本机计算直线方向；不发布测绘底图或地理信息服务，不测制地图，不传送基站改正数，不控制、干扰、发射或解密卫星信号，也不聚合上传坐标。没有外部卫星数据库的调用或再发布许可问题。没有把操作系统权限或公开 API 当成任何用途、任何地区的通用法律免责；本版限定上述用途。原有外部天气、地震数据许可继续单独适用。

本次查阅 Android 官方 GNSS、LocationManager、传感器与 BatteryManager 文档，依照其权限和生命周期要求实现。developer.android.com 此次连接失败，使用同属 Google 的 developer.android.google.cn 官方镜像核对；原响应与 SHA-256 仅留在本地研究目录。AOSP GnssStatus 源码的接口定义同时作范围核对，未复制其实现代码。没有把无法取得正文的政府网页当作已核实的法律许可。

## 指南针与方向

使用 TYPE_ROTATION_VECTOR；缺失时尝试加速度计和磁力计组合。按屏幕旋转 remapCoordinateSystem，读取设备上沿在水平面的方向，分为 16 方位（例如东南偏南）。默认磁北；真北选项依 Android GeomagneticField 与最近 WGS84 位置估算磁偏角，仍显示参考位置年龄。没有位置或方向源时明确等待；设备过于竖直时不假装有稳定朝向。显示系统精度、俯仰与横滚，提示远离磁场干扰、平放和 8 字校准。

方向做跨北点的短弧平滑，不把 359°→1° 绕南方旋转。屏幕图示约每 50ms 请求重绘，文字与其他状态每秒更新；传感器使用 SENSOR_DELAY_UI，不申请高采样率权限。具体速率取决于系统。

## 定位卫星

通过 GnssStatus.Callback 读取实际设备报告的星座类型、SVID、方位角、高度角、C/N0 与 usedInFix。明确支持美国 GPS、中国北斗、欧洲伽利略、俄罗斯 GLONASS，并如实标注设备还报告的 SBAS、QZSS、NavIC 或未知系统。系统类型编号取自 Android 官方常量。按星座与 SVID 去重，避免多信号重复计数。只显示设备接收到的集合，不声称是全部在轨或当地可见卫星。

必须主动点击开始及授予 ACCESS_FINE_LOCATION；近似位置不足以调用该接口。按钮单独请求 GPS_PROVIDER，原网络定位偏好保持。返回有效位置时存入原共享最近位置，可用于地震与收藏地点。卫星监听只在状态页前台；切页、锁屏或后台移除 GnssStatus 和 GPS 回调，清空旧卫星列表；与用户另行开启的持续位置前台服务区分。持续位置服务不会让此天空图在后台工作。无开机启动。

天空图北上东左，圆周高度 0°、中心 90°，方位角从真北顺时针。四系统使用不同颜色；实心是设备标注参与本次定位，空心是未参与，不等同于“故障”。可点选及展开参数。负高度角仍可在明细显示，地平线以下不画在天空中；无效角度不伪装成零。超过 15 秒无新卫星状态暂不绘制旧点。标签重合时仅尝试上下位置并隐藏冲突文字，明细仍可访问。模拟器卫星可能全部报告 0°，本应用如实显示，不编造分布。

## 地点与寻宝

单独的 status_places 私有存储保存最多 200 个地点：名称、双精度 WGS84 经度/纬度、ID、保存时间及目标 ID。允许手动输入或以最近定位预填并由用户确认，先经度后纬度、负数 W/S。支持选择、编辑和删除。清缓存与恢复设置保留地点，卸载应用删除；不上传、公开分享或导入未知来源地图。

采用半径 6371008.8m 的球面大圆地表距离与初始真北方位；不是路线距离，不计算障碍物或海拔差。最近位置、误差半径和年龄可见；无方向传感器时目标图真北向上，存在方向和位置时根据磁偏角转换，按当前屏幕上沿画相对目标点。外圈自动按目标距离调整，重合点不编造方位；没有“到达”的安全承诺。

## 其他设备状态

定位继续显示经纬度、水平误差半径和椭球高，补充可用的速度、移动方位、垂直误差；移动方位不是手机朝向。缺失字段如实注明。最近有效速度、方位与垂直误差随原位置在本机保留，带同一结果年龄。

前台显示陀螺仪三轴角速度 rad/s、含重力加速度、线性加速度、重力 m/s²、磁场 μT、气压 hPa、环境光 lx、距离 cm、环境温度及湿度；支持情况取决于设备。传感器轴按设备自然方向固定，方向盘另按显示旋转换算。不读取麦克风、摄像头或身体健康传感器。

电池来自 ACTION_BATTERY_CHANGED 与 BatteryManager：电量、状态、温度、电压、健康、供电、技术，以及系统提供时的瞬时电流和剩余电荷。未支持属性的 Integer.MIN_VALUE 不显示为零；剩余电荷不是设计容量。电池温度不冒充 CPU 或环境温度。设备信息包括厂商型号、Android/安全更新、内存、应用所在卷存储、开机时长与窗口尺寸，不读取序列号、IMEI、MAC、手机号或应用列表。

## 官方参考

- [GNSS 状态与星座常量](https://developer.android.google.cn/reference/android/location/GnssStatus)
- [LocationManager · 前台与精确定位要求](https://developer.android.google.cn/reference/android/location/LocationManager#registerGnssStatusCallback(android.location.GnssStatus.Callback,%20android.os.Handler))
- [传感器概览与前后台实践](https://developer.android.google.cn/develop/sensors-and-location/sensors/sensors_overview?hl=zh-cn)
- [方向传感器](https://developer.android.google.cn/develop/sensors-and-location/sensors/sensors_position?hl=zh-cn)
- [BatteryManager 字段和缺失值](https://developer.android.google.cn/reference/android/os/BatteryManager)

限制：真机磁场误差、接收芯片、系统网络位置服务、遮挡和电源管理影响实际效果。没有远程卫星位置兜底，也没有承诺所有手机同时报告四套星座。模拟器不能替代真机接收和精度验证。
