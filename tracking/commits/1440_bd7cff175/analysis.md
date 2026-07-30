# 提交 1440：Flink: Backport Avro planned reader (and corresponding tests) on Flink v1.18 and v1.19 (#11668)

## 提交信息

- **序号**：1440 / 4088
- **哈希**：bd7cff17590e285e804234e43086cd0fca18cb4b
- **短哈希**：bd7cff175
- **日期**：2024-11-27（Wed Nov 27 21:05:46 2024 +0100）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Flink: Backport Avro planned reader (and corresponding tests) on Flink v1.18 and v1.19 (#11668)
- **PR/Issue**：#11668
- **作用模块**：Flink v1.18 与 v1.19（主代码 + 测试）

## 总体目的

Iceberg 的 Flink 集成在 main 分支（Flink v1.20）已经引入了「planned Avro reader」——`FlinkPlannedAvroReader`，它基于 Iceberg 的 `AvroWithPartnerVisitor` 走读 Avro schema，按需构建 `ValueReader<RowData>`，采用「planned read」（按字段计划读取）的现代方式，性能与可维护性都优于旧的 `FlinkAvroReader`。后者基于传统的「read schema」全量解析方式，已被标记为 `@Deprecated`，计划在 1.8.0 移除。

本批次的提交 1434 已经为 Flink v1.20 的两种 reader 补齐了对比测试（`AbstractTestFlinkAvroReaderWriter` + 两个子类、参数化 `TestRowProjection`）。但 Flink v1.18 与 v1.19 路径下：

- 主代码仍只有旧的 `FlinkAvroReader`，没有 `FlinkPlannedAvroReader`，也没有 `FlinkValueReaders` 的 `PlannedStructReader` 支持；
- `RowDataFileScanTaskReader` 仍硬编码使用 `new FlinkAvroReader(schema, readSchema, idToConstant)`；
- 测试也只有单一的 `TestFlinkAvroReaderWriter`，未做双 reader 对比。

这导致 v1.18/v1.19 用户无法享受 planned reader 的改进，也无法验证两种 reader 行为一致性。本提交把 v1.20 上已经成熟的 `FlinkPlannedAvroReader` 实现、`FlinkValueReaders` 的 `PlannedStructReader`、`RowDataFileScanTaskReader` 的切换，以及提交 1434 中那套双 reader 测试结构，整体回迁到 Flink v1.18 与 v1.19 两个路径。这样三个 Flink 版本路径在 Avro reader 上对齐，便于后续统一维护与最终淘汰 deprecated reader。

## 如何达成设计目的

按 Flink 多版本目录的结构，在 v1.18 与 v1.19 各自的 `flink/src/main/java/org/apache/iceberg/flink/data/` 路径下同步落地相同的改动（两个版本的改动完全对称，diff 内容一致）。每个版本涉及：

1. **新增 `FlinkPlannedAvroReader`**：实现 `DatumReader<RowData>` 与 `SupportsRowPosition`，通过 `AvroWithPartnerVisitor.visit(expectedType, fileSchema, new ReadBuilder(idToConstant), FieldIDAccessors.get())` 在 `setSchema` 时构建 `ValueReader<RowData>`；内部 `ReadBuilder` 是 `AvroWithPartnerVisitor<Type, ValueReader<?>>` 的实现，按 Avro schema 节点类型（record/union/array/arrayMap/map/primitive）分发到对应的 `ValueReaders.*` 或 `FlinkValueReaders.*` 工厂；`primitive` 中还根据 Avro `LogicalType`（date/time-micros/timestamp-millis/timestamp-micros/decimal/uuid）选择对应 reader，并处理 INT→LONG、FLOAT→DOUBLE 的类型提升。
2. **`FlinkValueReaders` 新增 `PlannedStructReader`**：内部静态类，继承 `ValueReaders.PlannedStructReader<RowData>`，实现 `reuseOrCreate`（重用 `GenericRowData` 或新建）、`get`（返回 null，因为 planned reader 是写入式）、`set`（`((GenericRowData) struct).setField(pos, value)`）；并新增对外工厂 `struct(List<Pair<Integer, ValueReader<?>>> readPlan, int numFields)`。同时引入 `import org.apache.iceberg.util.Pair;`。
3. **`FlinkAvroReader` 标记 `@Deprecated`**：类与两个构造函数都加 `@Deprecated` 与 Javadoc「will be removed in 1.8.0; use FlinkPlannedAvroReader instead.」，与 v1.20 一致。
4. **`RowDataFileScanTaskReader` 切换 reader**：把 Avro 读取的 `createReaderFunc` 从 `readSchema -> new FlinkAvroReader(schema, readSchema, idToConstant)` 改为 `readSchema -> FlinkPlannedAvroReader.create(schema, idToConstant)`；导入从 `FlinkAvroReader` 改为 `FlinkPlannedAvroReader`。
5. **测试同步**：把 v1.20 上提交 1434 的测试结构（`AbstractTestFlinkAvroReaderWriter` 抽象基类 + `TestFlinkAvroPlannedReaderWriter` + `TestFlinkAvroDeprecatedReaderWriter` + 参数化 `TestRowProjection`）整体回迁到 v1.18 与 v1.19 的对应测试目录。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/data/FlinkPlannedAvroReader.java`（新增，192 行）
### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/data/FlinkPlannedAvroReader.java`（新增，192 行，与 v1.18 完全一致）

**修改目的**：为 v1.18/v1.19 引入 planned Avro reader 实现。

**工作逻辑**：
- 类 `FlinkPlannedAvroReader implements DatumReader<RowData>, SupportsRowPosition`，持 `Types.StructType expectedType` 与 `Map<Integer, ?> idToConstant`、`ValueReader<RowData> reader`；
- 静态工厂 `create(schema)` 与 `create(schema, constants)`；
- `setSchema(Schema fileSchema)`：通过 `AvroWithPartnerVisitor.visit(expectedType, fileSchema, new ReadBuilder(idToConstant), AvroWithPartnerVisitor.FieldIDAccessors.get())` 构建 reader；
- `read(RowData reuse, Decoder decoder)`：委托 `reader.read(decoder, reuse)`；
- `setRowPositionSupplier(Supplier<Long>)`：若 reader 是 `SupportsRowPosition` 则透传；
- 内部 `ReadBuilder extends AvroWithPartnerVisitor<Type, ValueReader<?>>`：
  - `record`：构造 `ValueReaders.buildReadPlan(expected, record, fieldReaders, idToConstant)` 后返回 `FlinkValueReaders.struct(readPlan, expected.fields().size())`；
  - `union`：`ValueReaders.union(options)`；
  - `array`：`FlinkValueReaders.array(elementReader)`；
  - `arrayMap`：`FlinkValueReaders.arrayMap(keyReader, valueReader)`；
  - `map`：`FlinkValueReaders.map(FlinkValueReaders.strings(), valueReader)`；
  - `primitive`：按 LogicalType（date/time-micros/timestamp-millis/timestamp-micros/decimal/uuid）与原始类型（NULL/BOOLEAN/INT/LONG/FLOAT/DOUBLE/STRING/FIXED/BYTES/ENUM）分发，并处理 INT→LONG、FLOAT→DOUBLE 的提升。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/data/FlinkAvroReader.java`
### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/data/FlinkAvroReader.java`

**修改目的**：标记旧 reader 为 deprecated。

**工作逻辑**：在类声明前与两个构造函数（无参 constants 与带 constants）前各加：
```java
/**
 * @deprecated will be removed in 1.8.0; use FlinkPlannedAvroReader instead.
 */
@Deprecated
```
共 12 行新增（每个文件 3 处 × 4 行 Javadoc+注解）。无逻辑改动，仅作为弃用信号。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/data/FlinkValueReaders.java`
### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/data/FlinkValueReaders.java`

**修改目的**：为 planned reader 提供「按字段计划」的 struct reader。

**工作逻辑**：
- 新增 `import org.apache.iceberg.util.Pair;`；
- 新增对外工厂方法：
  ```java
  static ValueReader<RowData> struct(List<Pair<Integer, ValueReader<?>>> readPlan, int numFields) {
    return new PlannedStructReader(readPlan, numFields);
  }
  ```
- 新增内部静态类 `PlannedStructReader extends ValueReaders.PlannedStructReader<RowData>`：
  ```java
  private static class PlannedStructReader extends ValueReaders.PlannedStructReader<RowData> {
    private final int numFields;
    private PlannedStructReader(List<Pair<Integer, ValueReader<?>>> readPlan, int numFields) {
      super(readPlan);
      this.numFields = numFields;
    }
    @Override
    protected RowData reuseOrCreate(Object reuse) {
      if (reuse instanceof GenericRowData && ((GenericRowData) reuse).getArity() == numFields) {
        return (RowData) reuse;
      }
      return new GenericRowData(numFields);
    }
    @Override
    protected Object get(RowData struct, int pos) {
      return null;
    }
    @Override
    protected void set(RowData struct, int pos, Object value) {
      ((GenericRowData) struct).setField(pos, value);
    }
  }
  ```
共 32 行新增（每个文件）。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/RowDataFileScanTaskReader.java`
### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/RowDataFileScanTaskReader.java`

**修改目的**：让 Flink source 实际使用 planned reader。

**工作逻辑**：
- 导入从 `import org.apache.iceberg.flink.data.FlinkAvroReader;` 改为 `import org.apache.iceberg.flink.data.FlinkPlannedAvroReader;`；
- Avro 读取的 `createReaderFunc` 从：
  ```java
  .createReaderFunc(readSchema -> new FlinkAvroReader(schema, readSchema, idToConstant));
  ```
  改为：
  ```java
  .createReaderFunc(readSchema -> FlinkPlannedAvroReader.create(schema, idToConstant));
  ```
共 4 行改动（每个文件 2 增 2 删）。

### 测试文件回迁（v1.18 与 v1.19 各 4 个文件，与提交 1434 在 v1.20 上的改动一致）

- `TestFlinkAvroReaderWriter.java` → `AbstractTestFlinkAvroReaderWriter.java`（重命名 + 改 abstract + 提取 `createAvroReadBuilder` 抽象方法 + 移除硬编码 reader 调用 + 删除多余空行）；
- `TestFlinkAvroDeprecatedReaderWriter.java`（新增，`@Deprecated` 子类，用 `createReaderFunc(FlinkAvroReader::new)`）；
- `TestFlinkAvroPlannedReaderWriter.java`（新增，子类，用 `createResolvingReader(FlinkPlannedAvroReader::create)`）；
- `TestRowProjection.java`（参数化改造，`@ExtendWith(ParameterizedTestExtension.class)` + `useAvroPlannedReader` 参数 + `@Test`→`@TestTemplate` + `writeAndRead` 内按参数选 reader）。

详细逻辑与本批次提交 1434 完全一致，此处不重复展开。

## 小结

- **成效**：把 Flink v1.20 上成熟的 `FlinkPlannedAvroReader` 实现、`FlinkValueReaders.PlannedStructReader`、`RowDataFileScanTaskReader` 切换，以及提交 1434 的双 reader 测试结构，整体回迁到 Flink v1.18 与 v1.19 两个路径，使三个 Flink 版本在 Avro reader 上完全对齐；旧 `FlinkAvroReader` 标记为 `@Deprecated`（计划 1.8.0 移除），新 reader 成为默认实现。共 16 个文件、约 708 新增/54 删除（每版本 8 个文件，两版本对称）。
- **影响范围**：Flink v1.18 与 v1.19 各 4 个主代码文件（`FlinkPlannedAvroReader` 新增、`FlinkAvroReader` 弃用标注、`FlinkValueReaders` 加 `PlannedStructReader`、`RowDataFileScanTaskReader` 切换 reader）+ 4 个测试文件。属功能回迁，主代码层面默认行为发生变化（source 默认走新 planned reader），但行为应与旧 reader 等价（由回迁的双 reader 测试保障）。
- **回迁到 1.4.x 的注意事项**：本提交本身就是从 main 回迁到 v1.18/v1.19。若 1.4.x 维护分支对应 Flink 版本包含 v1.18/v1.19，可进一步回迁到 1.4.x。需注意：
  - 1.4.x 的 `FlinkValueReaders` 是否已有 `ValueReaders.PlannedStructReader` 父类与 `ValueReaders.buildReadPlan` 工具方法（来自 core 模块）；若 core 侧尚未提供，需先回迁 core 侧支持；
  - 1.4.x 的 `AvroWithPartnerVisitor` 与 `SupportsRowPosition` API 若与 main 有差异，需按 1.4.x 的 API 适配 `FlinkPlannedAvroReader`；
  - 默认 reader 切换是行为变更（虽应等价），回迁 1.4.x 时建议同步回迁双 reader 测试以验证等价性，避免潜在的行为差异影响生产；
  - 若 1.4.x 已不再维护 v1.18/v1.19 中某一版本，则按实际维护范围选择性回迁；
  - 标记 `FlinkAvroReader` 为 `@Deprecated` 与「1.8.0 移除」的承诺需与 1.4.x 的发布路线一致；若 1.4.x 路线不同，可暂不标 `@Deprecated`，仅回迁 planned reader 实现与切换。
