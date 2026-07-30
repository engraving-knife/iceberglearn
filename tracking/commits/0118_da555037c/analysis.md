# 提交 0118：Spec: add nanosecond timestamp types (#8683)

## 提交信息

- **序号**：0118 / 4088
- **哈希**：da555037cf0f19a635927df043fd02af0193d49d
- **短哈希**：da555037c
- **日期**：2023-10-31
- **作者**：Jacob Marble
- **提交说明**：Spec: add nanosecond timestamp types (#8683)
- **PR/Issue**：#8683

## 总体目的

Iceberg 此前的规范只支持微秒精度的两种时间戳类型：`timestamp`（不带时区）与 `timestamptz`（带时区），均以 8 字节 long 存储自 Unix epoch 以来的微秒数。这对许多事件日志、金融、观测数据等需要纳秒级时间戳的应用来说精度不足，用户不得不绕道用 `long` 存原始纳秒值，丢失了类型语义和跨引擎的一致性。本提交依据设计文档（提交说明中链接的 Google Doc）正式在 Iceberg 规范中新增两种纳秒精度的时间戳类型 `timestamp_ns` 与 `timestamptz_ns`，并把它们标记为 v3 spec 引入的类型。

这是 Iceberg 规范层面一次重要的能力扩展。新增类型在物理存储上仍用 8 字节 long（存自 epoch 的纳秒数），覆盖约 1677 年以前到 2262 年以后的范围，足以满足绝大多数业务场景。由于是新类型而非对旧 `timestamp`/`timestamptz` 的精度升级，旧表与旧读取器不受影响，保持了向后兼容；同时规范明确把纳秒类型划归 v3，避免在 v1/v2 表上误用导致向前兼容性问题（v1/v2 旧读取器无法识别这些类型）。

除了新增类型本身，本提交还顺手做了两件规范化工作：一是在原语类型表里新增"Added by version"列，明确标注每个类型由哪个 spec 版本引入（此前只有零散的版本说明），让版本演进一目了然；二是对各序列化附录（Avro/Parquet/ORC/JSON/单值二进制/默认值）中时间戳相关条目的描述做了精度澄清，把"timestamp without zone / with zone"这种模糊行名改为"timestamp, microseconds, without/with zone"等更精确的表述，并补充了 ORC 写入器对微秒类型必须截断纳秒的强制要求。

## 如何达成设计目的

整体设计思路是：以新增两个独立类型（而非改造旧类型）的方式引入纳秒精度，并在规范的每一处涉及原语类型的地方同步登记这两个新类型，确保它们在分区变换、各存储格式序列化、哈希分桶、JSON 元数据序列化、单值二进制序列化、默认值表示等所有路径上都有明确语义。改动全部集中在 `format/spec.md` 一个文件（+62 / -36 行），是对规范的纯文档修订，不涉及任何代码实现（实现由后续 PR 完成）。

改动的整体结构为：(1) 原语类型表加列、加行；(2) 分区变换表扩展源类型；(3) Avro/Parquet/ORC 三种列存格式序列化表各加两行并补充说明；(4) 哈希分桶表加两行给出纳秒哈希实现与示例值；(5) JSON 类型序列化表与单值二进制序列化表各加两行；(6) JSON 默认值表示表加两行给出纳秒精度 ISO-8601 示例；(7) 在 v3 spec 章节声明新类型归属。

## 修改详情

### `format/spec.md` — 原语类型表（Primitive Types）

**修改目的**：新增 `timestamp_ns` 与 `timestamptz_ns` 两个纳秒精度时间戳类型，并引入"Added by version"列标注类型来源版本。

**工作逻辑**：
- 在原语类型表前新增说明文字，明确"v1 之后新增的原语类型带有'added by'版本，例如纳秒时间戳属于 v3 spec，在 v1/v2 表上使用会破坏向前兼容"。
- 新增表头列 `Added by verison`（注：此处规范原文存在拼写错误 "verison"，应为 "version"，但这是规范文件的实际状态），v1 已有类型该列为空，新类型标注 `[v3](#version-3)` 链接。
- 新增两行：`timestamp_ns`（纳秒精度、不带时区）与 `timestamptz_ns`（纳秒精度、带时区）。
- 把原 `timestamp` / `timestamptz` 的描述从"Timestamp without timezone / Timestamp with timezone"细化为"Timestamp, microsecond precision, without/with timezone"，明确区分微秒与纳秒精度。
- 把原 `timestamptz` 行"Stored as UTC"的说明移到下文 Notes 统一表述。

### `format/spec.md` — Notes（原语类型脚注）

**修改目的**：澄清微秒与纳秒精度的归属，统一时间戳带/不带时区的语义说明。

**工作逻辑**：原脚注 2 写"All time and timestamp values are stored with microsecond precision"，改为"`time`, `timestamp`, and `timestamptz` values are represented with _microsecond precision_. `timestamp_ns` and `timstamptz_ns` values are represented with _nanosecond precision_."（注：此处规范原文 `timstamptz_ns` 存在拼写错误，应为 `timestamptz_ns`，是规范实际状态）。同时把"Timestamps with/without time zone"改为"Timestamp values with/without time zone"，措辞更准确；并删去原来"Timestamp values are stored as a long that encodes microseconds from the unix epoch"这句与各序列化附录重复的描述。

### `format/spec.md` — 分区变换表（Partition Transforms）

**修改目的**：让纳秒时间戳类型支持所有时间相关的分区变换。

**工作逻辑**：在 `bucket[N]`、`year`、`month`、`day`、`hour` 五种变换的"Source types"列追加 `timestamp_ns` 与 `timestamptz_ns`。这意味着对纳秒时间戳同样可以按年/月/日/小时分区，也可参与哈希分桶，行为与微秒时间戳一致（按时间分量抽取，结果类型仍为 `int`）。

### `format/spec.md` — Avro 序列化附录

**修改目的**：定义纳秒时间戳在 Avro 中的逻辑类型表示。

**工作逻辑**：
- 新增两行：`timestamp_ns` 与 `timestamptz_ns`，均用 `{ "type": "long", "logicalType": "timestamp-nanos", "adjust-to-utc": false/true }`，存储自 `1970-01-01 00:00:00.000000000` 起的纳秒数。
- 在表后新增 Notes：(1) `adjust-to-utc` 是 Iceberg 约定，Avro 默认 `false`；(2) `timestamp-nanos` 逻辑类型是 Iceberg 约定，Avro 规范本身未定义该类型——这等于声明 Iceberg 在 Avro 之上做了扩展。
- 顺手把 `date` 行的"Stores days from the 1970-01-01"改为"Stores days from 1970-01-01"（去掉多余的 "the"），并把 `timestamp`/`timestamptz` 行加上脚注引用。

### `format/spec.md` — Parquet 序列化附录

**修改目的**：定义纳秒时间戳在 Parquet 中的逻辑类型注解。

**工作逻辑**：新增两行，`timestamp_ns` 与 `timestamptz_ns` 均存为 `int64`，Parquet 逻辑类型注解为 `TIMESTAMP_NANOS`，分别带 `adjustToUtc=false` / `true`，存储自 `1970-01-01 00:00:00.000000000` 起的纳秒数。同时把 `date` 行描述去掉多余的 "the"。

### `format/spec.md` — ORC 序列化附录

**修改目的**：定义纳秒时间戳在 ORC 中的表示，并强制要求微秒类型写入器截断纳秒。

**工作逻辑**：
- 新增两行：`timestamp_ns` 与 `timestamptz_ns` 均映射到 ORC 的 `timestamp` / `timestamp_instant` 类型（ORC 本身就存纳秒），存储自 `2015-01-01 00:00:00.000000000` 起的纳秒数（ORC 使用 2015 年作为时间基准，与其他格式的 1970 epoch 不同）。
- 顺手把原 `timestamp` / `timestamptz` 行的描述从仅"[1]"补充为"Stores microseconds from 2015-01-01 00:00:00.000000. [1], [2]"，明确微秒类型的存储基准与精度。
- 新增脚注 2："ORC `timestamp` 和 `timestamp_instant` 值存储纳秒精度。Iceberg ORC 写入器对 Iceberg 类型 `timestamp` 和 `timestamptz` **必须**把纳秒截断为微秒。"——这是关键约束：因为 ORC 底层天然支持纳秒，Iceberg 必须在写入微秒类型时主动截断，以保证 `timestamp` 类型严格保持微秒精度，避免读回时出现超出预期的亚微秒数据。

### `format/spec.md` — 哈希分布（bucket 变换的 32-bit Murmur3 实现）

**修改目的**：为纳秒时间戳定义哈希分桶的输入与示例哈希值。

**工作逻辑**：
- 新增两行 `timestamp_ns` 与 `timestamptz_ns`，哈希实现为 `hashLong(nanosecsFromUnixEpoch(v))`，即把自 Unix epoch 的纳秒数作为 long 喂给 Murmur3。
- 给出多个示例值，包括秒级、微秒级（`.000001`）、纳秒级（`.000000001`）三种精度的输入及其哈希结果，便于实现者对照测试。
- 同时为原 `timestamp` / `timestamptz` 行补充微秒级示例（`2017-11-16T22:31:08.000001 ￫ -1207196810`），此前只有秒级示例。

### `format/spec.md` — JSON 类型序列化表

**修改目的**：定义纳秒时间戳在 schema JSON 中的类型字符串。

**工作逻辑**：把原行名"timestamp without zone / timestamp with zone"改为更精确的"timestamp, microseconds, without/with zone"，并新增两行"timestamp, nanoseconds, without/with zone"，对应 JSON 字符串 `"timestamp_ns"` / `"timestamptz_ns"`。这确保 schema JSON 中类型字段可以区分微秒与纳秒精度。

### `format/spec.md` — 单值二进制序列化表

**修改目的**：定义纳秒时间戳作为单值（如分区值）的二进制序列化方式。

**工作逻辑**：
- 新增两行：`timestamp_ns` 与 `timestamptz_ns` 均存为 8 字节小端 long，存储自 `1970-01-01 00:00:00.000000000`（或 UTC）起的纳秒数。
- 把原行名"timestamp without zone / timestamp with zone"重命名为 `timestamp` / `timestamptz`，与类型名保持一致。

### `format/spec.md` — JSON 默认值表示表

**修改目的**：定义纳秒时间戳默认值的 JSON 字符串表示。

**工作逻辑**：新增两行，`timestamp_ns` 默认值为 ISO-8601 字符串如 `"2017-11-16T22:31:08.123456789"`（不带时区偏移），`timestamptz_ns` 默认值为 `"2017-11-16T22:31:08.123456789+00:00"`（必须带 `+00:00` 偏移）。两者均使用纳秒精度（9 位小数），与微秒类型（6 位小数）形成对照。

### `format/spec.md` — Version 3 章节

**修改目的**：在 v3 spec 演进说明中登记新类型。

**工作逻辑**：在 v3 章节追加一句"Types `timestamp_ns` and `timestamptz_ns` are added in v3."，明确这两个类型的版本归属，与原语类型表中的"Added by version"列相互呼应，作为规范的权威声明。

## 小结

本提交在 Iceberg 规范中正式新增 `timestamp_ns` 与 `timestamptz_ns` 两种纳秒精度时间戳类型（归属 v3），并在分区变换、Avro/Parquet/ORC 序列化、哈希分桶、JSON 元数据、单值二进制、默认值表示等所有相关章节同步登记，是 Iceberg 时间精度能力扩展的奠基性规范变更。
