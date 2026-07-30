# 提交 0941：Infra: Improve Bug report template (#10708)

## 提交信息

- **序号**：0941 / 4088
- **哈希**：ff7e833c64806915af47499cbecf926ecdc875ee
- **短哈希**：ff7e833c6
- **日期**：2024-07-17（Wed Jul 17 08:29:38 2024 +0200）
- **作者**：Eduard Tudenhoefner \<etudenhoefner@gmail.com\>
- **提交说明**：Infra: Improve Bug report template (#10708)
- **PR/Issue**：#10708

## 总体目的

本提交改进 Iceberg 仓库的 GitHub Bug 报告模板（`.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`）。该模板此前包含三个字段：Iceberg 版本（下拉选择）、查询引擎（下拉选择）、Bug 描述（文本域）。本次在模板末尾新增一个 "Willingness to contribute"（贡献意愿）的复选框区块，让 Bug 报告者在提交问题时表明是否愿意为修复该 Bug 贡献代码。

在开源项目治理中，了解报告者的贡献意愿有助于社区维护者进行 Bug 分流（triage）：

1. **优先级评估**：若报告者愿意独立贡献修复，维护者可以更快地把该 issue 推进到 PR 阶段，缩短修复周期。
2. **资源分配**：对于愿意在有指导下贡献的报告者，维护者可以安排导师资源，降低贡献门槛，培养新贡献者。
3. **社区参与度统计**：复选框选项便于后续统计社区贡献意愿，评估项目的健康度与新贡献者转化率。

Apache Iceberg 社区明确鼓励 Bug 修复贡献（description 中写道 "The Apache Iceberg community encourages bug fix contributions"），因此在模板中显式询问贡献意愿与社区文化一致。

## 如何达成设计目的

实现方式是在 YAML 模板的 `body` 列表末尾追加一个 `type: checkboxes` 字段，包含一个 `label: Willingness to contribute` 区块，下设三个互斥语义的复选选项，覆盖从"能独立修复"到"需要指导"再到"暂不贡献"的完整光谱。这样报告者在提交 Bug 时就会被引导表态，无需维护者事后逐一询问。

该字段未设置 `validations: required: true`，因此是可选的，不会阻止报告者提交 issue。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`

**修改目的**：在 Bug 报告模板末尾新增"贡献意愿"复选框区块，收集报告者是否愿意贡献修复。

**工作逻辑**：在 `body` 列表末尾追加一个 `checkboxes` 类型字段：

```yaml
  - type: checkboxes
    attributes:
      label: Willingness to contribute
      description: The Apache Iceberg community encourages bug fix contributions. Would you or another member of your organization be willing to contribute a fix for this bug to the Apache Iceberg codebase?
      options:
        - label: I can contribute a fix for this bug independently
        - label: I would be willing to contribute a fix for this bug with guidance from the Iceberg community
        - label: I cannot contribute a fix for this bug at this time
```

- **`type: checkboxes`**：GitHub Issue 表单支持的复选框字段类型，渲染为多个可勾选的选项。
- **`label: Willingness to contribute`**：字段标题，显示为"贡献意愿"。
- **`description`**：说明文字，告知报告者社区鼓励 Bug 修复贡献，并询问其本人或所在组织是否愿意为该 Bug 贡献修复。
- **`options`**：三个复选项，分别对应三种贡献意愿程度：
  1. `I can contribute a fix for this bug independently`（能独立贡献修复）—— 报告者有能力和意愿自行提交 PR。
  2. `I would be willing to contribute a fix for this bug with guidance from the Iceberg community`（在社区指导下愿意贡献修复）—— 报告者愿意贡献但需要指导，维护者可安排导师。
  3. `I cannot contribute a fix for this bug at this time`（暂无法贡献修复）—— 报告者仅报告问题，不参与修复。

该字段未设 `validations: required`，故为可选；新增内容共 8 行，追加在原有 `Please describe the bug 🐞` 文本域字段之后。

## 小结

- **成效**：在 Bug 报告模板中新增"贡献意愿"复选框区块，使报告者在提交 Bug 时能表明是否愿意贡献修复（独立修复 / 需要指导 / 暂不贡献），帮助维护者进行 Bug 分流、资源分配与社区参与度评估，契合 Iceberg 鼓励贡献的社区文化。
- **影响范围**：仅 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 一个文件，新增 8 行；不影响任何代码或构建，仅影响 GitHub issue 提交表单的展示与字段收集。
- **回迁到 1.4.x 的注意事项**：这是仓库基础设施（issue 模板）改进，回迁到 1.4.x 完全无风险，且对所有分支通用。若 1.4.x 分支共享相同的 bug 报告模板则建议回迁以统一社区贡献体验。cherry-pick 不涉及任何代码逻辑冲突。
