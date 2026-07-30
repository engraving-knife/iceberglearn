# 提交 3382：Flink CI: Define timeout to avoid CI jobs running indefinitely (#15617)

## 提交信息

- **序号**：3382 / 4088
- **哈希**：2cb3cc6d59ce478e503e16ee8409308101c88598
- **短哈希**：2cb3cc6d5
- **日期**：2026-03-13
- **作者**：JB Onofré
- **提交说明**：Flink CI: Define timeout to avoid CI jobs running indefinitely (#15617)
- **PR/Issue**：#15617

## 总体目的

本提交为 Flink CI 工作流中的 Scala 2.12 测试任务设置超时上限，防止因测试卡死或挂起导致 CI 任务无限期运行、浪费 GitHub Actions 计算资源并阻塞流水线。

在持续集成实践中，偶尔会出现测试因死锁、网络等待、资源竞争等原因挂起而永不退出的情况。若不设置超时，这类卡死的 job 会一直占用 runner 直到达到 GitHub Actions 的硬性上限（通常数小时），既浪费资源又会因迟迟不返回结果而拖延整个 PR 的合并流程。为 Flink 的 Scala 2.12 通用验证测试任务添加 `timeout-minutes` 后，一旦超过阈值 GitHub Actions 会自动终止该 job，使维护者能快速发现并处理卡死问题。

选择仅对 `flink-scala-2-12-tests` 任务设置超时，是因为该任务是 Flink 各版本的通用验证入口（注释明确说明用 Scala 2.12 做一般性验证），其卡死的影响面最大。

## 如何达成设计目的

在 `.github/workflows/flink-ci.yml` 中为 `flink-scala-2-12-tests` job 添加 `timeout-minutes: 60` 配置项，将该任务的最大运行时间限制为 60 分钟。超时后 GitHub Actions 自动取消该 job 及其后代 job。

## 修改详情

### `.github/workflows/flink-ci.yml` (+1/-0 lines)

**修改目的**：为 Flink Scala 2.12 测试 job 设置 60 分钟超时。

**工作逻辑**：
在 `flink-scala-2-12-tests` job 定义中、`runs-on: ubuntu-24.04` 之后添加 `timeout-minutes: 60`。GitHub Actions 的 `timeout-minutes` 字段定义了 job 的最长运行时间，超过后整个 job（包括其 matrix 中的所有子任务）会被自动标记为取消。60 分钟的阈值留出了 Flink 多版本矩阵测试的合理执行时间，同时能在卡死场景下及时止损。该配置位于 `strategy`（matrix）之前，对整个 job 生效。

## 总结

本提交是一笔 CI 健壮性改进，为 Flink CI 的 Scala 2.12 通用验证测试任务设置 60 分钟超时上限，避免因测试卡死导致 CI job 无限运行、浪费计算资源并阻塞流水线。改动极小但实用价值明显，是 CI 运维层面的防御性措施。
