# 提交 2410：Flink: Backport: DynamicSink: Convert existing required fields to optional when missing in the data schema (#13660)

## 提交信息

- **序号**：2410 / 4088
- **哈希**：16012afb3f6fe822318315fe1475fc1acf6d4770
- **短哈希**：16012afb3
- **日期**：2025-07-24 17:11:15 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: DynamicSink: Convert existing required fields to optional when missing in the data schema (#13660)
- **PR/Issue**：#13660（backport of #13659）

## 总体目的

本提交是 PR #13659 向 1.4.x 分支的回溯（backport）。它修复了 Flink Dynamic Iceberg Sink 中 `CompareSchemasVisitor` 的一个缺陷：当 Iceberg 表的 schema 中有一个 required（非空）字段，但写入数据的 schema 中不包含该字段时，系统未能正确地将该 required 字段降级为 optional 字段。

在动态 schema 演进场景下，Flink Dynamic Iceberg Sink 会比较写入数据的 schema 和 Iceberg 表的 schema，决定是否需要更新表 schema 或进行数据转换。此前的逻辑存在漏洞：当表 schema 中的 required 字段在数据 schema 中完全缺失时，visitor 不会遍历到该字段（因为遍历是基于数据 schema 的字段进行的），因此无法检测到 required/optional 不兼容的问题，最终错误地返回 `DATA_CONVERSION_NEEDED`，导致数据转换失败。

## 如何达成设计目的

设计思路是在 `CompareSchemasVisitor` 的 schema 比较逻辑中，增加一个额外的检查：遍历表 schema 中的所有字段，如果发现某个 required 字段在数据 schema 中不存在，则直接返回 `SCHEMA_UPDATE_NEEDED`，触发 schema 演进逻辑将该字段从 required 降级为 optional。

关键设计点：
1. 新增的检查放在字段类型比较之后、数据转换检查之前
2. 只对 required 字段进行此检查，因为 optional 字段缺失不会造成问题
3. 返回 `SCHEMA_UPDATE_NEEDED` 而非 `DATA_CONVERSION_NEEDED`，前者会触发表 schema 更新（将 required 改为 optional），后者会尝试数据转换（会失败）

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/CompareSchemasVisitor.java` (+9/-0 lines)

**修改目的**：在 schema 比较逻辑中增加对表 schema 中缺失的 required 字段的检查。

**工作逻辑**：在已有的字段类型比较逻辑之后，新增一个循环遍历表 schema 的所有字段。对于每个 required 字段，检查它是否存在于数据 schema 中。如果不存在，则返回 `SCHEMA_UPDATE_NEEDED`。注释解释了原因：如果表 schema 中的字段在输入 schema 中不存在，visitor 不会遍历到它，因此无法在正常的字段遍历过程中检查 required/optional 兼容性，唯一的选择是将该表字段改为 optional。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestCompareSchemasVisitor.java` (+22/-1 lines)

**修改目的**：为新增的逻辑添加单元测试。

**工作逻辑**：
- 将原有的 `testWithRequiredChange` 重命名为 `testRequiredChangeForMatchingField`，使其语义更清晰（字段在两个 schema 中都存在时的 required 变化）
- 新增 `testRequiredChangeForNonMatchingField`：测试当表 schema 有 required 字段但数据 schema 缺失该字段时，双向比较都应返回 `SCHEMA_UPDATE_NEEDED`
- 新增 `testNoRequiredChangeForNonMatchingField`：测试当缺失的字段是 optional 时，不需要 schema 更新，而是返回 `DATA_CONVERSION_NEEDED`

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+35/-6 lines)

**修改目的**：添加端到端集成测试，验证修复后的行为。

**工作逻辑**：
- 新增 `testRowEvolutionMakeMissingRequiredFieldOptional`：创建一个包含 required 字段 `data` 的表，然后用不包含该字段的 schema 写入数据，验证写入成功（表 schema 被自动更新，required 字段降级为 optional）
- 修改原有的 `testSchemaEvolutionNonBackwardsCompatible`：将其改为测试类型不兼容的场景（int 改为 string），而非之前测试的 required 字段缺失场景（因为后者现在已被正确处理不再报错）

### Flink v1.21 和 v1.22 版本的对应文件

同样的修改被应用到 `flink/v1.21` 和 `flink/v1.22` 两个版本的对应文件中，改动内容完全一致。

## 总结

本提交修复了 Flink Dynamic Iceberg Sink 在处理 schema 演进时的一个边界情况：当表中的 required 字段在写入数据中缺失时，应该自动将该字段降级为 optional，而非尝试不可能成功的数据转换。这使得动态 schema 演进更加健壮，特别是在字段被移除的场景下，允许数据继续写入而不会失败。
