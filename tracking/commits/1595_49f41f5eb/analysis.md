# 提交 1595：Doc: Add missing content value to manifests table (#11989)

## 提交信息

- **序号**：1595 / 4088
- **哈希**：49f41f5ebbd995657ecf77e61f265081ff326ceb
- **短哈希**：49f41f5eb
- **日期**：2025-01-17（Fri Jan 17 16:13:53 2025 +0900）
- **作者**：Yuya Ebihara <ebyhry@gmail.com>
- **提交说明**：Doc: Add missing content value to manifests table (#11989)
- **PR/Issue**：#11989

## 总体目的

Iceberg 文档中 `spark-queries.md` 介绍了通过 `prod.db.table.manifests` 元数据表查询表的 manifest 文件清单。该表的第一列 `content` 用于区分 manifest 是数据文件 manifest（值为 0）还是删除文件 manifest（值为 1）。在展示查询结果示例的 Markdown 表格里，表头声明了 `content` 列，但数据行第一个单元格却把这一列的值漏掉了，直接从 `path`（s3 路径）开始，造成表头与数据列数错位、读者无法对齐示例。

本提交在该示例数据行的最前面补上缺失的 `0`（表示这是一条数据 manifest），使示例表格与表头一致，避免用户在阅读文档时对 `content` 列含义产生误解或误以为该列实际无值。这是一个纯文档修复，没有代码变更。

## 如何达成设计目的

直接编辑 `docs/docs/spark-queries.md` 中 `SELECT * FROM prod.db.table.manifests;` 查询结果示例表格的数据行，在第一个单元格 `s3://.../table/metadata/45b5290b-...-m0.avro` 之前插入一个值为 `0` 的单元格，并用 `|` 分隔。表格共 12 列：`content | path | length | partition_spec_id | added_snapshot_id | added_data_files_count | existing_data_files_count | deleted_data_files_count | added_delete_files_count | existing_delete_files_count | deleted_delete_files_count | partition_summaries`，修复后数据行也恰好提供 12 个值，与表头对齐。

### 修改详情

#### `docs/docs/spark-queries.md`

**修改目的**：补齐 manifests 元数据表查询示例中缺失的 `content` 列值。

**工作逻辑**：在示例数据行原首单元格前追加 `0 |`，使数据行变为：

```
| 0 | s3://.../table/metadata/45b5290b-ee61-4788-b324-b1e2735c0e10-m0.avro | 4479 | 0 | 6668963634911763636 | 8 | 0 | 0 | 0 | 0 | 0 | [[false,null,2019-05-13,2019-05-15]] |
```

值 `0` 对应 Iceberg 中 `ManifestContent.DATA`（数据 manifest），与该示例 manifest 文件名后缀 `-m0`（数据 manifest）一致；若是删除文件 manifest 则 `content` 应为 `1`。此次仅修复文档展示，没有变更任何代码或元数据语义。

## 小结

- **成效**：修复了 `spark-queries.md` 中 manifests 表示例与表头列数不一致的小错误，使读者能正确对照 `content` 列与 manifest 类型。
- **影响范围**：仅一处 Markdown 表格数据行，新增一个 `0 |` 单元格，无代码 / 构建 / 测试变更。
- **回迁到 1.4.x 的注意事项**：纯文档修复，与版本无关。1.4.x 分支若有相同文档可选择性回迁以保持文档准确性，但不影响发布产物。
