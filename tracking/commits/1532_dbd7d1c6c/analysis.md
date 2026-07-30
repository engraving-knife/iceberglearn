# 提交 1532 dbd7d1c6c 分析

## 提交信息
- 哈希：dbd7d1c6c32834a8708211f19ad6f6901de5433e
- 日期：2024-12-23（Mon Dec 23 23:26:32 2024 +0800）
- 作者：Manu Zhang <OwenZhang1990@gmail.com>
- 消息：Build: Fix ignoring `.asf.yaml` in PR (#11860)

## 总体目的

Apache Iceberg 仓库根目录下维护着 `.asf.yaml` 文件（ASF 仓库配置文件，用于配置 GitHub 仓库的元信息如描述、labels、主页等）。各 CI workflow 在 `on.push.paths-ignore` 与 `on.pull_request.paths-ignore` 中列出了若干"不触发 CI"的路径，原本意图是：当 PR 仅修改这些与代码无关的辅助文件时，跳过昂贵的 CI 构建以节省资源。

然而 6 个 CI workflow（delta-conversion-ci、flink-ci、hive-ci、java-ci、kafka-connect-ci、spark-ci）在 `paths-ignore` 列表中误把文件名写成了 `.asf.yml`（少了一个 `a`），而仓库里实际存在的文件是 `.asf.yaml`。这导致忽略规则形同虚设——任何只改 `.asf.yaml` 的 PR 都不会被忽略，依然会触发完整 CI 运行，造成算力浪费与不必要的等待时间。

本提交将这 6 个 workflow 中的 `.asf.yml` 统一改为正确的 `.asf.yaml`，让忽略规则真正生效，恢复 CI 跳过的预期行为。这是一处典型的小拼写错误修复，但因影响所有主流 CI workflow 的触发逻辑，对工程效率有实际改善。

## 如何达成设计目的

通过遍历 6 个 CI workflow 的 YAML 配置文件，将 `paths-ignore` 列表中的 `- '.asf.yml'` 行替换为 `- '.asf.yaml'`。改动是纯文本替换，每文件仅 1 行变更（共 6 行）。修改后，GitHub Actions 在判定 PR 是否仅改动忽略路径时，会正确匹配到 `.asf.yaml` 文件名，从而跳过 CI。

### 修改详情

#### `.github/workflows/delta-conversion-ci.yml`
将 `paths-ignore` 中的 `- '.asf.yml'` 改为 `- '.asf.yaml'`，使 Delta Conversion CI 在 PR 仅修改 `.asf.yaml` 时跳过。

#### `.github/workflows/flink-ci.yml`
将 `paths-ignore` 中的 `- '.asf.yml'` 改为 `- '.asf.yaml'`，使 Flink CI 在 PR 仅修改 `.asf.yaml` 时跳过。

#### `.github/workflows/hive-ci.yml`
将 `paths-ignore` 中的 `- '.asf.yml'` 改为 `- '.asf.yaml'`，使 Hive CI 在 PR 仅修改 `.asf.yaml` 时跳过。

#### `.github/workflows/java-ci.yml`
将 `paths-ignore` 中的 `- '.asf.yml'` 改为 `- '.asf.yaml'`，使 Java CI 在 PR 仅修改 `.asf.yaml` 时跳过。

#### `.github/workflows/kafka-connect-ci.yml`
将 `paths-ignore` 中的 `- '.asf.yml'` 改为 `- '.asf.yaml'`，使 Kafka Connect CI 在 PR 仅修改 `.asf.yaml` 时跳过。

#### `.github/workflows/spark-ci.yml`
将 `paths-ignore` 中的 `- '.asf.yml'` 改为 `- '.asf.yaml'`，使 Spark CI 在 PR 仅修改 `.asf.yaml` 时跳过。

## 小结

- **成效**：修复了 6 个 CI workflow 中 `.asf.yaml` 文件名拼写错误（`.asf.yml`），使 `paths-ignore` 规则真正生效，避免仅修改仓库元配置文件的 PR 触发不必要的 CI 构建，节省 CI 资源与开发者等待时间。
- **影响范围**：仅 `.github/workflows/` 下 6 个 YAML 文件，每文件 1 行变更，共 6 行修改，无任何代码或构建产物变更。
- **回迁到 1.4.x 的注意事项**：这是 CI 基础设施修复，对 1.4.x 运行时无影响。1.4.x 维护分支通常不单独调整 CI workflow（由 main 统一维护），**无需回迁**。即便 1.4.x 分支的 workflow 文件存在同样拼写问题，也不影响其发布产物。
