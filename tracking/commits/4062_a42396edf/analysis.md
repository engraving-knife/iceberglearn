# 提交 4062：Spark 3.5: Upgrade to Spark 3.5.9 (#17180)

## 提交信息

- **序号**：4062 / 4088
- **哈希**：a42396edfd1ca01dd28d5950280261bf9b938c07
- **短哈希**：a42396edf
- **日期**：2026-07-17 10:48:46 -0700
- **作者**：Manu Zhang
- **提交说明**：Spark 3.5: Upgrade to Spark 3.5.9 (#17180)
- **PR/Issue**：#17180

## 总体目的

将 Iceberg 的 Spark 3.5 集成所依赖的 Spark 版本从 3.5.8 升级到 3.5.9。Spark 3.5.9 是 Spark 3.5 系列的维护版本，包含 bug 修复、性能改进和潜在的安全补丁，不引入 API 破坏性变更。Spark 3.5 是目前广泛使用的 LTS 版本，此次升级确保 Iceberg 在该版本上保持最新补丁。提交由 Codex 辅助生成。

## 如何达成设计目的

通过 Gradle 版本目录 `gradle/libs.versions.toml` 中的 `spark35` 版本变量统一管理。将该变量从 `3.5.8` 改为 `3.5.9`，所有引用该变量的 Spark 3.5 依赖随之同步升级。patch 版本升级无需修改代码或 CI 配置。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Spark 3.5 版本变量。

**工作逻辑**：
```toml
spark35 = "3.5.9"
```
将 `spark35` 从 `3.5.8` 改为 `3.5.9`。同文件中 `spark40 = "4.0.4"`、`spark41 = "4.1.3"` 保持不变。

## 总结

常规的 Spark 3.5 LTS 依赖 patch 版本升级，通过版本目录变量一处修改完成全部 Spark 3.5 依赖同步升级，获取上游维护版本的 bug 修复。patch 级别升级风险很低，由 Codex 辅助生成。
