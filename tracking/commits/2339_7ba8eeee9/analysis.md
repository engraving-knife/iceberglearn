# 提交 2339：Build: Bump JUnit5 from 5.12.2 to 5.13.2 (#13280)

## 提交信息

- **序号**：2339 / 4088
- **哈希**：7ba8eeee9b0277b2d34ed0cd5968971d74fff736
- **短哈希**：7ba8eeee9
- **日期**：2025-07-11 08:10:54 +0200
- **作者**：Raveendra Pujari
- **提交说明**：Build: Bump JUnit5 from 5.12.2 to 5.13.2 (#13280)
- **PR/Issue**：#13280

## 总体目的

本提交将 Iceberg 项目的 JUnit 5 测试框架从 5.12.2 升级到 5.13.2，同时将 JUnit Platform 从 1.12.2 升级到 1.13.2。

JUnit 5 是 Iceberg 项目使用的核心测试框架。定期升级测试框架版本可以获取 bug 修复、新功能和性能改进。JUnit Platform 版本需要与 JUnit 5（Jupiter）版本保持对应关系（Platform 版本号 = Jupiter 版本号 - 1），因此两个版本号需要同步升级。

## 如何达成设计目的

在 Gradle 版本目录（version catalog）中更新两个版本号即可，Gradle 构建脚本会自动引用这些版本。

## 修改详情

### `gradle/libs.versions.toml` (+2/-2 lines)

**修改目的**：升级 JUnit 5 和 JUnit Platform 版本号。

**工作逻辑**：将 `junit = "5.12.2"` 改为 `junit = "5.13.2"`，将 `junit-platform = "1.12.2"` 改为 `junit-platform = "1.13.2"`。版本号保持对应关系（5.13.2 对应 Platform 1.13.2）。

## 总结

本提交是依赖升级提交，将 JUnit 5 从 5.12.2 升级到 5.13.2，JUnit Platform 从 1.12.2 升级到 1.13.2，确保测试框架保持最新。
