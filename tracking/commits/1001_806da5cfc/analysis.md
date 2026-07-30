# 提交 1001：Infra: Improve feature request template (#10825)

## 提交信息

- **序号**：1001 / 4088
- **哈希**：806da5cfc7dba7b8fd872cf7fc6a6b36ac8a3876
- **短哈希**：806da5cfc
- **日期**：2024-08-01（Thu Aug 1 09:30:04 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Infra: Improve feature request template (#10825)
- **PR/Issue**：#10825

## 总体目的

Iceberg 仓库在 `.github/ISSUE_TEMPLATE/` 下维护 GitHub Issue 模板，其中 `iceberg_improvement.yml` 用于功能改进/新功能请求。社区希望在日常收到的 feature request 中提前了解提交者是否有意愿亲自参与实现，以便维护者更高效地评估 issue 的落地可能性与排期：愿意贡献的 issue 更容易被推进，而仅提出想法的 issue 则需社区投入更多人力。

本提交在该模板末尾新增一个"愿意贡献"（Willingness to contribute）的复选框区块，让提交者在创建 feature request 时声明三种意愿之一：能独立贡献、需要指导后贡献、暂不贡献。这一信息会随 issue 一起提交，帮助维护者优先处理有贡献者背书的请求，同时鼓励社区参与。

此外，原文件末尾缺少换行符（`No newline at end of file`），本次一并补上，符合文件末尾留空行的常见代码规范。

## 如何达成设计目的

直接编辑 `.github/ISSUE_TEMPLATE/iceberg_improvement.yml`，在原有最后一个字段（引擎选择的多选框）的 `validations` 之后追加一个新的 `type: checkboxes` 区块，包含三个选项标签。同时在文件末尾补一个换行符。这是纯模板配置改动，无代码逻辑。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_improvement.yml`

**修改目的**：在 feature request 模板中增加"愿意贡献"声明区块。

**工作逻辑**：在文件末尾（原有 `Other` 引擎多选框的 `required: false` 之后）追加：

```yaml
  - type: checkboxes
    attributes:
      label: Willingness to contribute
      description: The Apache Iceberg community encourages contributions. Would you or another member of your organization be willing to contribute this improvement/feature to the Apache Iceberg codebase?
      options:
        - label: I can contribute this improvement/feature independently
        - label: I would be willing to contribute this improvement/feature with guidance from the Iceberg community
        - label: I cannot contribute this improvement/feature at this time
```

该区块在 GitHub Issue 创建界面会显示为一组复选框，提交者可勾选其一（或多选）。`description` 鼓励贡献并说明意图。同时原文件末尾的 `No newline at end of file` 被修正为正常换行。

## 小结

- **成效**：feature request 模板现可收集提交者的贡献意愿，便于维护者评估与排期，同时鼓励社区参与贡献；并修复了文件末尾缺少换行符的小问题。
- **影响范围**：仅 `.github/ISSUE_TEMPLATE/iceberg_improvement.yml` 一个文件，新增约 8 行配置，无代码、构建或文档逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是仓库基础设施（Issue 模板）改进，与产品版本功能无关，对 1.4.x 运行时无任何影响。1.4.x 作为维护分支一般不单独调整 Issue 模板（Issue 模板由 main 分支统一维护并作用于整个仓库），**无需回迁**。即使 1.4.x 分支的该文件与 main 不同，也不影响其发布产物。
