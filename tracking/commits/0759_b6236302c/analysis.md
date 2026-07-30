# 提交 0759：Parquet: Add Bloom filter FPP config (#10149)

## 提交信息

- **序号**：0759 / 4088
- **哈希**：b6236302c4d215e8f4fcab43571700093972887a
- **短哈希**：b6236302c
- **日期**：2024-05-13 12:47:51 -0700
- **作者**：Huaxin Gao
- **提交说明**：Parquet: Add Bloom filter FPP config (#10149)
- **PR/Issue**：#10149

## 总体目的

本提交为 Iceberg 的 Parquet 写入路径新增"每列 Bloom filter 误判率（False Positive Probability，FPP）"配置能力。在此提交之前，Iceberg 已支持通过表属性 `write.parquet.bloom-filter-enabled.column.<col>` 为指定列开启 Parquet Bloom filter，并通过 `write.parquet.bloom-filter-max-bytes` 限制 Bloom filter 位数组的最大字节数，但用户无法精细控制每列 Bloom filter 的误判率。Bloom filter 的误判率直接决定其在固定元素数量下的位数组大小与哈希函数个数：FPP 越低，位数组越大、哈希函数越多，过滤效果越好但空间与计算开销也越大；FPP 越高则相反。不同列的数据分布与查询模式对 FPP 的需求不同（例如高基数主键列希望更低的 FPP 以减少误判带来的多余 IO，而辅助列可能接受较高 FPP 以节省空间），因此提供按列配置 FPP 的能力，让用户能在过滤效果与空间开销之间为每列单独权衡。

## 如何达成设计目的

### 设计思路

Bloom filter 的 FPP 是 Parquet 写入侧 `ParquetProperties` 已原生支持的配置项（`ParquetProperties.Builder.withBloomFilterFPP(ColumnPath, double)`），但 Iceberg 此前的 `Parquet` 写入构建器只暴露了 `withBloomFilterEnabled`（是否开启）与 `withBloomFilterMaxBytes`（最大字节数）两个旋钮，没有把 FPP 透传给底层 Parquet writer。本提交的设计是在 Iceberg 表属性层新增一个按列前缀的配置键 `write.parquet.bloom-filter-fpp.column.<col>`，值为 double 类型字符串（如 `"0.05"`），默认值 `0.01`；然后在 `Parquet` 写入构建器的 `WriteContext` 中读取该前缀下的所有列配置，在构建 `ParquetProperties` 与 `ParquetWriter` 时逐列调用 `withBloomFilterFPP` 透传给底层。这与既有的 `bloom-filter-enabled.column.` 前缀模式完全对称，保持配置风格一致。

### FPP 与 Bloom filter 的工作原理

Bloom filter 是一种概率型数据结构，用于判断某元素"可能在集合中"或"一定不在集合中"。对查询引擎而言，读取 Parquet 文件时可以利用列的 Bloom filter 快速跳过不包含待查值的 row group，从而减少不必要的 IO。FPP 是"误判率"——即元素实际不在集合中却被判定为"可能在"的概率。给定预期元素数 n 和目标 FPP p，Bloom filter 所需位数 m ≈ -(n * ln p) / (ln 2)^2，哈希函数个数 k ≈ (m/n) * ln 2。因此 FPP 是决定 Bloom filter 空间与时间开销的核心参数。默认 0.01 意味着约 1% 的误判率，是一个在过滤效果与空间开销间较为平衡的常用值；允许按列覆盖则满足差异化需求。

### 实现路径

1. **新增表属性常量**：在 `TableProperties` 中定义 `PARQUET_BLOOM_FILTER_COLUMN_FPP_PREFIX = "write.parquet.bloom-filter-fpp.column."` 与默认值 `PARQUET_BLOOM_FILTER_COLUMN_FPP_DEFAULT = 0.01`。
2. **WriteContext 解析配置**：在 `Parquet.WriteContext` 中通过 `PropertyUtil.propertiesWithPrefix(config, PARQUET_BLOOM_FILTER_COLUMN_FPP_PREFIX)` 提取所有以该前缀开头的表属性，得到 `Map<列路径, FPP字符串>`，存入 `WriteContext` 字段。
3. **写入时透传**：在 `Parquet` 写入构建器的两处构建路径（`ParquetProperties` 构建路径与 `ParquetWriteBuilder` 路径）遍历该 Map，逐列调用 `propsBuilder.withBloomFilterFPP(colPath, Double.parseDouble(fpp))` / `parquetWriteBuilder.withBloomFilterFPP(colPath, Double.parseDouble(fpp))`，把 FPP 传给底层 Parquet writer。
4. **文档同步**：在 `docs/docs/configuration.md` 的表属性表格中新增 `write.parquet.bloom-filter-fpp.column.col1` 一行，标注默认值 0.01 与取值约束（必须 > 0.0 且 < 1.0）。
5. **测试验证**：新增 `testFpp` 测试，通过反射访问 `ParquetWriter` 私有 `props` 字段，读取写入侧实际生效的 FPP 并断言与配置值一致。

### 两处构建路径的处理

`Parquet.java` 中存在两处需要透传 FPP 的构建路径，原因是 Iceberg 的 Parquet 写入有两条入口：
- **`ParquetProperties` 构建路径**（约第 349 行）：用于构建 `org.apache.iceberg.parquet.ParquetWriter`，这是 Iceberg 自有的 Parquet writer 封装，在内部构建 `ParquetProperties` 时通过 `propsBuilder.withBloomFilterFPP` 设置。
- **`ParquetWriteBuilder` 路径**（约第 392 行）：用于构建底层 `org.apache.parquet.hadoop.ParquetWriter`（通过 `ParquetWriteAdapter` 适配），在 `parquetWriteBuilder` 上直接调用 `withBloomFilterFPP`。

两处都需要遍历 `columnBloomFilterFpp` 并透传，确保无论走哪条写入路径，FPP 配置都能生效。这种对称处理与既有的 `bloom-filter-enabled` 处理方式完全一致。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java`

**修改目的**：新增 Bloom filter 按列 FPP 配置的属性键前缀与默认值常量。

**工作逻辑**：在已有的 `PARQUET_BLOOM_FILTER_MAX_BYTES` 系列常量之后，新增：

```java
public static final String PARQUET_BLOOM_FILTER_COLUMN_FPP_PREFIX =
    "write.parquet.bloom-filter-fpp.column.";
public static final double PARQUET_BLOOM_FILTER_COLUMN_FPP_DEFAULT = 0.01;
```

前缀常量用于通过 `PropertyUtil.propertiesWithPrefix` 批量提取按列配置；默认值常量 `0.01` 作为文档参考（实际解析时若某列未配置 FPP，则不会出现在前缀 Map 中，底层 Parquet 自有默认行为）。注意常量声明顺序放在 `PARQUET_BLOOM_FILTER_COLUMN_ENABLED_PREFIX` 之前，仅是源码排版，不影响逻辑。

### `docs/docs/configuration.md`

**修改目的**：在表属性配置表格中记录新增的按列 FPP 配置项。

**工作逻辑**：在 `write.parquet.bloom-filter-max-bytes` 行之后新增一行：

```
| write.parquet.bloom-filter-fpp.column.col1 | 0.01 | The false positive probability for a bloom filter applied to 'col1' (must > 0.0 and < 1.0) |
```

同时把上一行 `write.parquet.bloom-filter-enabled.column.col1` 的说明中的列名引用从 `col1` 规范化为 `'col1'`（加引号），与新行风格保持一致。

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java`

**修改目的**：在 Parquet 写入构建器中读取并透传按列 FPP 配置。

**工作逻辑**：共 6 处改动：

1. **import**：新增 `import static org.apache.iceberg.TableProperties.PARQUET_BLOOM_FILTER_COLUMN_FPP_PREFIX;`。

2. **`build` 方法（`ParquetProperties` 路径，约第 285 行）**：从 `context` 取出 `Map<String, String> columnBloomFilterFpp = context.columnBloomFilterFpp();`，与既有的 `columnBloomFilterEnabled` 并列。随后在构建 `ParquetProperties` 时（约第 349 行），在已有的 `bloom-filter-enabled` 遍历之后，新增遍历：

   ```java
   for (Map.Entry<String, String> entry : columnBloomFilterFpp.entrySet()) {
     String colPath = entry.getKey();
     String fpp = entry.getValue();
     propsBuilder.withBloomFilterFPP(colPath, Double.parseDouble(fpp));
   }
   ```

   将每列 FPP 通过 `propsBuilder.withBloomFilterFPP` 传给 `ParquetProperties.Builder`。

3. **`build` 方法（`ParquetWriteBuilder` 路径，约第 392 行）**：对称地，在 `parquetWriteBuilder` 上新增同样的遍历，调用 `parquetWriteBuilder.withBloomFilterFPP(colPath, Double.parseDouble(fpp))`，把 FPP 传给底层 `org.apache.parquet.hadoop.ParquetWriter` 的构建器。

4. **`WriteContext` 字段（约第 412 行）**：新增字段 `private final Map<String, String> columnBloomFilterFpp;`，并在构造函数参数中新增 `Map<String, String> columnBloomFilterFpp`（位于 `bloomFilterMaxBytes` 与 `columnBloomFilterEnabled` 之间），在构造体中赋值 `this.columnBloomFilterFpp = columnBloomFilterFpp;`。

5. **`WriteContext.from` 静态工厂（约第 495 行）**：新增从配置 Map 解析按列 FPP：

   ```java
   Map<String, String> columnBloomFilterFpp =
       PropertyUtil.propertiesWithPrefix(config, PARQUET_BLOOM_FILTER_COLUMN_FPP_PREFIX);
   ```

   并把它作为构造参数传给 `WriteContext`。`PropertyUtil.propertiesWithPrefix` 会提取所有以 `write.parquet.bloom-filter-fpp.column.` 开头的表属性，去掉前缀后以列路径为键、FPP 字符串为值返回。

6. **`WriteContext` 默认实例（约第 583 行）**：在 `Parquet` 类内部的默认 `WriteContext` 构造（无配置时）中，新增 `ImmutableMap.of()` 作为 `columnBloomFilterFpp` 的空默认值，与 `columnBloomFilterEnabled` 的空默认值对称。

7. **访问器（约第 631 行）**：新增 `Map<String, String> columnBloomFilterFpp()` 访问方法，返回 `columnBloomFilterFpp` 字段。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkParquetWriter.java`

**修改目的**：验证按列 FPP 配置能正确透传到 Parquet writer 的 `ParquetProperties`。

**工作逻辑**：

1. **新增 Schema 常量**：定义 `SCHEMA`，含两个 required 列 `id`（IntegerType）与 `id_long`（LongType），用于测试写入。

2. **新增 `testFpp` 测试方法**：
   - 创建临时 Parquet 文件。
   - 用 `Parquet.write(...)` 构建 writer，设置：
     - `PARQUET_BLOOM_FILTER_COLUMN_ENABLED_PREFIX + "id" = "true"`（为 `id` 列开启 Bloom filter）
     - `PARQUET_BLOOM_FILTER_COLUMN_FPP_PREFIX + "id" = "0.05"`（为 `id` 列设置 FPP 为 0.05）
   - 通过反射访问 `ParquetWriter` 的私有字段 `props`（`ParquetProperties` 类型）。
   - 用 `ParquetSchemaUtil.convert(SCHEMA, "test")` 得到 Parquet 的 `MessageType`，取 `id` 列的 `ColumnDescriptor`。
   - 调用 `props.getBloomFilterFPP(descriptor).getAsDouble()` 读取实际生效的 FPP。
   - 断言 `fpp == 0.05`，验证配置已透传。

   使用反射的原因是 `ParquetWriter.props` 是私有字段，无公开访问器，测试需要直接读取底层实际生效的 `ParquetProperties` 以验证透传正确性，而非仅靠写入文件的间接行为。这保证了配置链路（表属性 → `WriteContext` → `ParquetProperties.Builder` → `ParquetWriter`）端到端正确。

## 小结

- **成效**：为 Iceberg Parquet 写入路径新增按列 Bloom filter 误判率（FPP）配置能力，用户可通过表属性 `write.parquet.bloom-filter-fpp.column.<col>` 为每列单独设定 FPP（默认 0.01），在过滤效果与空间开销之间按列精细权衡。配置模式与既有的 `bloom-filter-enabled.column.` 前缀完全对称，风格一致。两条写入路径（`ParquetProperties` 与 `ParquetWriteBuilder`）均透传 FPP，保证一致性。测试通过反射验证 FPP 端到端透传正确。
- **影响范围**：影响 Parquet 写入路径（`parquet` 模块的 `Parquet.java` 写入构建器与 `WriteContext`）及表属性定义（`core` 模块的 `TableProperties`）。对已开启 Bloom filter 的列，若未显式配置 FPP，行为不变（使用底层 Parquet 默认 FPP），因此向后兼容；仅当用户显式设置新的 `bloom-filter-fpp.column.` 属性时才改变写入行为。文档同步更新。读取侧无需改动（Bloom filter 的读取与利用由 Parquet reader 与查询引擎负责）。
- **回迁注意事项**：
  1. 此提交涉及 `core`、`parquet`、`docs`、`spark/v3.5`（测试）四个模块，回迁到 1.4.x 分支需逐文件 cherry-pick 或整体应用。
  2. `Parquet.java` 的 `WriteContext` 构造函数新增了 `columnBloomFilterFpp` 参数，若 1.4.x 分支的 `WriteContext` 已有其他构造参数调整，cherry-pick 时需手动对齐参数顺序与位置。两处构建路径（`ParquetProperties` 与 `ParquetWriteBuilder`）的遍历插入点需与 1.4.x 分支的代码上下文匹配。
  3. `TableProperties` 新增的两个常量（`PARQUET_BLOOM_FILTER_COLUMN_FPP_PREFIX`、`PARQUET_BLOOM_FILTER_COLUMN_FPP_DEFAULT`）是纯新增，回迁无冲突风险。
  4. 测试 `testFpp` 使用反射访问 `ParquetWriter` 私有字段 `props`，依赖 `ParquetWriter` 的字段名与 `ParquetProperties.getBloomFilterFPP(ColumnDescriptor)` API 在 1.4.x 分支的 Parquet 依赖版本中存在，回迁时需确认 1.4.x 使用的 Parquet 版本支持该方法（`getBloomFilterFPP` 返回 `OptionalDouble`，需 Parquet 1.13+）。
  5. FPP 配置不在本提交中做取值校验（`must > 0.0 and < 1.0` 仅是文档说明），实际解析时 `Double.parseDouble(fpp)` 若传入非法值（如 `0`、`1`、负数、非数字）会抛 `NumberFormatException` 或由底层 Parquet 校验失败。回迁后若需更严格的校验，需额外补丁，但本提交行为与上游一致。
