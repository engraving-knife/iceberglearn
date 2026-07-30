# 提交 3924：Docs: Clarify test method naming guidance (#16866)

## 提交信息

- **序号**：3924 / 4088
- **哈希**：0b30919372df34afb632f037df88c05cdba0b134
- **短哈希**：0b3091937
- **日期**：2026-06-21 11:09:59 -0700
- **作者**：Manu Zhang
- **提交说明**：Docs: Clarify test method naming guidance (#16866)
- **PR/Issue**：#16866

## 总体目的

这次提交是对项目贡献者指南文档 `AGENTS.md` 中关于测试方法命名约定的澄清。Iceberg 项目使用 JUnit 5 + AssertJ 作为测试框架，原本的指南中关于测试方法命名的表述存在歧义，可能被误解为"不要在 `@Test` 方法名中使用 test 前缀"这一绝对规则。

实际上，Iceberg 代码库中存在大量以 `test` 为前缀的既有测试方法（这是 JUnit 4 时代的惯例）。此次澄清的目的是明确：新添加的测试方法应避免使用 `test` 前缀（采用 JUnit 5 的命名风格），但并不要求重命名已有的测试方法。这样既统一了新代码的命名风格，又避免对历史代码进行大规模、无功能价值的重命名改动。

## 如何达成设计目的

通过将原来单行的命名指引拆分为两行，分别说明 JUnit 5 + AssertJ 的使用约定（`@Test`、`assertThat`、`assertThatThrownBy`）和新测试方法避免 `test` 前缀的约定。拆分后语义更清晰，避免将"不使用 test 前缀"误读为 `@Test` 注解本身的属性说明。

## 修改详情

### `AGENTS.md` (+2/-1 lines)

**修改目的**：澄清测试方法命名指引。

**工作逻辑**：
将 `- JUnit 5 + AssertJ: \`@Test\` (no \`test\` prefix), \`assertThat\`, \`assertThatThrownBy\`.` 拆分为两行：
- `- JUnit 5 + AssertJ: \`@Test\`, \`assertThat\`, \`assertThatThrownBy\`.`
- `- Avoid using \`test\` prefixes for newly added tests.`

第一行聚焦于 JUnit 5 + AssertJ 的 API 使用约定，第二行单独明确新增测试避免 `test` 前缀的命名约定。

## 总结

这是一次小而重要的文档澄清，通过拆分原本有歧义的命名指引，明确"新测试避免 test 前缀"是针对新代码的约定，而非对历史代码的强制要求。这有助于减少代码审查中关于命名风格的不必要争论，同时保持新代码的 JUnit 5 风格一致性。
