# 天文星表与星图说明 · 3.0.5

## 新增恒星

| 显示名称 | HIP | 星表名称 | 赤经 | 赤纬 | V 波段视星等 |
| --- | ---: | --- | ---: | ---: | ---: |
| 五车一 | 23015 | Iota Aurigae | 74.24843701788° | +33.16601473369° | 2.69 |
| 壁宿一 | 1067 | Gamma Pegasi | 3.30897038983° | +15.18355073109° | 2.84 |

HIP 86032（蛇夫座 α 星）的中文传统名称为“侯”；勾陈一显示为规范星名，内部恒星身份与显隐设置键保持不变。

## 数据来源与许可

中文星名及其 HIP 对应关系取自 Stellarium 中国星空文化文件，固定提交 `568d88df8126c45949e19564fe312de577340e5c`，来源文件为 `skycultures/chinese/star_names.zh_CN.fab`，该文化资料标注 CC BY-SA 4.0。HIP 86032 对应“侯”。

赤经、赤纬和 V 波段视星等读取自同一提交的 Stellarium HIP–Gaia3 星表。星表格式和 HIP 字段说明见 `guide/app_star_catalogue.tex`；其数据说明追溯至 ESA Hipparcos 与 Gaia。此处只打包恒星识别和数值资料，不包含 Stellarium 程序、二进制星表文件或图像，也不在运行时访问 Stellarium。

赤经、赤纬和视星等是所引星表的固定历元值。星图不对本版星点计算自行运动或大气折射，坐标只用于本地近似观星示意。选中天体的升落时间按当地日期近似计算；恒星升落按几何地平线判断，不模拟地形遮挡和大气折射。太阳、月球采用现有视半径与地平线近似，行星位置仍使用本地低精度模型。

完整对象字段、来源版本和显示行为见 [JSON 记录](ASTRONOMY-3.0.5.json)。许可与署名同时列于 `NOTICE.md` 和天文应用包内的第三方数据声明。

## 来源链接

- [Stellarium 中文星名资料](https://github.com/Stellarium/stellarium/blob/568d88df8126c45949e19564fe312de577340e5c/skycultures/chinese/star_names.zh_CN.fab)
- [Stellarium HIP–Gaia3 星表格式与来源](https://github.com/Stellarium/stellarium/blob/568d88df8126c45949e19564fe312de577340e5c/guide/app_star_catalogue.tex)
- [Stellarium 数据署名](https://github.com/Stellarium/stellarium/blob/568d88df8126c45949e19564fe312de577340e5c/CREDITS.md)
- [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)
