# 多语言资源维护

`pre2.0.0-1` 建立了多语言的资源入口。默认中文在 `app/src/main/res/values/strings.xml`，英文入口在 `app/src/main/res/values-en/strings.xml`。Android 根据系统语言选择目录；缺少翻译的资源自动回退到默认中文。

新增语言时复制 `values-en` 为 Android 语言目录，例如日语使用 `values-ja`，保留资源键，只修改资源值：

```text
values/strings.xml       默认中文
values-en/strings.xml    英文
values-ja/strings.xml    日文（后续新增）
```

资源键是程序接口，不能改名。显示文字可以翻译，以下内容必须保持原样：

- SharedPreferences 键、JSON 字段、文件名和数据库标识；
- 数据源 ID、机构代码、URL、HTTP 字段和版本号；
- 课程、地点、收藏、路线和用户输入内容；
- 天体内部名称及迁移键。显示名称以后可以通过单独的显示资源翻译。

当前版本先迁移应用外壳、底部导航、天气栏目、常用操作和来源入口，其他页面仍使用 Java 中的中文并在英文环境回退显示。后续迁移一个页面时，应把完整句子放入资源，使用带编号占位符的格式字符串，避免把一句话拆成多个无法调整语序的片段。迁移后运行核心测试、原生 Android 测试和 APK 构建。

本预览版没有加入语言切换菜单，也没有引入在线翻译、第三方运行时依赖或新的网络权限。
