# 提交 1282：[KafkaConnect] Fix RecordConverter for UUID and Fixed Types (#11346)

## 提交信息

- **序号**：1282 / 4088
- **哈希**：9ecd97bf4538ca94276fb019c2ec477d28e4bf7c
- **短哈希**：9ecd97bf4
- **日期**：2024-10-25（Fri Oct 25 13:39:48 2024 -0700）
- **作者**：Prashant Singh <35593236+singhpk234@users.noreply.github.com>
- **提交说明**：[KafkaConnect] Fix RecordConverter for UUID and Fixed Types (#11346)
- **PR/Issue**：#11346

## 总体目的

Kafka Connect 集成模块（`kafka-connect`）中的 `RecordConverter` 负责把 Kafka Connect 侧传来的数据（`Map<String, Object>`）转换成 Iceberg 的 `Record`，供 IcebergSink 写入表。该转换器按 Iceberg schema 的 `Type` 分派到对应的 `convertXxx` 方法。本次修复其中两个类型转换缺陷：

1. **UUID 类型写入 Parquet 失败**：Iceberg 的 UUID 逻辑类型在 Parquet 中以 16 字节定长二进制存储，Iceberg 的 Parquet writer 期望拿到 `byte[]`（16 字节）而非 `java.util.UUID` 对象。旧版 `convertUUID` 始终返回 `UUID` 对象，导致写入 Parquet 时 UUID 字段无法被正确序列化（报错或写出错误数据）。

2. **Fixed 类型产出错误的数据载体**：Iceberg 数据模型中 `FixedType`（定长字节串）以 `byte[]` 表示，而 `BinaryType`（变长字节串）以 `ByteBuffer` 表示。旧代码把 `FIXED` 与 `BINARY` 共用同一分支，都返回 `ByteBuffer`，导致 Fixed 字段写出的载体类型与 Iceberg 约定不符。

本提交让 UUID 转换感知目标文件格式（仅 Parquet 转成 16 字节 `byte[]`，其余格式仍返回 `UUID` 对象），并让 Fixed 类型显式转换为 `byte[]`，从而修复这两类字段在 Kafka Connect 集成下的写入正确性。

## 如何达成设计目的

整体思路是**让转换结果随目标文件格式与 Iceberg 类型约定对齐**：

1. **UUID 的格式感知转换**：`convertUUID` 的返回类型由 `UUID` 放宽为 `Object`。在解析出 `UUID` 后，读取 `config.writeProps()` 中的 `TableProperties.DEFAULT_FILE_FORMAT`（小写比较），若为 `parquet` 则调用 `UUIDUtil.convert(uuid)` 得到 16 字节 `byte[]`；否则直接返回 `UUID` 对象。这样 Parquet 路径产出 `byte[]`（与 Parquet writer 期望一致），Avro/ORC 等路径仍产出 `UUID`（与这些格式的原生 UUID 支持一致）。

2. **Fixed 类型显式产出 `byte[]`**：把 `case FIXED` 从原来与 `BINARY` 共用分支中拆出，单独调用 `ByteBuffers.toByteArray(convertBase64Binary(value))`——先用既有的 `convertBase64Binary`（解码 Base64 得到 `ByteBuffer`），再用 `ByteBuffers.toByteArray` 转成 `byte[]`，符合 Iceberg Fixed 类型的载体约定。`BINARY` 仍走原逻辑返回 `ByteBuffer`。

3. **测试同步校正**：新增 Parquet 下 UUID 转换的专项测试；并修正既有 `createMapData` / `assertRecordValues`，使其输入与断言与新语义一致（Fixed 字段输入 `ByteBuffer`、断言 `byte[]`；Binary 字段输入 `ByteBuffer`、断言 `ByteBuffer`）。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/RecordConverter.java`

**修改目的**：修复 UUID（Parquet 场景）与 Fixed 类型的转换结果载体。

**工作逻辑**：

- 新增 import：`org.apache.iceberg.FileFormat`、`org.apache.iceberg.util.ByteBuffers`、`org.apache.iceberg.util.UUIDUtil`（`java.util.Locale` 已有）。
- 在 `convert(Object value, Type type, ...)` 的分派中：
  - `case BINARY:` 保留 `return convertBase64Binary(value);`（返回 `ByteBuffer`）；
  - 新增 `case FIXED: return ByteBuffers.toByteArray(convertBase64Binary(value));`（返回 `byte[]`），不再与 BINARY 共用分支。
- `convertUUID(Object value)` 由 `protected UUID` 改为 `protected Object`，逻辑改为：先解析出 `uuid`（支持 `String` 与 `UUID` 输入，其余抛 `IllegalArgumentException`），再判断 `config.writeProps().get(TableProperties.DEFAULT_FILE_FORMAT)` 是否等于 `"parquet"`（`FileFormat.PARQUET.name().toLowerCase(Locale.ROOT)`），是则 `return UUIDUtil.convert(uuid)`（16 字节 `byte[]`），否则 `return uuid`。

> 说明：`UUIDUtil.convert(UUID)` 定义于 `api` 模块，返回 `byte[]`（内部 `convertToByteBuffer(value).array()`，16 字节、BIG_ENDIAN）。这与 Iceberg Parquet writer 对 UUID 逻辑类型的写入期望一致。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/RecordConverterTest.java`

**修改目的**：覆盖 UUID 在 Parquet 下的转换，并校正 Fixed/Binary 字段的输入断言。

**工作逻辑**：

- 新增 import：`java.util.Locale`、`org.apache.iceberg.FileFormat`、`org.apache.iceberg.util.UUIDUtil`。
- 新增 `testUUIDConversionWithParquet()`：mock 一个含 `UUIDType` 字段的 `Table`，设置 `writeProps` 为 `DEFAULT_FILE_FORMAT=parquet`，构造 `RecordConverter` 转换含 UUID 字符串的数据，断言 `record.getField("uuid")` 等于 `UUIDUtil.convert(UUID_VAL)`（即 16 字节 `byte[]`）。
- `createMapData()` 由 `private` 改为 `public static`（供其他测试类复用）；其中 Fixed 字段 `f` 与 Binary 字段 `bi` 的输入由 `BYTES_VAL.array()` 改为直接放入 `BYTES_VAL`（`ByteBuffer`），更贴近真实 Kafka Connect 输入。
- `assertRecordValues(Record)` 调整断言：`f`（Fixed）断言为 `BYTES_VAL.array()`（`byte[]`），`bi`（Binary）断言为 `BYTES_VAL`（`ByteBuffer`），并将 `u`（UUID）的断言移至末尾（默认非 Parquet 格式下仍断言为 `UUID_VAL` 对象）。

## 小结

- **成效**：修复了 Kafka Connect 集成下 UUID 字段写入 Parquet 失败、以及 Fixed 字段产出错误载体（`ByteBuffer` 而非 `byte[]`）两个缺陷，使 UUID/Fixed 类型在 IcebergSink 中能被正确写入。
- **影响范围**：改动 2 个文件，新增 47 行、删除 10 行。仅涉及 `kafka-connect` 模块的 `RecordConverter` 及其测试，无 core/api 公共契约变更；改动向后兼容（仅修正产出值，不改方法签名对外语义——`convertUUID` 为 `protected`，放宽返回类型不破坏子类）。
- **回迁到 1.4.x 的注意事项**：
  - **模块路径需确认**：1.4.x 分支当前不存在 `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/RecordConverter.java` 这一文件路径（`git ls-tree` 未命中）。回迁前须先确认 1.4.x 是否包含 `kafka-connect` 模块以及 `RecordConverter` 的实际路径/结构是否与 main 一致；若 1.4.x 的 kafka-connect 模块结构不同，需按对应路径手动适配。
  - **依赖的工具类需存在**：改动用到 `org.apache.iceberg.util.UUIDUtil.convert(UUID)`（位于 `api` 模块）与 `org.apache.iceberg.util.ByteBuffers.toByteArray(ByteBuffer)`，回迁前确认 1.4.x 的这两个工具方法签名一致（均为既有稳定 API，预期可用）。
  - **风险**：属于缺陷修复，影响 Kafka Connect 写入路径，回迁后建议跑 `RecordConverterTest`（含新增的 `testUUIDConversionWithParquet`）与 kafka-connect 端到端写入测试（覆盖 Parquet 与 Avro/ORC 两种格式下的 UUID 字段）。
  - 若 1.4.x 不发布 kafka-connect 产物或该模块未受影响，可酌情不回迁。
