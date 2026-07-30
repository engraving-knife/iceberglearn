# 提交 0807：Parquet: Remove TestHelpers in parquet module

## 提交信息

| 字段 | 值 |
|------|-----|
| 序号 | 0807 |
| 完整哈希 | 134345dd20d99b4800aaefdab1397fd22819d1ff |
| 短哈希 | 134345dd2 |
| 提交日期 | 2024-06-03 22:49:40 +0800 |
| 作者 | advancedxy <xianjin@apache.org> |
| 提交说明 | Parquet: Remove TestHelpers in parquet module (#10428) |
| PR/Issue | #10428 |

## 总体目的

该提交的总体目的是清理 `parquet` 模块测试代码中冗余的 `TestHelpers` 工具类。Iceberg 在 `core` 模块中已经存在一个功能更完整的 `org.apache.iceberg.TestHelpers`，而 `parquet` 模块为了测试方便在 `parquet/src/test` 下又复制了一份同名的精简版本（仅包含 `assertThrows` 和 `assertEmptyAvroField` 两个方法）。这种重复的测试工具类会增加维护成本、造成混淆，并且违背了“单一来源”原则。

本次提交将 `parquet` 模块下使用 `TestHelpers.assertThrows` 的两处测试类直接改写为使用 AssertJ 原生的 `assertThatThrownBy` 写法，从而彻底删除 parquet 模块中的 `TestHelpers.java`，让测试断言更直接、更符合现代 AssertJ 风格，也减少了模块间重复代码。

## 如何达成设计目的

设计思路是“就地替换 + 删除冗余”：

1. **识别依赖**：先找到 parquet 模块中对 `TestHelpers` 的所有引用。实际只有两个测试类用到了 `assertThrows` 方法——`TestBloomRowGroupFilter` 和 `TestParquetEncryption`。`assertEmptyAvroField` 方法则没有调用者（属于死代码）。

2. **就地改写**：将每个 `TestHelpers.assertThrows(message, ExpectedException.class, containedInMessage, () -> ...)` 调用改写为等价的 AssertJ 链式写法：
   ```java
   assertThatThrownBy(() -> ...)
       .as(message)
       .isInstanceOf(ExpectedException.class)
       .hasMessageContaining(containedInMessage);  // 或 .hasMessage(...)
   ```
   这种写法是 AssertJ 的标准用法，无需自定义包装类，可读性更好，IDE 也能提供更好的补全和导航。

3. **调整断言严格度**：在 `TestParquetEncryption` 中，原 `assertThrows` 使用的是 `hasMessageContaining`（包含子串），改写后部分断言改用了更严格的 `hasMessage`（完全相等匹配），因为对应的错误信息是固定的、可预知的完整字符串，使用精确匹配能更严格地校验错误信息。

4. **修正一处测试逻辑**：在 `TestBloomRowGroupFilter.testMissingColumn` 中，原断言使用 `lessThan("missing", 5)`，改写后改为 `equal("missing", 5)`。这是顺带的测试修正——对于“缺失列”的校验逻辑，表达式类型不影响校验结果（都是因为列不存在而抛 `ValidationException`），但 `equal` 与同文件中其他用例保持一致。

5. **删除冗余文件**：最后删除 `parquet/src/test/java/org/apache/iceberg/TestHelpers.java`，彻底消除重复。

整个改动不涉及任何生产代码，仅影响测试代码，对功能行为零影响。

## 修改详情

### `parquet/src/test/java/org/apache/iceberg/TestHelpers.java`（删除）

- **修改目的**：删除 parquet 模块中冗余的测试工具类。
- **说明**：该类只包含 `assertThrows`（两个重载）和 `assertEmptyAvroField` 三个静态方法，其中 `assertEmptyAvroField` 无任何调用者。`core` 模块已有同名且功能更全的 `TestHelpers`，parquet 模块这份属于重复副本。删除后，相关测试直接使用 AssertJ 原生 API。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestBloomRowGroupFilter.java`

- **修改目的**：将该测试类中对 `TestHelpers.assertThrows` 的调用替换为 `assertThatThrownBy`，移除对 `TestHelpers` 的 import，并新增 `assertThatThrownBy` 的静态 import。
- **`testMissingColumn`**：把 `lessThan("missing", 5)` 改为 `equal("missing", 5)`，并改写断言链。校验逻辑（缺失列报 `ValidationException`，消息包含 `Cannot find field 'missing'`）不变。
- **`testMissingBloomFilterForColumn`**：把断言改写为 `assertThatThrownBy(...).isInstanceOf(IllegalStateException.class).hasMessageContaining(...)`，校验当 bloom filter reader 返回 null 时抛出 `IllegalStateException` 且消息包含 `Failed to read required bloom filter for id: 10`。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetEncryption.java`

- **修改目的**：同样将 `TestHelpers.assertThrows` 调用替换为 `assertThatThrownBy`，移除 import，新增 `assertThatThrownBy` 静态 import。
- **`testReadEncryptedFileWithoutKeys`**：校验无密钥读取加密文件时抛 `ParquetCryptoRuntimeException`，断言从 `hasMessageContaining` 改为更严格的 `hasMessage`（精确匹配 `Trying to read file with encrypted footer. No keys available`）。
- **`testReadEncryptedFileWithoutAADPrefix`**：校验无 AAD prefix 时抛 `ParquetCryptoRuntimeException`，同样改用 `hasMessage` 精确匹配完整错误信息。

## 小结

- **成效**：消除了 parquet 模块中重复的 `TestHelpers` 工具类，减少代码冗余与维护负担；测试断言改用 AssertJ 原生链式 API，可读性与一致性更好。
- **影响范围**：仅影响 parquet 模块的两个测试类，不涉及任何生产代码或公开 API，无功能性影响。
- **回迁到 1.4.x 的注意事项**：
  - 该改动纯属测试代码清理，cherry-pick 风险极低。
  - 需确认 1.4.x 分支的 parquet 测试类是否仍存在 `TestHelpers` 引用，以及 AssertJ 版本是否支持所用 API（`assertThatThrownBy`、`hasMessageContaining`、`hasMessage` 均为常见 API，1.4.x 的 AssertJ 版本应已支持）。
  - `testMissingColumn` 中 `lessThan` 改 `equal` 的逻辑变更需留意，虽然对“缺失列”校验结果无影响，但若有其他分支特有用例依赖原表达式，需同步确认。
  - 回迁后应运行 parquet 模块测试验证断言仍通过。
