# 提交 1465：Infra: Add 1.7.1 to issue template (#11711)

## 提交信息

- **序号**：1465 / 4088
- **哈希**：f931a3dcc5092533a1e383b926f7123dd5307d7b
- **短哈希**：f931a3dcc
- **日期**：2024-12-06（Fri Dec 6 12:09:29 2024 -0800）
- **作者**：Bryan Keller <bryanck@gmail.com>
- **提交说明**：Infra: Add 1.7.1 to issue template (#11711)
- **PR/Issue**：#11711

## 总体目的

Iceberg 仓库在 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 中维护 GitHub Bug 报告模板，其中包含一个让提交者选择所使用 Iceberg 版本的下拉选项（`dropdown`）区块。每当 Iceberg 发布新版本时，都需要在该模板中同步更新版本列表，以便提交 Bug 报告的用户能准确选择自己使用的版本，帮助维护者快速定位问题影响的版本范围。

本提交在 Iceberg 1.7.1 发布之际，将 1.7.1 加入 Bug 报告模板的版本下拉选项，并将其标记为"最新发布"（latest release），同时将原有的 1.7.0 降级为普通版本条目（去掉"latest release"标记）。这样用户在提交 Bug 时能选到最新发布的 1.7.1 版本，避免因版本列表过时而误选。

## 如何达成设计目的

直接编辑 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 中版本下拉选项的 `options` 列表，在顶部新增 `"1.7.1 (latest release)"` 条目，并把原来的 `"1.7.0 (latest release)"` 改为 `"1.7.0"`（去掉最新发布标记）。这是纯模板配置改动，无代码逻辑。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`

**修改目的**：在 Bug 报告模板的版本下拉选项中加入 1.7.1 并标记为最新发布。

**工作逻辑**：在 `body` 下版本选择字段（`What Apache Iceberg version are you using?`）的 `options` 列表中：
```yaml
-        - "1.7.0 (latest release)"
+        - "1.7.1 (latest release)"
+        - "1.7.0"
         - "1.6.1"
         - "1.6.0"
         - "1.5.2"
```
即在最前面插入 `1.7.1 (latest release)`，并将原 `1.7.0 (latest release)` 改为 `1.7.0`。下方更早的版本（1.6.1、1.6.0、1.5.2 等）保持不变。该区块在 GitHub Issue 创建界面会显示为下拉选择框，提交者从中选择自己使用的 Iceberg 版本。

## 小结

- **成效**：Bug 报告模板的版本下拉选项现包含 1.7.1（标记为最新发布），并将 1.7.0 降级为普通版本，便于用户准确选择所使用的版本。
- **影响范围**：仅 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 一个文件，新增 1 行、修改 1 行，无代码、构建或文档逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是仓库基础设施（Issue 模板）改进，与产品版本功能无关，对 1.4.x 运行时无任何影响。1.4.x 作为维护分支一般不单独调整 Issue 模板（Issue 模板由 main 分支统一维护并作用于整个仓库），**无需回迁**。即使 1.4.x 分支的该文件与 main 不同，也不影响其发布产物。
