# 提交 0409：Spec: Add multi-arg transform

## 提交信息

- **序号**：0409
- **哈希**：200b9c16b6f8d5fecb15556c8804e5dd521aedf6
- **短哈希**：200b9c16b
- **日期**：2024 年 1 月 26 日（Fri Jan 26 02:33:41 2024 +0800）
- **作者**：advancedxy <xianjin@apache.org>
- **提交说明**：Spec: Add multi-arg transform (#8579)
- **PR/Issue**：#8579

## 总体目的

这是一个 Iceberg 表格式规范（spec）层面的扩展，为分区转换（partition transform）和排序字段（sort field）引入"多参数 transform"的概念，让一个 transform 可以接受多个源列作为输入。在此前的规范中，每个分区字段或排序字段都由"一个 source column id + 一个 transform"组成——transform 作用于单列，产出一个分区值或排序值。这种单参数模型在常见的 bucket、truncate、year、month、day、hour 等 transform 上工作良好，但无法表达"需要联合多列才能计算分区值"的场景，例如基于多列做联合哈希分桶（multi-column bucketing）、按多列组合做 transform 等。

本次修改把规范中分区字段和排序字段的定义从"单 source column id"扩展为"单个 source column id 或一组 source column ids"。这是规范向前兼容的扩展：旧的单参数 transform 行为完全不变，依然走 `source-id` 字段；新的多参数 transform 通过新增的 `source-ids` 字段表达。为了保持向后兼容（旧 reader 不识别 `source-ids`），规范规定多参数场景下 `source-id` 必须设为 -1，作为"这是多参数 transform"的信号。这一设计在格式规范层为后续实现多列 transform（如 multi-arg bucket、自定义 transform）打开了空间，是 Iceberg 格式能力扩展的基础性铺垫。

需要强调的是，这个提交只改 `format/spec.md` 这一个文件——它是规范文档的变更，不是实现层的变更。规范先行、实现跟进是 Iceberg 项目演进的一贯节奏：先在 spec 中明确语义和序列化格式，再在各个客户端（Java 参考实现、Spark、Flink 等）逐步落地。这种做法确保跨语言实现有统一契约可依。

## 如何达成设计目的

实现路径集中在 `format/spec.md` 的三处文字描述上。第一处是"分区字段定义"段落，把"A source column id"改为"A source column id or a list of source column ids"，并把"A transform that is applied to the source column"改为"applied to the source column(s)"。第二处是"排序字段定义"段落，做同样的单数→单数或复数扩展。第三处是附录 C（JSON 序列化）中分区字段和排序字段的序列化表格，新增带编号的注释（Notes 1、2），明确单参数走 `source-id`、多参数走 `source-ids` 且 `source-id` 设为 -1 的兼容性约定。改动全部是文档层面的措辞与注释，不涉及代码。

## 修改详情

### format/spec.md

**修改目的**：在 Iceberg 格式规范中引入多参数 transform 的概念与序列化约定。

**工作逻辑**：修改分布在 spec.md 的三个区域：

1. **分区字段定义（Partition Spec 章节）**：将"A **source column id** from the table's schema"改为"A **source column id** or a list of **source column ids** from the table's schema"；将"A **transform** that is applied to the source column to produce a partition value"改为"...applied to the source column(s) to produce a partition value"。这把分区字段从"必须单列输入"扩展为"可单列或多列输入"。

2. **排序字段定义（Sort Order 章节）**：做对称修改，把 sort field 的 source column id 描述也扩展为"source column id 或一组 source column ids"，并把 transform 描述里的"source column"改为"source column(s)"。

3. **JSON 序列化表格与兼容性注释（附录 C）**：在 Partition Field 和 Sort Field 的序列化表格标题上加 `[1,2]` 角标引用，并在表格下方新增 Notes 段落，定义两条兼容性规则：

   - Note 1：对于 transform 只接受单个参数的分区/排序字段，源列的 ID 写在 `source-id` 上，`source-ids` 字段省略不写——这是 v1/v2 已有行为，完全向后兼容。
   - Note 2：对于 transform 接受多个参数的分区/排序字段，源列的 IDs 写在新增的 `source-ids` 数组上；为了向后兼容，`source-id` 必须设为 -1。这样旧的 reader 看到 `source-id = -1` 就能识别出"这是多参数 transform，需要去看 `source-ids`"，而不会把 -1 误当成有效的列 ID（Iceberg 表中列 ID 从 1 开始，-1 不是合法列 ID，因此可作为哨兵值）。

   这套设计同时保证：(a) 旧的单参数 transform 序列化形态不变，旧 reader 仍能正确读取；(b) 新的多参数 transform 通过 `source-id = -1` + `source-ids` 数组表达，新 reader 能识别，旧 reader 也不会把 -1 误解析成真实列；(c) 单/多参数在序列化层有明确区分，避免歧义。

## 小结

这是一个规范层面的能力扩展提交，模式是"spec 先行、为后续实现铺路"。改动只在 `format/spec.md` 一个文件内，把分区字段和排序字段从"单 source column id + 单参数 transform"扩展为"单个或一组 source column ids + 可多参数 transform"，并通过 `source-id = -1` 哨兵 + 新增 `source-ids` 数组的兼容性约定保证向后兼容。意义在于为 Iceberg 表格式支持多列联合 transform（如多列 bucket、自定义多参数 transform）打开规范层面的空间，是格式能力演进的奠基性变更。改动不触及任何代码实现，风险集中在规范措辞与序列化约定的清晰度上；后续 Java 参考实现和各引擎适配将基于此规范逐步落地。
