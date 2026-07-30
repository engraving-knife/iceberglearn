# 提交 3814：Spark 4.1: Upgrade to Spark 4.1.2 (#16365)

## 提交信息

- **序号**：3814 / 4088
- **哈希**：1172b10723b71f76d779b231e8cd57f3a3c66371
- **短哈希**：1172b1072
- **日期**：2026-06-01 11:49:53 -0700
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 4.1: Upgrade to Spark 4.1.2 (#16365)
- **PR/Issue**：#16365
- **协作者**：Codex（OpenAI）

## 总体目的

本提交将 Iceberg 项目针对 Spark 4.1 模块所依赖的 Spark 版本从 `4.1.1` 升级到 `4.1.2`。Spark 4.1.x 是 Spark 4.x 系列的一个长期支持方向，Iceberg 维护着专门的 `spark/v4.1` 模块来对接该版本。Spark 4.1.2 是 Spark 4.1 线上的发布的 patch 版本，通常包含 bug 修复、性能改进和兼容性修复。将 Iceberg 的 Spark 4.1 集成升级到最新 patch 版本，可以让用户及时获得 Spark 上游修复，并确保 Iceberg 与最新 Spark 4.1.x 发行版兼容。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml` 中的 `spark41` 版本号实现升级。版本目录集中管理所有依赖版本，Spark 4.1 模块的所有子项目通过 `libs.spark41` 引用该版本，因此只需修改一处即可全局生效。这是一个 patch 级升级（4.1.1 → 4.1.2），不涉及 API 破坏性变更。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Spark 4.1 依赖版本。

**工作逻辑**：
将版本目录中的 Spark 4.1 版本声明从 `4.1.1` 改为 `4.1.2`：
```toml
-spark41 = "4.1.1"
+spark41 = "4.1.2"
```
所有 `spark/v4.1` 下的子项目会自动使用新版本。

## 总结

这是一次 Spark 4.1 集成的常规 patch 级版本升级，改动仅一行。升级后 Iceberg 的 Spark 4.1 模块将基于 Spark 4.1.2 构建，用户可及时获得 Spark 上游修复。本提交由人工提交者与 OpenAI Codex 协作完成，体现了 AI 辅助在简单依赖升级任务中的应用。
