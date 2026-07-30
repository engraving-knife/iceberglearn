# 提交 2996：Throw CommitFailedException when BQ returns FAILED_PRECONDITION. (#14801)

## 提交信息

- **序号**：2996 / 4088
- **哈希**：36940952929af15fc204cc8548d374932cbc70a0
- **短哈希**：369409529
- **日期**：2025-12-10 15:42:18 -0800
- **作者**：Vladislav Sidorovich
- **提交说明**：Throw CommitFailedException when BQ returns FAILED_PRECONDITION. (#14801)
- **PR/Issue**：#14801

## 总体目的

Iceberg 的 BigQuery Catalog 在向 BigQuery 后端发起更新请求时，若 BigQuery 返回 HTTP `409 PRECONDITION_FAILED`（通常源于 etag 不匹配，即并发修改冲突），原先的处理路径存在两处与 Iceberg 提交语义不一致的问题：

1. 在 `BigQueryMetastoreClientImpl` 中，409 状态被统一翻译为 `ValidationException`。但 `ValidationException` 在 Iceberg 语义中表示"输入参数校验失败"，而 etag 不匹配本质上是乐观锁冲突——也就是说，提交失败应当通过 `CommitFailedException` 暴露给上层，以便上层（如引擎/事务协调器）按"提交冲突"路径处理（重试或回滚），而不是当作参数错误。

2. 在 `BigQueryTableOperations` 中，为了把 `ValidationException` 中包含 "etag mismatch" 的子集重新映射为 `CommitFailedException`，作者此前在 `update` 调用处包了一层 `try/catch`，通过 `toLowerCase(Locale.ENGLISH).contains("etag mismatch")` 字符串匹配来判断是否为冲突。这种基于错误消息文本的判断方式很脆弱：一旦 BigQuery 端错误文案变化，就会失效；同时也增加了不必要的复杂度。

本提交的目的就是把 409 的错误映射直接修正为抛出 `CommitFailedException`，从而让"冲突即提交失败"的语义在客户端层一次性确定下来，`BigQueryTableOperations` 不再需要做文本嗅探和二次翻译。

## 如何达成设计目的

整体思路是"在源头正确分类错误，调用方即可直接信任异常类型"。改动集中在 `bigquery` 模块的两个生产文件和一个测试文件：

- 在 `BigQueryMetastoreClientImpl` 中将 `PRECONDITION_FAILED` 分支抛出的 `ValidationException` 改为 `CommitFailedException`，并同步调整 import。
- 在 `BigQueryTableOperations` 中删除 `update` 方法里那段 `try/catch ValidationException` + "etag mismatch" 字符串判断逻辑，直接调用 `client.update(tableReference, table)`，让异常自然向上传播。
- 更新 `TestBigQueryTableOperations` 中对应的测试，断言改为期望直接收到带原始错误信息的 `CommitFailedException`。

## 修改详情

### `bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryMetastoreClientImpl.java` (+2/-2 lines)

**修改目的**：把 409 PRECONDITION_FAILED 的异常类型从 `ValidationException` 改为 `CommitFailedException`。

**工作逻辑**：
`BigQueryMetastoreClientImpl` 是 BigQuery Catalog 与 BigQuery REST API 之间的客户端封装，集中处理 HTTP 状态码到 Iceberg 异常的映射。原 `case HttpStatusCodes.STATUS_CODE_PRECONDITION_FAILED:` 抛出 `new ValidationException("%s", errorMessage)`，本提交改为 `new CommitFailedException("%s", errorMessage)`。import 一侧去掉 `ValidationException`、新增 `CommitFailedException`，保持语义干净。

需要注意的是，BigQuery 用 etag 做乐观并发控制：客户端更新时携带上次读取到的 etag，BigQuery 若发现当前资源的 etag 与请求中的不匹配就返回 409，这正是 Iceberg 中"提交冲突（concurrent update）"的标准场景。`CommitFailedException` 是 Iceberg 提交路径上的标准异常，框架会据此触发重试或向上报告提交失败，因此把 409 直接映射到 `CommitFailedException` 是与 Iceberg 提交模型对齐的正确做法。

### `bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryTableOperations.java` (+1/-11 lines)

**修改目的**：移除 `update` 调用中针对 `ValidationException` 的字符串嗅探与二次包装逻辑。

**工作逻辑**：
原 `persist` 流程在调用 `client.update(tableReference, table)` 之前用 `try/catch` 捕获 `ValidationException`，再用 `e.getMessage().toLowerCase(Locale.ENGLISH).contains("etag mismatch")` 判断是否为 etag 冲突——命中则包装成 `CommitFailedException`，未命中则原样抛出。这段逻辑在客户端已经直接抛出 `CommitFailedException` 后变成多余：上层不再需要做文本匹配。提交把整个 `try/catch` 块替换为直接的 `client.update(tableReference, table);` 调用，同时移除不再使用的 `java.util.Locale` import。这样既消除了脆弱的字符串匹配，也让异常路径变成一条直线——冲突直接以 `CommitFailedException` 形式传播。

### `bigquery/src/test/java/org/apache/iceberg/gcp/bigquery/TestBigQueryTableOperations.java` (+2/-3 lines)

**修改目的**：更新测试以匹配新的异常抛出路径。

**工作逻辑**：
`when(client.update(any(), any()))` 的 stub 从抛 `ValidationException("error message etag mismatch")` 改为抛 `CommitFailedException("error message etag mismatch")`，模拟新的客户端行为。断言从 `.hasMessageContaining("Updating table failed due to conflict updates (etag mismatch). Retry the update")` 改为 `.hasMessage("error message etag mismatch")`——因为不再有 `BigQueryTableOperations` 二次包装的消息，异常会保留底层原始错误文本。这同时验证了"不再做字符串嗅探包装"这一行为变化。

## 总结

该提交把 BigQuery Catalog 在 409 PRECONDITION_FAILED 上的错误映射直接对齐到 Iceberg 的提交冲突语义（`CommitFailedException`），并据此移除了 `BigQueryTableOperations` 中基于错误消息文本判断 etag 冲突的脆弱逻辑。改动虽小但语义价值明显：上层引擎能正确识别冲突并触发重试/回滚，代码路径更直接、更健壮，对 BigQuery 后端错误文案变化也不再敏感。
