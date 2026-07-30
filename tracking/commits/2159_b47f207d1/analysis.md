# 提交 2159：Docs: Update spec version for write modes (#13138)

## 提交信息

- **序号**：2159 / 4088
- **哈希**：b47f207d1fd14dedb9378c0642153f5b03f98b37
- **短哈希**：b47f207d1
- **日期**：2025-05-23 18:24:18 +0530
- **作者**：Ajantha Bhat
- **提交说明**：Docs: Update spec version for write modes (#13138)
- **PR/Issue**：#13138

## 总体目的

Iceberg 的配置文档中，`write.delete.mode`、`write.update.mode` 和 `write.merge.mode` 这三个表属性此前标注为"v2 only"，即 merge-on-read 模式仅在表格式版本 2 中可用。但随着 Iceberg 规范的发展，merge-on-read 写模式不再局限于 v2，未来版本（如 v3）也将支持。文档中写"v2 only"会产生误导，让用户以为只有 v2 表才能使用 merge-on-read 模式。该提交将文档中的"v2 only"更新为"v2 and above"（v2 及以上），使文档描述更加准确，避免用户误解。

## 如何达成设计目的

- 修改 `docs/docs/configuration.md` 文件，将 `write.delete.mode`、`write.update.mode`、`write.merge.mode` 三个属性描述中的"(v2 only)"统一替换为"(v2 and above)"。

## 修改详情

### `docs/docs/configuration.md` (修改, +3/-3 lines)

**修改目的**：修正写模式属性的版本适用范围描述。

**工作逻辑**：将三处"(v2 only)"修改为"(v2 and above)"，涉及 `write.delete.mode`、`write.update.mode`、`write.merge.mode` 三个属性的描述列。这使文档与实际功能保持一致——merge-on-read 模式适用于 v2 及以上版本的表。

## 总结

这是一个简单的文档修正提交，将写模式的版本适用范围从"v2 only"更正为"v2 and above"，消除文档中可能引起用户误解的表述。
