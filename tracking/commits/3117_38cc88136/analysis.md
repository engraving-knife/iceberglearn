# 提交 3117：BigQuery: Eliminate redundant table load by using ETag for conflict detection (#14940)

## 提交信息

- **序号**：3117 / 4088
- **哈希**：38cc88136684a57b61be4ae0d2c1886eff742a28
- **短哈希**：38cc88136
- **日期**：2026-01-15 14:52:53 -0800
- **作者**：Joy Haldar
- **提交说明**：BigQuery: Eliminate redundant table load by using ETag for conflict detection (#14940)
- **PR/Issue**：#14940

## 总体目的

BigQuery catalog 在执行表更新提交时，原有的冲突检测逻辑存在两个问题：性能与正确性。

性能方面：在 `BigQueryTableOperations` 中，`doRefresh()` 已经通过 `client.load(tableReference)` 加载了一次表对象以读取元数据位置；而随后 `updateTable()` 在提交阶段又会再次调用 `client.load(tableReference)` 重新加载表。这意味着每次提交都会对 BigQuery API 发起两次相同的 `load` 请求，造成冗余的 API 调用，既增加延迟也消耗配额。

正确性方面：原冲突检测采用"比较元数据位置"的方式——在 `updateTable` 里再次 `load` 表，把从 metastore 读到的 `metadataLocationFromMetastore` 与 `oldMetadataLocation`（base 的元数据位置）比较，若不一致则抛 `CommitFailedException`。但 BigQuery 的 `patch()` 调用本身支持通过 `If-Match` 头携带 ETag 进行乐观锁校验，这种基于位置的二次比较既冗余又不如 ETag 精确。本提交的目标是消除冗余的 `load` 调用，并把冲突检测统一收敛到 BigQuery 原生的 ETag 机制上。

## 如何达成设计目的

核心思路是把 `doRefresh()` 中加载到的 `Table` 对象缓存到成员变量 `metastoreTable`，在 `updateTable()` 中直接复用，从而省去第二次 `client.load` 调用。同时简化 `updateTable` 的冲突检测逻辑：移除原先基于元数据位置比较的校验，改为校验缓存表对象的 ETag 是否为空（为空说明是 legacy 表无法做乐观锁），冲突检测完全交给 BigQuery 服务端通过 `If-Match` ETag 机制完成。测试侧同步调整：`FakeBigQueryMetastoreClient` 清理注释并明确模拟 ETag 乐观锁；测试用例由验证"元数据位置不一致"改为验证"ETag 不匹配触发提交失败"，并断言 `client.load` 只被调用一次。

## 修改详情

### `bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryTableOperations.java` (+13/-22 lines)

**修改目的**：复用 refresh 阶段加载的表对象，移除冗余 `load` 调用与基于位置的冲突检测。

**工作逻辑**：
新增 `volatile Table metastoreTable` 成员变量（注释说明用于在 `doRefresh()` 与 `updateTable()` 间复用，避免冗余 API 调用；用 `volatile` 保证可见性）。

`doRefresh()` 中：先 `this.metastoreTable = null`，再 `this.metastoreTable = client.load(tableReference)`，随后用该对象取 `getExternalCatalogTableOptions()` 读取元数据位置。

`doCommit()` 中对已存在表的分支由 `updateTable(base.metadataFileLocation(), newMetadataLocation, metadata)` 改为 `updateTable(newMetadataLocation, metadata)`，不再传入 `oldMetadataLocation`。

`updateTable()` 方法签名从 `(String oldMetadataLocation, String newMetadataLocation, TableMetadata metadata)` 简化为 `(String newMetadataLocation, TableMetadata metadata)`。方法体中：先用 `Preconditions.checkState(metastoreTable != null, ...)` 确保 refresh 已加载表；检查 `metastoreTable.getEtag().isEmpty()` 抛 `ValidationException`（legacy 表无 ETag 无法做乐观锁，需手动更新或重建）；删除原先比较 `metadataLocationFromMetastore` 与 `oldMetadataLocation` 的整段逻辑；直接基于 `metastoreTable` 设置参数并 `client.update(tableReference, metastoreTable)`，最后 `this.metastoreTable = null` 清理。冲突检测的真正校验依赖 BigQuery `patch()` 的 `If-Match` ETag 头在服务端完成。

### `bigquery/src/test/java/org/apache/iceberg/gcp/bigquery/FakeBigQueryMetastoreClient.java` (+3/-10 lines)

**修改目的**：清理 Fake 客户端中过时的注释，明确 ETag 乐观锁模拟语义。

**工作逻辑**：
原先有一大段注释解释真实 `patch()` 用 `If-Match` 头、`BigQueryTableOperations` 不在传入 table 上设置 ETag、fake 需要基于状态模拟等历史背景。现简化为一句 `Simulate ETag-based optimistic locking. If the incoming table has an ETag, it must match the current ETag in the store.`，并把异常消息从 `"Etag mismatch for table: %s..."` 改为 `"Cannot commit: Etag mismatch for table: %s..."`，与新测试断言对齐。

### `bigquery/src/test/java/org/apache/iceberg/gcp/bigquery/TestBigQueryTableOperations.java` (+8/-13 lines)

**修改目的**：将冲突检测测试从"元数据位置不一致"改为"ETag 不匹配"，并验证 `load` 只调用一次。

**工作逻辑**：
测试方法 `failWhenMetadataLocationDiff` 重命名为 `failWhenConcurrentModificationDetected`。不再构造第二个带新元数据位置的 `tableWithNewMetadata`，也不再让 `client.load` 返回两次不同对象。改为：`client.load` 只返回 `tableWithEtag` 一次；`client.update` 直接 `thenThrow(new CommitFailedException("Cannot commit: Etag mismatch"))` 模拟服务端 ETag 校验失败；断言提交抛 `CommitFailedException` 且消息含 `"Cannot commit"`；新增 `verify(client, times(1)).load(TABLE_REFERENCE)` 断言确认 `load` 仅被调用一次，正是本次优化的核心验证点。

## 总结

本提交通过复用 `doRefresh()` 加载的表对象消除了 BigQuery 提交流程中冗余的第二次 `client.load` API 调用，并将冲突检测从应用层"比较元数据位置"收敛到 BigQuery 原生的 ETag 乐观锁机制，既降低了延迟与配额消耗，又使并发控制更精确、更贴近 BigQuery 服务端语义。测试同步验证了 `load` 调用次数与 ETag 不匹配场景，确保优化与语义变更的正确性。
