# 提交 1111：Core: Generate realistic bounds in benchmarks (#11022)

## 提交信息

- **序号**：1111 / 4088
- **哈希**：f88f128dd971ceaae071761102f021264bf133a0
- **短哈希**：f88f128dd
- **日期**：2024-08-27（Tue Aug 27 13:47:53 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Generate realistic bounds in benchmarks (#11022)
- **PR/Issue**：#11022
- **影响模块**：core 测试基础设施（`FileGenerationUtil.java`、新增 `TestFileGenerationUtil.java`）

## 总体目的

Iceberg core 测试套件中有一个 `FileGenerationUtil` 工具类，用于在测试与 benchmark 中快速生成 `DataFile`（含 `Metrics`）。原本 `generateRandomMetrics(Schema)` 对每个 primitive 列都生成 **16 字节随机数据**作为 lower/upper bounds，存在三个严重问题：

1. **类型不一致**：16 字节随机数据塞到 `ByteBuffer` 里后，与列声明的类型（如 `IntegerType`、`LongType`、`DateType`、`TimestampType`、`DecimalType` 等）完全脱节。当扫描/规划代码用 `Conversions.fromByteBuffer(type, buffer)` 反序列化这些 bounds 时，会得到类型错误的对象或直接抛异常。
2. **不尊重 MetricsMode**：表可以配置 `write.metadata.metrics.default` 为 `none`/`counts`/`truncate(N)`/`full`。`none` 与 `counts` 模式下**不应**有列 bounds；`truncate(N)` 模式下 bounds 应是截断后的值。原实现无视 mode，所有列都生成 bounds。
3. **顺序不保证**：lower 与 upper 是独立生成的 16 字节随机数据，`lower` 实际可能"大于"`upper`，违反 Iceberg 中 lower ≤ upper 的不变量。

这些问题让 benchmark 与测试产生的"假数据文件"的 metrics 极不真实，可能掩盖真实场景下扫描器/规划器/谓词评估的 bug，或反过来触发测试中不该触发的代码路径。

本提交重写 bounds 生成逻辑，让生成的 bounds：

- 类型正确（按列的 `PrimitiveType` 用 `RandomUtil.generatePrimitive` 生成）；
- 尊重 `MetricsConfig`（None/Counts → 不生成 bounds；Truncate(N) → 应用截断变换；Full → 原值）；
- 顺序正确（用 `Comparators.forType` 比较，小的作 lower、大的作 upper）；
- 支持调用方注入已知 bounds（新 `generateDataFile` 重载与 `generateRandomMetrics` 重载都接受 `lowerBounds`/`upperBounds` 参数），便于测试特定边界场景。

并新增 `TestFileGenerationUtil` 测试类覆盖上述行为。

## 如何达成设计目的

整体思路：

1. **`generateDataFile` 增加重载**：原 `generateDataFile(Table, StructLike)` 委托给新 `generateDataFile(Table, StructLike, Map<Integer, ByteBuffer> lowerBounds, Map<Integer, ByteBuffer> upperBounds)`；新重载在构造 `Metrics` 时调 `MetricsConfig.forTable(table)` 拿到表的 metrics 配置，连同调用方传入的已知 bounds 一起传给 `generateRandomMetrics`。
2. **`generateRandomMetrics` 重写**：签名改为 `generateRandomMetrics(Schema, MetricsConfig, Map knownLowerBounds, Map knownUpperBounds)`。对每个列：
   - 若调用方为该 fieldId 提供了已知 lower 与 upper，直接复用（让调用方能锁定某些列的 bounds）；
   - 否则若列是 primitive，按 `metricsConfig.columnMode(column.name())` 调 `generateBounds(type, mode)` 生成；
   - 否则（非 primitive 列如 struct/list/map）跳过，不写 bounds。
3. **新增 `generateBounds(PrimitiveType, MetricsMode)`**：调 `generateBound` 两次得到两个候选值，用 `Comparators.forType(type)` 比较，较小的作 lower、较大的作 upper，再用 `Conversions.toByteBuffer(type, value)` 转 `ByteBuffer` 返回 `Pair`。
4. **新增 `generateBound(PrimitiveType, MetricsMode)`**：
   - `mode instanceof None || mode instanceof Counts` → 返回 `null`（这些模式不应有 bounds，外层 `generateBounds` 不会调到这里，因为 `generateRandomMetrics` 对 None/Counts 也会走 null 分支；这里防御性返回 null）；
   - `mode instanceof Truncate` → `RandomUtil.generatePrimitive(type, random())` 生成原值，再 `Transforms.truncate(((Truncate) mode).length()).bind(type).apply(value)` 应用截断变换；若变换不可用（`canTransform` false）则返回原值；
   - 其他（即 `full` 模式）→ 直接 `RandomUtil.generatePrimitive(type, random())`。
5. **测试**：`TestFileGenerationUtil` 用一个含 int/long/decimal/date/timestamp/timestamp_tz/string 7 列的 schema，验证：
   - 默认 MetricsConfig 下，所有列都有 bounds，且 lower ≤ upper；
   - 调用方传入 int_col 的具体 bounds 时，int_col 使用注入值，其他列自动生成；
   - None/Counts 模式下列的 bounds 为 null。

## 修改详情

### `core/src/test/java/org/apache/iceberg/FileGenerationUtil.java`

**修改目的**：让生成的 metrics bounds 类型正确、尊重 MetricsMode、顺序正确，并支持调用方注入已知 bounds。

**工作逻辑**：

- 新增 import：`Comparator`、`MetricsModes.Counts`/`MetricsMode`/`None`/`Truncate`、`ImmutableMap`、`Transform`/`Transforms`、`Comparators`、`Type.PrimitiveType`、`Pair`、`RandomUtil`。
- `generateDataFile(Table, StructLike)` 改为委托：

  ```java
  public static DataFile generateDataFile(Table table, StructLike partition) {
    return generateDataFile(table, partition, ImmutableMap.of(), ImmutableMap.of());
  }
  ```

- 新增 `generateDataFile(Table, StructLike, Map<Integer, ByteBuffer> lowerBounds, Map<Integer, ByteBuffer> upperBounds)`：与原逻辑相同，只是改为 `MetricsConfig metricsConfig = MetricsConfig.forTable(table);` + `Metrics metrics = generateRandomMetrics(schema, metricsConfig, lowerBounds, upperBounds);`。
- `generateRandomMetrics` 签名改为 `(Schema, MetricsConfig, Map<Integer, ByteBuffer> knownLowerBounds, Map<Integer, ByteBuffer> knownUpperBounds)`。每列循环内的关键改动：

  ```java
  // 改前：16 字节随机数据
  byte[] lower = new byte[16];
  random().nextBytes(lower);
  lowerBounds.put(fieldId, ByteBuffer.wrap(lower));
  byte[] upper = new byte[16];
  random().nextBytes(upper);
  upperBounds.put(fieldId, ByteBuffer.wrap(upper));

  // 改后
  if (knownLowerBounds.containsKey(fieldId) && knownUpperBounds.containsKey(fieldId)) {
    lowerBounds.put(fieldId, knownLowerBounds.get(fieldId));
    upperBounds.put(fieldId, knownUpperBounds.get(fieldId));
  } else if (column.type().isPrimitiveType()) {
    PrimitiveType type = column.type().asPrimitiveType();
    MetricsMode metricsMode = metricsConfig.columnMode(column.name());
    Pair<ByteBuffer, ByteBuffer> bounds = generateBounds(type, metricsMode);
    lowerBounds.put(fieldId, bounds.first());
    upperBounds.put(fieldId, bounds.second());
  }
  // 非 primitive 列：跳过，不写 bounds
  ```

- 新增 `generateBounds(PrimitiveType type, MetricsMode mode)`：

  ```java
  Comparator<Object> cmp = Comparators.forType(type);
  Object value1 = generateBound(type, mode);
  Object value2 = generateBound(type, mode);
  if (cmp.compare(value1, value2) > 0) {
    return Pair.of(Conversions.toByteBuffer(type, value2),
                   Conversions.toByteBuffer(type, value1));
  } else {
    return Pair.of(Conversions.toByteBuffer(type, value1),
                   Conversions.toByteBuffer(type, value2));
  }
  ```

  通过比较保证 lower ≤ upper。

- 新增 `generateBound(PrimitiveType type, MetricsMode mode)`：

  ```java
  if (mode instanceof None || mode instanceof Counts) {
    return null;
  } else if (mode instanceof Truncate) {
    Object value = RandomUtil.generatePrimitive(type, random());
    Transform<Object, Object> truncate = Transforms.truncate(((Truncate) mode).length());
    if (truncate.canTransform(type)) {
      return truncate.bind(type).apply(value);
    } else {
      return value;
    }
  } else {
    return RandomUtil.generatePrimitive(type, random());
  }
  ```

  Truncate 模式下应用截断变换（某些类型如 binary/decimal 可能不能截断，回退原值）。

### `core/src/test/java/org/apache/iceberg/TestFileGenerationUtil.java`（新增 108 行）

**修改目的**：覆盖新 bounds 生成逻辑。

**工作逻辑**：

- 定义含 7 列的 schema：`int_col`/`long_col`/`decimal_col(10,10)`/`date_col`/`timestamp_col`/`timestamp_tz_col`/`str_col`，全部 `required`。
- `testBoundsWithDefaultMetricsConfig`：用 `MetricsConfig.getDefault()`，无已知 bounds，调 `generateRandomMetrics`；断言所有列都有 bounds（默认是 full mode），调 `checkBounds` 验证每列 lower ≤ upper。
- `testBoundsWithSpecificValues`：为 `int_col` 注入 `lower=0, upper=Integer.MAX_VALUE`，调 `generateRandomMetrics`；断言 int_col 的 bounds 等于注入值，其他列仍有自动生成的 bounds；调 `checkBounds`。
- 私有 `checkBounds(Metrics, MetricsConfig)`：遍历每列，若 mode 是 None/Counts 则断言 bounds 为 null，否则调 `checkBounds(PrimitiveType, lower, upper)`。
- 私有 `checkBounds(PrimitiveType, ByteBuffer, ByteBuffer)`：`Conversions.fromByteBuffer` 反序列化 lower/upper，`Comparators.forType` 比较，断言 `cmp.compare(lower, upper) ≤ 0`。

## 小结

- **成效**：benchmark 与测试生成的 `DataFile.metrics` 现在有类型正确、顺序正确、尊重 MetricsMode 的列 bounds，并支持调用方注入已知 bounds 测试特定场景。新增 `TestFileGenerationUtil` 覆盖默认配置、注入值、None/Counts 模式三类场景。这提升了依赖 `FileGenerationUtil` 的下游测试与 benchmark 的真实性，减少因假数据掩盖 bug 或触发非预期路径的可能。
- **影响范围**：仅 core 测试基础设施（1 个修改 + 1 个新增），位于 `src/test/java`，不影响生产代码或公共 API。
- **回迁到 1.4.x 的注意事项**：
  - 这是测试基础设施改进，**回迁成本极低**且对生产无影响，可考虑回迁以提升 1.4.x 测试质量。
  - 回迁前确认 1.4.x 的 `FileGenerationUtil`、`MetricsConfig`、`MetricsModes`、`Comparators`、`RandomUtil`、`Transforms`、`Conversions`、`Pair` API 与本提交时点一致；这些类在 core 内部相对稳定，1.4.x 应该都有。
  - 注意 `MetricsConfig.forTable(Table)` 与 `MetricsConfig.columnMode(name)` 在 1.4.x 中的签名；若 1.4.x 用的是更老的 API（如 `MetricsConfig.forTable(table)` 不存在），需手工对齐。
  - 由于只触及测试代码，回迁不会引入兼容性或运行时风险；建议回迁以让 1.4.x 的依赖测试也获得更真实的 metrics。
