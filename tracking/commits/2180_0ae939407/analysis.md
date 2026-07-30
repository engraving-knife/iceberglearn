# 提交 2180：Spec: Mark version 3 as completed (#13175)

## 提交信息

- **序号**：2180 / 4088
- **哈希**：0ae93940771bed2191d94c77ae1d24597edcd46f
- **短哈希**：0ae939407
- **日期**：2025-05-29 20:52:37 +0530
- **作者**：Ajantha Bhat
- **提交说明**：Spec: Mark version 3 as completed (#13175)
- **PR/Issue**：#13175

## 总体目的

此提交将 Iceberg 规范版本 3（Version 3）标记为已完成并被社区采纳。在此之前，规范文档中说明版本 1 和 2 是完整的，而版本 3 处于"积极开发中，尚未正式采纳"的状态。随着版本 3 的各项功能（如新数据类型、删除向量、表加密密钥等）已经实现并得到社区验证，此提交正式将版本 3 的状态更新为"已完成并被社区采纳"，同时将"积极开发中"的标签转移到版本 4。这是 Iceberg 规范演进的一个重要里程碑。

## 如何达成设计目的

- 修改 `format/spec.md` 中的版本状态说明
- 将版本 3 从"under active development"改为"complete and adopted by the community"
- 将"under active development"标签转移到版本 4
- 添加指向附录 E（版本 3 变更列表）的链接

## 修改详情

### `format/spec.md` (修改, +3/-2 lines)

**修改目的**：更新版本状态说明。

**工作逻辑**：
- 将 "Versions 1 and 2 of the Iceberg spec are complete and adopted by the community." 改为 "Versions 1, 2 and 3 of the Iceberg spec are complete and adopted by the community."
- 将 "Version 3 is under active development and has not been formally adopted." 改为 "Version 4 is under active development and has not been formally adopted."
- 在版本 3 功能描述末尾添加 "The full set of changes are listed in Appendix E" 的链接

## 总结

此提交是一个重要的规范里程碑更新，将 Iceberg 规范版本 3 正式标记为已完成并被社区采纳，同时将版本 4 标记为正在积极开发中。这反映了 Iceberg 社区对版本 3 功能（新数据类型、删除向量、表加密密钥等）的成熟度和采纳状态的认可。
