# 提交 0139：Spec: Clarify ns timestamps for ORC deserialization (#9007)

## 提交信息

- **序号**：0139 / 4088
- **哈希**：af132c7f8d0e820a3bdc23de4dd76f343c7bb399
- **短哈希**：af132c7f8
- **日期**：2023-11-08 12:51:38 -0800
- **作者**：Jacob Marble
- **提交说明**：Spec: Clarify ns timestamps for ORC deserialization (#9007)
- **PR/Issue**：#9007（帮助 #8657）

## 总体目的

这个提交修改了 Iceberg 规范文档 `format/spec.md` 中 ORC 类型映射表的一行规则：为 Iceberg 的 `timestamp`、`timestamptz`、`timestamp_ns`、`timestamptz_ns` 四种时间戳类型在 ORC 侧明确指定了一个新的类型属性 `iceberg.timestamp-unit`（取值 `MICROS` 或 `NANOS`），从而让 ORC 反序列化器能够唯一地判断一个 ORC `timestamp`/`timestamp_instant` 列到底对应 Iceberg 的微秒精度还是纳秒精度。这是一处规范层面的澄清，不包含代码实现，但为后续 ORC 读写器支持纳秒时间戳（对应 issue #8657）打下了规范基础。

背景与动机：Iceberg 在 spec 中已经引入了纳秒精度时间戳类型 `timestamp_ns` 与 `timestamptz_ns`，但 ORC 文件格式本身的 `timestamp` 和 `timestamp_instant` 类型只有一种存储精度——纳秒。也就是说，ORC 的 `timestamp` 既可能承载 Iceberg 的微秒时间戳（写入时截断纳秒），也可能承载 Iceberg 的纳秒时间戳（保留纳秒）。问题在于：之前规范表格里 `timestamp`/`timestamptz`/`timestamp_ns`/`timestamptz_ns` 四种 Iceberg 类型在 ORC 侧都映射到 `timestamp` 或 `timestamp_instant`，且没有区分属性，导致读端在反序列化时无法从 ORC schema 上区分"这一列到底是微秒还是纳秒"，只能默认按微秒处理或做不可靠推断。

提交说明里点明了关键点：为了让 ORC 类型 `timestamp` 和 `timestamp_instant` 能够正确转换为 Iceberg 的 `timestamp`、`timestamp_ns`、`timestamptz`、`timestamptz_ns`，需要一个 ORC 类型属性来显式声明精度单位。本次改动就是把这个属性 `iceberg.timestamp-unit`（取值 `MICROS`/`NANOS`）写进规范表格，并在注释 [2] 中补充说明：当该属性不存在时默认按 `MICROS` 处理，从而保持对旧文件的向后兼容。

这对 Iceberg 演进的意义是规范层面补齐了 ORC 对纳秒时间戳的支持契约：写端需要在 ORC schema 上写出 `iceberg.timestamp-unit` 属性，读端据此选择正确的精度反序列化路径，使 Iceberg 的纳秒时间戳能力在 ORC 格式上真正可用、可互操作。

## 如何达成设计目的

设计思路是在 ORC 类型映射表里给四种时间戳类型补上"ORC 类型属性"这一列的取值：微秒类型标 `iceberg.timestamp-unit`=`MICROS`，纳秒类型标 `iceberg.timestamp-unit`=`NANOS`；再在表格下方的注释 [2] 中追加一句默认值约定，说明属性缺失时按 `MICROS` 解释，保证对历史 ORC 文件的向后兼容。整段改动只触及 `format/spec.md` 一个文件、表格中的 4 行加注释里的 1 句，但把原本"类型映射有歧义"的局面改成"读端可据属性唯一判定精度"。

## 修改详情

### `format/spec.md`

**修改目的**：在 ORC 类型映射表中为 Iceberg 的四种时间戳类型显式指定 `iceberg.timestamp-unit` 属性，并在注释中补上属性缺失时的默认值约定。

**工作逻辑**：

1. 表格中 `ORC type attribute` 列的改动（针对四种时间戳类型）：

   - `timestamp`（Iceberg）→ ORC `timestamp`，属性由"空"改为 `iceberg.timestamp-unit`=`MICROS`。
   - `timestamptz`（Iceberg）→ ORC `timestamp_instant`，属性由"空"改为 `iceberg.timestamp-unit`=`MICROS`。
   - `timestamp_ns`（Iceberg）→ ORC `timestamp`，属性由"空"改为 `iceberg.timestamp-unit`=`NANOS`。
   - `timestamptz_ns`（Iceberg）→ ORC `timestamp_instant`，属性由"空"改为 `iceberg.timestamp-unit`=`NANOS`。

   这样，读端在拿到 ORC schema 时，只要看 `iceberg.timestamp-unit` 属性就能唯一确定这一列对应 Iceberg 的微秒还是纳秒类型，无需再做启发式推断。

2. 注释 [2] 的改动：

   原文末尾是"Iceberg ORC writers for Iceberg types `timestamp` and `timestamptz` **must** truncate nanoseconds to microseconds."，本次在其后追加一句：

   > `iceberg.timestamp-unit` is assumed to be `MICROS` if not present.

   这句话是关键的向后兼容条款：旧版 ORC 写入器不会写出 `iceberg.timestamp-unit` 属性，读端在遇到缺失属性时按 `MICROS` 处理，行为与改动前一致；只有写出 `NANOS` 属性的列才会被读端按纳秒精度处理。这样既支持了新的纳秒时间戳场景，又不破坏对历史文件的读取。

   注释 [1] 解释 ORC `TimestampColumnVector` 的 time/nanos 双字段机制，本次未改动，但它构成了"ORC 物理上始终存纳秒、需要属性来区分逻辑精度"这一设计的事实基础。

## 小结

在规范层面为 ORC 时间戳映射补上 `iceberg.timestamp-unit`（`MICROS`/`NANOS`）类型属性并约定缺失时默认 `MICROS`，让读端能唯一判定 ORC `timestamp`/`timestamp_instant` 列对应的 Iceberg 时间戳精度，为 Iceberg 纳秒时间戳在 ORC 格式上的正确读写与互操作奠定规范基础。
