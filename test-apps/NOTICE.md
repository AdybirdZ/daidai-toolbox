# 来源与许可声明

三个测试应用的新写代码和文档采用本目录的 `LICENSE`（MIT）。它们使用 Android / Java 系统 API，无新增第三方运行时依赖。用户自行导入的音频、歌词、曲绘、图片与聊天内容不因本应用而改变版权或使用范围。

## 呆呆色觉模拟

采用 Machado、Oliveira、Fernandes（2009）的色觉缺陷近似模型，使用严重程度为 1 的三组线性 sRGB 变换矩阵。论文：*A Physiologically-based Model for Simulation of Color Vision Deficiency*，IEEE TVCG 15(6)，DOI [10.1109/TVCG.2009.113](https://doi.org/10.1109/TVCG.2009.113)。未复制论文图像。

矩阵参考 [DaltonLens-Python](https://github.com/DaltonLens/DaltonLens-Python) 的 MIT 许可实现；完整声明保留在 `modules/colorvision/assets/licenses/DaltonLens-MIT.txt` 并随 APK 分发。模型只提供近似模拟，不能用于诊断。

## 呆呆音乐默认曲绘

`modules/music/res/drawable-nodpi/music_default_cover.png` 是用户提供并指定的默认曲绘，按其要求随音乐测试 APK 使用。用户提供图片及要求公开安装包，不等于向第三方授予图片的通用开源许可；本目录 MIT 代码许可不延伸至该图片。具体说明见 `modules/music/ASSETS.md`。

## 系统组件

Android SDK、JDK、系统解码器和系统字体不包含在本源码快照内，各自按其许可和设备实现提供。
