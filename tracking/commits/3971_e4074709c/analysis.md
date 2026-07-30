# 提交 3971：API: Add indexStatsNames to create field names for content stats (#17010)

## 提交信息

- **序号**：3971 / 4088
- **哈希**：e4074709c509bf41bf2b4a112773786105707765
- **短哈希**：e4074709c
- **日期**：2026-07-01 12:06:05 -0700
- **作者**：Ryan Blue
- **提交说明**：API: Add indexStatsNames to create field names for content stats (#17010)
- **PR/Issue**：#17010

## 总体目的

本提交新增 `TypeUtil.indexStatsNames()` 方法，用于为 schema 字段生成扁平化的统计字段名。这些名称用于内容统计（content stats）结构中的字段命名，将嵌套 schema 的字段路径用下划线连接为扁平名称（如 `location_lat`、`points_element_x`）。

此前，`IndexByName` 类使用点号（`.`）作为分隔符生成字段全名，适用于 schema 引用但不适用于统计字段名（统计结构中字段名不能包含点号）。本提交通过参数化分隔符和启用短名称模式来解决这一问题。

## 如何达成设计目的

1. 重构 `IndexByName` 类，将分隔符从硬编码的 `"."` 改为可配置参数，并新增 `useShortNames` 标志。
2. 新增包级构造函数 `IndexByName(String separator, Function<String, String> quotingFunc, boolean useShortNames)`。
3. `byId()` 方法在 `useShortNames` 为 true 时，同时包含全名和短名映射，使用 `buildKeepingLast()` 让叶节点的短名优先。
4. 在 `TypeUtil` 中新增 `indexStatsNames()` 方法，使用下划线分隔符和短名称模式。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/IndexByName.java` (+16/-5 lines)

**修改目的**：参数化分隔符并支持短名称模式。

**工作逻辑**：
- 移除硬编码的 `DOT` 常量，改为实例字段 `joiner`。
- 新增构造函数：
```java
IndexByName(String separator, Function<String, String> quotingFunc, boolean useShortNames) {
  this.joiner = Joiner.on(separator);
  this.quotingFunc = quotingFunc;
  this.useShortNames = useShortNames;
}
```
- `byId()` 在 `useShortNames` 为 true 时合并短名映射：
```java
if (useShortNames) {
  shortNameToId.forEach((key, value) -> builder.put(value, key));
  return builder.buildKeepingLast();  // 叶节点短名优先
}
```
- 字段名拼接从 `DOT.join(...)` 改为 `joiner.join(...)`。

### `api/src/main/java/org/apache/iceberg/types/TypeUtil.java` (+13/-0 lines)

**修改目的**：新增 indexStatsNames 方法。

**工作逻辑**：
```java
public static Map<Integer, String> indexStatsNames(Types.StructType struct) {
  IndexByName indexer = new IndexByName("_", Function.identity(), true);
  visit(struct, indexer);
  return indexer.byId();
}
```
使用下划线分隔符和短名称模式，生成如 `location_lat`、`values_element`、`addresses_value` 的扁平名。

### `api/src/test/java/org/apache/iceberg/types/TestTypeUtil.java` (+68/-1 lines)

**修改目的**：验证 indexStatsNames 的输出。

**工作逻辑**：新增 `testIndexStatsNames` 测试，构造包含嵌套 struct、list、map 的复杂 schema，验证生成的名称映射。例如：
- `location.lat` (field 10) → `location_lat`
- `values` list element (field 12) → `values_element`
- `points` list element struct field `x` (field 14) → `points_x`
- `addresses` map value struct field `zip` (field 23) → `addresses_zip`
- 注意 `addresses.value` (field 24) 与 `addresses_value` (map value, field 19) 短名冲突时，叶节点（field 24）通过 `buildKeepingLast()` 优先。

## 总结

本提交为内容统计功能提供了字段命名基础设施。通过参数化 `IndexByName` 的分隔符并启用短名称模式，可以生成扁平化的下划线分隔字段名，适用于统计结构中不能使用点号的场景。`buildKeepingLast()` 的设计巧妙地处理了嵌套 map value struct 中短名冲突的问题，确保叶节点优先。
