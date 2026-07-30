# 提交 2355：Hardcode BSD sed if running on MacOS (#13501)

## 提交信息

- **序号**：2355 / 4088
- **哈希**：44c02ad6bf5f1704c15bde6b0206f4bf2ee618bd
- **短哈希**：44c02ad6b
- **日期**：2025-07-15 12:11:39 +0200
- **作者**：Robin Moffatt
- **提交说明**：Hardcode BSD sed if running on MacOS (#13501)
- **PR/Issue**：#13501

## 总体目的

这个提交修复了在 macOS 上执行版本更新脚本时 `sed` 命令可能调用错误实现的问题。Iceberg 的文档站点维护脚本 `site/dev/common.sh` 中的 `update_version` 函数使用 `sed` 来更新 `mkdocs.yml` 文件中的版本号。

macOS 自带的 `sed` 是 BSD 实现，与 Linux 的 GNU `sed` 在 `-i` 参数行为上有差异（BSD `sed` 需要 `-i ''` 显式指定空备份后缀）。原脚本通过判断 `uname` 为 Darwin 来区分 macOS 并使用 BSD 风格的 `sed -i ''` 调用。然而，如果用户通过 Homebrew 安装了 GNU sed（`gsed`）并将其加入 PATH，或者通过其他方式覆盖了默认 `sed`，那么 `sed` 命令可能指向 GNU 实现而非 BSD 实现，导致 BSD 风格的 `-i ''` 参数在 GNU sed 上行为异常。

本提交通过硬编码 `/usr/bin/sed` 绝对路径，确保 macOS 上始终使用系统自带的 BSD sed，不受 PATH 中其他 sed 实现的影响。

## 如何达成设计目的

将 macOS 分支下的两处 `sed` 调用从 `sed` 改为 `/usr/bin/sed`，使用绝对路径绕过 PATH 解析。

## 修改详情

### `site/dev/common.sh` (+2/-2 lines)

**修改目的**：在 macOS 上硬编码使用 BSD sed 的绝对路径。

**工作逻辑**：将 `update_version` 函数中 Darwin 分支下的两行 `sed -i '' -E "..."` 命令改为 `/usr/bin/sed -i '' -E "..."`。`/usr/bin/sed` 是 macOS 系统自带 BSD sed 的标准路径，使用绝对路径可避免 Homebrew 的 `gsed` 或其他 PATH 中的 sed 实现干扰。Linux 分支的 `sed` 调用保持不变。

## 总结

该提交通过将 macOS 上的 `sed` 调用硬编码为 `/usr/bin/sed` 绝对路径，修复了因 PATH 中存在非 BSD sed 实现而导致版本更新脚本异常的问题。这是一个针对 macOS 开发环境兼容性的小修复。
