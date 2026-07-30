# 提交 1123：API: implement types timestamp_ns and timestamptz_ns (#9008)

## 提交信息

- **序号**：1123 / 4088
- **哈希**：113c6e7d62e53d3e3cb15b1712f3a1db473ca940
- **短哈希**：113c6e7d6
- **日期**：2024-09-03（Tue Sep 3 08:44:43 2024 -0700）
- **作者**：Jacob Marble <jacobmarble@gmail.com>
- **提交说明**：API: implement types timestamp_ns and timestamptz_ns (#9008)
- **PR/Issue**：#9008

## 总体目的

Iceberg 规范此前在 `format/spec.md` 中已声明了纳秒精度时间戳类型 `timestamp_ns`（无时区）和 `timestamptz_ns`（带时区），但 Java API 层并未实现这两个类型——`Type.TypeID` 枚举里没有对应项，`Types` 里没有对应类，表达式字面量、转换函数（year/month/day/hour）、bucket 分桶、比较器、序列化、Schema 兼容性校验等全部不支持。这导致想用纳秒时间戳的用户在 API 层就走不通。

本提交在 API 与 Core 层完整实现 `timestamp_ns` / `timestamptz_ns`，覆盖以下能力：

1. **类型定义**：新增 `Types.TimestampNanoType` 与 `Type.TypeID.TIMESTAMP_NANO`，类型底层用 `Long` 存储自 epoch 的纳秒数。
2. **格式版本门控**：`timestamp_ns` 仅在 format v3 及以上可用，通过新增 `Schema.checkCompatibility` 在 `TableMetadata` 建表/改 schema 时校验，防止 v1/v2 表误用。
3. **表达式与字面量**：新增 `TimestampNanoLiteral`，支持与 `Date`/`Timestamp`/`TimestampNano` 互转；`ExpressionUtil` 支持纳秒时间戳的 sanitize 与字符串解析。
4. **分区转换**：`year/month/day/hour` 转换支持纳秒输入，通过重构 `Timestamps` 枚举为"微秒族 + 纳秒族"两组实现完成；`Years/Months/Days/Hours` 这些 `TimeTransform` 子类统一走 `fromSourceType` 分派。
5. **bucket 分桶**：纳秒时间戳分桶时先转微秒再哈希，保证与 `timestamp` 类型分桶结果一致（规范附录 B 的 Note 3）。
6. **比较器/序列化/可读性**：`Comparators`、`Conversions`、`TypeUtil`、`Transform.toHumanString` 等均补齐对新类型的支持。
7. **规范文档**：修订 `format/spec.md` 附录 B，把 `timestamp_ns`/`timestamptz_ns` 的哈希规格从 `hashLong(nanosecsFromUnixEpoch(v))` 改为 `hashLong(microsecsFromUnixEpoch(v))`，并加 Note 3 说明"纳秒时间戳哈希前需转微秒以保证与 timestamp 同哈希"，相应调整示例哈希值与脚注编号。

## 如何达成设计目的

整体策略是"参照 `TimestampType` 的实现镜像一份纳秒版本，并在涉及类型分派的分支增加 `TIMESTAMP_NANO` case"，同时做必要的重构以避免重复代码。关键设计点：

- **类型族**：把纳秒类型作为独立 `PrimitiveType`，复用 `Long` 作为 Java 载体，`TypeID` 新增 `TIMESTAMP_NANO`，与 `TIMESTAMP` 平级。这样所有按 `TypeID` switch 的代码只需加一个 case。
- **转换函数重构**：原 `Timestamps` 枚举只有 4 个值（YEAR/MONTH/DAY/HOUR），每个值内部用 `Apply` 类按 `ChronoUnit` 分派。重构后扩展为 8 个值：`MICROS_TO_YEAR/MONTH/DAY/HOUR` 与 `NANOS_TO_YEAR/MONTH/DAY/HOUR`，每个值绑定一个独立的 `SerializableFunction`（`MicrosToYears` 等 8 个静态内部类），消除了原 `Apply` 内部的 switch。这样纳秒和微秒路径各自独立、互不干扰。
- **TimeTransform 统一分派**：把 `Years/Months/Days/Hours` 各自重复的 `satisfiesOrderOf` 上提到 `TimeTransform` 基类，统一用 `TransformUtil.satisfiesOrderOf(granularity, other.granularity())` 比较；新增 `fromSourceType(type, dateResult, microsResult, nanosResult)` 工具方法，按源类型（DATE/TIMESTAMP/TIMESTAMP_NANO）返回对应枚举，消除各子类里的 switch。
- **bucket 兼容**：纳秒分桶时先 `DateTimeUtil.nanosToMicros` 再哈希，确保 `timestamp` 与 `timestamp_ns` 在同一纳秒点（截断到微秒后）落到同一桶，支持 schema 演进下的分桶稳定性。
- **格式版本门控**：用 `MIN_FORMAT_VERSIONS` 映射表把 `TIMESTAMP_NANO -> 3`，在 `TableMetadata` 改 schema 时调用 `Schema.checkCompatibility`，对 v1/v2 表直接抛错。
- **负纳秒处理**：`DateTimeUtil.convertNanos` 对负值采用"加 1 纳秒再减 1 单位"的技巧，与现有 `convertMicros` 处理负微秒的方式一致，确保 epoch 之前的时刻边界正确。

## 修改详情

### 规范文档

#### `format/spec.md`

**修改目的**：修订纳秒时间戳的哈希规格，使其与微秒时间戳哈希一致。

**工作逻辑**：

- 把 `timestamp_ns` / `timestamptz_ns` 的哈希规格从 `hashLong(nanosecsFromUnixEpoch(v))` 改为 `hashLong(microsecsFromUnixEpoch(v))` 并标注脚注 [3]。
- 更新示例哈希值：`2017-11-16T22:31:08` 现在给出 `-2047944441`（与 `timestamp` 同），`2017-11-16T22:31:08.000001001` 给出 `-1207196810`（注意纳秒示例用 `.000001001` 而非 `.000001`，因为纳秒精度需 7-9 位小数）。
- 新增 Note 3："Nanosecond timestamps must be converted to microsecond precision before hashing to ensure timestamps have the same hash value."
- 原 Note 3（UUID 编码）顺延为 Note 4，原 Note 4（doubleToLongBits）顺延为 Note 5，对应表格里 `[3]`/`[4]` 脚注标号同步更新。

### API 类型层

#### `api/src/main/java/org/apache/iceberg/types/Type.java`

**修改目的**：在 `TypeID` 枚举中注册纳秒时间戳。

**工作逻辑**：在 `TIMESTAMP(Long.class)` 之后新增 `TIMESTAMP_NANO(Long.class)`，Java 载体为 `Long`（自 epoch 的纳秒数）。

#### `api/src/main/java/org/apache/iceberg/types/Types.java`

**修改目的**：定义 `TimestampNanoType` 类型类并注册到类型映射表。

**工作逻辑**：

- 在 `TYPES` ImmutableMap 中注册 `TimestampNanoType.withZone()/withoutZone()` 的字符串形式。
- 新增 `public static class TimestampNanoType extends PrimitiveType`，仿照 `TimestampType` 结构：单例 `INSTANCE_WITH_ZONE`/`INSTANCE_WITHOUT_ZONE`，`withZone()`/`withoutZone()` 工厂，`adjustToUTC` 布尔字段，`typeId()` 返回 `TIMESTAMP_NANO`，`toString()` 返回 `timestamptz_ns` 或 `timestamp_ns`，`equals` 比较 `adjustToUTC`，`hashCode` 用 `Objects.hash`。

#### `api/src/main/java/org/apache/iceberg/types/Comparators.java`

**修改目的**：为纳秒时间戳注册比较器。

**工作逻辑**：在 `COMPARATORS` map 中加入 `TimestampNanoType.withZone()/withoutZone()` -> `Comparator.naturalOrder()`（Long 自然序）。

#### `api/src/main/java/org/apache/iceberg/types/Conversions.java`

**修改目的**：支持纳秒时间戳与 `Long`/`ByteBuffer` 的互转。

**工作逻辑**：在 `toByteBuffer` 与 `fromByteBuffer` 的 switch 中，`LONG`/`TIME`/`TIMESTAMP` 分支旁加 `TIMESTAMP_NANO`，均按 8 字节 little-endian long 处理（与 timestamp 相同，因为都是 long 载体）。

#### `api/src/main/java/org/apache/iceberg/types/TypeUtil.java`

**修改目的**：纳秒时间戳的字节大小与 timestamp 一致。

**工作逻辑**：在 `getFixedSize` 的 switch 中 `TIME`/`TIMESTAMP` 旁加 `TIMESTAMP_NANO`，返回 8。

### Schema 与格式版本门控

#### `api/src/main/java/org/apache/iceberg/Schema.java`

**修改目的**：实现格式版本兼容性校验。

**工作逻辑**：

- 新增静态字段 `MIN_FORMAT_VERSIONS = ImmutableMap.of(Type.TypeID.TIMESTAMP_NANO, 3)`，声明 `TIMESTAMP_NANO` 最早在 v3 支持。
- 新增静态方法 `checkCompatibility(Schema schema, int formatVersion)`：遍历 schema 所有 `NestedField`，若某字段类型的 `typeId()` 在 `MIN_FORMAT_VERSIONS` 中且 `formatVersion < minFormatVersion`，则抛 `IllegalStateException`，消息指明字段名、类型、所需最低版本。

#### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：在建表/改 schema 时调用兼容性校验。

**工作逻辑**：在 `updateSchema`（或等价的 schema 变更入口）中，计算 `newLastColumnId` 后、`reuseOrCreateNewSchemaId` 前插入 `Schema.checkCompatibility(schema, formatVersion)`，确保 v1/v2 表无法引入 `timestamp_ns` 字段。

### 表达式与字面量

#### `api/src/main/java/org/apache/iceberg/expressions/Literals.java`

**修改目的**：新增 `TimestampNanoLiteral` 并支持与其它时间类型的互转。

**工作逻辑**：

- 新增 `static class TimestampNanoLiteral extends ComparableLiteral<Long>`：`to(Type)` 支持 `DATE`（用 `DateTimeUtil.nanosToDays`）、`TIMESTAMP`（`nanosToMicros`）、`TIMESTAMP_NANO`（自身）。
- `TimestampLiteral.to(Type)`：`TIMESTAMP_NANO` 分支注释说明"假设输入是微秒并转纳秒"，复用 `microsToNanos`，与 timestamp 字面量转纳秒语义一致。
- `TimestampLiteral.to(Type)` 的 `DATE` 分支改用 `DateTimeUtil.microsToDays(value)` 替代原内联的 `ChronoUnit.DAYS.between`，并新增 `TIMESTAMP_NANO` 分支用 `microsToNanos`。
- `StringLiteral.to(Type)`：新增 `TIMESTAMP_NANO` 分支，按 `shouldAdjustToUTC()` 分别用 `DateTimeUtil.isoTimestamptzToNanos` / `isoTimestampToNanos` 解析；同时把原 `TIMESTAMP` 分支内联的 `ChronoUnit.MICROS.between` 替换为 `DateTimeUtil.isoTimestamptzToMicros` / `isoTimestampToMicros`，统一走工具类。删除了不再使用的 `LocalDateTime` import。

#### `api/src/main/java/org/apache/iceberg/expressions/BoundLiteralPredicate.java`

**修改目的**：纳秒时间戳字面量可参与 `IN`/`NotIn` 等长整型谓词。

**工作逻辑**：在 `INTEGER_TYPES` Set 中 `TIMESTAMP` 旁加入 `TIMESTAMP_NANO`，使 `BoundLiteralPredicate` 能把纳秒字面量当 long 处理。

#### `api/src/main/java/org/apache/iceberg/expressions/ExpressionUtil.java`

**修改目的**：支持纳秒时间戳字面量的 sanitize（脱敏）与字符串解析。

**工作逻辑**：

- 新增常量 `FIVE_MINUTES_IN_NANOS` 与两个正则 `TIMESTAMPNS`、`TIMESTAMPTZNS`（要求小数部分 7-9 位以区分微秒）。
- `sanitizeValue` 的 `TIMESTAMP_NANO` 分支：`DateTimeUtil.nanosToMicros(value / 1000)` 后复用 `sanitizeTimestamp`（注意这里 `value` 是纳秒，`/1000` 是为了与微秒字面量路径在 `sanitizeTimestamp` 内部的取整行为对齐）。
- `sanitizeLiteral` 增加 `Literals.TimestampNanoLiteral` 分支，转微秒后调 `sanitizeTimestamp`。
- `sanitizeString` 增加 `TIMESTAMPNS`/`TIMESTAMPTZNS` 正则匹配分支，解析为纳秒字面量再转微秒脱敏。
- 新增 `DateTimeUtil` import。

### 分区转换重构

#### `api/src/main/java/org/apache/iceberg/transforms/Timestamps.java`

**修改目的**：让 `year/month/day/hour` 转换同时支持微秒与纳秒输入。

**工作逻辑**：这是本提交最大的单文件改动（原 4 枚举值扩展为 8 个）。

- 枚举值改为：`MICROS_TO_YEAR/MONTH/DAY/HOUR` 与 `NANOS_TO_YEAR/MONTH/DAY/HOUR`，每个值构造时绑定一个 `SerializableFunction<Long, Integer>`。
- 删除原内部 `Apply` 类（其按 `ChronoUnit` switch 的逻辑被拆分到 8 个独立静态内部类：`MicrosToYears`/`MicrosToMonths`/`MicrosToDays`/`MicrosToHours` 与 `NanosToYears`/`NanosToMonths`/`NanosToDays`/`NanosToHours`），每个类调对应 `DateTimeUtil.microsTo*` / `nanosTo*`。
- `apply(Long)` 直接委托给绑定的函数；`canTransform` 增加 `TIMESTAMP_NANO` 支持。
- `satisfiesOrderOf` 重构：不再只比较 `Timestamps` 之间，而是统一用 `TransformUtil.satisfiesOrderOf(granularity, other.granularity())`，覆盖 `Dates`、`Timestamps`、`TimeTransform` 三种 other。
- 新增 `granularity()` 方法暴露 `ChronoUnit`，供 `satisfiesOrderOf` 与 `PartitionSpecVisitor` 等使用。

#### `api/src/main/java/org/apache/iceberg/transforms/Dates.java`

**修改目的**：与重构后的 `Timestamps`/`TimeTransform` 对齐 `satisfiesOrderOf`。

**工作逻辑**：新增 `granularity()` 方法；`satisfiesOrderOf` 改为调 `TransformUtil.satisfiesOrderOf`，覆盖 `Dates`/`Timestamps`/`TimeTransform` 三类 other。

#### `api/src/main/java/org/apache/iceberg/transforms/Years.java` / `Months.java` / `Days.java` / `Hours.java`

**修改目的**：统一 `TimeTransform` 子类的分派与 `satisfiesOrderOf`。

**工作逻辑**：每个类都：

- 新增 `protected ChronoUnit granularity()` 返回各自的 `ChronoUnit.YEARS/MONTHS/DAYS/HOURS`。
- `toEnum(Type)` 改为调 `fromSourceType(type, Dates.X, Timestamps.MICROS_TO_X, Timestamps.NANOS_TO_X)`（Hours 的 dateResult 传 null，因为 hour 不支持 date 类型）。
- 删除各自的 `satisfiesOrderOf` 实现（上提到 `TimeTransform` 基类）。
- `Hours.canTransform` 增加 `TIMESTAMP_NANO` 支持。

#### `api/src/main/java/org/apache/iceberg/transforms/TimeTransform.java`

**修改目的**：提供统一的类型分派工具与 `satisfiesOrderOf` 基类实现。

**工作逻辑**：

- 新增静态工具 `fromSourceType(Type, R dateResult, R microsResult, R nanosResult)`：按 `DATE`/`TIMESTAMP`/`TIMESTAMP_NANO` 返回对应结果，dateResult 为 null 时表示该转换不支持 date。
- 新增抽象 `granularity()` 与基类 `satisfiesOrderOf`：用 `TransformUtil.satisfiesOrderOf` 与 `Dates`/`Timestamps`/`TimeTransform` 比较。
- `canTransform` 增加 `TIMESTAMP_NANO` 支持。

#### `api/src/main/java/org/apache/iceberg/transforms/Transform.java`

**修改目的**：`toHumanString` 支持纳秒时间戳。

**工作逻辑**：在 `TIMESTAMP` 分支后新增 `TIMESTAMP_NANO` 分支，按 `shouldAdjustToUTC()` 调 `TransformUtil.humanTimestampNanoWithZone` 或 `humanTimestampNanoWithoutZone`。

#### `api/src/main/java/org/apache/iceberg/transforms/TransformUtil.java`

**修改目的**：提供纳秒时间戳的人类可读格式化与 `satisfiesOrderOf` 工具。

**工作逻辑**：

- 新增 `humanTimestampNanoWithZone(Long)` / `humanTimestampNanoWithoutZone(Long)`，分别调 `DateTimeUtil.nanosToIsoTimestamptz` / `nanosToIsoTimestamp`。
- 把原 `humanTimestampWithZone/WithoutZone` 内联的 `ChronoUnit.MICROS.addTo` 改为调 `DateTimeUtil.microsToIsoTimestamptz` / `microsToIsoTimestamp`，统一格式化逻辑。
- 新增 `satisfiesOrderOf(ChronoUnit left, ChronoUnit right)`：比较 `left.getDuration().toHours() <= right.getDuration().toHours()`，把原散落在 `Dates`/`Timestamps` 里的比较逻辑收敛到此。
- 新增 `DateTimeUtil` import。

#### `api/src/main/java/org/apache/iceberg/transforms/Transforms.java`

**修改目的**：`fromString` 与 deprecated `year/month/day/hour(Type)` 工厂支持纳秒类型。

**工作逻辑**：

- `fromString` 重构为 switch on 小写 transform 名（`identity`/`year`/`month`/`day`/`hour`/`void`），其中 `year/month/day/hour` 调 `Years/Months/Days/Hours.get().toEnum(type)`，由 `toEnum` 内部按源类型分派到微秒或纳秒枚举。这样 `Transforms.fromString(type, "hour")` 对 `TIMESTAMP_NANO` 类型会自动返回 `Timestamps.NANOS_TO_HOUR`。
- deprecated `year(Type)/month(Type)/day(Type)/hour(Type)` 工厂从各自内联 switch 改为调 `Years/Months/Days/Hours.get().toEnum(type)`，消除重复。
- `fromString` 标记 `@Deprecated`（将在 2.0.0 移除）。
- 删除不再使用的 `Preconditions` import。

#### `api/src/main/java/org/apache/iceberg/transforms/PartitionSpecVisitor.java` / `SortOrderVisitor.java`

**修改目的**：识别新的 `Timestamps.MICROS_TO_*` / `NANOS_TO_*` 枚举值。

**工作逻辑**：两个 visitor 中原来判断 `transform == Timestamps.YEAR/MONTH/DAY/HOUR` 的地方，改为同时判断 `Timestamps.MICROS_TO_YEAR || Timestamps.NANOS_TO_YEAR`（其余 month/day/hour 同理），以兼容重构后的枚举名。

#### `api/src/main/java/org/apache/iceberg/transforms/Bucket.java`

**修改目的**：纳秒时间戳参与 bucket 分桶。

**工作逻辑**：

- `bind(Type)` 的 switch 增加 `TIMESTAMP_NANO` -> `BucketTimestampNano`。
- `canTransform` 增加 `TIMESTAMP_NANO`。
- 新增内部类 `BucketTimestampNano`：`hash(Long nanos)` 调 `BucketUtil.hash(DateTimeUtil.nanosToMicros(nanos))`，注释说明"为使纳秒时间戳与微秒时间戳落到同一桶，先转微秒再哈希"。

### 时间工具

#### `api/src/main/java/org/apache/iceberg/util/DateTimeUtil.java`

**修改目的**：提供纳秒与微秒/日期/ISO 字符串的互转工具。

**工作逻辑**：新增常量 `NANOS_PER_SECOND = 1_000_000_000L`、`NANOS_PER_MICRO = 1_000L`，并把原内联的 `DateTimeFormatter` 提为静态 `FORMATTER`。新增方法：

- `timestampFromNanos(long)` / `nanosFromTimestamp(LocalDateTime)`：LocalDateTime 与纳秒互转。
- `nanosFromTimestamptz(OffsetDateTime)`：带时区日期时间转纳秒。
- `nanosToMicros(long)` / `microsToNanos(long)`：纳秒与微秒互转（用 `Math.floorDiv` / `Math.multiplyExact`）。
- `nanosToIsoTimestamptz(long)` / `nanosToIsoTimestamp(long)`：纳秒转 ISO 字符串，复用 `FORMATTER`。
- `isoTimestamptzToNanos(CharSequence)` / `isoTimestampToNanos(CharSequence)`：ISO 字符串转纳秒。
- `nanosToYears/Months/Days/Hours(long)`：纳秒转年/月/日/时序号。
- 私有 `convertNanos(long, ChronoUnit)`：纳秒转指定粒度，对负值采用"加 1 纳秒再减 1 单位"的技巧（与现有 `convertMicros` 一致），处理 epoch 之前时刻的边界。

同时把 `microsToIsoTimestamptz` 内联的 `DateTimeFormatter` 构造替换为复用 `FORMATTER`。

### 测试（45 文件中约半数为测试）

新增/扩展的测试覆盖：

- `api/src/test/java/org/apache/iceberg/expressions/TestTimestampLiteralConversions.java`（新文件，245 行）：timestamp ↔ timestamp_ns ↔ date 互转，含 epoch 边界与负值。
- `TestMiscLiteralConversions.java` / `TestStringLiteralConversions.java`：扩展纳秒字面量与字符串解析。
- `TestExpressionUtil.java`：纳秒时间戳 sanitize。
- `TestLiteralSerialization.java`：纳秒字面量序列化。
- `transforms/TestTimestamps.java`（+398 行）：微秒与纳秒两条路径的 year/month/day/hour 转换、`satisfiesOrderOf`。
- `transforms/TestDates.java`、`transforms/TestTimeTransforms.java`、`transforms/TestBucketing.java`：纳秒类型在 date/time/bucket 转换中的行为。
- `types/TestTypes.java`、`TestComparators.java`、`TestConversions.java`、`TestReadabilityChecks.java`、`TestSerializableTypes.java`：类型元信息、比较器、序列化。
- `util/TestDateTimeUtil.java`：纳秒工具方法。
- `core/.../TestTableMetadata.java`（+51 行）：v1/v2 表拒绝 `timestamp_ns`、v3 表接受的兼容性校验。
- `api/.../PartitionSpecTestBase.java`、`TestAccessors.java`、`TestPartitionPaths.java`：分区规格与访问器对新类型的支持。

## 小结

- **成效**：Iceberg API/Core 层完整实现 `timestamp_ns` / `timestamptz_ns` 两个纳秒精度时间戳类型，覆盖类型定义、格式版本门控（仅 v3+）、字面量与表达式、分区转换（year/month/day/hour）、bucket 分桶（与 timestamp 同桶）、比较器、序列化、人类可读格式化与规范文档修订。是一次横跨 45 文件、1807 增 268 删的大型功能落地。重构把 `Timestamps` 枚举从 4 值扩展为 8 值、把 `satisfiesOrderOf` 与类型分派上提到 `TimeTransform`/`TransformUtil`，显著降低了后续维护的重复度。
- **影响范围**：API 与 Core 层。新增公共类型 `Types.TimestampNanoType`、新增 `TypeID.TIMESTAMP_NANO`、新增 `Schema.checkCompatibility` 公共方法、扩展 `Transforms.fromString`/`year/month/day/hour` 行为以接受纳秒类型。规范 `format/spec.md` 修订了纳秒时间戳的哈希规格（改为先转微秒再哈希）。对已使用 `timestamp` 类型的现有表与查询**无破坏性影响**——新类型是新增分支，不改变既有路径。
- **回迁到 1.4.x 的注意事项**：**不建议回迁**，理由如下：
  1. **格式版本门控**：`timestamp_ns` 仅 v3 表可用，而 1.4.x 是维护分支，主要面向已发布的 v1/v2 表生态，回迁后用户也无法在 v1/v2 表上使用，价值有限。
  2. **公共 API 变更**：本提交新增了 `TypeID.TIMESTAMP_NANO`、`Types.TimestampNanoType`、`Schema.checkCompatibility` 等公共 API。1.4.x 作为维护分支应保持 API 稳定，引入新公共 API 会与 1.4.x 的语义版本承诺冲突（1.4.x 仅应接受 bug 修复）。
  3. **规范修订**：`format/spec.md` 改了纳秒时间戳哈希规格，属于 spec 改动。按提交 1125 新增的合并规范，spec 改动需走投票；且 1.4.x 不应单方面改变 spec 表述。
  4. **依赖面广**：45 文件改动涉及大量测试与跨模块逻辑，回迁成本与回归风险都高。
  - 若 1.4.x 用户确有纳秒时间戳需求，正确的做法是升级到包含本提交的下一个 minor 版本（1.7.0+），而非在 1.4.x 上回迁。
