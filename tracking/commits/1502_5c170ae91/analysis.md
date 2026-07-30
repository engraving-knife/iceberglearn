# 提交序号 1502 短哈希 5c170ae91 分析

## 提交信息
- 哈希：5c170ae91b35e01f82d963a0459ec1f0f77643aa
- 日期：2024-12-17
- 作者：Manu Zhang <OwenZhang1990@gmail.com>
- 消息：docs: Default value of table level distribution-mode should be not set (#11663)

## 总体目的

本提交修正了 Iceberg 配置文档中关于表级 `write.distribution-mode` 系列属性默认值的不准确描述。在修改前，文档将这些属性的默认值错误地标注为具体的取值（例如 `none` 或 `hash`），但实际上在表属性层面，这些键的默认值是"未设置"（not set），真正的默认行为由各计算引擎（如 Spark）自行决定。

这种文档与实际行为的不一致容易误导用户：用户可能误以为在表属性层面设置这些键就会得到某个固定值，或者误以为不设置时 Iceberg 会回退到一个统一的默认值。实际上，Iceberg 在表属性层面并不强制设定默认值，而是将分发模式的默认行为交给具体的引擎实现来决定。例如 Spark 在不同写操作场景下有不同的默认分发模式。

通过本次修正，文档更加准确地反映了"表属性默认未设置，引擎有各自的默认值"这一设计，避免用户基于错误文档做出错误的配置决策。

## 如何达成设计目的

本提交通过修改 `docs/docs/configuration.md` 中四行配置表格条目来达成目的。具体做法是把"默认值"一列中给出的具体取值替换为"not set"或"(not set)"，并在说明列中指向各引擎的文档以便用户查阅真正的默认行为。

### 修改详情

#### docs/docs/configuration.md

该文件是 Iceberg 核心配置属性的参考文档，其中以表格形式列出了各项表属性及其默认值与说明。本次修改涉及以下四个属性：

1. `write.distribution-mode`：默认值由 `none` 改为 `not set`。这是写入数据的整体分发模式属性。修改前文档错误地标注为 `none`，但实际上表属性层面并不设置默认值，真正的默认值由引擎决定（参见 Spark Writes 文档）。

2. `write.delete.distribution-mode`：默认值由 `hash` 改为 `(not set)`。该属性控制删除写操作的分发模式。

3. `write.update.distribution-mode`：默认值由 `hash` 改为 `(not set)`。该属性控制更新写操作的分发模式。

4. `write.merge.distribution-mode`：默认值由 `none` 改为 `(not set)`。该属性控制合并写操作的分发模式。

对于后三个属性，使用 `(not set)` 这一括号写法与文档中其他"未设置"属性（如 `write.metadata.metrics.column.col1` 标注为 `(not set)`）保持风格一致；而第一个属性 `write.distribution-mode` 原本就采用了一种特殊的内联写法（值与说明合并在一行的较长格式），因此沿用 `not set` 不加括号以保持原有格式风格。这样既修正了内容错误，又保持了文档排版的一致性。

## 小结

这是一个纯文档修正提交，没有代码逻辑变更。其意义在于消除配置文档与实际行为之间的偏差，使用户能够正确理解表级分发模式属性的默认值语义：表属性层面默认不设置，真正的默认行为由各计算引擎自行决定。这有助于避免用户因误信文档而做出不正确的配置选择，提升了文档的准确性和可信度。
