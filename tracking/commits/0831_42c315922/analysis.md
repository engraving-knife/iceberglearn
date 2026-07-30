# 提交 0831：MR: Optimize schema string retrieval in Iceberg (#10489)

## 提交信息
- **序号**：0831 / 4088
- **哈希**：42c3159226ab8f7f3969760b6d75a40246a19bd7
- **短哈希**：42c315922
- **日期**：2024-06-14 15:03:20 -0700
- **作者**：GYoung <dzzxjl@gmail.com>
- **提交说明**：MR: Optimize schema string retrieval in Iceberg (#10489)
- **PR/Issue**：#10489

## 总体目的

本提交针对 MR（MapReduce）模块中 `Catalogs.createTable` 方法的一个小性能/可读性问题进行优化。在原代码中，`createTable` 方法首先从 `props` 中读取 `InputFormatConfig.TABLE_SCHEMA` 属性并赋值给局部变量 `schemaString`，紧接着又通过 `Preconditions.checkNotNull` 对该变量做了非空校验。然而在下一行解析 schema 时，代码并没有复用已经取到并校验过的 `schemaString` 变量，而是再次调用 `props.getProperty(InputFormatConfig.TABLE_SCHEMA)` 重新从 Properties 中获取同一属性的值。

虽然 `Properties.getProperty` 是基于哈希表的查找，开销很小，但重复调用既冗余又降低了代码可读性，并且容易在后续维护中造成混淆（例如修改其中一处而忘记另一处）。本提交的总体目的就是消除这种重复读取，复用已声明的局部变量 `schemaString`，使代码更简洁、意图更清晰。

这是一次纯粹的代码质量优化，不改变任何运行时行为，不引入新功能，也不修复功能性 bug。优化后 schema 字符串只从 Properties 中读取一次，校验与解析使用同一个引用，逻辑一致性更强。

## 如何达成设计目的

提交通过将 `SchemaParser.fromJson(props.getProperty(InputFormatConfig.TABLE_SCHEMA))` 中的实参替换为已声明的局部变量 `schemaString` 来达成目的。由于 `schemaString` 在上一行已经通过 `props.getProperty(InputFormatConfig.TABLE_SCHEMA)` 赋值，并通过 `Preconditions.checkNotNull` 确保非空，复用它不仅语义等价，还避免了第二次哈希查找。改动仅一行，风险极低，且不涉及方法签名或控制流的变更。

## 修改详情

### `mr/src/main/java/org/apache/iceberg/mr/Catalogs.java`
**修改目的**：复用已读取并校验过的 schema 字符串局部变量，避免重复调用 `props.getProperty`。
**工作逻辑**：在 `createTable(Configuration conf, Properties props)` 方法中，原代码流程为：①`String schemaString = props.getProperty(InputFormatConfig.TABLE_SCHEMA)` 读取 schema 字符串；②`Preconditions.checkNotNull(schemaString, "Table schema not set")` 非空校验；③`Schema schema = SchemaParser.fromJson(props.getProperty(InputFormatConfig.TABLE_SCHEMA))` 解析 schema。第③步原本再次调用 `props.getProperty(InputFormatConfig.TABLE_SCHEMA)` 取值，改为直接传入 `schemaString`。这样 schema 字符串只读取一次，校验与解析引用同一对象，逻辑更紧凑，可读性更好，同时避免了冗余的哈希查找。

## 小结
- **成效**：消除了 `Catalogs.createTable` 中对 `InputFormatConfig.TABLE_SCHEMA` 属性的重复读取，代码更简洁，可读性与可维护性略有提升，运行时行为完全不变。
- **影响范围**：仅影响 MR 模块的 `Catalogs.createTable` 方法，涉及创建 Iceberg 表时 schema 的解析路径。不改变方法签名、控制流或对外行为。
- **回迁注意事项**：回迁到 1.4.x 风险极低，属于一行替换。需注意 1.4.x 分支的 `Catalogs.java` 可能因分支独有的改动（如注释、行号偏移）导致上下文略有差异，但替换目标行明确（`SchemaParser.fromJson(props.getProperty(InputFormatConfig.TABLE_SCHEMA))` → `SchemaParser.fromJson(schemaString)`），定位无难度。无依赖、无测试变更。
