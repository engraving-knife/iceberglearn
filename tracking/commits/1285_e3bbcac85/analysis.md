# 提交 1285：Build: Bump Spark 3.4 to 3.4.4 (#11366)

## 提交信息

- **序号**：1285 / 4088
- **哈希**：e3bbcac8524f1835fc8737ec6af26de3177b4e01
- **短哈希**：e3bbcac85
- **日期**：2024-10-28（Mon Oct 28 17:04:24 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Build: Bump Spark 3.4 to 3.4.4 (#11366)
- **PR/Issue**：#11366

## 总体目的

Iceberg 的 `spark/v3.4` 模块依赖 Spark 3.4.x 进行编译与集成测试，版本号在 Gradle 版本目录 `gradle/libs.versions.toml` 中以 `spark-hive34` 统一管理。Spark 3.4.4 是 Spark 3.4 维护线的 patch 发布，包含 bug 修复与稳定性改进。本提交把 `spark-hive34` 从 `3.4.3` 升到 `3.4.4`，使 Iceberg 的 Spark 3.4 集成与测试基线跟上 Spark 3.4 最新 patch，获得上游修复收益。

## 如何达成设计目的

直接在版本目录把 `spark-hive34` 的值从 `3.4.3` 改为 `3.4.4`。由于该版本号在 `libs.versions.toml` 中集中声明、由 `spark/v3.4` 模块的 Gradle 配置统一引用，改一行即把所有用到 `spark-hive34` 的依赖（Spark SQL/Hive/相关依赖）整体拉到 3.4.4。这是 dependabot 式的单行版本对齐，无代码逻辑变更。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Spark 3.4 基线升到 3.4.4。

**工作逻辑**：

```toml
-spark-hive34 = "3.4.3"
+spark-hive34 = "3.4.4"
```

该键被 `spark/v3.4` 模块用于引入 Spark 3.4.x 的 hive/sql 等依赖。相邻的 `spark-hive33 = "3.3.4"`、`spark-hive35 = "3.5.2"` 不受影响。

## 小结

- **成效**：Spark 3.4 集成基线更新到 3.4.4（Spark 3.4 维护线最新 patch），获得上游 bug 修复与稳定性改进。
- **影响范围**：改动 1 个文件、1 行，仅构建依赖版本变更，无源代码变更。影响 `spark/v3.4` 模块的编译与测试 classpath。
- **回迁到 1.4.x 的注意事项**：
  - **可选回迁**：Spark patch 版本升级属构建侧维护，与 Iceberg 自身功能无关，回迁收益主要是让 1.4.x 的 Spark 3.4 测试基线与上游对齐、复现上游修复。若 1.4.x 仍以 3.4.3 测试且无问题，可不强求回迁；若希望 1.4.x 测试基线与 main 一致，则回迁安全（patch 版本向后兼容）。
  - **风险**：低。Spark 3.4.x patch 版本通常向后兼容，回迁后建议跑 `spark/v3.4` 模块的集成测试确认无回归。
