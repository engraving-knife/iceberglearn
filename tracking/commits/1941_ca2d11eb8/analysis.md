# 提交 1941：Docs: Update link for User-Defined Tag Restrictions in AWS documentation (#12698)

## 提交信息

- **序号**：1941 / 4088
- **哈希**：ca2d11eb86d8e12f395ab24bfa4f45f50f7d6ec0
- **短哈希**：ca2d11eb8
- **日期**：2025-04-01 09:04:07 +0200
- **作者**：Xu Bai
- **提交说明**：Docs: Update link for User-Defined Tag Restrictions in AWS documentation (#12698)
- **PR/Issue**：#12698

## 总体目的

这是一个文档链接修复提交。在 Iceberg 的 AWS 文档 `docs/docs/aws.md` 中，关于 S3 用户自定义标签（User-Defined Tag）限制的说明引用了一个 AWS 官方文档链接。原链接指向 `https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/allocation-tag-restrictions.html`（AWS 账单文档中的标签分配限制页面），但该链接已失效或被 AWS 迁移——AWS 将 S3 标签管理的内容整合到了 S3 用户指南中。

本提交将链接更新为 `https://docs.aws.amazon.com/AmazonS3/latest/userguide/tagging-managing.html`（Amazon S3 用户指南中的标签管理页面），使文档引用指向当前有效的 AWS 官方页面，避免用户点击后遇到 404 或内容不匹配。

## 如何达成设计目的

直接在 markdown 中替换超链接 URL，链接文本"User-Defined Tag Restrictions"保持不变。

## 修改详情

### `docs/docs/aws.md` (修改, +1/-1 lines)

**修改目的**：修复失效的 AWS 标签限制文档链接。

**工作逻辑**：将"For more details on tag restrictions, please refer [User-Defined Tag Restrictions](...)"中的 URL 从 `https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/allocation-tag-restrictions.html` 改为 `https://docs.aws.amazon.com/AmazonS3/latest/userguide/tagging-managing.html`。新链接指向 Amazon S3 用户指南的标签管理页面，内容更贴合 Iceberg S3 写标签功能的上下文。

## 总结

本次提交为文档链接修复，将 AWS 文档中失效的"User-Defined Tag Restrictions"链接从 AWS 账单文档更新为 Amazon S3 用户指南的标签管理页面，使引用指向当前有效的官方文档。
