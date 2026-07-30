# 提交 1633 a04220a19 分析

## 提交信息
- 哈希：a04220a198a03f4116d3012330f99b787dabc8e1
- 日期：2025-01-24 13:35:51 +0100
- 作者：Fokko Driesprong
- 消息：API: Add `UnknownType` (#12012)

## 总体目的

本提交在 Iceberg 的类型系统（api 模块）中新增一种原始类型 `UnknownType`（类型 ID 为 `UNKNOWN`），用于表示“未知的类型”。这是一种占位/容错类型：当读取端遇到一个自身无法识别的字段类型（例如由更高版本 Iceberg 写入、当前版本尚不支持的类型）时，可以用 `UnknownType` 来承载该字段，从而避免直接抛错导致整张表不可读，也避免对未识别字段做错误的语义解释。

这与 V3 规范的演进相关：`Schema.MIN_FORMAT_VERSIONS` 中将 `UNKNOWN` 标记为格式版本 3 才允许出现，即 `UnknownType` 仅在 V3 表中合法。该类型被设计为不可参与大多数分区变换（bucket/truncate/year/month/day/hour 都会显式拒绝），但可用于 identity 排序以及 alwaysNull（void）变换，体现出“保留结构、限制运算”的设计取向。

总体上，本提交为 Iceberg 向前兼容（forward compatibility）提供了一个类型层面的容错机制，是 V3 能力扩展（如 variant、nanos timestamp）的配套基础设施之一。

## 如何达成设计目的

设计思路：把 `UnknownType` 实现为一个不可变的单例原始类型（`PrimitiveType`），并在类型系统的各个关键扩展点（类型注册表、格式版本约束、表达式清洗、固定大小估算、变换绑定校验）统一处理它，确保它在“能被识别和透传”的同时“不能被错误地参与运算”。

### 修改详情

#### api/src/main/java/org/apache/iceberg/types/Type.java
在 `TypeID` 枚举中新增 `UNKNOWN(Object.class)`。`Object.class` 作为对应的 Java 类，表示该类型没有更具体的 Java 载体。这是整个变更的起点，所有依赖 `switch(typeId)` 的地方都需要在编译期/运行期感知到这个新枚举值。

#### api/src/main/java/org/apache/iceberg/types/Types.java
1. 在 `TYPES` 静态映射（按字符串名到类型的字典）中注册 `"unknown" -> UnknownType.get()`，使 `Types.fromPrimitiveString("unknown")` 能正确解析。
2. 新增内部类 `UnknownType extends PrimitiveType`：
   - 单例模式，通过 `UnknownType.get()` 获取。
   - `typeId()` 返回 `TypeID.UNKNOWN`。
   - `toString()` 返回 `"unknown"`（与注册键一致，也是序列化字符串）。

#### api/src/main/java/org/apache/iceberg/Schema.java
在 `MIN_FORMAT_VERSIONS` 映射中加入 `Type.TypeID.UNKNOWN, 3`，与 `TIMESTAMP_NANO`、`VARIANT` 并列。`MIN_FORMAT_VERSIONS` 用于在校验 schema 时判定某类型需要的最低表格式版本。这意味着只有 V3 表才允许使用 `UnknownType` 字段，V1/V2 表使用会被拒绝。

#### api/src/main/java/org/apache/iceberg/types/TypeUtil.java
在估算固定字节数（`fixedSizeInBytes` 相关逻辑）的 switch 中为 `UNKNOWN` 返回 `0`，注释为 “Consider Unknown as null”。即把未知类型按空值处理其大小估算，避免影响统计/规划逻辑。

#### api/src/main/java/org/apache/iceberg/expressions/ExpressionUtil.java
在 `sanitize`（表达式值清洗/脱敏）逻辑中：
- 为 `UNKNOWN` 分支返回字符串 `"(unknown)"`。
- 把注释中的类型列表从 “boolean, uuid, decimal, fixed, variant, and binary” 扩展为 “...variant, unknown, and binary”。
这保证当表达式中的字面量是未知类型时，能输出可读的占位文本而非抛出未处理分支异常。

#### api/src/test/java/org/apache/iceberg/types/TestSerializableTypes.java
新增 `testUnknown`，验证 `UnknownType` 可被序列化往返（round-trip）且结果相等。因为 `Type` 继承 `Serializable`，单例 `UnknownType` 必须能正确序列化/反序列化。

#### api/src/test/java/org/apache/iceberg/types/TestTypes.java
修改既有测试 `fromPrimitiveString` 的断言：原来用 `"Unknown"`（首字母大写、且会与新增类型名歧义）作为无法解析的输入并断言消息包含 “Unknown”；改为用 `"abcdefghij"` 作为无法解析输入，并断言消息为 “Cannot parse type string to primitive: abcdefghij”。这是因为新增的 `UnknownType` 使 `"unknown"` 成为合法类型字符串，原测试用 “Unknown” 作负例已不再合适（且大小写不同也会被解析为不同键），故换成更明确的无意义字符串作为负例。

#### api/src/test/java/org/apache/iceberg/TestSchema.java
在 `V3_TYPES` 列表中加入 `Types.UnknownType.get()`，使 V3 schema 相关测试覆盖到该新类型。

#### api/src/test/java/org/apache/iceberg/TestPartitionSpecValidation.java
1. SCHEMA 增加字段 `NestedField.required(8, "u", Types.UnknownType.get())`。
2. 新增 `testUnknownUnsupported`：用 `bucket[5]` 对 unknown 字段做分区，断言抛出 `ValidationException` 且消息为 “Invalid source type unknown for transform: bucket[5]”。验证 unknown 类型不能作为分区变换的源类型。

#### api/src/test/java/org/apache/iceberg/transforms/TestBucketing.java
新增 `testUnknownUnsupported`：分别对 `Transforms.bucket(UnknownType, 3)`（构造时绑定）和 `bucket(3).bind(UnknownType)`（运行时绑定）断言抛出 “Cannot bucket by type: unknown”，并断言 `canTransform` 返回 false。三重校验确保 bucket 变换完全拒绝 unknown。

#### api/src/test/java/org/apache/iceberg/transforms/TestDates.java
新增 `testUnknownUnsupportedYear/Month/Day` 三个测试，分别对 year/month/day 变换验证：构造时绑定与 `bind` 时均抛出 “Unsupported type: unknown”，`canTransform` 返回 false。覆盖日期类变换对 unknown 的拒绝。

#### api/src/test/java/org/apache/iceberg/transforms/TestTimestamps.java
新增 `testUnknownUnsupported`，对 hour 变换做同样三重校验，断言 “Unsupported type: unknown”。

#### api/src/test/java/org/apache/iceberg/transforms/TestTruncate.java
新增 `testUnknownUnsupported`：`Transforms.truncate(UnknownType, 22)` 抛 `UnsupportedOperationException`（“Cannot truncate type: unknown”）；`truncate(22).bind(UnknownType)` 抛 `IllegalArgumentException`（“Cannot bind to unsupported type: unknown”）；`canTransform` 返回 false。注意 truncate 在两个阶段抛出不同异常类型，测试精确覆盖。

#### api/src/test/java/org/apache/iceberg/transforms/TestIdentity.java
新增 `testUnknownToHumanString`：用 identity 变换对 unknown 类型调用 `toHumanString(unknownType, null)`，断言返回 “null”。这表明 identity 变换允许 unknown 类型（不抛错），且对 null 值输出 “null”，与 identity 对其它类型处理 null 的行为一致。

#### api/src/test/java/org/apache/iceberg/transforms/TestVoid.java（新文件）
新增测试类 `TestVoid`，含 `testUnknownToHumanString`：对 `Transforms.alwaysNull()`（void 变换）调用 `toHumanString(unknownType, null)`，断言返回 “null”。说明 alwaysNull 变换也兼容 unknown 类型。alwaysNull 通常用于隐藏/置空某列，是少数允许 unknown 的变换之一。

#### core/src/test/java/org/apache/iceberg/TestSortOrder.java
新增 `testUnknownSupported`：构造一张含 unknown 字段的 V3 schema，用 `SortOrder.builderFor(v3Schema).asc("u").build()` 构建排序规则，断言排序字段数=1 且 sourceId 正确。这验证 unknown 类型可用于 identity 排序字段（排序本质是 identity 变换）。注意此测试用 `@Test`（非参数化），独立于格式版本参数化运行。

## 小结

- 成效：本提交为 Iceberg 引入了 `UnknownType` 原始类型，作为 V3 表向前兼容的占位类型。它允许读取端在不识别某字段真实类型时仍能加载 schema、读取数据并参与 identity 排序，同时通过显式拒绝各类运算变换（bucket/truncate/date/timestamp/hour）防止误用。改动集中在 api 模块，测试覆盖全面（序列化、解析、分区校验、各类变换、排序）。
- 影响范围：仅影响 api 模块的类型系统与 core 的排序测试。由于 `UNKNOWN` 被限定为 V3 才可用，V1/V2 表不受影响。但所有依赖 `TypeID` switch 且未处理 `UNKNOWN` 分支的代码在编译期会因枚举新增而被检查（Java 中 enum switch 不强制覆盖，但运行期可能落入 default）。
- 回迁到 1.4.x 的注意事项：1.4.x 默认不支持 V3，且回迁本提交会引入一个新的 `TypeID.UNKNOWN` 枚举值，凡是对 `TypeID` 做 switch 且没有 default 兜底的代码都可能在运行期命中未处理分支。回迁需谨慎评估：若 1.4.x 不打算支持 V3 表，则该类型无实际用途，回迁意义不大；若要为 1.4.x 增加 V3 前向读取容错能力，则需同时回迁所有相关扩展点处理（ExpressionUtil、TypeUtil、Schema 校验、各 transform 的 bind/canTransform），并补齐 parquet/orc/avro 读写侧对 unknown 的处理，工作量较大且超出本提交范围。建议仅当 1.4.x 明确需要 V3 forward-read 时才回迁。
