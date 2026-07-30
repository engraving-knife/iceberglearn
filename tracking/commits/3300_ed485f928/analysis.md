# 提交 3300：Build: Bump junit from 5.14.2 to 5.14.3 (#15401)

## 提交信息

- **序号**：3300 / 4088
- **哈希**：ed485f928c42cd3cfe828e259273bc63fdba7442
- **短哈希**：ed485f928
- **日期**：2026-02-22
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump junit from 5.14.2 to 5.14.3 (#15401)
- **PR/Issue**：#15401

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 JUnit 5（Jupiter）从 5.14.2 升级到 5.14.3。JUnit 5 是 Iceberg 项目测试体系的核心框架，包含 `junit-jupiter`（聚合依赖，提供 JUnit Jupiter API、引擎等）和 `junit-jupiter-engine`（测试执行引擎）两个核心制品，负责测试的编写与执行。

需要指出的是，本提交与前一个提交 3296（升级 junit-platform 到 1.14.3）构成配套关系：JUnit 5 由 JUnit Platform（平台层）、JUnit Jupiter（编程与扩展模型）和 JUnit Vintage（JUnit 4 兼容层）三部分组成。JUnit 5.14.3 与 JUnit Platform 1.14.3 是同一发布周期内的配套版本，两者需要保持版本对齐才能确保测试引擎与平台层的正确协作。Dependabot 将两者拆分为独立的 PR（#15400 和 #15401），但实际合并时需保证两者同时生效以避免版本不匹配。

本次升级为补丁版本（semver-patch），`dependency-type` 为 `direct:production`，不包含破坏性变更，预期对 Iceberg 庞大的测试套件无影响。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml` 中的 `junit` 版本变量，从 `5.14.2` 提升到 `5.14.3`。项目中 `junit-jupiter` 和 `junit-jupiter-engine` 均通过 `version.ref = "junit"` 引用该变量，一处修改即同步升级全部 JUnit Jupiter 制品。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 JUnit Jupiter 版本变量。

**工作逻辑**：
将第 70 行的 `junit = "5.14.2"` 改为 `junit = "5.14.3"`。此时同文件中 `junit-platform` 已在提交 3296 中升级为 `1.14.3`，两者版本号对齐，符合 JUnit 5.14.3 与 Platform 1.14.3 的配套发布关系。该变量被 `[libraries]` 段的 `junit-jupiter` 和 `junit-jupiter-engine` 声明引用，统一控制 Iceberg 各模块测试代码所用的 JUnit 版本。

## 总结

本次提交将 JUnit Jupiter 升级到 5.14.3，与同批升级的 JUnit Platform 1.14.3 配套，完成 JUnit 5 测试框架的补丁版本对齐。作为 semver-patch 级别升级，无破坏性变更，确保 Iceberg 测试基础设施在最新稳定补丁版本上运行。
