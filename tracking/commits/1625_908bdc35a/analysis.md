# 提交 1625：Flink 1.20: Support default values in Parquet reader (#11839)

## 提交信息

- **序号**：1625 / 4088
- **哈希**：908bdc35a44460a9c271d1b92256450000a752b8
- **短哈希**：908bdc35a
- **日期**：2025-01-23（Thu Jan 23 20:42:45 2025 +0100）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Flink 1.20: Support default values in Parquet reader (#11839)
- **PR/Issue**：#11839

## 总体目的

Iceberg v3 规范为结构体字段引入了默认值机制（`initial-default` 与 `write-default`）。`initial-default` 用于读取旧数据文件（文件中缺少该字段时）填充的值——例如表新增了一个带默认值的字段后，读取新增前写入的旧文件时，该字段应填充 `initial-default` 而非 null。

Flink 1.20 的 Iceberg 集成中，`FlinkParquetReaders` 负责把 Parquet 文件按 Iceberg schema 投影读取为 Flink `RowData`。其 `struct` 方法在遍历 expected schema 的字段时，对于文件中不存在的字段（无对应 reader），此前的处理是：直接用 `ParquetValueReaders.nulls()` 填充 null。这对 v2 表（无默认值语义）是正确的，但对 v3 表就丢失了 `initial-default` 语义——用户定义了字段默认值，读取旧文件却得到 null 而非默认值，违反 v3 规范。

此外，对于 required 字段（非可选字段），如果文件中缺失且无默认值，此前代码也会静默填 null，这在语义上是错误的——required 字段不应为 null。

本提交修复这两个问题：

1. **支持 `initial-default`**：当字段在文件中不存在（无 reader）且 `field.initialDefault() != null` 时，用默认值构建 `ParquetValueReaders.constant(...)` 填充，默认值通过 `RowDataUtil.convertConstant(field.type(), field.initialDefault())` 转为 Flink 类型；
2. **required 字段校验**：当 required 字段在文件中不存在、无常量覆盖、无默认值时，抛 `IllegalArgumentException("Missing required field: ...")` 而非静默填 null。

## 如何达成设计目的

重构 `FlinkParquetReaders.struct` 方法中字段处理分支，从原来的"嵌套 if-else"改为"扁平 else-if 链"，新增两个分支：

1. **default value 分支**：`else if (field.initialDefault() != null)` → 用 `ParquetValueReaders.constant(RowDataUtil.convertConstant(...), maxDefinitionLevel)` 填充默认值；
2. **required error 分支**：把原来的"兜底 nulls()"改为 `else if (field.isOptional()) { nulls() } else { throw IllegalArgumentException }`——optional 字段填 null，required 字段抛错。

同时重构测试：`writeAndValidate` 方法拆分为接受 `writeSchema` 与 `expectedSchema` 两个参数的版本，支持"用 schema A 写文件、用 schema B 读文件"的 schema 演进测试场景；`supportsDefaultValues()` 返回 `true` 启用基类 `DataTest` 中的默认值测试用例。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java`（修改）

**修改目的**：在 struct 读取器中支持 initial-default 并校验 required 字段。

**工作逻辑**：`struct` 方法遍历 expected schema 字段时的分支链变更。原逻辑（嵌套）：

```
if idToConstant 含 id → 用常量
else if id == IS_DELETED → 用 false
else {
  reader = readersById.get(id)
  if reader != null → 用 reader
  else → 用 nulls()    // 无论 optional 还是 required 都填 null
}
```

新逻辑（扁平 else-if 链）：

```
reader = readersById.get(id)   // 提前取出
if idToConstant 含 id → 用常量（不变）
else if id == IS_DELETED → 用 false（不变）
else if reader != null → 用 reader（字段在文件中）
else if field.initialDefault() != null → 用常量填充默认值  // 新增
    ParquetValueReaders.constant(
        RowDataUtil.convertConstant(field.type(), field.initialDefault()),
        maxDefinitionLevelsById.getOrDefault(id, defaultMaxDefinitionLevel))
else if field.isOptional() → 用 nulls()    // 仅 optional 填 null
else → throw IllegalArgumentException("Missing required field: " + field.name())  // 新增
```

关键点：
- `reader` 提取到循环开头，避免嵌套；
- 默认值通过 `RowDataUtil.convertConstant` 把 Iceberg `Object` 默认值转为 Flink `Object`；
- 默认值的 `maxDefinitionLevel` 从 `maxDefinitionLevelsById` 取（该字段在 expected schema 中的定义级别），确保 Parquet 读取时 definition level 语义正确；
- 加 `@SuppressWarnings("checkstyle:CyclomaticComplexity")` 因为 else-if 分支增多导致圈复杂度超标。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java`（修改）

**修改目的**：启用默认值测试并支持 schema 演进场景。

**工作逻辑**：

1. **`supportsDefaultValues()` 重写返回 `true`**：基类 `DataTest` 据此决定是否运行默认值相关测试用例（如写入无某字段的文件、读取时该字段应填默认值）。

2. **`writeAndValidate` 方法拆分**：原方法 `writeAndValidate(Iterable<Record>, Schema)` 改为 `writeAndValidate(Iterable<Record>, Schema writeSchema, Schema expectedSchema)`：
   - 用 `writeSchema` 写 Parquet 文件；
   - 用 `expectedSchema` 投影读取；
   - 断言用 `writeSchema.asStruct()` 与 `FlinkSchemaUtil.convert(writeSchema)` 比对（因为数据是按 writeSchema 写的）。
   这支持"写时无某字段、读时该字段有默认值"的演进场景。

3. **既有 `writeAndValidate(Schema)` 适配**：内部调用三参数版本，`writeSchema = expectedSchema = schema`（无演进的对称场景）。

4. **新增 `writeAndValidate(Schema writeSchema, Schema expectedSchema)` 重写**：基类 `DataTest` 的默认值测试调用此方法，生成随机数据用 `writeSchema` 写入、用 `expectedSchema` 读取验证。

## 小结

- **成效**：Flink 1.20 的 Iceberg Parquet 读取器支持 Iceberg v3 的 `initial-default` 语义——读取旧文件中缺失的字段时填充默认值而非 null；同时对 required 字段缺失的情况从静默填 null 改为抛异常，避免数据正确性问题。这使 Flink 1.20 集成与 Spark 等引擎的默认值行为对齐，符合 v3 规范。
- **影响范围**：仅 `flink/v1.20` 模块的 `FlinkParquetReaders`（生产代码）与 `TestFlinkParquetReader`（测试），2 个文件。行为变更：v3 表读取缺失字段时从返回 null 变为返回 `initial-default`；required 字段缺失时从返回 null 变为抛异常。
- **回迁到 1.4.x 的注意事项**：本提交是 Flink 1.20 专属（`flink/v1.20/`）。回迁需注意：1.4.x 上 `FlinkParquetReaders` 是否已有 `field.initialDefault()` API（该 API 来自 Iceberg core 的 v3 默认值支持，需 core 侧已回迁）；`RowDataUtil.convertConstant` 是否可用。行为变更（required 字段缺失抛异常）可能影响 1.4.x 上此前依赖"静默 null"行为的用户，回迁后需通知。`maxDefinitionLevelsById` 字段需在 `FlinkParquetReaders` 中已存在（这是 Parquet 读取的内部状态，通常已有）。若 1.4.x 同时支持 Flink 1.18/1.19 且有相同问题，需评估是否一并修复（参见提交 1626 对 1.18/1.19 的回迁）。
