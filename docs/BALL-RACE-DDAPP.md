# 小球竞赛本地测试宿主指南

## 当前状态

小球竞赛尚未加入正式工具箱宿主或商店。原型源码位于 `modules/prototypes/ball-race/`，普通宿主构建不会编译原型应用代码，也不会登记 `ballrace`。

本地测试宿主是单独构建目标，显示名称为“测试呆呆工具箱”，使用独立 Android 包名，并以本机开发密钥签名。它复用工具箱现有宿主运行时、签名 `.ddapp` 安装器和 `daidai-toolbox/api-1`，桌面只放置小球竞赛。不要把该 APK、应用包或密钥上传 GitHub，也不要登记到正式商店。

测试宿主以本地 3.0.5 宿主框架为构建基础。主菜单标题居中，三个大按钮纵向排列，分别进入开始比赛、编辑器和默认设置。默认跟随方式为跟随第一名，默认障碍弹性为 0.62、小球弹性为 0.68、球间碰撞开启、重力为 0.75g。赛前可选择参赛小球、随机候选关卡，以及设置比赛关卡数与统一物理参数。每个颜色最多加入一颗小球；比赛底部只有暂停、跟随设置和宿主桌面入口。障碍物与赛道边界始终参与碰撞，球间碰撞开关只影响小球彼此的碰撞。

## 目录和数据格式

- `modules/prototypes/ball-race/src/` 保存与 Android 分离的竞速模型及版本化关卡格式。
- `modules/prototypes/ball-race/app/` 保存本地测试用应用页面、物理实现和中英资源。
- `modules/prototypes/ball-race/app-test/` 和 `src/test/` 保存模型测试。
- `modules/prototypes/ball-race/examples/vertical-course.json` 是 `ball-race-course` 格式的示例关卡。
- `modules/prototypes/ball-race/test-host/` 保存测试宿主使用的名称、图标及资源覆盖。

关卡编辑器允许逐一修改并重命名五种内置模板。圆形障碍可添加、删除、拖动和调整半径；斜坡还可调整长度、厚度及旋转角度。修改以独立 JSON 保存到本机应用偏好，并会进入后续竞速；编辑页也可单独试跑所选模板。按名称跟随时，已到终点的小球会从实时领先目标中略过，改跟随尚未完成者中进度领先的小球。

关卡数据以 `ball-race-course` 和 `schemaVersion` 标识格式，按关卡保存世界尺寸、原点与旋转、入口/出口和连接关系。每个物块具有稳定 ID、类型、关卡内坐标、旋转角度、尺寸或顶点、颜色及碰撞参数。坐标与角度使用有限数值，解析时检查数量上限、重复 ID、越界物件和无效连接。

Java 包名 `org.daidai.modules.ballrace` 与 `org.daidai.prototypes.ballrace` 保持中性内部名称，不随应用显示名称改变。应用中英文显示名分别为“小球竞赛”和“Ball Race”。

## 构建和检查

从工作区根目录运行 `scripts/build.ps1` 构建普通宿主，运行 `scripts/build-ball-race-test.ps1` 构建测试宿主及本地 `.ddapp`。所有 APK 与 `.ddapp` 输出到 `work/builds/3.0.5-test-host/`。测试目标使用已存在的本机开发签名，不在仓库创建或保存密钥。

运行模型检查可使用：

```powershell
python modules/prototypes/ball-race/tests/run.py --json-jar tools/test-libs/json.jar
```

在已启动的隔离 Android 模拟器上运行宿主交互检查：

```powershell
scripts/test-ball-race-host.ps1
```

检查内容包括常规宿主构建、两个宿主包并存、测试宿主桌面仅显示小球竞赛、签名 `.ddapp` 安装、打开应用、运行竞速、返回桌面及再次打开。模拟器实测不能代替用户对目标设备布局、性能和触控表现的人工检查。

## 边界

本地测试宿主的名称、包名、资源、`ballrace` 注册和默认安装行为只在测试构建配置生效。它不改变正式宿主的应用列表、商店目录、默认应用、API 资源编号或宿主 Java 接口。测试源中的应用注册不表示正式集成。

未来正式接入时，先以目标宿主重新审阅 `AGENTS.md`、完整开发者准则和 `docs/MODULE-HOST.md`，再按当时版本的应用结构、签名与兼容条件评估迁移。不要将本地测试构建产物当作正式应用包。
