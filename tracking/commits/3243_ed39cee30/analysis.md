# 提交 3243：API, Spark: Support StringLiteral to Fixed and StringLiteral to Binary Conversions (#14882)

## 提交信息

- **序号**：3243 / 4088
- **哈希**：ed39cee30d4e229cb71bbcbc7faa081375fcb179
- **短哈希**：ed39cee30
- **日期**：2026-02-12
- **作者**：Prashant Singh
- **提交说明**：API, Spark: Support StringLiteral to Fixed and StringLiteral to Binary Conversions (#14882)
- **PR/Issue**：#14882

## 总体目的

本提交为 Iceberg 表达式系统补全了 `StringLiteral` 向 `Fixed`（固定长度字节数组）和 `Binary`（变长字节数组）类型的字面量转换能力。在 Iceberg 的表达式体系中，`Literal.to(Type)` 方法负责将一个字面量值转换为目标类型。此前 `StringLiteral` 已支持向 Boolean、Integer、Long、Float、Double、Decimal、Date、Time、Timestamp 等类型的转换，但缺少向 `Fixed` 和 `Binary` 类型的转换——这两个 case 落入 `default` 分支直接返回 `null`，表示不支持。

这一缺失导致了一个实际问题：在 REST Catalog 的远程扫描计划（remote scan planning）场景中，Spark 将过滤条件序列化为 JSON 发送给 REST 服务端，服务端通过 `ExpressionParser.fromJSON` 反序列化时，二进制/固定长度的字面量以十六进制字符串形式传输。服务端需要将这个字符串（`StringLiteral`）转换为 `BinaryLiteral` 或 `FixedLiteral` 来重建过滤表达式，但由于转换方法未实现，解析失败。这就是为什么 `TestRemoteScanPlanning.testBinaryInFilter` 测试此前被 `@Disabled` 并注释"binary filter that is used by Spark is not working because ExpressionParser.fromJSON doesn't have the Schema to properly parse the filter expression"的原因。

本提交通过在 `StringLiteral.to()` 方法中新增 `case FIXED` 和 `case BINARY` 分支，使用 Base16（十六进制）解码将字符串转换为字节缓冲区，从而补全了这一转换链路。同时在四个 Spark 版本中移除了被禁用的 `testBinaryInFilter` 测试（现可正常运行），并新增了 `testFixedInFilter` 测试覆盖 Fixed 类型的过滤场景。

## 如何达成设计目的

在 `Literals.java` 的 `StringLiteral` 内部类的 `to(Type)` 方法中新增两个 case 分支：`FIXED` 和 `BINARY`。两者都使用 Guava 的 `BaseEncoding.base16()` 将十六进制字符串解码为字节数组，包装为 `ByteBuffer` 后创建对应的 `FixedLiteral` 或 `BinaryLiteral`。Fixed 类型额外校验解码后的字节长度是否与 `Types.FixedType` 声明的长度一致，不一致则返回 `null`。解码前对字符串做 `toUpperCase(Locale.ROOT)` 以同时支持大小写十六进制。解码失败（非法十六进制字符）时捕获 `IllegalArgumentException` 返回 `null`。同时在四个 Spark 版本的 `TestRemoteScanPlanning` 中移除被 `@Disabled` 的 `testBinaryInFilter`，在 `TestSelect` 中新增 `testFixedInFilter` 测试。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/Literals.java` (+30/-2 lines)

**修改目的**：为 `StringLiteral.to()` 方法新增向 Fixed 和 Binary 类型的转换逻辑。

**工作逻辑**：

- 新增静态常量 `BASE16_ENCODING = BaseEncoding.base16()`，并新增 `import java.util.Locale`，避免重复创建编码实例。

- `case FIXED`：在 `StringLiteral.to()` 方法中新增。先通过 `BASE16_ENCODING.decode(value().toString().toUpperCase(Locale.ROOT))` 将十六进制字符串解码为字节数组并包装为 `ByteBuffer`。然后获取目标类型 `Types.FixedType fixed = (Types.FixedType) type`，检查 `buffer.remaining() == fixed.length()`——只有解码后的字节长度与 Fixed 类型声明的长度完全一致时才创建 `new FixedLiteral(buffer)` 返回；长度不匹配返回 `null`。整个解码过程用 try-catch 捕获 `IllegalArgumentException`（非法十六进制字符串），异常时返回 `null`。这与该方法中其他转换失败返回 `null` 的约定一致。

- `case BINARY`：类似地，将十六进制字符串解码为字节数组包装为 `ByteBuffer`，创建 `new BinaryLiteral(buffer)` 返回。Binary 是变长类型，无需长度校验。同样捕获 `IllegalArgumentException` 返回 `null`。

- `BinaryLiteral.toString()` 和 `FixedLiteral.toString()` 的优化：将原来每次调用时 `BaseEncoding.base16().encode(bytes)` 改为复用 `BASE16_ENCODING.encode(bytes)` 常量实例，避免重复创建 `BaseEncoding` 对象。

### `api/src/test/java/org/apache/iceberg/expressions/TestMiscLiteralConversions.java` (+56/-0 lines)

**修改目的**：验证 StringLiteral 向 Fixed 和 Binary 的转换逻辑。

**工作逻辑**：

- `testStringToFixed()`：测试合法十六进制字符串（`"000102"` → `{0, 1, 2}`）、小写十六进制（`"0a0b0c"` → `{10, 11, 12}`）、长度不匹配时返回 `null`（`"0001"` 对 `FixedType.ofLength(3)`）、非法十六进制字符（`"GGHHII"`）返回 `null`。

- `testStringToBinary()`：测试合法十六进制字符串、小写十六进制、非法十六进制字符返回 `null`。Binary 无长度约束，不测试长度不匹配场景。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoteScanPlanning.java` (+0/-9 lines)

**修改目的**：移除此前被禁用的 `testBinaryInFilter` 测试。

**工作逻辑**：删除了 `@TestTemplate @Disabled("binary filter...")` 标注的 `testBinaryInFilter()` 方法及其 `import org.junit.jupiter.api.Disabled` 和 `import org.junit.jupiter.api.TestTemplate`。该方法此前因 StringLiteral 到 Binary 的转换未实现而被禁用。现在转换已支持，父类 `TestSelect` 中的 `testBinaryInFilter` 可以正常被 `TestRemoteScanPlanning` 继承执行，无需在子类中覆盖禁用。移除后该测试会随父类测试套件自动运行。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+27/-0 lines)

**修改目的**：新增 Fixed 类型列的过滤测试。

**工作逻辑**：新增 `testFixedInFilter()` 测试。由于 Spark SQL DDL 不支持直接创建 Fixed 类型列，测试通过编程方式创建 Schema（`Types.FixedType.ofLength(2)`）和表。插入三行数据 `(1, X'0000')`、`(2, X'1111')`、`(3, X'0011')`，然后执行 `SELECT id, fixed FROM %s WHERE fixed > X'0011'`，期望只返回 `id=2`（`X'1111' > X'0011'`）。这验证了 Fixed 类型字面量在 Spark 过滤条件中的正确解析和比较。新增了 `PartitionSpec`、`Schema`、`TableIdentifier`、`Types` 的 import。

### `spark/v3.5`、`spark/v4.0`、`spark/v4.1` 下对应的 `TestRemoteScanPlanning.java` 和 `TestSelect.java`

**修改目的**：在四个 Spark 版本中同步应用相同的测试改动。

**工作逻辑**：与 v3.4 完全一致——`TestRemoteScanPlanning` 移除被禁用的 `testBinaryInFilter`，`TestSelect` 新增 `testFixedInFilter`。四个 Spark 版本的 `TestRemoteScanPlanning.java` 改动后内容完全相同（同一 blob hash `ed90da7fd`），`TestSelect.java` 的 `testFixedInFilter` 测试逻辑也一致。

## 总结

本提交补全了 Iceberg 表达式系统中 `StringLiteral` 向 `Fixed` 和 `Binary` 类型的字面量转换能力，使用 Base16 十六进制解码实现字符串到字节的映射。这一缺失此前导致 REST Catalog 远程扫描计划场景下包含 Binary/Fixed 字面量的过滤条件无法正确反序列化（`ExpressionParser.fromJSON` 解析失败），相关测试被 `@Disabled`。修复后，被禁用的 `testBinaryInFilter` 测试得以恢复运行，并新增了 `testFixedInFilter` 覆盖 Fixed 类型过滤场景。改动在 API 层和四个 Spark 版本中同步应用，完整地解决了 Binary/Fixed 字面量在 Spark 过滤表达式中的端到端处理问题。
