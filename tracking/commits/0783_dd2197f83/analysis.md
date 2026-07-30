# 提交 0783：API: Fix aggregate pushdown when optional DataFile stats are null (#10273)

## 提交信息

- **序号**：0783 / 4088
- **哈希**：dd2197f835aa3f180c0f49246055ce665f9fef59
- **短哈希**：dd2197f83
- **日期**：2024-05-23 15:48:33 -0400
- **作者**：Joshua Kolash
- **提交说明**：API: Fix aggregate pushdown when optional DataFile stats are null (#10273)
- **PR/Issue**：#10273

## 总体目的

本提交修复 Iceberg 聚合下推（aggregate pushdown）在 `DataFile` 的可选统计信息（stats）为 `null` 时抛 `NullPointerException` 的缺陷。Iceberg 支持在扫描阶段利用每个数据文件预先统计的 `valueCounts`、`nullValueCounts`、`lowerBounds`、`upperBounds` 等信息，直接从文件级元数据计算 `count`/`count(*)`/`max`/`min` 等聚合结果，从而避免读取实际数据（即"聚合下推到文件统计"）。但 `DataFile` 接口中这些统计 Map 都是**可选**的——写入端可以省略部分或全部统计信息（例如用户关闭了 `write.metadata.metrics.default`，或某些旧版本文件未记录这些统计）。原实现在 `BoundAggregate` 的各子类 `hasValue(DataFile)` 方法中直接对这些 Map 调用 `containsKey`，一旦 Map 本身为 `null` 就会 NPE，导致整个聚合下推路径崩溃。本提交引入 `safeContainsKey` 工具方法，对 null Map 安全地返回 `false`（即"该文件不提供此聚合所需的统计"），使聚合下推能优雅降级：有统计的文件正常下推，无统计的文件让对应聚合器标记为无效，最终查询回退到读取实际数据计算。

## 如何达成设计目的

### bug 成因

`BoundAggregate` 是 Iceberg 表达式层所有绑定聚合（bound aggregate）的基类，其内部定义了 `Aggregator` 接口和 `NullSafeAggregator` 抽象实现。聚合下推的核心流程是：对每个待聚合的数据文件，调用 `Aggregator.update(DataFile file)`：

```java
public void update(DataFile file) {
  if (isValid) {
    if (hasValue(file)) {          // 文件是否提供了此聚合所需的统计
      R value = aggregate.eval(file);
      if (value != null) { update(value); }
    } else {
      this.isValid = false;        // 该文件无统计 → 聚合器失效，需回退读数据
    }
  }
}
```

`hasValue(DataFile)` 由各具体聚合子类实现，用于判断"这个文件的统计信息是否足以支撑该聚合的下推计算"。原实现直接对 `DataFile` 返回的统计 Map 调用 `containsKey`：

- `CountNonNull.hasValue`：`file.valueCounts().containsKey(fieldId) && file.nullValueCounts().containsKey(fieldId)`
- `MaxAggregate.hasValue`：`file.upperBounds().containsKey(fieldId)` （外加对 valueCount/nullCount 的判断）
- `MinAggregate.hasValue`：`file.lowerBounds().containsKey(fieldId)` （同上）

问题在于：`DataFile.valueCounts()`、`nullValueCounts()`、`lowerBounds()`、`upperBounds()` 的返回值都可能是 `null`（这些是可选统计）。当任一为 `null` 时，`.containsKey(...)` 直接 NPE。注意 `BoundAggregate` 已有 `safeGet` 工具方法对 null Map 做了保护（用于 `valueCount`/`nullCount` 的读取），但 `containsKey` 调用路径漏掉了同等保护，导致下推判定本身在 null stats 下崩溃。

触发场景：当表的写入门控关闭了部分 metrics（如 `write.metadata.metrics.default=none` 或 `counts-only`），或文件由不支持完整统计的写入器生成时，`DataFile` 的这些 Map 为 null。一旦查询带了可下推的聚合（如 `SELECT count(x), max(x) FROM t`），扫描计划阶段就会 NPE。

### 修复逻辑

1. **新增 `safeContainsKey` 工具方法**（`BoundAggregate` 类）：

```java
<V> boolean safeContainsKey(Map<Integer, V> map, int key) {
  if (map == null) {
    return false;
  }
  return map.containsKey(key);
}
```

与既有 `safeGet` 对称：null Map 视作"不含任何键"，返回 `false`。语义上，"该文件没有提供此统计 Map"等价于"该文件未统计此字段"，因此 `hasValue` 返回 false 是正确的降级——聚合器随之标记 `isValid=false`，查询引擎检测到聚合器失效后会回退到读取实际数据行来计算聚合，结果正确性不受影响，只是失去了该文件的统计下推加速。

2. **`CountNonNull.hasValue`**：将 `file.valueCounts().containsKey(fieldId)` 替换为 `safeContainsKey(file.valueCounts(), fieldId)`。

3. **`MaxAggregate.hasValue`**：将 `file.upperBounds().containsKey(fieldId)` 替换为 `safeContainsKey(file.upperBounds(), fieldId)`。后续对 `valueCounts`/`nullValueCounts` 的读取仍用既有的 `safeGet`（已 null-safe）。

4. **`MinAggregate.hasValue`**：将 `file.lowerBounds().containsKey(fieldId)` 替换为 `safeContainsKey(file.lowerBounds(), fieldId)`。

修复后的 `hasValue` 在 stats 全部为 null 时安全返回 false，触发 `NullSafeAggregator` 的 `isValid=false` 分支，聚合下推优雅降级为读数据计算，不再 NPE。

### 关于 CountNonNull 中第二个 containsKey 的说明

值得注意：`CountNonNull.hasValue` 修复后为：

```java
return safeContainsKey(file.valueCounts(), fieldId)
    && file.nullValueCounts().containsKey(fieldId);   // 仍未用 safeContainsKey
```

由于 `&&` 短路求值，当 `valueCounts()` 为 null 时 `safeContainsKey` 返回 false，第二个调用不执行，无 NPE。但当 `valueCounts()` 非 null 且**包含** `fieldId`（第一个条件为 true）时，第二个 `file.nullValueCounts().containsKey(fieldId)` 仍会执行——若此时 `nullValueCounts()` 为 null，理论上仍会 NPE。本提交未覆盖这一边角组合（`valueCounts` 有值但 `nullValueCounts` 为 null）。在实际写入路径中，`valueCounts` 与 `nullValueCounts` 通常同时写入或同时缺失，故该组合罕见，本提交的测试用例（`MISSING_ALL_OPTIONAL_STATS` 将两者都置 null）也未触发该边角。这是一处可改进的残留点，但不影响本提交对已报告 NPE 场景的修复有效性。

### 测试验证

新增测试数据文件 `MISSING_ALL_OPTIONAL_STATS`：所有可选统计（valueCounts、nullValueCounts、nanValueCounts、lowerBounds、upperBounds）均为 null，仅 recordCount=20 有值。

新增两个测试：

1. **`testIntAggregateAllMissingStats`**：对 `id` 列做 `count(*)`、`count(id)`、`max(id)`、`min(id)`，喂入 `MISSING_ALL_OPTIONAL_STATS`。断言：
   - `allAggregatorsValid()` 为 false（count(id)/max/min 因无统计而失效；count(*) 用 recordCount 仍有效，但因其他失效导致整体 false）。
   - 结果为 `{20L, null, null, null}`：count(*)=20（来自 recordCount），其余三个因失效返回 null。

2. **`testOptionalColAllMissingStats`**：对可选列 `no_stats` 做同样四个聚合，断言相同。覆盖可选列场景。

修复前，这两个测试会在 `hasValue` 调用 `containsKey` 时 NPE；修复后正常返回 false 并降级，结果符合预期。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/BoundAggregate.java`

**修改目的**：提供 null-safe 的 `containsKey` 工具方法。

**修改内容**：在既有 `safeGet` 系列方法旁新增：

```java
<V> boolean safeContainsKey(Map<Integer, V> map, int key) {
  if (map == null) {
    return false;
  }
  return map.containsKey(key);
}
```

包级可见（无修饰符），供同包的 `CountNonNull`、`MaxAggregate`、`MinAggregate` 等子类使用。

### `api/src/main/java/org/apache/iceberg/expressions/CountNonNull.java`

**修改目的**：`hasValue` 对 null `valueCounts` 安全降级。

**修改内容**：

```java
- return file.valueCounts().containsKey(fieldId) && file.nullValueCounts().containsKey(fieldId);
+ return safeContainsKey(file.valueCounts(), fieldId)
+     && file.nullValueCounts().containsKey(fieldId);
```

仅 `valueCounts()` 调用改为 `safeContainsKey`，依赖短路求值在 valueCounts 为 null 时避免对 `nullValueCounts()` 的调用（见上文说明）。

### `api/src/main/java/org/apache/iceberg/expressions/MaxAggregate.java`

**修改目的**：`hasValue` 对 null `upperBounds` 安全降级。

**修改内容**：

```java
- boolean hasBound = file.upperBounds().containsKey(fieldId);
+ boolean hasBound = safeContainsKey(file.upperBounds(), fieldId);
```

后续 `safeGet(file.valueCounts(), fieldId)` 与 `safeGet(file.nullValueCounts(), fieldId)` 已是 null-safe，无需改动。

### `api/src/main/java/org/apache/iceberg/expressions/MinAggregate.java`

**修改目的**：`hasValue` 对 null `lowerBounds` 安全降级。

**修改内容**：

```java
- boolean hasBound = file.lowerBounds().containsKey(fieldId);
+ boolean hasBound = safeContainsKey(file.lowerBounds(), fieldId);
```

与 `MaxAggregate` 对称。

### `api/src/test/java/org/apache/iceberg/expressions/TestAggregateEvaluator.java`

**修改目的**：新增全 null 统计的测试数据与回归测试。

**修改内容**：
1. 新增静态常量 `MISSING_ALL_OPTIONAL_STATS`（`TestDataFile`，所有统计 Map 为 null，recordCount=20）。
2. 新增 `testIntAggregateAllMissingStats`：对 `id` 列做 count(*)/count/max/min，断言 `allAggregatorsValid()` 为 false，结果 `{20L, null, null, null}`。
3. 新增 `testOptionalColAllMissingStats`：对 `no_stats` 可选列做同样聚合，断言相同。

## 小结

- **成效**：修复了聚合下推在 `DataFile` 可选统计为 null 时的 NPE，使下推路径能优雅降级为读数据计算，结果正确性得到保障。修复方式最小化（新增一个工具方法 + 三处调用替换），与既有 `safeGet` 风格对称，符合代码库约定。新增两个回归测试覆盖全 null 统计场景。
- **影响范围**：影响 `api` 模块的聚合下推核心（`BoundAggregate` 及其子类 `CountNonNull`/`MaxAggregate`/`MinAggregate`）。所有使用 Iceberg 表达式层聚合下推的上游引擎（Spark、Flink、Trino 等通过 `AggregateEvaluator`）在遇到无统计文件时不再崩溃。对有完整统计的文件行为不变（`safeContainsKey` 在 Map 非 null 时等价于 `containsKey`）。
- **回迁注意事项**：
  1. 本提交纯 `api` 模块改动，无外部依赖，回迁到 1.4.x 分支冲突风险低。
  2. `safeContainsKey` 方法签名为包级可见，回迁时需确认 `CountNonNull`/`MaxAggregate`/`MinAggregate` 与 `BoundAggregate` 同包（`org.apache.iceberg.expressions`），1.4.x 中应一致。
  3. 如前文所述，`CountNonNull.hasValue` 中第二个 `nullValueCounts().containsKey` 未做 null-safe 处理，回迁后若 1.4.x 用户反馈 `valueCounts` 有值但 `nullValueCounts` 为 null 的边角 NPE，可顺手补为 `safeContainsKey(file.nullValueCounts(), fieldId)`——但本提交未做此改动，回迁应保持与上游一致。
  4. 测试数据 `MISSING_ALL_OPTIONAL_STATS` 使用 `TestDataFile` 构造器，需确认 1.4.x 的 `TestDataFile` 支持全 null 统计参数（该测试辅助类在 `api` 模块 `TestHelpers` 中，回迁时一并确认）。
