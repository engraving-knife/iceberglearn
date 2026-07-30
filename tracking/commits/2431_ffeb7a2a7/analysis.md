# 提交 2431：Spark: Use hasSameSizeAs in collection size assertion (#13701)

## 提交信息

- **序号**：2431 / 4088
- **哈希**：ffeb7a2a7aedebd66c0f55b9a37f4cce9f45259e
- **短哈希**：ffeb7a2a7
- **日期**：2025-07-30 08:07:58 +0200
- **作者**：Aihua Xu
- **提交说明**：Spark: Use hasSameSizeAs in collection size assertion (#13701)
- **PR/Issue**：#13701

## 总体目的

本提交将 Spark 测试中一处集合大小的断言从 `assertThat(rows.size()).isEqualTo(records.size())` 改为 AssertJ 提供的更地道的 `assertThat(rows).hasSameSizeAs(records)`。

原写法先分别取两个集合的 size 再比较相等，虽然功能正确，但失败时给出的错误信息只是两个整数不相等，不够直观。使用 AssertJ 的 `hasSameSizeAs` 后，断言语义更直接表达"两个集合大小相同"的意图，且失败信息会包含两个集合的详细内容，便于调试。这是一个测试代码质量改进，属于编码风格优化，不影响功能逻辑。

## 如何达成设计目的

直接将一处断言调用替换为 AssertJ 的专用方法，无需其他改动。

## 修改详情

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/DataFrameWriteTestBase.java` (+1/-1 lines)

**修改目的**：使用更地道的 AssertJ 断言方法比较两个集合大小。

**工作逻辑**：在验证 DataFrame 写入后读回的行数与原始记录数一致时，将 `assertThat(rows.size()).isEqualTo(records.size())` 替换为 `assertThat(rows).hasSameSizeAs(records)`。`rows` 是 `InternalRow` 的集合，`records` 是 `Record` 的集合。新写法直接比较两个集合的大小，语义更清晰。

## 总结

这是一个非常小的测试代码质量改进提交。通过使用 AssertJ 提供的 `hasSameSizeAs` 替代手动取 size 后比较相等的方式，使断言意图更明确、失败信息更友好。不影响任何功能逻辑。
