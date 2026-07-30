# 提交 1912：Core: Add commit metrics for rewriting manifests (#12630)

## 提交信息

- **序号**：1912 / 4088
- **哈希**：4f06b0911e870ad4ef329e77d68ae5ebe9135961
- **短哈希**：4f06b0911
- **日期**：2025-03-24 13:46:55 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Add commit metrics for rewriting manifests (#12630)
- **PR/Issue**：#12630

## 总体目的

重写清单（rewrite manifests）操作在快照摘要（snapshot summary）中已经写入了四个计数指标：`manifests-created`、`manifests-kept`、`manifests-replaced`、`entries-processed`，但这些指标此前是 `BaseRewriteManifests` 内部的私有常量字符串，并未在 `SnapshotSummary` 中公开为公共常量，也没有在提交指标模型 `CommitMetricsResult` 中暴露。结果是下游通过 `CommitReport` 拿不到重写清单特有的指标，无法对重写清单操作做可观测性分析。

本提交把这些清单重写相关的计数提升为 `SnapshotSummary` 的公共常量，并在 `CommitMetricsResult`、其 JSON 解析器（`CommitMetricsResultParser`）中新增对应字段，使重写清单操作产生的 commit report 中能包含 `manifestsCreated`、`manifestsReplaced`、`manifestsKept`、`manifestEntriesProcessed` 四个计数器结果，对外可通过统一指标体系消费。

## 如何达成设计目的

1. 把 `BaseRewriteManifests` 中四个私有常量提升为 `SnapshotSummary` 的 `public static final` 常量（`CREATED_MANIFESTS_COUNT`、`REPLACED_MANIFESTS_COUNT`、`KEPT_MANIFESTS_COUNT`、`PROCESSED_MANIFEST_ENTRY_COUNT`），并在 `BaseRewriteManifests.summary()` 中改为引用公共常量，删除本地副本。
2. 在 `CommitMetricsResult` 接口（Immutables 注解风格）中新增四个 `CounterResult` 字段（默认 null）以及对应的字符串常量，并在 `from(...)` 工厂方法中从快照摘要中读取这四个键构造计数器。
3. 在 `CommitMetricsResultParser` 的 `toJson` / `fromJson` 中补齐这四个字段的序列化/反序列化。
4. 测试：`TestCommitReporting.addAndDeleteManifests` 改用 `clusterBy`/`rewriteIf` API 触发真正的重写，并断言 commit metrics 中的清单计数；新增 `TestSnapshotSummary.rewriteManifestsWithDuplicateFiles` 校验快照摘要；扩展 `TestCommitMetricsResultParser` 校验新字段的 JSON 往返。

注意键名差异：快照摘要里写入的是 `entries-processed`（`PROCESSED_MANIFEST_ENTRY_COUNT = "entries-processed"`），而 `CommitMetricsResult` 对外暴露的 JSON 字段名常量是 `"manifest-entries-processed"`，两者通过 `CommitMetricsResult.from` 映射。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseRewriteManifests.java` (修改, +6/-9 lines)

**修改目的**：去掉本地私有常量，改为引用 `SnapshotSummary` 公共常量。

**工作逻辑**：删除四个 `private static final String` 常量定义；`summary()` 中 `summaryBuilder.set(...)` 改为传入 `SnapshotSummary.CREATED_MANIFESTS_COUNT` 等公共常量，逻辑保持不变（统计 created/kept/replaced/processed 数量写入快照摘要）。

### `core/src/main/java/org/apache/iceberg/SnapshotSummary.java` (修改, +4 lines)

**修改目的**：公开清单重写相关计数常量。

**工作逻辑**：

```java
public static final String CREATED_MANIFESTS_COUNT = "manifests-created";
public static final String REPLACED_MANIFESTS_COUNT = "manifests-replaced";
public static final String KEPT_MANIFESTS_COUNT = "manifests-kept";
public static final String PROCESSED_MANIFEST_ENTRY_COUNT = "entries-processed";
```

### `core/src/main/java/org/apache/iceberg/metrics/CommitMetricsResult.java` (修改, +33 lines)

**修改目的**：在 commit 指标模型中新增四个清单计数器字段。

**工作逻辑**：

- 新增常量：`KEPT_MANIFESTS_COUNT = "manifests-kept"`、`CREATED_MANIFESTS_COUNT = "manifests-created"`、`REPLACED_MANIFESTS_COUNT = "manifests-replaced"`、`PROCESSED_MANIFEST_ENTRY_COUNT = "manifest-entries-processed"`。
- 新增四个 Immutables `@Value.Default default CounterResult manifestsCreated/manifestsReplaced/manifestsKept/manifestEntriesProcessed()`，默认返回 null（向后兼容）。
- 在 `from(CommitMetrics, Map<String,String> snapshotSummary)` 中用 `counterFrom(snapshotSummary, SnapshotSummary.CREATED_MANIFESTS_COUNT)` 等填充这四个字段，使重写清单的提交指标能从快照摘要中重建。

### `core/src/main/java/org/apache/iceberg/metrics/CommitMetricsResultParser.java` (修改, +28/-1 lines)

**修改目的**：补齐四个清单计数的 JSON 序列化与反序列化。

**工作逻辑**：

- `toJson` 中对每个非 null 的清单计数器写 `CommitMetricsResult.CREATED_MANIFESTS_COUNT` 等字段名，用 `CounterResultParser.toJson` 写值；同时把 `@SuppressWarnings` 加上 `MethodLength`（方法变长）。
- `fromJson` 中用 `CounterResultParser.fromJson(CommitMetricsResult.CREATED_MANIFESTS_COUNT, json)` 等读取并设置到 builder。

### `core/src/test/java/org/apache/iceberg/TestCommitReporting.java` (修改, +14/-13 lines)

**修改目的**：用真实重写清单操作验证 commit metrics。

**工作逻辑**：`addAndDeleteManifests` 不再手工构造 manifest 调用 `deleteManifest/addManifest`，改为：

```java
table.rewriteManifests().clusterBy(file -> "file").rewriteIf(ignored -> true).commit();
```

随后断言 commit metrics：`totalDataFiles=2`、`totalRecords=2`、`totalFilesSizeInBytes=20`、`manifestsCreated=1`、`manifestsKept=0`、`manifestsReplaced=2`、`manifestEntriesProcessed=2`。

### `core/src/test/java/org/apache/iceberg/TestSnapshotSummary.java` (修改, +25 lines)

**修改目的**：新增重写清单的快照摘要测试。

**工作逻辑**：`rewriteManifestsWithDuplicateFiles` 连续 append 三个文件后用 `clusterBy(file -> "file").rewriteIf(ignored -> true)` 重写清单，断言快照摘要包含 12 项，并校验清单计数：`PROCESSED_MANIFEST_ENTRY_COUNT=3`、`CREATED_MANIFESTS_COUNT=1`、`KEPT_MANIFESTS_COUNT=0`、`REPLACED_MANIFESTS_COUNT=3`，以及 `TOTAL_DATA_FILES=3` 等。

### `core/src/test/java/org/apache/iceberg/metrics/TestCommitMetricsResultParser.java` (修改, +24 lines)

**修改目的**：验证四个新字段的 from/JSON 往返。

**工作逻辑**：在构造快照摘要 map 时加入四个清单计数键（值 10/4/6/20），断言 `CommitMetricsResult.from` 后对应字段值正确，并扩展期望 JSON 字符串包含 `manifests-created`、`manifests-replaced`、`manifests-kept`、`manifest-entries-processed` 四个对象。

## 总结

本提交把重写清单操作产生的清单计数指标（created/replaced/kept/entries-processed）从 `BaseRewriteManifests` 私有常量提升为 `SnapshotSummary` 公共常量，并在 `CommitMetricsResult` 与其 JSON 解析器中新增对应字段，使 commit report 能完整暴露重写清单的可观测性指标。同时改进测试用真实重写 API 验证指标值与快照摘要。
