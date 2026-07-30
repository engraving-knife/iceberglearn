# 提交 3598：Spark 4.1: Parameterize TestDeleteFrom with format-version (#16098)

## 提交信息

- **序号**：3598 / 4088
- **哈希**：2a615803ef9b05d00372bd20474a06e0013b8da9
- **短哈希**：2a615803e
- **日期**：2026-04-27 10:13:52 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 4.1: Parameterize TestDeleteFrom with format-version (#16098)
- **PR/Issue**：#16098

## 总体目的

这个提交将 Spark 4.1 的 `TestDeleteFrom` 测试类参数化，使其能够针对不同的表格式版本（format-version）运行测试用例。

之前 `TestDeleteFrom` 测试类只使用默认的 format-version 创建表，没有覆盖不同的格式版本。Iceberg 表有不同的格式版本（v1、v2、v3），不同版本支持的特性不同（例如 deletion vector（DV）只在 v3 及以上版本支持）。为了确保 DELETE FROM 操作在不同格式版本下都能正确工作，需要将测试参数化，让每个测试方法都针对多个 format-version 运行。

## 如何达成设计目的

通过 JUnit 5 的参数化测试扩展（`ParameterizedTestExtension`）实现。具体做法是：
1. 在测试类中新增一个 `@Parameter(index = 3)` 字段 `formatVersion`，作为第 4 个参数（前 3 个是 catalog 相关参数）。
2. 重写 `parameters()` 方法，将 catalog 参数与 `TestHelpers.V2_AND_ABOVE` 中的每个 format-version 进行笛卡尔积组合。
3. 新增 `tableProperties()` 辅助方法，用于生成包含 `format-version` 表属性的 SQL 片段。
4. 在所有建表语句中追加 `tableProperties()` 生成的 TBLPROPERTIES 子句。
5. 对于需要 v3 及以上版本的测试（如 `truncateWithDVs`），使用 `assumeThat` 进行条件假设。

## 修改详情

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestDeleteFrom.java` (+55/-10 lines)

**修改目的**：将测试类参数化以覆盖多个 format-version。

**工作逻辑**：

1. **新增参数字段和参数生成方法**：
```java
@Parameter(index = 3)
private int formatVersion;

@Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}, formatVersion = {3}")
protected static Object[][] parameters() {
  List<Object[]> parameters = Lists.newArrayList();
  for (Object[] catalogParams : CatalogTestBase.parameters()) {
    for (int version : TestHelpers.V2_AND_ABOVE) {
      parameters.add(
          new Object[] {catalogParams[0], catalogParams[1], catalogParams[2], version});
    }
  }
  return parameters.toArray(new Object[0][]);
}
```
参数方法将 catalog 参数与 `TestHelpers.V2_AND_ABOVE`（即 v2 及以上版本）组合，生成完整的测试参数矩阵。

2. **新增 `tableProperties()` 辅助方法**：
```java
private String tableProperties() {
  return tableProperties(ImmutableMap.of());
}

private String tableProperties(Map<String, String> additionalProperties) {
  ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
  builder.putAll(additionalProperties);
  builder.put(TableProperties.FORMAT_VERSION, String.valueOf(formatVersion));
  return String.format("TBLPROPERTIES (%s)", tablePropsAsString(builder.buildKeepingLast()));
}
```
该方法生成包含 format-version（以及其他额外属性）的 TBLPROPERTIES SQL 子句。

3. **修改建表语句**：所有 `CREATE TABLE` 语句都追加了 `%s` 占位符和 `tableProperties()` 参数，例如：
```java
sql("CREATE TABLE %s (id bigint, data string) USING iceberg %s", tableName, tableProperties());
```

4. **条件跳过 DV 相关测试**：
```java
assumeThat(formatVersion).isGreaterThanOrEqualTo(3);
```
`truncateWithDVs` 测试只在 format-version >= 3 时运行，因为 deletion vector 只在 v3 中支持。同时该测试还通过 `tableProperties()` 传入 `write.delete.mode=merge-on-read` 属性。

## 总结

这个提交通过参数化测试增强了 DELETE FROM 操作的测试覆盖率，确保其在 v2 及以上所有格式版本下都能正确工作。这是 Iceberg 多版本格式支持质量保障的重要一环，特别是针对 v3 新特性（如 deletion vector）的测试覆盖。使用 `assumeThat` 来条件性跳过不适用版本的测试是合理的做法。
