# 提交 4045：Build: Bump the codeql-action group with 3 updates (#17228)

## 提交信息

- **序号**：4045 / 4088
- **哈希**：d2bb155a0f60a4a0afc4361e770f36928f629455
- **短哈希**：d2bb155a0
- **日期**：2026-07-15 18:52:40 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump the codeql-action group with 3 updates (#17228)
- **PR/Issue**：#17228

## 总体目的

这是 Dependabot 自动生成的「分组升级」提交，一次性将 codeql-action 组内的三个组件统一升级到 4.37.0。这正是提交 4038（将 codeql-action 配置为分组的成果）所期望的工作方式——三个 codeql-action 步骤在同一个 PR 中一起升级，确保它们版本一致，避免因版本不一致导致 CodeQL Analyze 任务失败。

具体升级内容：
- `github/codeql-action/init`：4.36.2 → 4.37.0
- `github/codeql-action/analyze`：4.36.2 → 4.37.0
- `github/codeql-action/upload-sarif`：4.36.3 → 4.37.0

注意 `upload-sarif` 此前是 4.36.3（与 init/analyze 的 4.36.2 不同），这正是 4038 提交所要消除的「版本不一致」问题；本次分组升级后三者统一到 4.37.0。

## 如何达成设计目的

Dependabot 根据 4038 提交配置的 `codeql-action` 分组（pattern `github/codeql-action*`），把所有匹配的 action 升级合并到一个 PR 中。由于 Iceberg 固定 action 到 commit SHA，每个 action 的 `uses` 行都需要更新 SHA 和版本注释。本次涉及两个工作流文件：
- `codeql.yml`：更新 `init` 和 `analyze` 两处。
- `cve-scan.yml`：更新 `upload-sarif` 一处。

## 修改详情

### `.github/workflows/codeql.yml` (+2/-2 lines)

**修改目的**：升级 CodeQL 工作流中的 init 和 analyze action。

**工作逻辑**：
```yaml
- uses: github/codeql-action/init@99df26d4f13ea111d4ec1a7dddef6063f76b97e9 # v4.37.0
- uses: github/codeql-action/analyze@99df26d4f13ea111d4ec1a7dddef6063f76b97e9 # v4.37.0
```
两处 SHA 从 `8aad20d150bbac5944a9f9d289da16a4b0d87c1e`（v4.36.2）统一更新为 `99df26d4f13ea111d4ec1a7dddef6063f76b97e9`（v4.37.0）。`with` 配置（`languages: actions` 和 `category`）不变。

### `.github/workflows/cve-scan.yml` (+1/-1 lines)

**修改目的**：升级 CVE 扫描工作流中的 upload-sarif action。

**工作逻辑**：
```yaml
- uses: github/codeql-action/upload-sarif@99df26d4f13ea111d4ec1a7dddef6063f76b97e9 # v4.37.0
```
SHA 从 `54f647b7e1bb85c95cddabcd46b0c578ec92bc1a`（v4.36.3）更新为 `99df26d4f13ea111d4ec1a7dddef6063f76b97e9`（v4.37.0），与 init/analyze 统一。`with` 配置（`sarif_file`、`category`）不变。

## 总结

这是 codeql-action 分组配置生效后的首个分组升级 PR，验证了 4038 提交的设计意图：三个 codeql-action 组件（init、analyze、upload-sarif）被合并到一个 PR 中统一升级到 4.37.0，消除了此前 upload-sarif 与 init/analyze 版本不一致（4.36.3 vs 4.36.2）的隐患。这体现了分组配置对 CI 依赖升级可靠性的实际改善。
