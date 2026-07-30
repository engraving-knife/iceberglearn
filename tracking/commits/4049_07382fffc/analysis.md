# 提交 4049：Spark 4.0: Upgrade to Spark 4.0.4 (#17181)

## 提交信息

- **序号**：4049 / 4088
- **哈希**：07382fffc540eedc68e3268f0abcb2a277c4c6dc
- **短哈希**：07382fffc
- **日期**：2026-07-16 10:38:14 -0700
- **作者**：Manu Zhang
- **提交说明**：Spark 4.0: Upgrade to Spark 4.0.4 (#17181)
- **PR/Issue**：#17181

## 总体目的

这个提交将 Iceberg 的 Spark 4.0 集成所依赖的 Spark 版本从 4.0.3 升级到 4.0.4。Spark 4.0.4 是 Spark 4.0 系列的维护版本，通常包含 bug 修复、性能改进和潜在的安全补丁，不引入 API 破坏性变更。

值得注意的是，提交信息标注 `Generated-by: Codex`，表明该升级由 OpenAI Codex 工具辅助生成，体现了 AI 辅助开发在版本升级这类机械性任务上的应用。

## 如何达成设计目的

通过 Gradle 版本目录 `gradle/libs.versions.toml` 中的 `spark40` 版本变量统一管理 Spark 4.0 的版本。将该变量从 `4.0.3` 改为 `4.0.4`，所有引用该变量的 Spark 4.0 依赖随之同步升级。由于是 patch 版本升级，无需修改任何 Java/Scala 代码或 CI 配置。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Spark 4.0 版本变量。

**工作逻辑**：
```toml
spark40 = "4.0.4"
```
将 `spark40` 变量从 `4.0.3` 改为 `4.0.4`。该变量被 Spark 4.0 模块的依赖引用，更新后所有 Spark 4.0 相关依赖升级到 4.0.4。从上下文可见同文件中 `spark35 = "3.5.8"`、`spark41 = "4.1.2"` 等其他 Spark 版本变量保持不变。

## 总结

常规的 Spark 4.0 依赖 patch 版本升级，通过版本目录变量一处修改完成全部 Spark 4.0 依赖的同步升级，获取上游维护版本的 bug 修复与安全补丁。patch 级别升级风险很低，无需代码适配。该提交由 Codex 辅助生成，展示了 AI 工具在机械性版本升级任务中的实用性。
