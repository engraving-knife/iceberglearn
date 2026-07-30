# 提交 3167：Build: Fix typo in variable name spaceSeparatedPlanId (#15158)

## 提交信息

- **序号**：3167 / 4088
- **哈希**：1bf44d9fed8b99dd73115ec31659266248cbb363
- **短哈希**：1bf44d9fe
- **日期**：2026-01-27
- **作者**：Resort_Annex
- **提交说明**：Build: Fix typo in variable name spaceSeparatedPlanId (#15158)
- **PR/Issue**：#15158

## 总体目的

这是一次纯代码质量/拼写修正提交。在 `core` 模块的 REST 测试类 `TestResourcePaths.java` 中，存在一个局部变量名 `spaceSeperatedPlanId`，其中 "Seperated" 是 "Separated" 的常见拼写错误（应为 `spaceSeparatedPlanId`）。该变量用于测试当 planId 中包含空格时，`ResourcePaths.plan(...)` 方法能否正确对空格进行 URL 编码（编码后为 `plan+with+spaces`）。

变量名拼写错误虽不影响测试逻辑和断言结果（变量仅在该测试方法内部被引用 4 次，且引用处一并修正），但会降低代码可读性，并可能在后续维护中被复制传播到其他地方。提交者 Resort_Annex 通过 PR #15158 将变量声明及其全部引用统一更正为 `spaceSeparatedPlanId`，使命名与英文单词 "separated" 一致。

这类拼写修正属于社区贡献中常见的低风险改动，有助于保持代码库的整洁与专业度。提交标题以 "Build:" 前缀归类，但实际改动属于测试代码质量范畴。

## 如何达成设计目的

改动集中在一个测试方法内，将变量声明 `String spaceSeperatedPlanId = "plan with spaces";` 以及后续三处引用（两次 `withPrefix.plan(tableId, spaceSeperatedPlanId)` 与 `withoutPrefix.plan(tableId, spaceSeperatedPlanId)`）中的变量名统一替换为 `spaceSeparatedPlanId`。由于是局部变量且引用范围封闭，重命名不会产生任何行为变化，测试断言保持不变。

## 修改详情

### `core/src/test/java/org/apache/iceberg/rest/TestResourcePaths.java` (+4/-4 lines)

**修改目的**：修正局部变量名拼写错误。

**工作逻辑**：
该测试方法验证 REST 资源路径在 planId 含空格时的编码行为。变量 `spaceSeperatedPlanId`（拼写错误）被重命名为 `spaceSeparatedPlanId`（正确拼写），涉及声明处 1 行与引用处 3 行，共 4 行改动。测试逻辑保持不变：仍使用字符串 `"plan with spaces"` 作为输入，断言编码结果为 `plan+with+spaces`，并分别验证带前缀（`v1/ws/catalog/...`）和不带前缀（`v1/...`）两种路径构造方式。

## 总结

本次提交修正了 REST 路径编码测试中变量名 "Seperated" 的拼写错误，统一为 "Separated"，属于低风险的代码质量改进，提升了测试代码的可读性与命名规范性，对功能行为无任何影响。
