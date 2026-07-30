# 提交 1482：Docs: fix typos in spec (#11759)

## 提交信息

- **序号**：1482 / 4088
- **哈希**：af5e156edec0b225b20cb81662abd491c6d289dd
- **短哈希**：af5e156ed
- **日期**：2024-12-12（Thu Dec 12 15:50:21 2024 +0800）
- **作者**：xxchan <xxchan22f@gmail.com>
- **提交说明**：Docs: fix typos in spec (#11759)
- **PR/Issue**：#11759

## 总体目的

Iceberg 的格式规范文档 `format/spec.md` 是整个项目的核心契约文档，描述分区规范、投影规则、快照引用等内容，被多种引擎实现（Spark / Flink / Trino / Hive 等）以及外部社区参考。文档中存在两处英文拼写错误会降低规范的可读性与专业性，尤其对于按规范独立实现 Iceberg 读写器的下游项目，文档措辞就是接口契约的一部分。

本提交修掉两处明显拼写错误，使规范表述更准确：
1. "parition" → "partition"
2. "equivelant" → "equivalent"

这是纯文档维护性改动，不涉及任何代码、构建或行为变更。

## 如何达成设计目的

直接修改 `format/spec.md` 中两个段落里的拼写错误，无其它改动。改动量极小，仅在原句替换单词。

## 修改详情

### `format/spec.md`

**修改目的**：修复规范文档中的拼写错误。

**工作逻辑**：

1. 第 441 行附近（分区规范等价性段落）：
   - 修改前："Writers must not create a new **parition** spec if there already exists a compatible partition spec defined in the table."
   - 修改后："Writers must not create a new **partition** spec if there already exists a compatible partition spec defined in the table."
   - 修复 `parition` → `partition`。这一句规范说明：当表里已有等价分区规范时，writer 不应新建分区规范，是分区字段 ID 复用规则的前置说明，措辞准确性有实际意义。

2. 第 792 行附近（Notes 第 3 条，浮点分区值相等性段落）：
   - 修改前："...with NaNs normalized to have only the the most significant mantissa bit set (the **equivelant** of calling `Float.floatToIntBits` or `Double.doubleToLongBits` in Java)."
   - 修改后："...with NaNs normalized to have only the the most significant mantissa bit set (the **equivalent** of calling `Float.floatToIntBits` or `Double.doubleToLongBits` in Java)."
   - 修复 `equivelant` → `equivalent`。这段说明浮点分区值比较时 NaN 规范化规则，参考 Java 标准库行为，措辞准确性对实现者有指导意义。

> 注：上述第二处句子中的 "the the" 重复冠词未被本提交修复，仍保留原文。

## 小结

- **成效**：规范文档 `format/spec.md` 中两处明显拼写错误被修复，文档表述更专业。
- **影响范围**：仅 1 个文件、2 行改动，纯文档，无任何代码、构建、测试或运行时行为变化。
- **回迁到 1.4.x 的注意事项**：`format/spec.md` 是仓库根目录下的规范文档，不随发布产物打包，也不影响 1.4.x 运行时行为。1.4.x 作为维护分支，其 `format/spec.md` 内容在 1.4.x 发布时点的状态已固化。此类文档拼写修正**可回迁也可不回迁**：
  - 若 1.4.x 分支的 `format/spec.md` 仍保留这两个错误且社区希望维护分支文档也保持准确，可干净 cherry-pick（无冲突风险）；
  - 若 1.4.x 已偏离 main 较多或不在意维护分支的文档细节，可不做处理，对发布产物无影响。
  - 建议优先回迁，因为 cherry-pick 成本几乎为零，且能让维护分支文档保持与 main 一致的专业度。
