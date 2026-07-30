# 提交 2332：Build: Bump nessie to 0.104.2 skipping tests in JDK 11 (#13490)

## 提交信息

- **序号**：2332 / 4088
- **哈希**：78cfad95b6dfb95f8d812f08e0864eb90590a743
- **短哈希**：78cfad95b
- **日期**：2025-07-09 18:04:38 +0200
- **作者**：Manu Zhang
- **提交说明**：Build: Bump nessie to 0.104.2 skipping tests in JDK 11 (#13490)
- **PR/Issue**：#13490

## 总体目的

本提交将 Iceberg 项目中 Nessie 依赖版本从 0.104.1 升级到 0.104.2，同时处理了 Nessie 0.104.2 引入的 JDK 版本兼容性问题。

Nessie 是一个提供 Git-like 数据版本控制的 Iceberg catalog 实现。从 Nessie 0.104.2 开始，其测试依赖需要 JDK 17 或更高版本。然而 Iceberg 项目仍需要支持 JDK 11 的构建和测试。因此，在升级 Nessie 版本的同时，需要在 JDK 11 环境下跳过 Nessie 模块的测试，以避免因 JDK 版本不兼容导致构建失败。

## 如何达成设计目的

设计思路分为两步：
1. 在版本目录（version catalog）中将 Nessie 版本号从 0.104.1 更新为 0.104.2。
2. 在 `build.gradle` 的 `iceberg-nessie` 项目配置中添加条件，当当前 JDK 版本为 11 时跳过测试。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Nessie 版本号。

**工作逻辑**：将 `nessie = "0.104.1"` 改为 `nessie = "0.104.2"`。

### `build.gradle` (+2/-0 lines)

**修改目的**：在 JDK 11 环境下跳过 Nessie 模块测试。

**工作逻辑**：在 `project(':iceberg-nessie')` 配置块中添加 `test.onlyIf { JavaVersion.current() != JavaVersion.VERSION_11 }`，并附注释说明"从 Nessie 0.104.2 开始，测试依赖需要 JDK 17+"。这样在 JDK 11 下运行构建时，Nessie 模块的测试会被自动跳过。

## 总结

本提交是依赖升级提交，将 Nessie 从 0.104.1 升级到 0.104.2。关键处理点在于通过 Gradle 的 `onlyIf` 条件在 JDK 11 环境下跳过 Nessie 测试，确保 Iceberg 在保持 JDK 11 兼容性的同时能够使用最新版本的 Nessie。
