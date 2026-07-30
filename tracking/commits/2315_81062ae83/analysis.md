# 提交 2315：Docs: update daft docs (#13463)

## 提交信息

- **序号**：2315 / 4088
- **哈希**：81062ae83aff9a881b3223a34818f019d0d41406
- **短哈希**：81062ae83
- **日期**：2025-07-04 08:10:22 +0200
- **作者**：ccmao1130
- **提交说明**：Docs: update daft docs (#13463)
- **PR/Issue**：#13463

## 总体目的

这个提交更新了 Iceberg 文档中关于 Daft 集成的页面。Daft 是一个用 Python 和 Rust 编写的分布式查询引擎，与 Iceberg 有集成支持。

此次更新的主要原因是 Daft 项目进行了品牌和域名迁移：网站从 `www.getdaft.io` 迁移到 `www.daft.ai`（及文档站点 `docs.daft.ai`），pip 安装包名从 `getdaft` 改为 `daft`。文档中的所有相关链接和安装说明都需要同步更新，以确保用户能访问到正确的资源。

## 如何达成设计目的

通过批量替换文档中所有 `getdaft.io` 域名为 `daft.ai`，以及将 pip 安装命令中的 `getdaft` 改为 `daft`。同时修正了一个表格中的 Unicode 字符差异（可能是编码一致性修正）。

## 修改详情

### `docs/docs/daft.md` (+22/-22 lines)

**修改目的**：更新 Daft 相关文档的链接和安装说明。

**工作逻辑**：
1. 网站链接从 `https://www.getdaft.io/` 改为 `https://www.daft.ai`
2. 文档 API 链接从 `https://www.getdaft.io/projects/docs/en/latest/...` 改为 `https://docs.daft.ai/en/latest/...`
3. pip 安装命令从 `pip install getdaft pyiceberg` 改为 `pip install daft pyiceberg`
4. 类型映射表中所有 DataType 链接同步更新

## 总结

这是一个纯文档维护提交，更新 Daft 集成文档以反映其项目域名和包名的变更。变更不涉及任何代码逻辑，共修改 22 处链接和安装说明。
