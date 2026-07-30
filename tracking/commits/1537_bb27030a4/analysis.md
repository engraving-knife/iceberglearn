# 提交 1537 bb27030a4 分析

## 提交信息
- 哈希：bb27030a4c857e24ab2c7479c3b950388925dd60
- 日期：2024-12-26（Thu Dec 26 05:26:27 2024 +0800）
- 作者：Manu Zhang <OwenZhang1990@gmail.com>
- 消息：Build: Fix ignoring `license-check.yml` in PR (#11873)

## 总体目的

本提交与紧邻的提交 1532（修复 `.asf.yaml` 拼写）属同一类 CI 触发规则修复，由同一作者连续提交。Iceberg 仓库 `.github/workflows/` 下维护着 `license-check.yml`（许可证检查 workflow），而 6 个主流 CI workflow（delta-conversion-ci、flink-ci、hive-ci、java-ci、kafka-connect-ci、spark-ci）在 `paths-ignore` 列表中误将其拼写为英式拼写 `licence-check.yml`（`licence` vs 美式 `license`）。

由于 GitHub Actions 的 `paths-ignore` 是精确字符串匹配，拼写不一致导致忽略规则对该文件失效：当 PR 仅修改 `license-check.yml` 时，本应被跳过的各引擎 CI 仍会被触发，浪费构建资源。

本提交将 6 个 workflow 中的 `licence-check.yml` 统一改为正确的 `license-check.yml`（与实际文件名一致），让忽略规则生效。这是又一处典型拼写错误修复，与 1532 同属"清理 `paths-ignore` 文件名不匹配"的系列工作。

注：在该提交时刻仓库中实际文件名为 `license-check.yml`（连字符），本提交修正后与之匹配。后续（本提交之后）该文件被进一步重命名为 `license_check.yml`（下划线），属另一独立的重命名变更，不在本提交范围内。

## 如何达成设计目的

对 6 个 CI workflow 的 YAML 文件，将 `paths-ignore` 列表中的 `- '.github/workflows/licence-check.yml'` 替换为 `- '.github/workflows/license-check.yml'`。每文件 1 行变更，共 6 行，纯文本替换。

### 修改详情

#### `.github/workflows/delta-conversion-ci.yml`
将 `paths-ignore` 中的 `licence-check.yml` 改为 `license-check.yml`，使 Delta Conversion CI 在 PR 仅修改 license-check workflow 时跳过。

#### `.github/workflows/flink-ci.yml`
将 `paths-ignore` 中的 `licence-check.yml` 改为 `license-check.yml`，使 Flink CI 在 PR 仅修改 license-check workflow 时跳过。

#### `.github/workflows/hive-ci.yml`
将 `paths-ignore` 中的 `licence-check.yml` 改为 `license-check.yml`，使 Hive CI 在 PR 仅修改 license-check workflow 时跳过。

#### `.github/workflows/java-ci.yml`
将 `paths-ignore` 中的 `licence-check.yml` 改为 `license-check.yml`，使 Java CI 在 PR 仅修改 license-check workflow 时跳过。

#### `.github/workflows/kafka-connect-ci.yml`
将 `paths-ignore` 中的 `licence-check.yml` 改为 `license-check.yml`，使 Kafka Connect CI 在 PR 仅修改 license-check workflow 时跳过。

#### `.github/workflows/spark-ci.yml`
将 `paths-ignore` 中的 `licence-check.yml` 改为 `license-check.yml`，使 Spark CI 在 PR 仅修改 license-check workflow 时跳过。

## 小结

- **成效**：修复了 6 个 CI workflow 中 `license-check.yml` 的英式拼写错误（`licence-check.yml`），使 `paths-ignore` 规则真正生效，避免仅修改 license-check workflow 的 PR 触发不必要的各引擎 CI 构建。
- **影响范围**：仅 `.github/workflows/` 下 6 个 YAML 文件，每文件 1 行变更，共 6 行修改，无代码或构建产物变更。
- **回迁到 1.4.x 的注意事项**：这是 CI 基础设施拼写修复，对 1.4.x 运行时无影响。1.4.x 维护分支通常不单独调整 CI workflow（由 main 统一维护），**无需回迁**。
