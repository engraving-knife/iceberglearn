# 提交 0372：Docs: Enhance documentation on identifier fields

## 提交信息

- **序号**：0372
- **哈希**：36841521137713d3962bb08a5d5df6490c8ac466
- **短哈希**：368415211
- **日期**：2024-01-17（作者日期 Wed Jan 17 02:26:22 2024 +0800，提交日期 Tue Jan 16 10:26:22 2024 -0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Enhance documentation on identifier fields (#9478)
- **PR/Issue**：PR #9478

## 总体目的

这是一个纯文档改进提交，目标是完善 Spark DDL 文档中关于 Iceberg identifier fields（标识字段）的说明。identifier fields 是 Iceberg 规范中的一个重要概念，用于标识一条记录的主键字段，下游引擎（特别是 Flink）依赖它来做 upsert 操作。原文档存在三处不足：(1) 在 `CREATE TABLE` 示例中，`id` 字段没有 `NOT NULL` 约束，但 identifier fields 必须是 `NOT NULL`，示例与约束说明之间存在不一致，容易误导用户；(2) 在 `SET IDENTIFIER FIELDS` 章节没有提供指向 Iceberg spec 的链接，用户难以跳转到规范原文理解概念；(3) 没有明确说明 identifier fields 的实际用途（例如支持 Flink SQL upsert），用户难以理解为什么要使用这个特性；(4) 原文 "Identifier fields must be `NOT NULL`, The later `ALTER` statement will overwrite the previous setting." 表述不够准确，"must be NOT NULL" 没有说明时机（是创建时还是后续），且大写字母 "The" 起头的句子结构松散。

提交通过补全这些细节，让文档自洽、准确，并显式建立 identifier fields 与 Flink upsert 之间的关联，降低用户在跨引擎（Spark 建表 + Flink 读写）场景下的认知成本。

## 如何达成设计目的

实现路径非常直接：仅修改 `docs/spark-ddl.md` 一个文件，做三处文本调整。第一处在 `CREATE TABLE` 示例中给 `id bigint` 加上 `NOT NULL`，使示例与 identifier fields 的约束一致；第二处在 `SET IDENTIFIER FIELDS` 章节加入指向 Iceberg spec identifier-field-ids 段落的超链接，并新增一行说明 Spark 表若有 identifier fields 即可支持 Flink SQL upsert；第三处把原来含糊的 "must be NOT NULL, The later ALTER ..." 拆成两句更清晰的描述，明确 NOT NULL 约束需在创建或添加字段时具备，并独立说明后续 ALTER 会覆盖先前设置。

## 修改详情

### docs/spark-ddl.md

**修改目的**：修正并补充 Spark DDL 文档中关于 identifier fields 的说明。

**工作逻辑**：本文件是 Spark DDL 操作手册，提交做了三处修改：

1. 在文件开头的 `CREATE TABLE` 示例中，把 `id bigint COMMENT 'unique id'` 改为 `id bigint NOT NULL COMMENT 'unique id'`。这样示例本身就符合 identifier fields 必须为 NOT NULL 的要求，避免用户照搬示例后在后续 `SET IDENTIFIER FIELDS` 时遇到错误。

2. 在 `ALTER TABLE ... SET IDENTIFIER FIELDS` 小节，把原文 "Iceberg supports setting identifier fields to a spec using `SET IDENTIFIER FIELDS`:" 改为 "Iceberg supports setting [identifier fields](https://iceberg.apache.org/spec/#identifier-field-ids) to a spec using `SET IDENTIFIER FIELDS`:"，给 "identifier fields" 加上指向 Iceberg spec 的超链接；并在其后新增一行 "Spark table can support Flink SQL upsert operation if the table has identifier fields."，明确 identifier fields 的一个关键用途——让 Spark 创建的表能被 Flink 用于 upsert，建立跨引擎互操作的语义桥梁。

3. 把原文 "identifier fields must be `NOT NULL`, The later `ALTER` statement will overwrite the previous setting." 改为两行："Identifier fields must be `NOT NULL` columns when they are created or added." 和 "The later `ALTER` statement will overwrite the previous setting."。修改有三点：(a) 句首字母大写 "Identifier"；(b) 明确 NOT NULL 约束的时机——"when they are created or added"，即字段在创建或被添加为 identifier 时就必须是 NOT NULL，而不是含糊的 "must be NOT NULL"；(c) 把两个语义独立的句子拆开成两行，结构更清晰。

## 小结

这是一个小巧但务实的文档提交，体现了 Iceberg 社区对文档质量的持续打磨。改动虽然只有 5 行新增、3 行删除，但解决了示例与约束不一致的问题、补全了概念链接和用途说明、并修正了表述歧义。值得注意的是它显式建立了 Spark DDL 与 Flink upsert 之间的关联——这反映了 Iceberg 作为多引擎表格式的一个重要定位：表由一个引擎（如 Spark）创建，但可被另一个引擎（如 Flink）消费，identifier fields 是这种跨引擎互操作的关键契约之一。把这一点写进 Spark DDL 文档，有助于用户理解为什么在 Spark 建表时需要考虑其他引擎的需求。
