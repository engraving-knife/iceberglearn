# 提交 0749：Infra: Add Iceberg 1.5.2 to issue template (#10296)

## 提交信息

- **序号**：0749 / 4088
- **哈希**：e6586e947f8881d010a496a731dbb5524c0e8510
- **短哈希**：e6586e947
- **日期**：2024-05-09 11:42:37 -0600
- **作者**：Amogh Jahagirdar <amogh@tabular.io>
- **提交说明**：Infra: Add Iceberg 1.5.2 to issue template (#10296)
- **PR/Issue**：#10296

## 总体目的

这是 1.5.2 版本发布配套的 GitHub Issue 模板维护提交。Iceberg 在 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 中维护了 bug 报告模板，要求报告者选择所使用的 Iceberg 版本。1.5.2 发布后，需要将模板的版本下拉选项更新为以 1.5.2 为最新发布版本，1.5.1 降级为普通选项，引导用户在报告问题时优先选择 1.5.2。这是每次新版本发布后的标准维护动作。

## 如何达成设计目的

直接编辑 bug 报告模板 YAML 中版本选择控件的 `options` 列表：将原来标注为最新的 `1.5.1 (latest release)` 拆为两行——新增 `1.5.2 (latest release)` 置于列表顶部，把 `1.5.1` 作为普通选项跟在后面。GitHub 表单会将列表第一项作为默认选中项，因此新模板会默认引导用户选择 1.5.2。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`

**修改目的**：将 bug 报告模板的版本选项更新为以 1.5.2 为最新版，1.5.1 降级为普通选项。

**工作逻辑**：改动位于模板 `body` 中版本选择控件的 `options` 数组。修改前顶部为 `"1.5.1 (latest release)"`，修改后顶部变为 `"1.5.2 (latest release)"`，紧跟 `"1.5.1"`（去掉 latest 标注），其余历史版本选项（1.5.0、1.4.3、1.4.2 等）保持不变。

## 小结

- **成效**：完成 1.5.2 发版的 Issue 模板更新，确保 1.5.2 发布后社区能正确收集针对该版本的 bug 报告。
- **影响范围**：仅影响 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 一个文件，不涉及任何代码变更。
- **回迁到 1.4.x 的注意事项**：这是 1.5.2 发版配套的基础设施维护变更，与 1.4.x 分支无关，无需回迁。
