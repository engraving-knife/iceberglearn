# 提交 3769：Add `.claude/` to .gitignore (#16533)

## 提交信息

- **序号**：3769 / 4088
- **哈希**：ad9af85139d93487696f59faba743a4a38a558f2
- **短哈希**：ad9af8513
- **日期**：2026-05-22 10:46:18 -0700
- **作者**：Xiening Dai
- **提交说明**：Add `.claude/` to .gitignore (#16533)
- **PR/Issue**：#16533

## 总体目的

这个提交将 `.claude/` 目录添加到 `.gitignore` 文件中。Claude Code 是 Anthropic 的 AI 编码助手工具，它会在项目根目录下创建 `.claude/` 目录来存储本地配置和缓存。将此目录加入 `.gitignore` 可以防止开发者在使用 Claude Code 时意外将其本地配置提交到仓库中。

## 如何达成设计目的

在 `.gitignore` 文件末尾添加 `.claude/` 目录的忽略规则。

## 修改详情

### `.gitignore` (+3/-0 lines)

**修改目的**：忽略 Claude Code 的本地配置目录。

**工作逻辑**：
在 `.gitignore` 末尾新增：
```
# claude code
.claude/
```

## 总结

这是一个简单的仓库维护提交，将 Claude Code AI 助手的本地配置目录 `.claude/` 添加到 `.gitignore`，防止意外提交。这反映了 AI 辅助开发工具在项目开发中的使用日益普遍。
