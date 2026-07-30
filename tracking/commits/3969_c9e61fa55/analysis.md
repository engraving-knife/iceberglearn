# 提交 3969：CVE Scan: Test PR failure reporting UI (#16962)

## 提交信息

- **序号**：3969 / 4088
- **哈希**：c9e61fa55da1b2f47d94a1988b4a5a358248e8c9
- **短哈希**：c9e61fa55
- **日期**：2026-07-01 08:22:22 -0700
- **作者**：Kevin Liu
- **提交说明**：CVE Scan: Test PR failure reporting UI (#16962)
- **PR/Issue**：#16962

## 总体目的

本提交改进了 CVE 扫描 CI workflow 中的 PR 失败报告体验。此前，当 Trivy 扫描发现 CVE 时，扫描步骤直接以 exit-code 1 退出，GitHub 不会展开报告步骤的详情，开发者需要手动翻找日志才能看到 CVE 详情。

改进后的方案让 Trivy 始终以 exit-code 0 完成（生成 SARIF），然后由专门的报告步骤解析 SARIF、格式化为 Markdown 表格、在 PR 上标注错误注释，并仅在 PR 事件中因发现 HIGH/CRITICAL CVE 而失败。这样 GitHub 会自动展开失败的报告步骤，直接显示可操作的 CVE 详情。

## 如何达成设计目的

1. 将 Trivy 的 `exit-code` 从条件性（PR 为 1，push 为 0）改为始终 0，确保 SARIF 总是生成。
2. 重写报告步骤（从 "Print Trivy scan results" 改为 "Report Trivy scan results"），使用 jq 解析 SARIF，提取 CVE ID、严重性、包名、版本、修复版本、链接等信息，格式化为 Markdown 表格输出到 `GITHUB_STEP_SUMMARY`。
3. 通过 `FAIL_ON_FINDINGS` 环境变量控制是否在发现时失败（PR 为 true，push 为 false）。
4. 使用 `::error` 注解在 PR checks UI 上标注 CVE 概要。
5. 缺失或无法解析的 SARIF 仍视为失败。

## 修改详情

### `.github/workflows/cve-scan.yml` (+99/-13 lines)

**修改目的**：改进 CVE 扫描的 PR 失败报告 UI。

**工作逻辑**：
- 更新注释文档，说明新的行为模式：Trivy 始终 exit-code 0，报告步骤负责失败判定。
- Trivy step：`exit-code: '0'`（始终成功），确保 SARIF 输出。
- 报告步骤重写为模块化的 shell 脚本：
  - `extract_findings()`：使用 jq 从 SARIF 提取 findings，解析 `message.text` 中的 `Severity:`、`Package:`、`Installed Version:`、`Fixed Version:`、`Link:` 前缀字段。
  - `report_findings()`：生成 Markdown 表格 `| CVE | Severity | Package | ... |`。
  - `markdown_escape()` 和 `escape_annotation()`：转义特殊字符。
  - 缺失 SARIF 文件或解析失败时 `exit 1`。
  - PR 事件中发现 findings 时，通过 `::error` 注解标注并 `exit 1`。

```bash
if [ "${FAIL_ON_FINDINGS}" = "true" ]; then
  annotation_message="Trivy found ${finding_count} HIGH/CRITICAL vulnerabilities..."
  echo "::error title=Trivy CVE scan failed::${annotation}"
  exit 1
fi
```

## 总结

本提交显著改善了 CVE 扫描的 PR 体验：从"扫描失败但详情隐藏在日志中"变为"扫描成功、报告步骤格式化展示 CVE 表格并标注到 PR UI"。这是一个 CI/CD 可用性改进，使开发者能更快地了解和响应 CVE 发现。注意提交消息提到此 PR 是为了"测试" failure reporting UI（曾临时移除 Spark 3.5 Jackson CVE 忽略项以触发失败，然后恢复）。
