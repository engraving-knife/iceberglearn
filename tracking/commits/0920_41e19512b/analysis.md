# 提交 0920：Rename & enforce constants to be all uppercase (#10673)

## 提交信息

- **序号**：0920 / 4088
- **哈希**：41e19512b996ac2768ca33d9b4c8c470ce40b76e
- **短哈希**：41e19512b
- **日期**：2024-07-11 15:42:38 +0200
- **作者**：Attila Kreiner <kreiner.attila@gmail.com>
- **提交说明**：Rename & enforce constants to be all uppercase (#10673)
- **PR/Issue**：#10673

## 总体目的

Java 编码规范要求 `static final` 常量使用全大写加下划线命名（SCREAMING_SNAKE_CASE）。Iceberg 代码库中存在大量违反此规范的常量，使用了驼峰命名，散布在生产代码和测试代码的各个模块中。此前提交 #10675（序号 0917）仅修复了 Flink 和 Spark 测试模块中的存量违规。

本提交有两个目的：
1. **修复存量**：将全代码库（api、core、aws、hive、nessie、parquet、pig、delta、aliyun 等所有模块）中剩余的驼峰命名 `static final` 常量统一重命名为全大写。
2. **强制约束**：在 Checkstyle 配置中新增 `ConstantName` 模块，强制校验所有 `static final` 常量必须匹配 `^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$` 模式，从 CI 层面防止未来引入新的违规命名。

通过"先修复存量、再启用规则"的两步策略，确保规则启用时不会因大量历史违规而导致构建失败。

## 如何达成设计目的

1. 在 `.baseline/checkstyle/checkstyle.xml` 中新增 `ConstantName` 模块，配置正则 `^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$`，违反时报错 "Constant name must match pattern"。
2. 遍历全代码库，将所有不匹配该模式的 `static final` 字段重命名为全大写，并同步更新所有引用处。
3. 对于枚举类中的 `static final` 字段（如 `HiveVersion.current`）和 `ThreadLocal`、`Map`、`Class<?>`、`DynConstructors` 等复杂类型的常量，同样执行重命名。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml`

**修改目的**：新增 Checkstyle `ConstantName` 规则，强制 `static final` 常量全大写命名。

**工作逻辑**：在 `MemberName` 模块之后新增：
```xml
<module name="ConstantName">
    <property name="format" value="^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$"/>
    <message key="name.invalidPattern" value="Constant name ''{0}'' must match pattern ''{1}''."/>
</module>
```
该规则匹配以大写字母开头、由大写字母/数字/下划线组成的常量名。Checkstyle 在构建时扫描所有 Java 源文件，发现违规即报错。

### `api/src/main/java/org/apache/iceberg/events/Listeners.java`

**修改目的**：将事件监听器注册表的常量重命名。

**工作逻辑**：`listeners` → `LISTENERS`，`register()` 和 `notifyAll()` 方法中的引用同步更新。

### `api/src/main/java/org/apache/iceberg/util/CharSequenceSet.java`

**修改目的**：将 `ThreadLocal` 包装器常量重命名。

**工作逻辑**：`wrappers` → `WRAPPERS`，`contains()` 和 `remove()` 方法中的引用同步更新。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3OutputStream.java`

**修改目的**：将 S3 输出流的摘要算法常量重命名。

**工作逻辑**：`digestAlgorithm` → `DIGEST_ALGORITHM`，构造函数和 `newStream()` 中 `MessageDigest.getInstance(digestAlgorithm)` 的引用同步更新。

### `core/src/main/java/org/apache/iceberg/avro/AvroIO.java`

**修改目的**：将 Avro IO 的多个反射相关常量重命名。

**工作逻辑**：
- `fsDataInputStreamClass` → `FS_DATA_INPUT_STREAM_CLASS`
- `relocated` → `RELOCATED`
- `avroFsInputCtor` → `AVRO_FS_INPUT_CTOR`
- `stream()` 方法中所有引用同步更新。

### `core/src/main/java/org/apache/iceberg/encryption/StandardKeyMetadata.java`

**修改目的**：将加密元数据常量重命名。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveVersion.java`

**修改目的**：将 Hive 版本枚举的当前版本常量重命名。

**工作逻辑**：`current` → `CURRENT`，`current()` 和 `min()` 方法中的引用同步更新。

### `mr/hive3/src/main/java/org/apache/iceberg/mr/hive/ql/exec/vector/VectorizedSupport.java`

**修改目的**：新增文件或补充常量声明以满足 Checkstyle 规则（1 行改动）。

### `pig/src/main/java/org/apache/iceberg/pig/IcebergStorage.java`

**修改目的**：将 Pig 存储模块的常量重命名（14 行改动）。

### 各模块测试文件（共约 30 个测试文件）

涉及模块：`aliyun`、`api`、`aws`（Glue 测试）、`core`（表达式、元数据表、Hadoop catalog 测试）、`data`（metrics 过滤器测试）、`delta`（Delta Lake 类型转换和快照测试）、`hive-metastore`（Hive 表和提交测试）、`hive3`（mr hive 测试）、`nessie`（多客户端和表测试）、`parquet`（Bloom/字典/加密过滤器测试）。

**修改目的**：将各测试类中的 `static final` 常量重命名为全大写。

**工作逻辑**：各文件中局部常量重命名，如 Glue 测试中的 `catalog` → `CATALOG`、`extensions` → `EXTENSIONS` 等，引用处同步更新。改动模式与生产代码一致。

## 小结

- **成效**：在全代码库范围内完成了 `static final` 常量从驼峰命名到全大写命名的统一重命名，并通过 Checkstyle `ConstantName` 规则从 CI 层面强制约束未来代码，防止新的违规命名引入。
- **影响范围**：涉及 40 个文件（1 个 Checkstyle 配置 + 约 10 个生产代码文件 + 约 29 个测试文件），横跨 api、core、aws、hive-metastore、hive3、nessie、parquet、pig、delta、aliyun 等模块，共 359 行新增、346 行删除。其中生产代码改动需注意 API/行为兼容性（虽为内部 `private` 字段，不影响公开 API）。
- **回迁到 1.4.x 的注意事项**：Checkstyle 规则和常量重命名均适合回迁，但需注意：(1) 回迁 Checkstyle 规则后，1.4.x 中所有存量违规也需同步修复，否则 CI 会失败，因此规则和重命名必须同时回迁；(2) 大部分常量是 `private` 字段，不影响公开 API 兼容性；(3) `HiveVersion.current` 虽然是 `private static final`，但 `HiveVersion.current()` 公开方法不变，无兼容性问题。建议整体回迁以保持代码规范一致性。
