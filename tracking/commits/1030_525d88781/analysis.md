# 提交 1030：Spec: Clarify identity partition edge cases (#10835)

## 提交信息

- **序号**：1030 / 4088
- **哈希**：525d887811b2fd2140779e125243cb70742e169c
- **短哈希**：525d88781
- **日期**：2024-08-05 18:06:36 -0700
- **作者**：emkornfield <emkornfield@gmail.com>
- **提交说明**：Spec: Clarify identity partition edge cases (#10835)
- **PR/Issue**：#10835

## 总体目的

Iceberg 的表格式规范 `format/spec.md` 描述了表的元数据、数据文件、分区、schema 等各方面的约定。在"列投影（Column Projection）"一节，原规范仅简单说明：数据文件中的列通过 field id 选取；若某 field id 在数据文件中缺失，则该列每行返回 `null`。这一描述过于简略，没有覆盖几种重要的边界情况，尤其是与身份分区转换（Identity Partition Transform）和 schema 演进相关的场景。

具体存在两类需要澄清的问题：

1. **数据文件是否应写出与 manifest 元数据冗余的列**：当某列被用作身份分区列时，分区值已记录在 manifest 的 `partition` 结构里，那么数据文件本身是否还需要再写一遍这个列的值？原规范未明确。本提交新增"Writing data files"小节，明确规定所有列都必须写入数据文件（即使与 manifest 中的分区元数据冗余），作为元数据层损坏或 bug 时的备份。

2. **数据文件中缺失某 field id 时如何取值**：原规范只说"返回 null"，但实际存在多种来源可能提供该值，需要规定优先级顺序：身份分区列可从 manifest 的分区元数据取值（这支持 Hive 表的"仅元数据迁移"场景）；可通过 `schema.name-mapping.default` 将 field id 映射到无 field id 的旧列名并取该列值；可使用列的 `initial-default` 默认值；以上都不适用时才返回 `null`。本提交把这些规则补充进规范，消除实现分歧。

这两点澄清对实现一致性至关重要：不同引擎（Spark、Flink、Trino 等）读取 Iceberg 表时若按不同规则处理缺失列，会导致同一表在不同引擎下查询结果不一致。

## 如何达成设计目的

在 `format/spec.md` 中做两处增补：
1. 在"Readable properties"小节附近新增"##### Writing data files"子小节，一句话明确所有列（含身份分区列）都必须写入数据文件，理由是作为元数据损坏/bug 时的备份；
2. 重写"#### Column Projection"小节中关于"field id 缺失时取值"的描述，把原来的一句话拆成一条总述 + 一个有序规则列表，按优先级依次列出四种取值来源。

## 修改详情

### `format/spec.md`

**修改目的**：澄清身份分区列在数据文件中的写入要求，以及数据文件中缺失 field id 时的取值优先级规则。

**工作逻辑**：分两处改动：

1. 新增"##### Writing data files"小节（紧跟在 v1/v2 metadata 兼容性说明之后）：

```diff
 Readers may be more strict for metadata JSON files because the JSON files are not reused and will always match the table version. Required v2 fields that were not present in v1 or optional in v1 may be handled as required fields. For example, a v2 table that is missing `last-sequence-number` can throw an exception.

+##### Writing data files
+
+All columns must be written to data files even if they introduce redundancy with metadata stored in manifest files (e.g. columns with identity partition transforms). Writing all columns provides a backup in case of corruption or bugs in the metadata layer.
+
 ### Schemas and Data Types
```

明确写入要求：所有列（含身份分区列）必须写入数据文件，冗余是有意为之，作为元数据层的备份。

2. 重写"#### Column Projection"中关于缺失 field id 取值的描述：

```diff
 #### Column Projection

-Columns in Iceberg data files are selected by field id. The table schema's column names and order may change after a data file is written, and projection must be done using field ids. If a field id is missing from a data file, its value for each row should be `null`.
+Columns in Iceberg data files are selected by field id. The table schema's column names and order may change after a data file is written, and projection must be done using field ids.
+
+Values for field ids which are not present in a data file must be resolved according the following rules:
+
+* Return the value from partition metadata if an [Identity Transform](#partition-transforms) exists for the field and the partition value is present in the `partition` struct on `data_file` object in the manifest. This allows for metadata only migrations of Hive tables.
+* Use `schema.name-mapping.default` metadata to map field id to columns without field id as described below and use the column if it is present.
+* Return the default value if it has a defined `initial-default` (See [Default values](#default-values) section for more details).
+* Return `null` in all other cases.

 For example, a file may be written with schema `1: a int, 2: b string, 3: c double` and read using projection schema `3: measurement, 2: name, 4: a`. This must select file columns `c` (renamed to `measurement`), `b` (now called `name`), and a column of `null` values called `a`; in that order.
```

新规则按优先级依次为：
1. 若该字段存在 Identity Transform 且 manifest 的 `partition` 结构中有对应分区值，则返回分区元数据中的值——支持 Hive 表的"仅元数据迁移"（不改数据文件，只补 manifest）；
2. 否则用 `schema.name-mapping.default` 将 field id 映射到无 field id 的旧列名，若数据文件中存在该列则取用；
3. 否则若该字段定义了 `initial-default`，则返回默认值；
4. 以上都不适用，返回 `null`。

保留原有的投影示例不变。

## 小结

- **成效**：澄清了 Iceberg 规范中两处与身份分区相关的边界情况——明确所有列（含分区列）必须写入数据文件作为冗余备份；明确了数据文件中缺失 field id 时的取值优先级（分区元数据 > name-mapping > initial-default > null），消除多引擎实现分歧，并支持 Hive 表的仅元数据迁移。
- **影响范围**：仅 `format/spec.md` 一个文件，新增 12 行（1 个新小节 + 重写投影取值规则），不涉及代码改动。
- **回迁到 1.4.x 的注意事项**：规范文档澄清，回迁风险低。但需注意：取值优先级规则是**行为约定**的明确化，1.4.x 的读取实现若与此规则不一致（例如未实现从分区元数据回填身份分区列、或未支持 name-mapping 取值），回迁规范文本后可能出现"规范说应有、实现却没做"的偏差。回迁规范文本本身无害，但应同时核查 1.4.x 引擎实现是否已符合这些规则，必要时补齐实现。本提交仅改文档不改实现，单独回迁只是让规范更明确，不会改变运行行为。
