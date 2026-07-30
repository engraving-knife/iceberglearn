# 提交 0211：Spec: Clarify partition equality (#9125)

## 提交信息

- **序号**：0211 / 4088
- **哈希**：1ed1b4ba965462698ec24bd2920d29195e2acae4
- **短哈希**：1ed1b4ba9
- **日期**：2023-12-04 11:39:27 -0800
- **作者**：emkornfield
- **提交说明**：Spec: Clarify partition equality (#9125)
- **PR/Issue**：#9125

## 总体目的

本提交是 Iceberg 规格文档（`format/spec.md`）的一处澄清性修改，不涉及任何代码改动。它的目的是消除规格中关于"分区相等（partition equality）"的歧义，把过去隐含在多处实现中、但没有在规格里明确陈述的语义写清楚。

具体而言，规格先前对两个 partition spec 何时算"等价"、partition field ID 何时必须复用、equality delete 在何种 partition 关系下生效、以及浮点 partition 值如何比较相等，都没有给出清晰的、可被实现一致遵循的定义。这导致不同引擎/写入器在边界情况下可能产生不一致行为：例如一个写入器看到逻辑上等价的 spec 后又新建了一个 ID 不同的 spec，或者读取器在判断 equality delete 是否要应用到某个 data file 时对 "partition 相等" 理解不同，从而错误地跳过或重复应用 delete。本提交通过补充三段说明，把这些语义在规格层面固定下来，使后续实现有据可依，也使一致性测试有明确依据。

这对 Iceberg 的多引擎生态尤其重要：Trino、Spark、Flink、Presto 以及各厂商实现都依赖这份规格来保证互操作，规格模糊会直接演化为跨引擎 bug。

## 如何达成设计目的

提交完全通过编辑 `format/spec.md` 一处文档达成目的，共 3 处增量：在 "Partition_specs" 一节补充"两个 partition spec 等价"的定义和"partition field ID 必须复用"的约束；在 "Scan Planning" 节把 equality delete 的应用条件从 "both spec and partition values" 改为 "both spec id and partition values"；并在 Scan Planning 的 Notes 中新增一条浮点 partition 值相等的精确定义（基于 IEEE 754 位布局并归一化 NaN）。

## 修改详情

### `format/spec.md`

**修改目的**：从规格层面澄清 partition equality 的三个维度——spec 等价、spec id 而非 spec 本身用于 delete 应用判断、浮点 partition 值的位级相等定义。

**工作逻辑**：

1. **新增 partition spec 等价定义**（约 305 行附近）：明确两个 spec 等价当且仅当字段数相同，且对应字段的 source column ID、transform 定义、partition name 都相同。并强制写入器：若表中已有等价 spec，不得新建 spec。这与之前仅在代码中隐含的"spec 复用"行为对齐，避免相同逻辑 spec 被赋予不同 ID，进而导致 scan planning 与 delete 应用错误。

2. **强制复用 partition field ID**：紧接上条新增一句"Partition field IDs must be reused if an existing partition spec contains an equivalent field."，进一步约束写入器在演进出等价字段时复用 ID，保证字段身份稳定。

3. **修正 equality delete 应用条件**（约 598 行）：把
   `The data file's partition (both spec and partition values) is equal to the delete file's partition`
   改为
   `The data file's partition (both spec id and partition values) is equal to the delete file's partition`。
   用 "spec id" 替代 "spec"，把判断从"两个 spec 对象相等"收紧为"两个 spec ID 相等"，这与 Iceberg 实现中按 `specId` 匹配 delete 应用范围的实际语义一致，避免在 spec 演进（spec id 变化但逻辑等价）时错误地把一个 delete 应用到不该应用的分区。

4. **新增浮点 partition 值相等定义**（Notes 第 3 条，约 610 行）：规定浮点 partition 值相等当且仅当其 IEEE 754 "single format" 位布局相等，且 NaN 被归一化为只置最高 mantissa 位（等价于 Java 的 `Float.floatToIntBits` / `Double.doubleToLongBits`）。并说明 Avro 规格已要求所有浮点值按此格式编码，因此该定义与现有序列化兼容。这解决了 `NaN` 的多种位表示、`-0.0` 与 `+0.0` 等边界情况在分区匹配上的歧义。

## 小结

通过在规格层面把 partition spec 等价、spec id 维度的 delete 应用判断、以及浮点 partition 值的位级相等三条语义写清楚，本提交消除了 Iceberg 规格中长期存在的歧义，为多引擎互操作和实现一致性提供了明确依据。
