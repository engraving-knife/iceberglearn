# 提交 1477：Core, Flink, Spark: Drop deprecated APIs scheduled for removal in 1.8.0 (#11721)

## 提交信息

- **序号**：1477 / 4088
- **哈希**：28e81809e64b3d6c5b1f8d40f53fcb5edc1ec3e9
- **短哈希**：28e81809e
- **日期**：2024-12-10（Tue Dec 10 20:36:12 2024 +0100）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Core, Flink, Spark: Drop deprecated APIs scheduled for removal in 1.8.0 (#11721)
- **PR/Issue**：#11721

## 总体目的

Apache Iceberg 在 1.7.0 引入了一批 API 的弃用标记（`@Deprecated`），并在 Javadoc 中明确标注"will be removed in 1.8.0"。这些 API 包括：

- Core 中 `Deletes` 工具类的 5 个旧方法（`toPositionIndex`、`streamingFilter`、`streamingMarker` 等带 `ExecutorService` 或 `dataLocation` 参数的旧重载），已被"delete loaders"新机制取代；
- Arrow 向量化 Parquet 读取器中 `FixedWidthTypeBinaryBatchReader`、`FixedWidthBinaryDictEncodedReader`、`FixedWidthBinaryPageReader`、`FixedWidthBinaryReader` 等 4 个内部类及其工厂方法，用于读 fixed-width binary（如 `BYTE[7]`），但 Spark 不支持该类型，已被移除；
- Flink v1.18/v1.19/v1.20 三个版本的 `FlinkAvroReader`（被 `FlinkPlannedAvroReader` 取代）；
- Spark v3.5 的 `SparkAvroReader`（被 `SparkPlannedAvroReader` 取代）。

本提交是 Iceberg "废弃 API 清理节奏"的常规一步：在 1.7.0 标注弃用、在 1.8.0 删除。当前 main 分支即将演进到 1.8.0，本提交把这些已声明"在 1.8.0 移除"的 API 真正删除，同时把对应的旧测试代码同步清理或迁移。这样能让代码库保持精简，避免新旧两套 API 长期并存增加维护成本与读者困惑。

对于 `revapi.yml`（API 兼容性检查配置）的更新则记录"这些是预期内的破坏性变更"，让 CI 检查通过。

## 如何达成设计目的

通过删除 deprecated 代码 + 删除/迁移 deprecated 测试 + 在 `revapi.yml` 中以 `justification: "Removing deprecated code"` 登记这些"已接受的破坏"完成。具体按模块组织：

- Core：删除 `Deletes.java` 中 5 个 `@Deprecated` 方法，去掉 `TestPositionFilter.java` 中针对 deprecated 重载的参数化测试，改用新 API。
- Arrow：从 4 个 vectorized Parquet 读取器类中删除 `FixedWidth*` 相关内部类、方法、import。
- Flink：直接删除三个版本下的 `FlinkAvroReader.java`（每个 181 行）和对应的 `TestFlinkAvroDeprecatedReaderWriter.java`；并把 `TestRowProjection.java` 从参数化（旧/新 reader 都跑）改为只跑新 reader。
- Spark：删除 `SparkAvroReader.java`（180 行）。
- 全局：在 `.palantir/revapi.yml` 中新增 1.7.0 → 1.8.0 的 5 条"接受的破坏"记录。

## 修改详情

### `.palantir/revapi.yml`

**修改目的**：登记本次删除带来的 API 兼容性破坏，让 revapi Maven/Gradle 插件认可这些破坏是"已接受的"。

**工作逻辑**：在 `acceptedBreaks` 下新增 `"1.7.0"` 版本块（这里 1.7.0 指"被比较的旧版本"，即与 1.8.0 比较的基准），列出 `iceberg-core` 模块下 5 个被删除的方法签名：

```yaml
"1.7.0":
  org.apache.iceberg:iceberg-core:
  - code: "java.method.removed"
    old: "method ... Deletes::toPositionIndex(CharSequence, List<...>)"
    justification: "Removing deprecated code"
  - code: "java.method.removed"
    old: "method ... Deletes::toPositionIndex(CharSequence, List<...>, ExecutorService)"
    justification: "Removing deprecated code"
  - code: "java.method.removed"
    old: "method ... Deletes::streamingFilter(...)"
    justification: "Removing deprecated code"
  ... (5 条)
```

注意只列了 `iceberg-core`，因为 `Deletes` 在 core 模块；Arrow/Flink/Spark 的删除属于模块内部实现细节，不是公开 API surface（或者 revapi 只跟踪特定模块）。

### `core/src/main/java/org/apache/iceberg/deletes/Deletes.java`

**修改目的**：删除 5 个 `@Deprecated` 方法及其相关 import。

**工作逻辑**：删除以下 5 个 public 静态方法：
1. `toPositionIndex(CharSequence dataLocation, List<CloseableIterable<T>> deleteFiles)` — 旧重载，默认使用 `ThreadPools.getDeleteWorkerPool()`；
2. `toPositionIndex(CharSequence dataLocation, List<...>, ExecutorService deleteWorkerPool)` — 显式指定线程池版本；
3. `streamingFilter(rows, rowToPosition, posDeletes)` — 旧重载，内部自动 new `DeleteCounter`；
4. `streamingFilter(rows, rowToPosition, posDeletes, DeleteCounter)` — 完整版；
5. `streamingMarker(rows, rowToPosition, posDeletes, markRowDeleted)`。

这些方法在 1.7.0 已标注 `@deprecated since 1.7.0, will be removed in 1.8.0; use delete loaders.`，即推荐使用新的 "delete loaders" 机制（一种基于 `DeleteLoader` 接口加载删除文件的更清晰抽象）。同时清理不再使用的 import：`ExecutorService`、`ParallelIterable`、`ThreadPools`。

并修订了 `toPositionIndex`（保留版本）的 Javadoc，把原"Unlike {@link #toPositionIndex(CharSequence, List)}..."的交叉引用去掉（因为被引用的方法已删除），改为直接描述该方法的语义。

### `core/src/test/java/org/apache/iceberg/deletes/TestPositionFilter.java`

**修改目的**：去掉针对 deprecated 重载的参数化测试，改用新 API。

**工作逻辑**：
- 删除 `executorServiceProvider()` 参数源（提供 `null` 与一个 4 线程池）；
- 把 `testCombinedPositionSetRowFilter` 从 `@ParameterizedTest + @MethodSource` 改为普通 `@Test`；
- 测试体内原本调用 `Deletes.toPositionIndex("file_a.avro", ImmutableList.of(...), executorService)` 改为：
  ```java
  CloseableIterable<Long> positions =
      CloseableIterable.transform(
          CloseableIterable.filter(
              CloseableIterable.concat(ImmutableList.of(positionDeletes1, positionDeletes2)),
              row -> "file_a.avro".equals(row.get(0, String.class))),
          row -> row.get(1, Long.class));
  Predicate<StructLike> isDeleted =
      row -> Deletes.toPositionIndex(positions).isDeleted(row.get(0, Long.class));
  ```
  即手动做过滤+映射得到 `CloseableIterable<Long>`，再调用保留的重载 `toPositionIndex(CloseableIterable<Long>)`。这反映了新 API 的使用方式：由调用方负责准备 positions 流，`Deletes` 不再承担"按 dataLocation 过滤+并行合并"的职责。
- 同时清理不再使用的 import：`ExecutorService`、`Executors`、`ThreadPoolExecutor`、`Stream`、`MoreExecutors`、`ParameterizedTest`、`MethodSource`。

### Arrow 模块的 4 个文件

`arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/`：

1. **`VectorizedColumnIterator.java`**：删除内部类 `FixedWidthTypeBinaryBatchReader`（26 行）和工厂方法 `fixedWidthTypeBinaryBatchReader()`。该 reader 之前委托给 `vectorizedPageIterator.fixedWidthBinaryPageReader()`。

2. **`VectorizedDictionaryEncodedParquetValuesReader.java`**：删除内部类 `FixedWidthBinaryDictEncodedReader`（实现 `nextVal` 用 `dict.decodeToBinary` 解码并写入 vector 的 data buffer）和工厂方法 `fixedWidthBinaryDictEncodedReader()`。

3. **`VectorizedPageIterator.java`**：删除内部类 `FixedWidthBinaryPageReader`（继承 `BasePageReader`，把读取委托给 `vectorizedDefinitionLevelReader.fixedWidthBinaryReader()`）和工厂方法 `fixedWidthBinaryPageReader()`；并删除对应的 import `VarBinaryVector`。原 Javadoc 提到"Spark does not support fixed width binary data type"——这是删除的合理性依据。

4. **`VectorizedParquetDefinitionLevelReader.java`**：删除内部类 `FixedWidthBinaryReader`（实现 `nextVal` 用 `valuesReader.getBuffer` 读字节并写入 `VarBinaryVector`，以及 `nextDictEncodedVal` 处理 RLE/PACKED 两种字典编码模式）和工厂方法 `fixedWidthBinaryReader()`。同样删除 `VarBinaryVector` import。

这 4 个删除是连贯的：`VectorizedColumnIterator` 的工厂方法 → `VectorizedPageIterator` 的 reader → `VectorizedParquetDefinitionLevelReader` 的 reader → `VectorizedDictionaryEncodedParquetValuesReader` 的 dict reader，形成一条完整的"fixed-width binary 向量化读取"链路，全部移除。

### Flink 模块（v1.18 / v1.19 / v1.20 各一份）

对每个 Flink 版本（v1.18、v1.19、v1.20）执行相同操作：

1. **删除 `flink/vX.YZ/flink/src/main/java/org/apache/iceberg/flink/data/FlinkAvroReader.java`**（每个 181 行）：整个文件被删除。该类实现 `DatumReader<RowData>`，原来通过 `AvroSchemaWithTypeVisitor` 构建 `ValueReader<RowData>` 来读 Avro 数据，1.7.0 已标注 `@deprecated will be removed in 1.8.0; use FlinkPlannedAvroReader instead.`。

2. **删除 `TestFlinkAvroDeprecatedReaderWriter.java`**（每个 38 行）：原来通过 `Avro.read(...).createReaderFunc(FlinkAvroReader::new)` 测试旧 reader，旧 reader 已删，测试随之删除。

3. **简化 `TestRowProjection.java`**：原测试是参数化的，参数 `useAvroPlannedReader` 取 `FALSE`（用旧 `FlinkAvroReader`）和 `TRUE`（用新 `FlinkPlannedAvroReader`）两套，从而保证旧 reader 仍能用。本提交：
   - 删除 `@ExtendWith(ParameterizedTestExtension.class)`、`@Parameter`、`@Parameters`；
   - 把所有 `@TestTemplate` 改为 `@Test`；
   - 在 `writeAndRead` 中删除"if useAvroPlannedReader 才用新 reader"分支，统一用 `createResolvingReader(FlinkPlannedAvroReader::create)`；
   - 清理对应 import：`Arrays`、`List`、`Map`、`Parameter`、`ParameterizedTestExtension`、`Parameters`、`Maps`、`TestTemplate`、`ExtendWith`；
   - 顺手删除了未使用的私有方法 `toStringMap`（v1.20）。

### Spark 模块

**删除 `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkAvroReader.java`**（180 行）：结构与 `FlinkAvroReader` 几乎一致，实现 `DatumReader<InternalRow>`，1.7.0 已标注 `@deprecated will be removed in 1.8.0; use SparkPlannedAvroReader instead.`。

注意 Spark 只删了 v3.5（main 分支当前维护的 Spark 版本），而 Flink 删了 v1.18/v1.19/v1.20 三个仍在维护的版本。

## 小结

- **成效**：从 main 分支清理了 1.7.0 弃用、1.8.0 计划删除的全部 API，包括 Core 的 5 个 `Deletes` 旧方法、Arrow 的 4 个 fixed-width binary 向量化读取类、Flink 三个版本的 `FlinkAvroReader`、Spark v3.5 的 `SparkAvroReader`，以及对应的旧测试。代码净减少约 1147 行（1232 删除 - 85 新增），同时把 5 条"已接受的破坏"登记到 `revapi.yml` 让 CI 通过。
- **影响范围**：17 个文件，涉及 core、arrow、flink（v1.18/v1.19/v1.20）、spark/v3.5 四个模块；纯删除性变更，新代码仅集中在 `TestPositionFilter` 改写和 `revapi.yml` 登记。
- **回迁到 1.4.x 的注意事项**：**不要回迁**。1.4.x 是早于 1.7.0 的分支，这些 API 在 1.4.x 中尚未标记 `@Deprecated`，仍是合法且可能被外部使用的 API。如果回迁本提交，会直接删除 1.4.x 用户正在使用的公开方法，造成严重的二进制与源码不兼容。1.4.x 的弃用-删除节奏应按自己的版本计划走（先在某个 1.4.x 版本标记 `@Deprecated`，再在后续版本删除），不能跳跃式删除。
