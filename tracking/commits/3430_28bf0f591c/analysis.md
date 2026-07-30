# 提交 3430：Build: Stop ignoring gradle directory (#15705)

## 提交信息

- **序号**：3430 / 4088
- **哈希**：28bf0f591c9eeb8876ee75f3888b0d26518fa6f5
- **短哈希**：28bf0f591c
- **日期**：2026-03-20 15:40:25 -0700
- **作者**：Kevin Liu
- **提交说明**：Build: Stop ignoring gradle directory (#15705)
- **PR/Issue**：#15705

## 总体目的

从 `.gitignore` 中移除对 gradle 目录的忽略规则。此前 `.gitignore` 中有一条规则忽略了 gradle 目录，但这可能导致 gradle wrapper 相关文件（如 `gradle/wrapper/gradle-wrapper.jar` 和 `gradle/wrapper/gradle-wrapper.properties`）无法被提交到仓库。移除该忽略规则确保 gradle wrapper 文件能被正确追踪。

## 如何达成设计目的

- 从 `.gitignore` 文件中删除忽略 gradle 目录的行

## 修改详情

### `.gitignore` (-1 line)

**修改目的**：移除 gradle 目录的忽略规则。

**工作逻辑**：
- 删除 `.gitignore` 中忽略 gradle 目录的那一行
- 这使得 gradle wrapper 相关文件（如 `gradle/wrapper/` 下的文件）不再被忽略，可以被提交到仓库

## 总结

本提交从 `.gitignore` 中移除了对 gradle 目录的忽略规则，确保 gradle wrapper 相关文件能被正确追踪和提交到仓库。
