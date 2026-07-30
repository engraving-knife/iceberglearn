# 提交 1231：Arrow: Deprecate unused fixed width binary reader classes (#11292)

## 提交信息

- **序号**：1231 / 4088
- **哈希**：12ff959cc366a93af3feb7be4008be5b7428621b
- **短哈希**：12ff959cc
- **日期**：2024-10-13（Sun Oct 13 22:53:40 2024 -0700）
- **作者**：Wing Yew Poon <wypoon@cloudera.com>
- **提交说明**：Arrow: Deprecate unused fixed width binary reader classes (#11292)
- **PR/Issue**：#11292

## 总体目的

Iceberg 的 Arrow 向量化读取模块（`arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/`）中包含一组用于读取 Parquet 定宽二进制（fixed width binary）类型的向量化读取器类。这些类最初为支持 Spark 读取 Parquet 中的定宽二进制数据（如 `BYTE[7]`）而设计，但由于 Spark 本身不支持定宽二进制数据类型，这些读取器实际上从未被外部调用方使用，属于死代码。

本提交将这组未使用的定宽二进制读取器类及其工厂方法标记为 `@Deprecated`，并在 Javadoc 中注明"since 1.7.0, will be removed in 1.8.0"，为后续在 1.8.0 版本中彻底移除做铺垫。这是 Iceberg 代码清理工作的一部分，旨在减少维护负担和代码复杂度。

## 如何达成设计目的

在 4 个 Arrow 向量化 Parquet 读取器类中，为每个涉及定宽二进制读取的内部类和对应的工厂方法添加 `@Deprecated` 注解和 Javadoc 弃用说明。不删除任何代码，仅添加弃用标记，保证向后兼容性（编译时仅产生弃用警告，不影响运行时行为）。

具体涉及的类和方法分布在 4 个文件中：
1. `VectorizedColumnIterator`：`FixedWidthTypeBinaryBatchReader` 内部类 + `fixedWidthTypeBinaryBatchReader()` 工厂方法
2. `VectorizedDictionaryEncodedParquetValuesReader`：`FixedWidthBinaryDictEncodedReader` 内部类 + `fixedWidthBinaryDictEncodedReader()` 工厂方法
3. `VectorizedPageIterator`：`FixedWidthBinaryPageReader` 内部类 + `fixedWidthBinaryPageReader()` 工厂方法
4. `VectorizedParquetDefinitionLevelReader`：`FixedWidthBinaryReader` 内部类 + `fixedWidthBinaryReader()` 工厂方法

## 修改详情

### 1. `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedColumnIterator.java`

**修改目的**：弃用 `FixedWidthTypeBinaryBatchReader` 内部类及其工厂方法。

**工作逻辑**：
- 在 `FixedWidthTypeBinaryBatchReader` 类声明前添加 `@Deprecated` 注解和 Javadoc：
  ```java
  /**
   * @deprecated since 1.7.0, will be removed in 1.8.0.
   */
  @Deprecated
  public class FixedWidthTypeBinaryBatchReader extends BatchReader {
  ```
- 在 `fixedWidthTypeBinaryBatchReader()` 工厂方法前添加同样的注解和 Javadoc。

`FixedWidthTypeBinaryBatchReader` 是 `BatchReader` 的子类，其 `nextBatchOf` 方法委托给 `VectorizedPageIterator.fixedWidthBinaryPageReader()` 读取定宽二进制数据。虽然该类在 `VectorizedColumnIterator` 内部被 `fixedWidthTypeBinaryBatchReader()` 方法实例化，但该方法本身从未被外部调用方使用（实际类型分发逻辑不会路由到定宽二进制分支）。

### 2. `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedDictionaryEncodedParquetValuesReader.java`

**修改目的**：弃用 `FixedWidthBinaryDictEncodedReader` 内部类及其工厂方法。

**工作逻辑**：
- 在 `FixedWidthBinaryDictEncodedReader` 类声明前添加 `@Deprecated` 注解和 Javadoc。
- 在 `fixedWidthBinaryDictEncodedReader()` 工厂方法前添加同样的注解和 Javadoc。

`FixedWidthBinaryDictEncodedReader` 是 `BaseDictEncodedReader` 的子类，用于读取字典编码的定宽二进制 Parquet 数据。其 `nextVal` 方法从字典中解码定宽二进制值并写入 Arrow `VarBinaryVector`。对应的工厂方法 `fixedWidthBinaryDictEncodedReader()` 创建该读取器实例，但从未被实际调用。

### 3. `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedPageIterator.java`

**修改目的**：弃用 `FixedWidthBinaryPageReader` 内部类及其工厂方法。

**工作逻辑**：
- 在 `FixedWidthBinaryPageReader` 类声明前添加 `@Deprecated` 注解，并在已有 Javadoc 中追加 `@deprecated` 标签：
  ```java
  /**
   * Method for reading batches of fixed width binary type (e.g. BYTE[7]). Spark does not support
   * fixed width binary data type. To work around this limitation, the data is read as fixed width
   * binary from parquet and stored in a {@link VarBinaryVector} in Arrow.
   *
   * @deprecated since 1.7.0, will be removed in 1.8.0.
   */
  @Deprecated
  class FixedWidthBinaryPageReader extends BasePageReader {
  ```
- 在 `fixedWidthBinaryPageReader()` 工厂方法前添加 `@Deprecated` 注解和 Javadoc。

原有的 Javadoc 已明确说明"Spark does not support fixed width binary data type"（Spark 不支持定宽二进制数据类型），这印证了这些类未被实际使用的原因：读取器的设计是为了绕过 Spark 不支持定宽二进制的限制，将定宽二进制数据读取后存储为 Arrow 的 `VarBinaryVector`（变宽二进制向量），但由于 Spark 端不消费这种数据，整个读取路径实际未被激活。

### 4. `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedParquetDefinitionLevelReader.java`

**修改目的**：弃用 `FixedWidthBinaryReader` 内部类及其工厂方法。

**工作逻辑**：
- 在 `FixedWidthBinaryReader` 类声明前添加 `@Deprecated` 注解和 Javadoc。
- 在 `fixedWidthBinaryReader()` 工厂方法前添加同样的注解和 Javadoc。

`FixedWidthBinaryReader` 是 `BaseReader` 的子类，用于非字典编码的定宽二进制 Parquet 数据读取。其 `nextVal` 方法从 Parquet 页面中读取定宽二进制值并写入 Arrow `VarBinaryVector`。对应的工厂方法 `fixedWidthBinaryReader()` 创建该读取器实例，但同样从未被实际调用。

## 小结

- **成效**：将 Arrow 向量化 Parquet 读取模块中 4 组未使用的定宽二进制读取器类（共 4 个内部类 + 4 个工厂方法）标记为 `@Deprecated`，计划在 1.8.0 版本移除。这是代码清理的第一步，为后续彻底删除死代码做准备。
- **影响范围**：修改 4 个 Java 文件，共新增 31 行（均为注解和 Javadoc），无代码逻辑变更，无行为变化。这些类目前未被任何外部调用方使用，弃用标记仅产生编译时警告。
- **回迁到 1.4.x 的注意事项**：此提交是纯注解添加，不影响运行时行为，回迁风险极低。但需考虑：1) 1.4.x 的版本号低于 1.7.0，标记"since 1.7.0"在 1.4.x 中语义不准确——若回迁到 1.4.x，应将弃用版本改为 1.4.x 对应版本；2) 这些类在 1.4.x 中可能存在但状态不同，需确认 1.4.x 的代码结构是否与 main 一致；3) 若 1.4.x 不计划在后续版本移除这些类，则回迁此弃用标记意义不大。建议暂不回迁，除非 1.4.x 也计划在相应版本移除这些类。
