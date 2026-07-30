# 提交 2639：docs: use * for path expansion instead of hardcoding version (#13989)

## 提交信息

- **序号**：2639 / 4088
- **哈希**：754679ddccdf81a97dc65d40f1a2a6fb9f6ee9b0
- **短哈希**：754679ddc
- **日期**：2025-09-15 15:31:08 -0700
- **作者**：Kevin Liu
- **提交说明**：docs: use * for path expansion instead of hardcoding version (#13989)
- **PR/Issue**：#13989

## 总体目的

`how-to-release.md` 文档中记录了验证发布产物的步骤，包括 GPG 验证、校验和验证、解压源码包等。这些命令中使用了 `apache-iceberg-{{ icebergVersion }}.tar.gz` 这样的模板变量来引用版本化的文件名。然而文档示例命令中直接硬编码了版本号模板变量，用户复制粘贴后仍需手动替换，且 `cd` 进入解压目录时无法用通配符匹配。

本提交将文档中的命令改为使用 shell 通配符 `*` 进行路径扩展（如 `apache-iceberg-*.tar.gz`、`apache-iceberg-*/`），使用户无需关心具体版本号即可执行验证和解压命令，提升文档的易用性。

## 如何达成设计目的

将 `how-to-release.md` 中四处硬编码 `{{ icebergVersion }}` 的命令替换为通配符 `*`：
- `gpg --verify apache-iceberg-*.tar.gz.asc`
- `shasum -a 512 --check apache-iceberg-*.tar.gz.sha512`
- `tar xzf apache-iceberg-*.tar.gz`
- `cd apache-iceberg-*/`

## 修改详情

### `site/docs/how-to-release.md` (+4/-4 lines)

**修改目的**：将验证命令中的版本号模板替换为通配符。

**工作逻辑**：四处修改，将 `{{ icebergVersion }}` 替换为 `*`，分别用于 GPG 验证、校验和验证、解压和进入目录。使用通配符后用户可直接复制命令执行，无需手动替换版本号变量。

## 总结

这是一次纯文档易用性改进，将发布验证步骤中的硬编码版本号模板替换为 shell 通配符，方便用户直接复制执行命令。不影响代码逻辑。
