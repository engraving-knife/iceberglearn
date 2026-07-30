# 提交 2501：Docs: Update testing guidelines to reflect full JUnit 5 migration and AssertJ usage. (#13822)

## 提交信息

- **序号**：2501 / 4088
- **哈希**：d005d7bf9c935ed8f02b1400d4a2e41945653532
- **短哈希**：d005d7bf9
- **日期**：2025-08-15 12:59:39 +0200
- **作者**：slfan1989
- **提交说明**：Docs: Update testing guidelines to reflect full JUnit 5 migration and AssertJ usage. (#13822)
- **PR/Issue**：#13822

## 总体目的

本提交更新了贡献指南文档中的测试规范说明，反映了 Iceberg 项目已完成的从 JUnit 4 到 JUnit 5 的全面迁移，以及 AssertJ 断言库的统一使用。

原先的文档说明 Iceberg 使用 JUnit 4 和 JUnit 5 的混合测试，并建议新测试类尽量使用 JUnit 5。这一描述已经过时，因为项目已经完成了全部测试的 JUnit 5 迁移。本提交将文档更新为反映当前状态：所有测试已使用 JUnit 5，新测试应使用 JUnit 5 并遵循 AssertJ 风格的断言写法。

这与之前提交 2493（将 Flink 测试中的 `assertThrows` 替换为 `assertThatThrownBy` 并新增 checkstyle 规则）是一脉相承的，体现了项目统一测试框架和断言风格的系统性努力。

## 如何达成设计目的

将 `contribute.md` 中"JUnit4 / JUnit5"章节重命名为"JUnit 5 / AssertJ"，并更新描述文字以反映全面迁移完成后的状态和要求。

## 修改详情

### `site/docs/contribute.md` (+3/-3 lines)

**修改目的**：更新测试规范文档。

**工作逻辑**：
- 章节标题从 "JUnit4 / JUnit5" 改为 "JUnit 5 / AssertJ"。
- 删除原描述"Iceberg currently uses a mix of JUnit4 and JUnit5... new test classes should be written purely in JUnit5 where possible."
- 新增描述"Iceberg has now fully migrated to JUnit 5 (org.junit.jupiter.api imports) for all tests. Any new test classes should be written using JUnit 5, and assertions should follow the AssertJ style to ensure consistency and readability."

## 总结

本提交同步了文档与实际代码状态，确保贡献指南准确反映项目已完成 JUnit 5 全面迁移和 AssertJ 统一使用的现状。这对于新贡献者正确编写测试具有重要指导意义，也与代码层面的 checkstyle 规则和测试改写形成闭环。
