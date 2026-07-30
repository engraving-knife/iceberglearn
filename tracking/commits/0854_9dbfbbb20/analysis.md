# 提交 0854：API, Core, Flink, Avro, Parquet: Remove dead code and update javadocs (#10530)

## 提交信息
- **序号**：0854 / 4088
- **哈希**：9dbfbbb203c007592b8ba5f4816925043c08923a
- **短哈希**：9dbfbbb20
- **日期**：2024-06-18
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：API, Core, Flink, Avro, Parquet: Remove dead code and update javadocs (#10530)
- **PR/Issue**：#10530

## 总体目的
本提交是一次跨模块的代码清理工作，主要做两件事：一是删除项目中各处未被任何代码调用的死代码（包括未使用的方法、构造器、内部接口与类型参数），二是把若干 Flink 模块方法上「仅一行 `@return`」的简陋 javadoc 升级为带有方法描述句的标准 javadoc 格式。

死代码清理的动机很直接：未被调用的方法与字段会占据阅读者的注意力、增加维护成本，并在重构时形成「虚假的依赖面」，让人误以为某些行为仍被使用。本提交借助 IDE 的未引用检查（或类似的静态分析）一次性清掉了多个模块的此类代码。javadoc 升级则是配合本次清理的「顺手」工作——遇到方法描述缺失或不规范时一并补齐，使文档与代码质量同步提升。

此次修改属于纯重构与文档清理，不改变任何运行时行为，不修改公共 API（被删的均为未被外部使用的私有或包级方法，或被等价重载替代的构造器）。

## 如何达成设计目的
提交的达成路径分两类：

**死代码删除**：对每个被删的成员，提交者都已确认其在整个代码库中无引用。例如 `Projections.BaseProjectionEvaluator.isCaseSensitive()` 是一个 protected 方法但无任何子类或调用方使用；`DeleteFileIndex` 中删除的 `spec()`、`partition()`、`content()`、`nanValueCounts()` 都是简单委托到 `wrapped` 字段的转发方法，但调用方都已直接使用 `wrapped` 本身；`ParquetValueReaders.StructReader` 中删除的 `Setter` 接口与 `newSetter` 方法是早先为优化 struct 字段写入而预留的快速路径，但实际从未被调用（构造器中创建了 `setters` 数组却从未读取）；`ParquetValueWriters` 中删除的 `writeBoolean`、`writeLong` 也是从未被调用的便捷方法。`HadoopStreams` 中删除的 `read(ByteBuffer)` 同样无引用，并连带删除了不再需要的 `ByteBuffer` 导入。`ParquetAvro.Pair.of(K, V)` 静态工厂方法被删是因为外部已直接使用 `new Pair<>(first, second)` 构造。`GenericArrowVectorAccessorFactory.FixedSizeBinaryAccessor` 删除的是单参数构造器（仅保留双参数版本，单参数版本因 `stringFactory=null` 与字段 final 约束并存而无意义）。`ExpressionUtil.abbreviateValues` 删除的是方法签名上未被使用的泛型类型参数 `<T>`（该方法体内不依赖任何类型参数）。

**javadoc 升级**：Flink 的 `MapRangePartitioner` 与 `EnumerationHistory` 在三个 Flink 版本目录（v1.16、v1.17、v1.18）下分别有同一份代码副本，每个副本都把原来形如 `/** @return subtask id */` 的单行 javadoc 改写为先有一句方法描述、再跟 `@return` 的标准格式。例如 `MapRangePartitioner.SubtaskSelector.select()` 补充了 "Select a subtask for the key."，`getSubtaskAssignments()` 补充了 "Returns assignment summary for every subtask."，`EnumerationHistory.shouldPauseSplitDiscovery` 补充了 "Checks whether split discovery should be paused."。

## 修改详情
### `api/src/main/java/org/apache/iceberg/expressions/ExpressionUtil.java`
**修改目的**：删除私有方法 `abbreviateValues` 上未使用的泛型类型参数 `<T>`。
**工作逻辑**：原签名为 `private static <T> List<String> abbreviateValues(List<String> sanitizedValues)`，方法体内不引用 `T`，属于无意义的类型参数。改为 `private static List<String> abbreviateValues(List<String> sanitizedValues)`，调用方无需调整（调用点的类型推断不受影响）。

### `api/src/main/java/org/apache/iceberg/expressions/Projections.java`
**修改目的**：删除 `BaseProjectionEvaluator` 中未被调用的 `isCaseSensitive()` 方法。
**工作逻辑**：该方法体仅 `return caseSensitive;`，但全代码库无任何调用方（既无外部调用，子类也未重写或访问）。删除后不影响任何投影求值行为，`caseSensitive` 字段本身仍由基类维护，仅供内部使用。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/GenericArrowVectorAccessorFactory.java`
**修改目的**：删除 `FixedSizeBinaryAccessor` 中未使用的单参数构造器。
**工作逻辑**：原构造器 `FixedSizeBinaryAccessor(FixedSizeBinaryVector vector)` 调用 `super(vector)` 后将 `vector` 赋给字段、并将 `stringFactory` 置为 `null`。但实际调用方均使用双参数构造器 `FixedSizeBinaryAccessor(FixedSizeBinaryVector vector, StringFactory<Utf8StringT> stringFactory)`，单参数版本无任何引用。删除单参数构造器后，类仅保留双参数构造器。

### `core/src/main/java/org/apache/iceberg/DeleteFileIndex.java`
**修改目的**：删除 `DeleteFileIndex`（实为内部包装类）中四个未被调用的委托方法。
**工作逻辑**：删除的方法包括 `spec()`（返回 `spec` 字段）、`partition()`（委托 `wrapped.partition()`）、`content()`（委托 `wrapped.content()`）、`nanValueCounts()`（委托 `wrapped.nanValueCounts()`）。这些方法均无调用方——调用方已直接使用 `wrapped` 对象上的对应方法，或通过其他途径获取相同信息。保留的 `applySequenceNumber()`、`equalityFields()`、`nullValueCounts()`、`hasLowerAndUpperBounds()` 等方法因确实被使用而未删除。

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopStreams.java`
**修改目的**：删除未被调用的 `read(ByteBuffer)` 方法并清理对应的导入。
**工作逻辑**：原方法 `public int read(ByteBuffer buf) throws IOException { return stream.read(buf); }` 无任何调用方。删除该方法后，文件顶部的 `import java.nio.ByteBuffer;` 也变为未使用，一并删除。其余 `read(byte[])`、`read(byte[], int, int)` 等方法保留。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitioner.java` 及 v1.17、v1.18 同名文件
**修改目的**：补全方法的 javadoc 描述。
**工作逻辑**：将 `getSubtaskAssignments()` 的 javadoc 从 `/** @return ... */` 改为先写 "Returns assignment summary for every subtask." 再跟 `@return`；将 `SubtaskSelector.select()` 的 javadoc 从 `/** @return subtask id */` 改为先写 "Select a subtask for the key." 再跟 `@return subtask id`。三个 Flink 版本目录下的同名文件做完全相同的修改。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/EnumerationHistory.java` 及 v1.17、v1.18 同名文件
**修改目的**：补全 `shouldPauseSplitDiscovery` 方法的 javadoc 描述。
**工作逻辑**：将原 `/** @return true if split discovery should pause because assigner has too many splits already. */` 改写为先有 "Checks whether split discovery should be paused." 一句描述，再跟 `@return` 标签。三个 Flink 版本目录下做相同修改。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetAvro.java`
**修改目的**：删除未被调用的静态工厂方法 `Pair.of(K, V)`。
**工作逻辑**：原方法 `public static <K, V> Pair<K, V> of(K first, V second) { return new Pair<>(first, second); }` 无任何调用方（代码库中已直接使用 `new Pair<>(...)` 构造）。删除后 `Pair` 类仅保留实例方法 `getFirst()`、`getSecond()` 等。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReaders.java`
**修改目的**：删除 `StructReader` 中未被使用的内部接口 `Setter<R>` 及其构造逻辑。
**工作逻辑**：原 `StructReader` 内部定义了函数式接口 `Setter<R>`，并在构造器中创建 `Setter<I>[] setters` 数组、为每个 reader 调用 `newSetter(reader, types.get(i))` 填充。`newSetter` 方法根据 reader 类型（`UnboxedReader` + 原始类型）返回对应的 setter lambda（如 `setBoolean`、`setInteger`、`setLong`、`setFloat`、`setDouble`、`setBinary` 等），或对非原始类型返回一个「读取后判空再 set」的通用 setter。然而整个 `setters` 数组在创建后从未被读取，`Setter` 接口与 `newSetter` 方法均属死代码。本次提交一并删除接口声明、`setters` 数组创建、`newSetter` 方法实现及构造器中对应的循环填充语句，并移除因此变为多余的 `@SuppressWarnings("unchecked")` 注解。`StructReader` 的实际读路径仍通过 `get(intermediate, pos)` + `setField`/`set` 完成字段写入，不受影响。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueWriters.java`
**修改目的**：删除 `ColumnWriter` 子类中未被调用的 `writeBoolean` 与 `writeLong` 便捷方法。
**工作逻辑**：原 `public void writeBoolean(int repetitionLevel, boolean value) { column.writeBoolean(repetitionLevel, value); }` 与 `public void writeLong(int repetitionLevel, long value) { column.writeLong(repetitionLevel, value); }` 无任何调用方。保留的 `writeInteger`、`writeFloat` 等方法因确实被使用而未删除。删除后该 writer 子类仍可通过 `column` 字段直接访问底层列写入器。

## 小结
- **成效**：清除了 API、Core、Arrow、Parquet 模块中多处长期存在的死代码（未使用的方法、构造器、内部接口、类型参数），减少了维护负担与阅读干扰；同时补全了 Flink 三个版本目录下若干方法的 javadoc 描述，使文档更规范。净变更 37 行新增 / 92 行删除，删除量大于新增量正是因为死代码被整体移除。
- **影响范围**：所有变更均为删除未引用代码或补全文档，不改变任何运行时行为、公共 API 或测试结果。涉及 14 个文件，分布在 api、arrow、core、flink（三个版本目录）、parquet 五个模块。需要说明的是，提交说明中列出 "Avro" 模块，实际并未修改 avro 模块代码，所谓 "Avro" 指的是 parquet 模块下的 `ParquetAvro.java`（该类负责 Parquet 与 Avro 的 schema 集成）。另外 `arrow` 模块的 `GenericArrowVectorAccessorFactory.java` 改动也未在提交说明的模块列表中显式列出。
- **回迁注意事项**：回迁到 1.4.x 风险较低，因为均为删除未引用代码与文档清理。但需逐项核对：被删的方法/构造器在 1.4.x 上是否真的未被引用——如果 1.4.x 上有 main 分支已删除的调用方仍在使用这些方法（例如 1.4.x 特有的代码路径），直接删除会导致编译失败。建议回迁时配合 IDE 的「查找用法」逐一验证。Flink 三个版本目录下的 javadoc 修改在 1.4.x 上若 Flink 版本集合不同（如 1.4.x 可能不含 v1.18），应只回迁存在的版本目录。`ParquetValueReaders` 的 `Setter`/`newSetter` 删除涉及较大代码块，回迁前应重点确认 1.4.x 上 `setters` 数组确实未被读取。本提交与上下游无功能耦合，可独立回迁。
