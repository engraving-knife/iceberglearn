# 提交 2585：Release: add link to verify release (#13960)

## 提交信息

- **序号**：2585 / 4088
- **哈希**：d8767f1b56a4de379d3d49a5c2f02bab4b0e330f
- **短哈希**：d8767f1b5
- **日期**：2025-08-31 22:32:04 -0700
- **作者**：Kevin Liu
- **提交说明**：Release: add link to verify release (#13960)
- **PR/Issue**：#13960

## 总体目的

此次提交在 Iceberg 源码发布脚本 `dev/source-release.sh` 生成的发布投票邮件模板中，新增指向"如何验证发布"文档的链接。Iceberg 的发布流程中，发布管理者运行 `source-release.sh` 脚本会生成一封发送到 dev 邮件列表的投票邮件，邮件正文包含源码和二进制产物的下载地址，并请社区成员在 72 小时内投票。

此前邮件模板中只说"Please download, verify, and test."（请下载、验证和测试），但没有给出具体的验证步骤指引。对于不熟悉发布验证流程的社区成员，缺乏明确的操作指南可能降低参与验证的积极性或导致验证不充分。

此次修改在"请下载验证测试"之后新增一行，指向 `https://iceberg.apache.org/how-to-release/#how-to-verify-a-release` 文档链接，使投票者能直接跳转到验证发布的具体步骤说明（如如何校验签名、检查 LICENSE/NOTICE、构建测试等），提升发布验证的规范性和参与度。

## 如何达成设计目的

- 在 `dev/source-release.sh` 脚本中生成投票邮件正文的位置，于"Please download, verify, and test."之后、"Please vote in the next 72 hours."之前，插入两行文本：说明验证发布的指引可在指定 URL 找到，并给出链接。

## 修改详情

### `dev/source-release.sh` (+3)

**修改目的**：在发布投票邮件模板中新增验证发布文档链接。

**工作逻辑**：在邮件模板的下载验证段落之后新增：
```
Instructions for verifying a release can be found here:
* https://iceberg.apache.org/how-to-release/#how-to-verify-a-release
```
使投票者可点击链接查看完整的发布验证步骤。

## 总结

一次发布流程文档改进提交，在 `source-release.sh` 生成的投票邮件模板中新增指向"如何验证发布"文档的链接，方便社区成员参与发布验证时获取具体操作指引，提升发布验证的规范性和参与度。无功能代码变更。
