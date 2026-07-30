# 提交 3997：Docs: Clarify AGENTS comment guidance (#16998)

## 提交信息

- **序号**：3997 / 4088
- **哈希**：2a0f392051b85a4cd47b81a2c9356ea9b6c1d678
- **短哈希**：2a0f39205
- **日期**：2026-07-08 08:57:08 -0700
- **作者**：Kevin Liu
- **提交说明**：Docs: Clarify AGENTS comment guidance (#16998)
- **PR/Issue**：#16998

## 总体目的

本次提交对项目根目录下的 `AGENTS.md`（面向 AI 代理和贡献者的代码风格指南）中关于注释和 Javadoc 的指导规则进行澄清和完善。原指导相对简略且存在歧义，本次扩展为更具体、更可操作的规则，明确"何时写注释"、"写什么内容"，以及"不要描述历史变更"等约束，减少注释噪音和文档 churn。

## 如何达成设计目的

通过在 `AGENTS.md` 的代码风格部分扩充原有几条 bullet，把模糊的"不要 restate code"细化为三个明确维度：
1. 注释应解释非显而易见的意图或约束，不重复代码已表达的内容。
2. Javadoc 区分公共 API 与实现：公共 API 保持简洁、描述调用方所需契约；不要泄漏实现细节。
3. 注释和 Javadoc 描述当前行为或契约，不要描述"过去如何变化"（避免历史性注释造成 churn）。

## 修改详情

### `AGENTS.md` (+3/-2 lines)

**修改目的**：澄清注释/Javadoc 指导规则。

**工作逻辑**：
将原两条规则扩展为三条：
- 原文：`Magic numbers should be named constants. No personal pronouns in comments.`
  改为：`Magic numbers should be named constants. No personal pronouns in comments. Comments should explain non-obvious intent or constraints; don't restate what the code already says.`
- 原文：`Javadoc describes the function or purpose of a class or method, not the implementation. Only describe what callers need to use the component, as though it were defined by an interface. Don't leak implementation details — over-documenting creates churn when the implementation changes.`
  改为更精炼且区分公共 API 的版本：`Javadoc describes the function or purpose of a class or method, not the implementation. For public APIs, keep it brief and describe only what callers need to use the component, as though it were defined by an interface. Don't leak implementation details.`
- 新增一条：`Comments and Javadocs should describe the current behavior or contract, not how it changed over time.`

## 总结

这是一次纯文档维护提交，目的是让 AI 代理和人类贡献者对注释规范有更清晰一致的认知，避免过度文档化和历史性注释带来的维护负担。改动很小但对代码评审标准有实际指导意义。
