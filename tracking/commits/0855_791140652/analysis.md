# 提交 0855：Fix JVM locale dependent casing (#10521)

## 提交信息
- **序号**：0855 / 4088
- **哈希**：791140652c39d9c912ac008c64edea7db6d047f0
- **短哈希**：791140652
- **日期**：2024-06-18
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Fix JVM locale dependent casing (#10521)
- **PR/Issue**：#10521

## 总体目的
本提交是一次跨多模块的国际化（i18n）健壮性修复，目的是消除项目中所有「使用 JVM 默认 locale 进行大小写转换」的调用点，改为显式使用 `Locale.ROOT`，从而保证大小写转换在不同 locale 的 JVM 上行为一致。

Java 中 `String.toLowerCase()` 与 `String.toUpperCase()` 的无参重载会使用 JVM 默认 locale（由 `Locale.getDefault()` 决定）。在土耳其语（tr-TR）locale 下，`String.toLowerCase()` 会把 ASCII 的 `I` 转换为 `ı`（无点小写 i，U+0131）而非 `i`；`String.toUpperCase()` 会把 `i` 转换为 `İ`（带点大写 I，U+0130）而非 `I`。这一「土耳其语 I 问题」会导致：

- 协议/格式判定失效：例如把 OSS/ ECS 的 scheme `https` 与 `HTTPS` 用 `toLowerCase()` 比对时，在土耳其语 locale 下转换结果与预期不符，导致合法 location 被误判为非法。
- 元数据标识符错位：例如 Hive 元存储中表类型值 `ICEBERG` 经 `toUpperCase()` 后在 tr locale 下结果异常，导致 `TABLE_TYPE_PROP` 写入与读取时无法正确匹配。
- 文件名/路径匹配失败：例如按文件格式后缀 `.parquet`/`.orc` 过滤文件时，因后缀转换异常而漏匹配。
- 测试结果不确定：在 tr locale JVM 上跑测试时，断言中 `toLowerCase()` 产生的字符串与期望值不一致，测试偶发性失败。
- 加密密钥别名错位：`KeyStoreKmsClient` 中 keytool 把 key 名称以小写存储，若 `wrappingKeyId.toLowerCase()` 在 tr locale 下产生 `ı` 而非 `i`，会导致 wrap/unwrap 时查找的 key 别名不一致，加解密失败。

本提交把所有这类无参 `toLowerCase()`/`toUpperCase()` 调用统一改为 `toLowerCase(Locale.ROOT)`/`toUpperCase(Locale.ROOT)`，确保转换结果只由 Unicode 规则决定，与运行环境 locale 解耦。这是 Java 国际化编程的标准最佳实践。

## 如何达成设计目的
提交的达成路径是机械式的「查找-替换」：扫描项目中所有 `String.toLowerCase()` 与 `String.toUpperCase()` 的无参调用，统一改为带 `Locale.ROOT` 参数的版本，并在对应文件的 import 区新增 `import java.util.Locale;`。

之所以选择 `Locale.ROOT` 而非 `Locale.US` 或 `Locale.ENGLISH`，是因为 `Locale.ROOT` 是「语言中性」的 locale，语义上最贴近「与 locale 无关的大小写转换」这一意图——它既不偏向任何特定语言，又满足「使用 Unicode 默认大小写规则」的需求。这与 JDK 文档中推荐「用于程序化标识符比较时使用 `Locale.ROOT`」的建议一致。

修复点覆盖：scheme 校验（OSSURI、EcsURI）、表标识符小写化（TableIdentifier.toLowerCase）、Avro 字段名编码（AvroSchemaUtil 中 `_x<hex>` 的十六进制大写化）、S3 签名中 header 名小写比对（S3SignerServlet 与 TestS3RestSigner）、Hive 表类型值大写化（HiveIcebergMetaHook、TestHiveIcebergStorageHandlerNoScan）、Hive 字段名小写化（IcebergRecordObjectInspector）、Hive 配置 key 元素类型枚举（CachedClientPool 中 `KeyElementType.valueOf(trimmed.toUpperCase())`）、Hive 向量化支持枚举（VectorizedSupport.Support 构造器）、加密 key 别名小写化（KeyStoreKmsClient.wrapKey/unwrapKey）、测试中的类型名小写化（TestHiveIcebergStorageHandlerWithEngine 中按 `type.typeId().toString().toLowerCase()` 构造表名/列名）、文件格式后缀小写匹配（TestIcebergStreamWriter）、测试中元数据表类名小写匹配（TestStaticTable）、测试中数据大写化（TestBaseTaskWriter 中 `data.toUpperCase()`）。

Flink 的 `TestIcebergStreamWriter` 在三个版本目录（v1.16、v1.17、v1.18）下分别有同一份代码副本，三个副本都做了相同修改。

## 修改详情
### `aliyun/src/main/java/org/apache/iceberg/aliyun/oss/OSSURI.java`
**修改目的**：scheme 校验时使用 locale 无关的小写化。
**工作逻辑**：构造器中 `VALID_SCHEMES.contains(scheme.toLowerCase())` 改为 `scheme.toLowerCase(Locale.ROOT)`，避免在土耳其语 locale 下 `https` 中的 `i` 经大写-小写往返后变成 `ı` 导致合法 scheme 被误判非法。新增 `import java.util.Locale;`。

### `api/src/main/java/org/apache/iceberg/catalog/TableIdentifier.java`
**修改目的**：`toLowerCase()` 方法生成小写表标识符时使用 locale 无关的大小写转换。
**工作逻辑**：`TableIdentifier.toLowerCase()` 用于把表名（含 namespace 各级）小写化以用于大小写不敏感的对比。原实现 `name().toLowerCase()` 在 tr locale 下可能把 `I` 开头的表名转成 `ı` 开头，导致后续与 Hive/REST catalog 中的表名不匹配。改为 `name().toLowerCase(Locale.ROOT)`。注意 namespace 各级的小写化仍用 `String::toLowerCase`（流式方法引用），因 `toLowerCase(Locale.ROOT)` 无法直接作为方法引用，未在本次修改范围；仅 `name()` 一行被改。新增 `import java.util.Locale;`。

### `aws/src/test/java/org/apache/iceberg/aws/s3/signer/S3SignerServlet.java`
**修改目的**：S3 签名时 unsigned/signed header 分组使用 locale 无关的小写化。
**工作逻辑**：`UNSIGNED_HEADERS.contains(e.getKey().toLowerCase())` 在两处（unsigned/signed 分支）均改为 `e.getKey().toLowerCase(Locale.ROOT)`，保证 header 名（如 `x-amz-content-sha256`）在土耳其语 locale 下小写化后仍能正确匹配 `UNSIGNED_HEADERS` 集合，否则可能导致本应签名的 header 被误归为 unsigned，或反之，破坏签名正确性。新增 `import java.util.Locale;`。

### `aws/src/test/java/org/apache/iceberg/aws/s3/signer/TestS3RestSigner.java`
**修改目的**：测试中 unsigned headers 过滤使用 locale 无关的小写化，与 `S3SignerServlet` 保持一致。
**工作逻辑**：测试中复制了与生产代码一致的 `UNSIGNED_HEADERS.contains(e.getKey().toLowerCase())` 过滤逻辑，改为 `e.getKey().toLowerCase(Locale.ROOT)`。新增 `import java.util.Locale;`。

### `core/src/main/java/org/apache/iceberg/avro/AvroSchemaUtil.java`
**修改目的**：Avro 字段名编码中十六进制大写化使用 locale 无关的大小写转换。
**工作逻辑**：`makeSafeName` 方法把 Avro 中非法的字段名字符编码为 `_x<hex>` 形式，原 `Integer.toHexString(character).toUpperCase()` 在 tr locale 下可能把 `i`（若 hex 中含 i）转成 `İ`，破坏 hex 编码的可逆性。改为 `toUpperCase(Locale.ROOT)`。新增 `import java.util.Locale;`。

### `core/src/test/java/org/apache/iceberg/encryption/KeyStoreKmsClient.java`
**修改目的**：加密 key 别名小写化使用 locale 无关的大小写转换。
**工作逻辑**：`wrapKey` 与 `unwrapKey` 中 `wrappingKeyId.toLowerCase()` 用于与 keytool 存储 key 时使用的小写名匹配，在 tr locale 下可能把 `I` 开头的 key 别名转成 `ı` 开头，导致 wrap 时写入的别名与 unwrap 时查找的别名不一致，加解密失败。两处均改为 `toLowerCase(Locale.ROOT)`。新增 `import java.util.Locale;`。

### `core/src/test/java/org/apache/iceberg/hadoop/TestStaticTable.java`
**修改目的**：测试中元数据表类名小写匹配使用 locale 无关的大小写转换。
**工作逻辑**：`testMetadataTables` 中 `type.name().replace("_", "").toLowerCase()` 与 `getStaticTable(type).getClass().getName().toLowerCase()` 两处均改为 `toLowerCase(Locale.ROOT)`，保证在 tr locale JVM 上跑测试时断言中 `contains(enumName)` 仍能正确匹配。新增 `import java.util.Locale;`。

### `data/src/test/java/org/apache/iceberg/io/TestBaseTaskWriter.java`
**修改目的**：测试中数据大写化使用 locale 无关的大小写转换。
**工作逻辑**：测试构造记录时 `createRecord(id, data.toUpperCase())` 改为 `data.toUpperCase(Locale.ROOT)`，保证测试期望值在 tr locale JVM 上不产生异常字符。新增 `import java.util.Locale;`。

### `dell/src/main/java/org/apache/iceberg/dell/ecs/EcsURI.java`
**修改目的**：ECS scheme 校验时使用 locale 无关的小写化。
**工作逻辑**：构造器中 `VALID_SCHEME.contains(uri.getScheme().toLowerCase())` 改为 `uri.getScheme().toLowerCase(Locale.ROOT)`，避免在土耳其语 locale 下合法 ECS scheme 被误判非法。新增 `import java.util.Locale;`。

### `flink/v1.16|v1.17|v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergStreamWriter.java`
**修改目的**：测试中按文件格式后缀过滤文件时使用 locale 无关的小写化。
**工作逻辑**：`path.getName().endsWith("." + format.toString().toLowerCase())` 改为 `format.toString().toLowerCase(Locale.ROOT)`，保证 `parquet`/`orc`/`avro` 后缀在 tr locale JVM 上仍能正确匹配，避免测试因后缀漏匹配而失败。三个 Flink 版本目录下做相同修改。新增 `import java.util.Locale;`。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/CachedClientPool.java`
**修改目的**：Hive 客户端池配置 key 元素类型枚举值大写化使用 locale 无关的大小写转换。
**工作逻辑**：`KeyElementType.valueOf(trimmed.toUpperCase())` 改为 `trimmed.toUpperCase(Locale.ROOT)`，避免在 tr locale 下把 `user_name` 中的 `i` 转成 `İ`，导致 `KeyElementType.valueOf` 抛 `IllegalArgumentException`。新增 `import java.util.Locale;`。

### `mr/src/main/java/org/apache/hadoop/hive/ql/exec/vector/VectorizedSupport.java`
**修改目的**：Hive 向量化支持枚举名小写化使用 locale 无关的大小写转换。
**工作逻辑**：枚举 `Support` 的构造器中 `this.lowerCaseName = name().toLowerCase()` 改为 `name().toLowerCase(Locale.ROOT)`，避免在 tr locale 下枚举名（如 `DECIMAL_64`）小写化后包含异常字符，导致 `nameToSupportMap` 查找失败。新增 `import java.util.Locale;`。

### `mr/src/main/java/org/apache/iceberg/mr/hive/HiveIcebergMetaHook.java`
**修改目的**：Hive 元存储表类型值大写化使用 locale 无关的大小写转换。
**工作逻辑**：`BaseMetastoreTableOperations.ICEBERG_TABLE_TYPE_VALUE.toUpperCase()` 改为 `toUpperCase(Locale.ROOT)`，避免在 tr locale 下 `ICEBERG` 中的 `I` 大写化结果异常，导致 Hive 元存储中 `TABLE_TYPE_PROP` 值与读取方期望不一致。新增 `import java.util.Locale;`。

### `mr/src/main/java/org/apache/iceberg/mr/hive/serde/objectinspector/IcebergRecordObjectInspector.java`
**修改目的**：Hive 字段名小写化使用 locale 无关的大小写转换。
**工作逻辑**：`field.name().toLowerCase()` 改为 `field.name().toLowerCase(Locale.ROOT)`，避免在 tr locale 下字段名（含 `i`/`I`）小写化结果异常，导致 Hive serde 与 Iceberg schema 字段对应错位。新增 `import java.util.Locale;`。

### `mr/src/test/java/org/apache/iceberg/mr/hive/TestHiveIcebergStorageHandlerNoScan.java`
**修改目的**：测试中断言 Hive 表类型值大写化使用 locale 无关的大小写转换。
**工作逻辑**：断言 `BaseMetastoreTableOperations.ICEBERG_TABLE_TYPE_VALUE.toUpperCase()` 改为 `toUpperCase(Locale.ROOT)`，与 `HiveIcebergMetaHook` 的写入行为保持一致。新增 `import java.util.Locale;`。

### `mr/src/test/java/org/apache/iceberg/mr/hive/TestHiveIcebergStorageHandlerWithEngine.java`
**修改目的**：测试中按类型名小写化构造表名/列名使用 locale 无关的大小写转换。
**工作逻辑**：测试中多处 `type.typeId().toString().toLowerCase()` 用于构造表名/列名（如 `long_table_0`、`long_column`），改为 `toLowerCase(Locale.ROOT)`，避免在 tr locale JVM 上跑测试时表名/列名包含异常字符导致 Hive 查询失败。新增 `import java.util.Locale;`。

## 小结
- **成效**：消除了 Iceberg 在多模块中对 JVM 默认 locale 的依赖，所有 `toLowerCase()`/`toUpperCase()` 无参调用统一改为 `Locale.ROOT` 版本，使项目在土耳其语（及任何特殊大小写规则）locale 的 JVM 上行为与英语 locale 完全一致。修复了潜在的协议判定、元数据匹配、加解密、文件匹配、测试确定性等多类问题。
- **影响范围**：涉及 18 个文件、48 行新增 / 26 行删除，分布在 aliyun、api、aws、core、data、dell、flink（三个版本目录）、hive-metastore、mr 多个模块。生产代码与测试代码均被覆盖。修改本身是机械替换，但语义影响深远——在非英语 locale JVM 上修复了若干潜在 bug。
- **回迁注意事项**：回迁到 1.4.x 风险较低，因为修改是机械的「`toLowerCase()` → `toLowerCase(Locale.ROOT)`」替换，无逻辑变更。需注意：1.4.x 上某些文件路径可能与 main 不同（例如 Hive 模块在 1.4.x 上的代码布局、Flink 版本目录集合），应按 1.4.x 实际存在的文件回迁。另外需扫描 1.4.x 上是否还有遗漏的 `toLowerCase()`/`toUpperCase()` 无参调用点未被本提交覆盖（本提交是基于 main 分支扫描的，1.4.x 上可能有 main 已删除但仍存在的旧调用点需要一并修复）。`TableIdentifier.toLowerCase()` 中 namespace 各级的小写化仍用 `String::toLowerCase` 方法引用，本次未改（因方法引用形式无法直接传 `Locale.ROOT`），1.4.x 上如需彻底修复可考虑改为显式 lambda。本提交与上下游无功能耦合，可独立回迁。
