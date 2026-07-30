# 提交 3896：Data: Improve format model test coverage and diagnostics (#16832)

## 提交信息

- **序号**：3896 / 4088
- **哈希**：bbf57bc77d2507f1fc4ac7fb7f8c5f6b8c8b3829
- **短哈希**：bbf57bc77
- **日期**：2026-06-17 10:40:37 -0700
- **作者**：GuoYu
- **提交说明**：Data: Improve format model test coverage and diagnostics (#16832)
- **PR/Issue**：#16832

## 总体目的

改善 `BaseFormatModelTests` 测试基类的覆盖度和诊断能力。该测试基类为各格式（Parquet、ORC、Avro 等）的 FormatModel 实现提供通用测试。本次改进主要解决三个问题：

1. **拼写修正**：将测试方法名中的 `RowLinage`（缺少字母 n）修正为 `RowLineage`，将 `ExistValue` 修正为 `ExistingValues`
2. **诊断改进**：将指标缺失断言从简单的布尔检查改为带描述信息的 `doesNotContainKey` 断言，失败时能提供更有用的错误信息
3. **覆盖度提升**：在写入引擎记录时支持传入 engineSchema，使测试能覆盖需要 engineSchema 的写入路径

## 如何达成设计目的

通过重构测试基类中的断言辅助方法、修正拼写错误、添加 engineSchema 参数传递链路来达成目标。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+35/-10 lines)

**修改目的**：改善测试覆盖度和诊断信息。

**工作逻辑**：

1. **拼写修正**：
```java
-void testReadMetadataColumnRowLinage(FileFormat fileFormat) throws IOException {
+void testReadMetadataColumnRowLineage(FileFormat fileFormat) throws IOException {
```
```java
-void testReadMetadataColumnRowLinageExistValue(FileFormat fileFormat) throws IOException {
+void testReadMetadataColumnRowLineageExistingValues(FileFormat fileFormat) throws IOException {
```

2. **诊断改进** - 提取 `assertMetricMissing` 辅助方法：
```java
private static <T> void assertMetricMissing(
    Map<Integer, T> metrics, Types.NestedField field, String metricName) {
  if (metrics != null) {
    assertThat(metrics)
        .as("%s should not contain field '%s' (id=%s)", metricName, field.name(), field.fieldId())
        .doesNotContainKey(field.fieldId());
  }
}
```
旧代码使用 `assertThat(lowerBounds == null || lowerBounds.get(field.fieldId()) == null).isTrue()` 只能报告 true/false，新方法在失败时能报告具体是哪个指标、哪个字段导致了失败。

3. **engineSchema 传递**：
新增 `writeEngineRecords` 重载方法支持传入 engineSchema：
```java
private DataFile writeEngineRecords(
    FileFormat fileFormat, Schema schema, List<T> records, boolean overwrite, Object engineSchema)
    throws IOException {
  // ...
  if (engineSchema != null) {
    writerBuilder.engineSchema(engineSchema);
  }
  // ...
}
```
使测试能验证需要 engineSchema 的写入路径（如 Variant shredding 场景）。

4. **类型修正**：
```java
-var readerBuilder =
+ReadBuilder<T, ?> readerBuilder =
```
将 `var` 改为显式类型声明，提高代码可读性。

## 总结

改善了 FormatModel 测试基类的质量：修正了两个方法名的拼写错误、通过提取带描述信息的断言方法提升了失败诊断能力、通过支持 engineSchema 参数扩展了写入路径的测试覆盖。这些改进有助于更快定位测试失败原因并覆盖更多代码路径。
