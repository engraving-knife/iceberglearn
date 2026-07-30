# 提交 3753：Backport #16065 to Flink v2.0 and v1.20 (#16429)

## 提交信息

- **序号**：3753 / 4088
- **哈希**：e2569ef893e8f9acbea22798cc938ac3b885bd02
- **短哈希**：e2569ef89
- **日期**：2026-05-20 09:13:52 -0700
- **作者**：Han You
- **提交说明**：Backport #16065 to Flink v2.0 and v1.20 (#16429)
- **PR/Issue**：#16429（原始 PR #16065）

## 总体目的

本提交将 PR #16065（即 #3747 提交中所分析的 slot sharing group 功能）回移植到 Flink v2.0 和 v1.20 两个版本分支，使这两个版本的 DynamicIcebergSink 也支持设置 slot sharing group 以进行细粒度资源管理。

PR #16065 最初只针对 Flink v2.1 实现，允许用户为 generator（及链式 forward-writer）和 shuffle sink（writer + committer）分别指定 slot sharing group 名称，以支持 Flink 的细粒度资源管理。为了让使用 Flink v2.0 和 v1.20 的用户也能享受这一功能，需要将相同的改动同步到这两个版本分支。

## 如何达成设计目的

将 v2.1 中的四处改动（`FlinkDynamicSinkOptions`、`FlinkDynamicSinkConf`、`DynamicIcebergSink`、`TestDynamicIcebergSink`）完全同步到 v2.0 和 v1.20 两个版本分支。改动内容与 v2.1 完全相同，包括新增两个配置选项、两个配置解析方法、两个 Builder API 方法、拓扑构建时应用 slot sharing group 的逻辑，以及对应的测试用例。

## 修改详情

### Flink v2.0 四个文件（+136/-1 lines）

**修改目的**：将 slot sharing group 功能同步到 Flink v2.0。

**工作逻辑**：
与 v2.1（#3747）完全相同的改动：
- `FlinkDynamicSinkOptions.java` (+20/-0)：新增 `GENERATOR_SLOT_SHARING_GROUP` 和 `SHUFFLE_SINK_SLOT_SHARING_GROUP` 两个 `ConfigOption<String>`。
- `FlinkDynamicSinkConf.java` (+16/-0)：新增 `generatorSlotSharingGroup()` 和 `shuffleSinkSlotSharingGroup()` 两个解析方法。
- `DynamicIcebergSink.java` (+36/-1)：Builder 新增 `generatorSlotSharingGroup(String)` 和 `shuffleSinkSlotSharingGroup(String)` 方法；拓扑构建时为 generator/forward-writer 和 shuffle sink 应用 slot sharing group。
- `TestDynamicIcebergSink.java` (+63/-0)：新增 `testSlotSharingGroup` 测试。

### Flink v1.20 四个文件（+136/-1 lines）

**修改目的**：将 slot sharing group 功能同步到 Flink v1.20。

**工作逻辑**：
与 v2.0 和 v2.1 完全相同的改动，涉及相同的四个文件。

## 总结

本提交将 PR #16065 的 slot sharing group 功能回移植到 Flink v2.0 和 v1.20 两个版本分支，使这两个版本的 DynamicIcebergSink 也支持为 generator 和 shuffle sink 分别指定 slot sharing group，以支持 Flink 细粒度资源管理。改动内容与 v2.1 完全相同，涵盖配置选项、配置解析、Builder API、拓扑构建逻辑和测试。这使使用 Flink v2.0 和 v1.20 的用户也能享受细粒度资源管理能力，保持多版本功能一致性。
