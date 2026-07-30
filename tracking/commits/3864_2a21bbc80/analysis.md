# 提交 3864：Spark 4.0: Upgrade to Spark 4.0.3 (#16717)

## 提交信息

- **序号**：3864 / 4088
- **哈希**：2a21bbc805d300aef7b7fb0c53713e537e0b0263
- **短哈希**：2a21bbc80
- **日期**：2026-06-11 19:40:37 -0700
- **作者**：Manu Zhang
- **提交说明**：Spark 4.0: Upgrade to Spark 4.0.3 (#16717)
- **PR/Issue**：#16717

## 总体目的

本提交将 Iceberg 的 Spark 4.0 模块依赖的 Spark 版本从 4.0.2 升级到 4.0.3。Spark 4.0.3 是 Spark 4.0 系列的 patch 版本更新，通常包含 bug 修复和改进，不引入破坏性变更。

Iceberg 需要与 Spark 的最新 patch 版本保持同步，以获得最新的 bug 修复和性能改进，确保集成稳定性和正确性。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 中 `spark40` 的版本号实现升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Spark 4.0 版本号。

**工作逻辑**：
```toml
spark40 = "4.0.3"
```
从 `4.0.2` 升级到 `4.0.3`。注意 `spark35`（3.5.8）和 `spark41`（4.1.2）保持不变。

## 总结

这是一次例行的依赖 patch 版本升级，将 Spark 4.0 依赖从 4.0.2 升级到 4.0.3。作为 patch 版本更新，风险低，主要是获取最新的 bug 修复和改进。仅影响 Spark 4.0 模块的测试和构建。
