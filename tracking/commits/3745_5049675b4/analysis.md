# 提交 3745：infra: add 1.11.0 to issue template (#16413)

## 提交信息

- **序号**：3745 / 4088
- **哈希**：5049675b46d18bed257b17a9cb25a48df23e7b2a
- **短哈希**：5049675b4
- **日期**：2026-05-18 23:56:14 -0700
- **作者**：Aihua Xu
- **提交说明**：infra: add 1.11.0 to issue template (#16413)
- **PR/Issue**：#16413

## 总体目的

本提交将新发布的 Iceberg 1.11.0 版本添加到 GitHub issue 模板的版本选择列表中，并标记为 latest release。

随着 Iceberg 1.11.0 版本的发布，issue 模板中的版本选项需要同步更新。原模板将 1.10.2 标记为 "latest release"（在 #16404 中刚更新），现在需要将 1.11.0 标记为 "latest release" 并保留 1.10.2 作为历史版本选项。这样用户在提交 Bug 报告时能准确选择他们实际使用的版本，便于维护者定位和复现问题。

## 如何达成设计目的

修改 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 中版本下拉选项，将 "1.10.2 (latest release)" 改为 "1.11.0 (latest release)"，并在其下方新增 "1.10.2" 选项（去掉 latest release 标记）。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` (+2/-1 lines)

**修改目的**：在 Bug 报告模板的版本选项中新增 1.11.0 并调整 latest release 标记。

**工作逻辑**：
将版本选项从：
```yaml
options:
  - "1.10.2 (latest release)"
  - "1.10.1"
```
改为：
```yaml
options:
  - "1.11.0 (latest release)"
  - "1.10.2"
  - "1.10.1"
```
1.11.0 成为新的 latest release，1.10.2 降级为普通历史版本选项。

## 总结

本提交是 1.11.0 版本发布配套的基础设施更新，将新版本添加到 GitHub issue 模板的版本选择列表中并标记为 latest release，同时保留 1.10.2 作为历史版本选项。改动仅涉及 issue 模板配置，便于用户准确报告所使用的版本。
