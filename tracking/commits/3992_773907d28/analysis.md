# 提交 3992：Core: Add id tracking to MetricsConfig (#17022)

## 提交信息

- **序号**：3992 / 4088
- **哈希**：773907d28b46f984698bb85b52b3de47053063cd
- **短哈希**：773907d28
- **日期**：2026-07-07 12:32:18 -0700
- **作者**：Ryan Blue
- **提交说明**：Core: Add id tracking to MetricsConfig (#17022)
- **PR/Issue**：#17022

## 总体目的

本提交为 `MetricsConfig` 添加了基于字段 ID 的追踪能力。此前，`MetricsConfig` 仅通过字段名（column alias）来追踪哪些列需要收集 metrics，但字段名可能在 schema 演化中改变（如重命名列），导致 metrics 配置失效或错误匹配。

新增的 `idToName` 映射使 `MetricsConfig` 能通过字段 ID 查找 metrics mode，ID 是 schema 演化中稳定的标识符。新增 `columnMode(int id)` 和 `metricsFieldIds()` 方法支持按 ID 查询。同时新增 `MetricsMode.hasBounds()` 方法，方便判断一个 mode 是否产生边界值（bounds）。

此外，本提交还重构了验证逻辑，新增 `MetricsConfig.validate()` 静态方法统一验证入口，并改进了 position delete 的默认 metrics 配置。

## 如何达成设计目的

1. 在 `MetricsConfig` 中新增 `Map<Integer, String> idToName` 字段，在 `from()` 方法中构建 schema 时同步填充 ID 到名称的映射。
2. 新增 `columnMode(int id)` 和 `metricsFieldIds()` 方法。
3. 新增 `validate()` 静态方法，封装 `from()` + `validateReferencedColumns()`。
4. 在 `MetricsModes.MetricsMode` 接口中新增 `hasBounds()` 默认方法，各实现类（None/Counts/Truncate/Full）覆盖返回相应值。
5. 移除已废弃的 `forPositionDelete(Table)` 方法，改进 `POSITION_DELETE_MODE` 常量。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetricsConfig.java` (+96/-72 lines)

**修改目的**：添加 ID 追踪和验证重构。

**工作逻辑**：
- 新增 `idToName` 字段和构造参数。
- `from()` 方法在填充 columnModes 时同步填充 idToName：
```java
for (int id : ids) {
  idToName.put(id, schema.findColumnName(id));
}
```
- 新增方法：
```java
public Iterable<Integer> metricsFieldIds() { return idToName.keySet(); }
public MetricsMode columnMode(int id) {
  String name = idToName.get(id);
  return name != null ? columnMode(name) : defaultMode;
}
public static void validate(Map<String, String> props, Schema schema) {
  from(props, schema, null).validateReferencedColumns(schema);
}
```
- `validateReferencedColumns` 新增 ID 一致性检查。
- `POSITION_DELETE_MODE` 常量重构，包含 idToName 映射。
- 移除废弃的 `forPositionDelete(Table)` 方法。

### `core/src/main/java/org/apache/iceberg/MetricsModes.java` (+20/-1 lines)

**修改目的**：新增 hasBounds 方法。

**工作逻辑**：
```java
public interface MetricsMode extends Serializable {
  default boolean hasBounds() {
    throw new UnsupportedOperationException("Unexpected implementation of MetricsMode without hasBounds");
  }
}
```
- `None.hasBounds()` → false
- `Counts.hasBounds()` → false
- `Truncate.hasBounds()` → true
- `Full.hasBounds()` → true

### `core/src/main/java/org/apache/iceberg/PropertiesUpdate.java` 和 `TableMetadata.java` (+2/-2 lines)

**修改目的**：使用新的 validate 方法。

**工作逻辑**：将 `MetricsConfig.fromProperties(props).validateReferencedColumns(schema)` 改为 `MetricsConfig.validate(props, schema)`。

### 多个 writer factory 和测试文件

**修改目的**：适配 API 变更。

**工作逻辑**：更新 `BaseFileWriterFactory`、`GenericAppenderFactory`、`GenericFileWriterFactory`、`RegistryBasedFileWriterFactory`、`FlinkAppenderFactory`、`SparkFileWriterFactory` 等的构造调用。测试文件中的 `MetricsConfig.fromProperties(...)` 调用改为 `MetricsConfig.from(..., schema, null)`。

### `.palantir/revapi.yml` (+3/-0 lines)

**修改目的**：记录 API 兼容性变更。

### `core/src/test/java/org/apache/iceberg/TestMetricsConfig.java` (+93/-0 lines)

**修改目的**：测试新的 ID 追踪功能。

## 总结

本提交为 `MetricsConfig` 添加了基于字段 ID 的追踪能力，使 metrics 配置在 schema 演化（如列重命名）下更健壮。同时通过 `hasBounds()` 方法和 `validate()` 统一入口改善了 API 可用性。这是一个基础设施改进，为后续基于 ID 的 metrics 处理奠定基础。
