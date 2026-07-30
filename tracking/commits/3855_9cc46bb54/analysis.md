# 提交 3855：Docs: Add Javadoc guidance to AGENTS.md (#16764)

## 提交信息

- **序号**：3855 / 4088
- **哈希**：9cc46bb5411d4530a2e10912e7ea2d9c0411e09a
- **短哈希**：9cc46bb54
- **日期**：2026-06-10 18:17:56 -0600
- **作者**：Steven Zhen Wu
- **提交说明**：Docs: Add Javadoc guidance to AGENTS.md (#16764)
- **PR/Issue**：#16764

## 总体目的

本提交在 `AGENTS.md` 文件中新增了关于 Javadoc 编写规范的指导。`AGENTS.md` 是 Iceberg 项目为 AI 助手（如 Claude、Codex 等）和贡献者提供的代码风格和行为指南文档。

该指导源自 PR #16689 中 Ryan Blue（rdblue）的指示：Javadoc 应当描述类或方法的功能/目的（如同它由接口定义一样），而非重述或泄露实现细节。过度文档化实现细节会在实现变更时产生不必要的文档维护负担（churn）。

这一指导对于 AI 助手生成代码尤为重要——AI 助手倾向于过度文档化实现细节，该规则帮助它们生成更稳定、更符合项目规范的 Javadoc。

## 如何达成设计目的

在 `AGENTS.md` 的代码风格列表中新增一行 Javadoc 指导规则，与现有的 `this.` 使用、`Preconditions` 调用顺序、`final` 禁用等规则并列。

## 修改详情

### `AGENTS.md` (+1/-0 lines)

**修改目的**：新增 Javadoc 编写规范。

**工作逻辑**：
在代码风格规则列表中新增：
```markdown
- Javadoc describes the function or purpose of a class or method, not the implementation. Only describe what callers need to use the component, as though it were defined by an interface. Don't leak implementation details — over-documenting creates churn when the implementation changes.
```

核心要点：
1. Javadoc 描述功能/目的，而非实现
2. 只描述调用者需要知道的信息（如同接口定义）
3. 不泄露实现细节——过度文档化在实现变更时产生维护负担

## 总结

这是一次文档更新，在 `AGENTS.md` 中新增了 Javadoc 编写规范。该规范要求 Javadoc 关注"做什么"而非"怎么做"，避免泄露实现细节，减少文档维护负担。这对 AI 助手和人类贡献者都有指导意义，有助于提升项目文档质量和稳定性。
