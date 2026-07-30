# 提交 2357：Data: Fix typo in TestDataFileIndexStatsFilters (#13540)

## 提交信息

- **序号**：2357 / 4088
- **哈希**：aea3e2833eb619bc3e91494320bb0b8d09bd6ecb
- **短哈希**：aea3e2833
- **日期**：2025-07-15 12:39:37 +0200
- **作者**：Hussein Awala
- **提交说明**：Data: Fix typo in TestDataFileIndexStatsFilters (#13540)
- **PR/Issue**：#13540

## 总体目的

这个提交修复了测试文件 `TestDataFileIndexStatsFilters` 中的一处拼写错误。在断言描述中，"manifests" 被误写为 "manfiests"，本提交将其更正。

该测试文件验证 Iceberg 数据文件索引统计过滤器（DataFileIndexStatsFilters）的行为，其中一处断言的描述信息存在拼写错误。虽然这不影响测试逻辑和断言结果，但修正拼写错误有助于提升代码可读性和专业性。

## 如何达成设计目的

将断言描述字符串中的 "manfiests" 修正为 "manifests"。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/TestDataFileIndexStatsFilters.java` (+1/-1 lines)

**修改目的**：修复拼写错误。

**工作逻辑**：将第 367 行 `assertThat(scanReport.totalDeleteManifests().value())` 的描述字符串从 `"Should be 2 delete manfiests, one for odds and one with both odds and evens"` 改为 `"Should be 2 delete manifests, one for odds and one with both odds and evens"`，仅修正 "manfiests" → "manifests"。

## 总结

该提交修复了 `TestDataFileIndexStatsFilters` 测试中断言描述的拼写错误（"manfiests" → "manifests"）。纯拼写修正，不影响任何功能逻辑。
