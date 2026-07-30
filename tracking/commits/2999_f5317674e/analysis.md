# 提交 2999：Support for TIME, TIMESTAMPNTZ_NANO, UUID types in Inclusive Metrics Evaluator (#13195)

## 提交信息

- **序号**：2999 / 4088
- **哈希**：f5317674e2f7ce8eac54fb062073d33f833750ff
- **短哈希**：f5317674e
- **日期**：2025-12-11 09:33:10 -0800
- **作者**：Manikandan R
- **提交说明**：Support for TIME, TIMESTAMPNTZ_NANO, UUID types in Inclusive Metrics Evaluator (#13195)
- **PR/Issue**：#13195

## 总体目的

Iceberg 的 `InclusiveMetricsEvaluator` 用于基于数据文件的列级上下界（lower/upper bounds）来判断一个查询表达式是否可能命中该文件，是文件裁剪（file pruning）的核心。Variants（变体类型）是 Iceberg 新引入的半结构化数据类型，存储为 Variant 物理格式；`VariantExpressionUtil` 负责把 Variant 值（以 `PhysicalType` 描述）"提取/转换"为 Iceberg 类型系统中对应类型的字面值，以便 `extract(...)` 表达式能与文件统计信息（lower/upper bounds，也是 Variant 编码）做比较。

此前的实现存在功能缺口：源码里直接以 TODO 标注了三类尚未支持的 `PhysicalType`：

```java
// TODO: Implement PhysicalType.TIME
// TODO: Implement PhysicalType.TIMESTAMPNTZ_NANO and PhysicalType.TIMESTAMPTZ_NANO
// TODO: Implement PhysicalType.UUID
```

这意味着：当用户对 Variant 列里的 `event_timestamp`（用纳秒时间戳存储）或 `event_uuid` 字段做 `equal` / `notEqual` 过滤时，`castTo` 在这些 `PhysicalType` 上无法把 Variant 的物理值转换成 Iceberg `TimestampNanoType` / `TimeType` / `UUIDType` 期望的字面值——结果要么返回 `null` 导致比较失败、要么直接绕过文件裁剪逻辑，使本应被裁掉的文件仍被读取，或在边界比较时给出错误结果。也就是说，对纳秒时间戳/UUID 列的过滤要么"裁不掉"，要么"裁错"。本提交的目的就是补齐这三类 Variant 物理类型在 Inclusive Metrics Evaluator 中的支持，并加上跨类型时间戳互转（micros ↔ nanos、date ↔ timestamp）能力，使 `extract(...)` 表达式对这几种类型也能正确参与文件级上下界裁剪。

此外，原 `castTo` 只支持"目标类型与物理类型一一对应"的快路径（`NO_CONVERSION_NEEDED`）以及少数整数/浮点/decimal/boolean 的宽化转换，但缺少"目标 TIMESTAMP(micros) ↔ 物理时间戳(nanos)"、"目标 DATE ↔ 物理时间戳/纳秒时间戳"这种语义等价的跨精度转换。这在实际场景中很常见：用户可能用 `TimestampNanoType` 写入，但用 `TimestampType`（micros）做查询，或反过来；如果 `castTo` 不处理，等值/不等值比较就会失败。本次提交一并把这些跨精度时间戳转换补全。

## 如何达成设计目的

整体思路分两层：

1. **类型映射注册**：在 `VariantExpressionUtil.NO_CONVERSION_NEEDED` 映射中把 `TimestampNanoType.withoutZone/withZone` → `PhysicalType.TIMESTAMPNTZ_NANOS/TIMESTAMPTZ_NANOS`、`TimeType` → `PhysicalType.TIME`、`UUIDType` → `PhysicalType.UUID` 注册进去。这意味着当查询类型与 Variant 物理类型一一对应时，走 `castTo` 的快路径直接返回原值（`value.asPrimitive().get()`），UUID/Time/纳秒时间戳不再返回 `null`。

2. **跨精度转换**：在 `castTo` 的 `switch (type.typeId())` 中新增 `TIMESTAMP`、`TIMESTAMP_NANO`、`DATE` 三个 case，借助 `DateTimeUtil` 的 `nanosToMicros` / `microsToNanos` / `microsToDays` / `nanosToDays` / `microsFromTimestamp` / `nanosFromTimestamp` / `dateFromDays` 等工具，实现目标类型与物理类型之间的双向转换。

由于新增的 case 让 `castTo` 分支数量上升、圈复杂度变高，作者用 `@SuppressWarnings({"unchecked", "CyclomaticComplexity"})` 暂时压制 checkstyle 警告，并在 commit message 中标注后续会用更合适的方式重构（"added to do to fix this later using correct approach"）。

测试侧新增了三个参数化测试方法 `testDateAndTimestampTypesEq`、`testDateAndTimestampTypesNotEq`、`testUUIDEq`，覆盖 Date / Timestamp(micros) / TimestampNano 三种目标类型与 Date / Timestamp / TimestampNano 三种 Variant 物理值的两两组合，以及 UUID 等值场景。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/VariantExpressionUtil.java` (+40/-4 lines)

**修改目的**：为 `TIME`、`TIMESTAMPNTZ_NANO`、`TIMESTAMPTZ_NANO`、`UUID` 这些 Variant 物理类型提供类型映射与跨精度转换。

**工作逻辑**：

第一处改动是删除三个 TODO 注释，并在 `NO_CONVERSION_NEEDED` 映射中追加四条：

```java
.put(Types.TimestampNanoType.withoutZone(), PhysicalType.TIMESTAMPNTZ_NANOS)
.put(Types.TimestampNanoType.withZone(), PhysicalType.TIMESTAMPTZ_NANOS)
.put(Types.TimeType.get(), PhysicalType.TIME)
.put(Types.UUIDType.get(), PhysicalType.UUID)
```

`NO_CONVERSION_NEEDED` 用于 `castTo` 开头的快路径判断：`if (NO_CONVERSION_NEEDED.get(type) == value.type()) return (T) value.asPrimitive().get();`。补齐后，对 `TimestampNanoType` 目标 + `TIMESTAMPNTZ_NANOS` 物理值、`UUIDType` 目标 + `UUID` 物理值、`TimeType` 目标 + `TIME` 物理值，`castTo` 会直接返回底层原始 long/UUID，而不再返回 `null`——这是 Inclusive Metrics Evaluator 能对这几种类型做等值/不等值裁剪的前提。

第二处改动是 `@SuppressWarnings` 增加 `"CyclomaticComplexity"`，因为接下来新增的 case 让圈复杂度超过 checkstyle 阈值。

第三处改动是在 `castTo` 的 switch 中新增 `TIMESTAMP`、`TIMESTAMP_NANO`、`DATE` 三个 case，处理跨精度/跨语义转换：

- `case TIMESTAMP`（目标为 micros 时间戳）：当 Variant 物理类型是 `TIMESTAMPTZ_NANOS`/`TIMESTAMPNTZ_NANOS` 时，用 `DateTimeUtil.nanosToMicros(...)` 把纳秒截断为微秒；当物理类型是 `DATE` 时，把日期转成"当天 00:00 的 micros 时间戳"（`dateFromDays(...).atStartOfDay()` → `microsFromTimestamp(...)`）。
- `case TIMESTAMP_NANO`（目标为纳秒时间戳）：当物理类型是 `TIMESTAMPTZ`/`TIMESTAMPNTZ`（micros）时，用 `microsToNanos(...)` 把微秒扩展为纳秒；物理类型是 `DATE` 时类似地转换为"当天 00:00 的纳秒时间戳"。
- `case DATE`（目标为天数）：当物理类型是 micros 时间戳时用 `microsToDays(...)`；是纳秒时间戳时用 `nanosToDays(...)`。

这些转换函数与 Iceberg 字面值（`Literal`）中已有的转换保持一致（commit message 中也提到"Simplified conversions for timestamp, timestampnano, date based on the conversions used in literals"），确保 Variant 物理值与查询字面值在同一时间轴上可比。这正是 Inclusive Metrics Evaluator 比较上下界时所需的前提：lower/upper bound 是 Variant 编码的物理值，需要先 `castTo` 成查询目标类型，再与查询字面值比较。

### `core/src/test/java/org/apache/iceberg/expressions/TestInclusiveMetricsEvaluatorWithExtract.java` (+350/-0 lines)

**修改目的**：覆盖新增类型支持的端到端裁剪行为。

**工作逻辑**：

新增 import：`java.nio.ByteBuffer`、`java.util.UUID`、`org.apache.iceberg.variants.PhysicalType`、`org.apache.iceberg.variants.VariantValue`、`org.junit.jupiter.params.provider.Arguments`。

新增两个参数化数据源：

- `DATEANDTIMESTAMPTYPESEQPARAMETERS`：一组 `Arguments`，外层按目标类型（`TimestampNanoType.withoutZone()` / `DateType` / `TimestampType.withoutZone()`）分组，每组内含若干 `Arguments`，每个 `Arguments` 形如 `(查询字面值字符串, lower bound Variant 值, upper bound Variant 值, 期望是否应读取)`。覆盖"查询值低于下界→不应读""介于上下界之间→应读""等于上界→应读""高于上界→不应读"等典型场景，以及目标类型与 Variant 物理类型的多种组合（例如目标 TimestampNano + 物理 Timestamp(micros)、目标 Date + 物理 TimestampNano 等）。
- `DATEANDTIMESTAMPTYPESNOTEQPARAMETERS`：类似结构，但用于 `notEqual` 测试——`notEqual` 在 Inclusive Metrics Evaluator 中只要文件可能含有不等于查询值的记录就应读取，因此期望通常为 `true`。

新增三个测试方法：

```java
@ParameterizedTest
@FieldSource("DATEANDTIMESTAMPTYPESEQPARAMETERS")
public void testDateAndTimestampTypesEq(String variantType, Arguments args) {
  ...
  DataFile file = new TestDataFile("file.parquet", Row.of(), 50, null, null, null, lowerBounds, upperBounds);
  Expression expr = equal(extract("variant", "$.event_timestamp", variantType), args.get()[0]);
  assertThat(shouldRead(expr, file)).isEqualTo(args.get()[3]);
}
```

该方法构造一个带 lower/upper bound 的 `DataFile`（bounds 以 Variant 编码），用 `extract("variant", "$.event_timestamp", variantType)` 提取 Variant 路径并指定目标类型，然后用 `equal` 与查询字面值比较，断言 `shouldRead` 与参数中预期一致。这同时验证了 `castTo` 的快路径与跨精度转换路径，以及 Inclusive Metrics Evaluator 对 Variant `extract` 表达式的整体裁剪逻辑。

`testDateAndTimestampTypesNotEq` 结构类似，断言恒为 `true`（"Should read: many possible timestamps"）。

`testUUIDEq` 是 UUID 专项测试：用 `UUID.randomUUID()` 生成 UUID，构造 lower=upper=该 UUID 的 Variant bounds（即文件中该 UUID 列取值唯一且等于查询值），用 `equal(extract("variant", "$.event_uuid", PhysicalType.UUID.name()), uuid)` 做等值过滤，断言 `shouldRead` 为 `true`。这验证了 UUID 走 `NO_CONVERSION_NEEDED` 快路径后能正确参与等值裁剪。

## 总结

该提交补齐了 `VariantExpressionUtil` 对 `TIME`、`TIMESTAMPNTZ_NANO`、`TIMESTAMPTZ_NANO`、`UUID` 四种 Variant 物理类型的支持，并在 `castTo` 中增加了 micros/nanos/date 之间的跨精度时间戳转换，使 Inclusive Metrics Evaluator 能对 Variant 列中这些类型的字段做正确的文件级上下界裁剪。改动以注册映射 + switch case 的方式实现，圈复杂度暂时通过 `@SuppressWarnings` 压制并留 TODO 待后续重构；测试侧通过参数化用例覆盖了目标类型与物理类型的多种组合以及 UUID 等值场景，把"对纳秒时间戳/UUID 列过滤裁不掉/裁错"的功能缺口填上。
