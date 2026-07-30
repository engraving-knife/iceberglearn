# 提交 1565：Parquet: Use compatible column name to set Parquet bloom filter (#11799)

## 提交信息

- **序号**：1565
- **哈希**：3dbb5cc429e1a52555dddbac62a6087bd651cc5c
- **短哈希**：3dbb5cc42
- **日期**：2025-01-10（Fri Jan 10 07:22:02 2025 -0800）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Parquet: Use compatible column name to set Parquet bloom filter (#11799)
- **PR/Issue**：#11799

## 总体目的

Iceberg 在写 Parquet 文件时支持为指定列启用 bloom filter，由 `WriteBuilder` 通过 `context.columnBloomFilterEnabled()` 和 `context.columnBloomFilterFpp()` 拿到配置（key 是 Iceberg 的列名，如 `incompatible-name`），再调用 ParquetWriter 的 `withBloomFilterEnabled(colPath, ...)` 与 `withBloomFilterFPP(colPath, ...)`。

问题在于：Parquet 在 schema 序列化时对列名做了"兼容化"处理——例如 Iceberg 字段名 `incompatible-name`（含连字符 `-`）在 Parquet 列路径里被改写为 `_incompatible_x2Dname`（连字符替换为 `_x2D`）。但旧代码直接把 Iceberg 列名当作 Parquet 列路径传给 `withBloomFilterEnabled`，导致 Parquet 在内部按真实列名查找时找不到对应列，bloom filter 被静默丢弃（不报错，但功能失效）。

本提交修复该 bug：在调用 Parquet 的 bloom filter 设置方法前，先把 Iceberg 列名经 field-id 解析为 Parquet 实际使用的列路径（如 `_incompatible_x2Dname`），确保配置真正生效。修复方式是利用 Parquet schema 中携带的 field-id 元数据建立 `fieldId → parquetColumnPath` 映射，再通过 Iceberg schema 把列名翻译为 field-id 后查表。

## 如何达成设计目的

1. 新增私有方法 `setBloomFilterConfig(Context, MessageType, BiConsumer<String, Boolean>, BiConsumer<String, Double>)`，统一处理两种 bloom filter 配置（enabled + FPP）。
2. 把原来散落在两处（`ParquetProperties.Builder` 与 `ParquetWriter.Builder`）的循环配置逻辑都替换为对该方法的调用，避免重复。
3. 关键映射：用 `parquetSchema.getColumns()` 流式构造 `Map<Integer, String> fieldIdToParquetPath`，key 是 `col.getPrimitiveType().getId().intValue()`（即 Parquet 类型上由 Iceberg 写入的 field-id），value 是 `String.join(".", col.getPath())`（Parquet 列路径，可能是兼容化后的名字）。
4. 遍历 `context.columnBloomFilterEnabled()`，对每个 Iceberg 列名：
   - 通过 `schema.findField(colPath)` 找到对应的 `Types.NestedField`（按名字查找）；找不到时打 warn 日志跳过。
   - 用 `fieldId` 在 `fieldIdToParquetPath` 中查到实际的 Parquet 列路径；查不到时同样 warn 跳过。
   - 调用 `withBloomFilterEnabled.accept(parquetColumnPath, ...)`。
   - 若 `context.columnBloomFilterFpp().get(colPath)` 非空，调用 `withBloomFilterFPP.accept(parquetColumnPath, ...)`。
5. 把 `Parquet` 类的常量从 `private static final` 改为可日志记录（新增 `LOG`），用于 warn 信息。
6. 顺手修正测试中三个方法名的拼写错误：`testIntDeciamlEq`/`testLongDeciamlEq`/`testFixedDeciamlEq` → `testIntDecimalEq`/`testLongDecimalEq`/`testFixedDecimalEq`。

### 修改详情

#### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java`
**修改目的**：把 Iceberg 列名转换为 Parquet 实际列路径后再设置 bloom filter。

**关键变更**：
- 新增 import：`java.util.function.BiConsumer`、`org.apache.iceberg.types.Types`、`org.slf4j.Logger/LoggerFactory`。
- 新增类常量 `private static final Logger LOG = LoggerFactory.getLogger(Parquet.class);`。
- 新增方法 `setBloomFilterConfig(...)`（见上）。
- `build()` 中两处原 `for` 循环（`columnBloomFilterEnabled` 与 `columnBloomFilterFpp`）替换为：
  ```java
  setBloomFilterConfig(context, type, propsBuilder::withBloomFilterEnabled, propsBuilder::withBloomFilterFPP);
  ```
  以及 `parquetWriteBuilder` 那一处对应的调用。这样把"按 Iceberg 列名"改为"按 Parquet 列路径"传入。
- 删除原 `build()` 中对 `columnBloomFilterFpp`/`columnBloomFilterEnabled` 局部变量的提前抓取（已由 `context` 内部访问）。

#### `parquet/src/test/java/org/apache/iceberg/parquet/TestBloomRowGroupFilter.java`
**修改目的**：覆盖"含不兼容字符的列名"场景，确保该列也能正确生成 bloom filter 并被 `ParquetBloomRowGroupFilter` 应用。

**关键变更**：
- `FILE_SCHEMA` 与对应的 `_`-前缀版本各新增一列 `optional(28, "incompatible-name", Types.DecimalType.of(8, 2))`，Parquet 兼容化后的名字为 `_incompatible_x2Dname`（在测试代码中通过 `compatibleFieldName` 变量引用）。
- 写入 50 条记录时为该列赋值 `77.77 + i`，并设置 `PARQUET_BLOOM_FILTER_COLUMN_ENABLED_PREFIX + "_incompatible-name" = "true"`，验证 Iceberg 列名能被正确映射到 Parquet 列路径，使 bloom filter 写入成功。
- 新增测试 `testIncompatibleColumnNameEq`：用 `equal("incompatible-name", ...)` 过滤，命中范围内应读（true），范围外不应读（false），证明 bloom filter 真正生效。
- 顺手修正 `testIntDeciamlEq`/`testLongDeciamlEq`/`testFixedDeciamlEq` 三个方法名的拼写错误（`Deciaml` → `Decimal`）。

## 小结

- **成效**：修复了 Iceberg 列名含 Parquet 不兼容字符（如 `-`）时 bloom filter 配置失效的 bug。修复后通过 field-id 把 Iceberg 列名映射到 Parquet 真实列路径，保证 bloom filter 按预期写入并被读路径使用。
- **影响范围**：仅 Parquet 模块的 `Parquet.java` 写入路径与对应测试。无 API 变更，对调用方透明。
- **回迁到 1.4.x 的注意事项**：这是一个 bug 修复，影响 bloom filter 在含特殊列名表上的可用性。1.4.x 若已包含 bloom filter 写入功能（`Parquet` 的 `setBloomFilterConfig` 逻辑），建议回迁以修复同样的 bug。回迁范围小（一个方法新增 + 两处调用点替换），冲突风险低。**建议回迁**。
