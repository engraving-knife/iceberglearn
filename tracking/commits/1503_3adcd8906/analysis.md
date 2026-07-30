# 提交序号 1503 短哈希 3adcd8906 分析

## 提交信息
- 哈希：3adcd89061ae0f4db7d53ad5bc548464a27f2b27
- 日期：2024-12-17
- 作者：Manu Zhang <OwenZhang1990@gmail.com>
- 消息：Docs: Fix Spark catalog `table-override` description (#11684)

## 总体目的

本提交修正了 Spark 配置文档中对 `table-override` 属性描述的不准确措辞。修改前的描述为"Enforced Iceberg table property value for property key _propertyKey_, which cannot be overridden by user"，即"不能被用户覆盖"。这种表述过于宽泛，容易让人理解为该属性值在任何情况下都不能被用户修改。

实际上，`table-override` 的语义是：在表创建时（table creation），由 catalog 强制设定的属性值不能被用户覆盖。也就是说，覆盖限制的作用范围是"建表时"，而非一个绝对的、无条件的禁令。本次修改通过在描述中加入"on table creation"这一限定条件，使描述更精确地反映实现行为，避免用户对属性覆盖机制产生误解。

## 如何达成设计目的

本提交通过对 `docs/docs/spark-configuration.md` 中一行表格描述的措辞调整来达成目的。核心改动是在"cannot be overridden"之后补充"on table creation"作为作用域限定。

### 修改详情

#### docs/docs/spark-configuration.md

该文件描述了 Spark catalog 的各项配置属性。本次修改针对 `spark.sql.catalog._catalog-name_.table-override._propertyKey_` 这一属性行：

- 修改前：`Enforced Iceberg table property value for property key _propertyKey_, which cannot be overridden by user`
- 修改后：`Enforced Iceberg table property value for property key _propertyKey_, which cannot be overridden on table creation by user`

加入的"on table creation"明确了 `table-override` 的强制覆盖行为只在建表这一动作发生时生效。这与 `table-default`（仅在用户未指定时作为默认值）形成对照：`table-default` 允许用户在建表时覆盖默认值，而 `table-override` 则在建表时强制使用 catalog 设定的值、不允许用户覆盖。明确作用域后，用户能更清楚地区分这两种机制各自的适用场景。

## 小结

这是一个单行文档措辞修正提交。其意义在于精确化 `table-override` 属性的语义说明，明确"覆盖禁止"仅作用于建表时刻，避免用户误以为该属性在所有场景下都绝对不可被用户修改。这提升了文档的精确性，有助于用户正确理解和使用 `table-default` 与 `table-override` 这两类 catalog 属性。
