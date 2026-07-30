# 提交 1039：Docs: Fix catalog name for S3 MRAP example (#10897)

## 提交信息

- **序号**：1039 / 4088
- **哈希**：71b64399dd5c74a63f97022cdb42d0cdcf615862
- **短哈希**：71b64399d
- **日期**：2024-08-07 13:08:23 +0200
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Docs: Fix catalog name for S3 MRAP example (#10897)
- **PR/Issue**：#10897

## 总体目的

Iceberg 的 AWS 文档 `docs/docs/aws.md` 中有一个 S3 Multi-Region Access Point（MRAP）的 Spark 配置示例。该示例展示了如何为 `my_catalog` 这个 Spark catalog 配置 S3 访问点，使指定 bucket 的 S3 操作走 MRAP。

示例的前几行配置都正确使用了 catalog 名 `my_catalog`（如 `spark.sql.catalog.my_catalog.type=glue`、`spark.sql.catalog.my_catalog.io-impl=...`），但最后两行关于 `s3.access-points` 的配置却错误地写成了 `test` 作为 catalog 名：

```
--conf spark.sql.catalog.test.s3.access-points.my-bucket1=arn:aws:s3::...
--conf spark.sql.catalog.test.s3.access-points.my-bucket2=arn:aws:s3::...
```

这里的 `test` 与上下文中的 `my_catalog` 不一致。读者如果照抄示例，会因为 catalog 名不匹配而导致 MRAP 配置不生效（配置项挂在了不存在的 `test` catalog 下，而实际使用的 `my_catalog` catalog 没有该配置）。

本提交的目的是将这两行配置中的 catalog 名从 `test` 修正为 `my_catalog`，使整个示例的配置键前缀统一一致，读者可以直接复制使用。

## 如何达成设计目的

直接将 `docs/docs/aws.md` 中 MRAP 示例代码块里两行配置的 `spark.sql.catalog.test.` 前缀替换为 `spark.sql.catalog.my_catalog.`，与示例中其他配置行保持一致。这是纯文档文本修正，不涉及任何代码逻辑。

## 修改详情

### `docs/docs/aws.md`

**修改目的**：修正 S3 MRAP 示例中 catalog 名不一致的问题。

**工作逻辑**：将两行配置的 catalog 名从 `test` 改为 `my_catalog`：

```diff
-    --conf spark.sql.catalog.test.s3.access-points.my-bucket1=arn:aws:s3::123456789012:accesspoint:mfzwi23gnjvgw.mrap \
-    --conf spark.sql.catalog.test.s3.access-points.my-bucket2=arn:aws:s3::123456789012:accesspoint:mfzwi23gnjvgw.mrap
+    --conf spark.sql.catalog.my_catalog.s3.access-points.my-bucket1=arn:aws:s3::123456789012:accesspoint:mfzwi23gnjvgw.mrap \
+    --conf spark.sql.catalog.my_catalog.s3.access-points.my-bucket2=arn:aws:s3::123456789012:accesspoint:mfzwi23gnjvgw.mrap
```

修改后，示例中所有配置项都统一使用 `spark.sql.catalog.my_catalog.*` 前缀，与上方 `spark-sql --conf spark.sql.catalog.my_catalog=...` 等行保持一致。共 2 行修改。

## 小结

- **成效**：修复了 S3 MRAP 示例中 catalog 名 `test` 与 `my_catalog` 不一致的问题，使示例可直接复制使用，避免读者因配置键前缀不匹配而遇到 MRAP 不生效的问题。
- **影响范围**：仅 `docs/docs/aws.md` 一个文件、2 行文本修改，无代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：可以回迁，风险极低。纯文档示例修正，不涉及代码。1.4.x 分支的 `aws.md` 若存在相同示例（大概率存在），建议直接回迁以保持文档正确性。若 1.4.x 的示例措辞已不同，则按需手动修正 catalog 名。
