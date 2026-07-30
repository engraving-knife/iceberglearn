# 提交 1356：Infra: Add 1.7.0 to issue template (#11491)

## 提交信息

- **序号**：1356 / 4088
- **哈希**：bbc0d9aad515dc7c9c38e5f37b6d6fa521e7eab3
- **短哈希**：bbc0d9aad
- **日期**：2024-11-08（Fri Nov 8 13:34:56 2024 -0600）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：Infra: Add 1.7.0 to issue template (#11491)
- **PR/Issue**：#11491
- **协作者**：Fokko Driesprong、Amogh Jahagirdar

## 总体目的

`.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 是用户在 GitHub 上提交 Iceberg Bug 报告时填写的表单模板，其中有一个"使用版本"下拉框（`options`），列出可选的 Iceberg 版本，最新发布版本会被标注 "(latest release)" 以引导用户选择。1.7.0 发布后，模板中仍是 `1.6.1 (latest release)`，会让用户误以为最新版本是 1.6.1，且无法选择 1.7.0。

本提交把 1.7.0 加入版本下拉框并标注为最新发布，同时把 1.6.1 降级为普通选项（去掉 "(latest release)" 标记），与 1.6.0、1.5.2 等历史版本并列。

## 如何达成设计目的

直接编辑 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`，在 `options` 列表顶部插入 `1.7.0 (latest release)`，并把原 `1.6.1 (latest release)` 改为 `1.6.1`。这是纯模板配置改动，无代码逻辑。提交说明中的 "Missing `)`" 指协作者审阅时发现初稿遗漏右括号并补上的小修正，最终文件中 `1.7.0 (latest release)` 含完整括号。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`

**修改目的**：在 Bug 报告模板的版本下拉框中加入 1.7.0 并标注为最新发布。

**工作逻辑**：把 `options` 数组的头两项替换为：

```yaml
      options:
        - "1.7.0 (latest release)"
        - "1.6.1"
        - "1.6.0"
        - "1.5.2"
        - "1.5.1"
```

GitHub Issue 创建界面会按此顺序展示版本下拉框，1.7.0 排在最前并提示为最新发布，引导用户优先选择当前维护版本，同时保留历史版本以便旧版本用户提交 Bug。

## 小结

- **成效**：Bug 报告模板的版本下拉框现包含 1.7.0 并标注为最新发布，用户可在提交 Issue 时准确选择 1.7.0；1.6.1 降级为普通选项。
- **影响范围**：仅 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 一个文件，新增 1 行、修改 1 行，无代码、构建或运行时变更。
- **回迁到 1.4.x 的注意事项**：Issue 模板由 main 分支统一维护并作用于整个仓库，与维护分支的发布产物无关。1.4.x 不应回迁此改动——回迁反而会让 1.4.x 分支的模板指向 1.7.0，与该分支维护的版本不符。**无需回迁**。
