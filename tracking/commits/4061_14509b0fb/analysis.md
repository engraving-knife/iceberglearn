# 提交 4061：Spark 4.1: Upgrade to Spark 4.1.3 (#17182)

## 提交信息

- **序号**：4061 / 4088
- **哈希**：14509b0fbee426edf553750234fbab84be28726a
- **短哈希**：14509b0fb
- **日期**：2026-07-17 10:47:41 -0700
- **作者**：Manu Zhang
- **提交说明**：Spark 4.1: Upgrade to Spark 4.1.3 (#17182)
- **PR/Issue**：#17182

## 总体目的

将 Iceberg 的 Spark 4.1 集成所依赖的 Spark 版本从 4.1.2 升级到 4.1.3。Spark 4.1.3 是 Spark 4.1 系列的维护版本，包含 bug 修复、性能改进和潜在的安全补丁，不引入 API 破坏性变更。提交由 Codex 辅助生成。

## 如何达成设计目的

通过 Gradle 版本目录 `gradle/libs.versions.toml` 中的 `spark41` 版本变量统一管理。将该变量从 `4.1.2` 改为 `4.1.3`，所有引用该变量的 Spark 4.1 依赖随之同步升级。patch 版本升级无需修改代码或 CI 配置。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Spark 4.1 版本变量。

**工作逻辑**：
```toml
spark41 = "4.1.3"
```
将 `spark41` 从 `4.1.2` 改为 `4.1.3`。同文件中 `spark35 = "3.5.8"`、`spark40 = "4.0.4"` 保持不变。

## 总结

常规的 Spark 4.1 依赖 patch 版本升级，通过版本目录变量一处修改完成全部 Spark 4.1 依赖同步升级，获取上游维护版本的 bug 修复。patch 级别升级风险很低，由 Codex 辅助生成。
