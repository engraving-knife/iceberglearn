# 提交 1618 9a46890e1 分析

## 提交信息
- 哈希：9a46890e171fc0ebfff7bdd608b65a0f0d6aef45
- 日期：2025-01-22 14:57:03 +0100
- 作者：Fokko Driesprong
- 消息：Build: Nightly build for Iceberg REST fixtures (#12008)

  提交消息正文包含两条子项：
  - Build: Nighly build for Iceberg REST fixtures
  - On version as well

## 总体目的

本提交为 Iceberg REST fixture Docker 镜像的发布工作流添加每日定时构建（nightly build）。Iceberg 提供一个名为 `iceberg-rest-fixture` 的 Docker 镜像（发布到 Docker Hub 的 `apache/iceberg-rest-fixture`），它是 Iceberg REST Catalog 的一个参考实现/测试 fixture，供用户本地试用或集成测试。在此之前，该镜像只在打发布标签（`apache-iceberg-X.Y.Z`）时才构建并推送 `X.Y.Z` 版本标签，以及手动 `workflow_dispatch` 触发。这导致 `latest` 标签长期停留在上次发布时的状态，无法反映 main 分支上的最新修复。

为了让用户拉取 `apache/iceberg-rest-fixture:latest` 时能拿到基于最新 main 分支构建的镜像（包含未发布的 bug 修复与改进），本提交在工作流的 `on` 触发器中新增 `schedule` 定时触发，每天 UTC 时间 2:00 自动运行一次构建并推送 `latest` 标签。

## 如何达成设计目的

设计思路是利用 GitHub Actions 的 `schedule` 触发器配合 cron 表达式实现每日定时构建。`schedule` 是 GitHub Actions 原生支持的触发方式，cron 表达式 `0 2 * * *` 表示每天 2:00 UTC 执行。该触发器与原有的 `push.tags`（发布标签时触发）和 `workflow_dispatch`（手动触发）并存，三种触发方式共享同一份 job 步骤。

需要注意：
- 定时触发的构建在 `Set the tagged version` 步骤中不会命中条件（因为 `github.event_name` 是 `schedule` 而非 `push`，且 `github.ref` 不含 `refs/tags/`），所以 `DOCKER_IMAGE_VERSION` 保持 env 中默认的 `latest`，最终推送到 `apache/iceberg-rest-fixture:latest`。
- 发布标签触发的构建仍会按标签解析出版本号（如 `1.7.1`），推送到 `apache/iceberg-rest-fixture:1.7.1`。
- 这样 `latest` 始终反映 main 分支的最新状态，而发布版本镜像则按标签保留，互不冲突。

提交消息中的"On version as well"指该 nightly 构建同样会经过版本镜像的构建流程（即同样的 `shadowJar`、QEMU 多架构构建、Docker push 等步骤），只是版本号落在 `latest` 上。cron 时刻选在 UTC 2:00 是为了避开 GitHub Actions 上深夜定时任务的高峰期，降低队列延迟。

### 修改详情

#### .github/workflows/publish-iceberg-rest-fixture-docker.yml

在 `on:` 触发器的 `push.tags` 之后、`workflow_dispatch:` 之前新增两行：

```
  schedule:
    - cron: '0 2 * * *' # run at 2 AM UTC
```

仅此一处改动，共 2 行新增、0 行删除。工作流的其余部分（env、jobs.build.steps 等）完全未变，因此定时触发的运行会复用已有的构建逻辑：checkout → setup Java 21 → `./gradlew :iceberg-open-api:shadowJar` 构建 OpenAPI 项目 → 登录 Docker Hub → （仅 push 事件才设置版本号）→ setup QEMU + Buildx → 多平台（linux/amd64, linux/arm64）构建并推送。

## 小结

此次改动让 `apache/iceberg-rest-fixture:latest` Docker 镜像每天自动从 main 分支重建并推送，使用户能持续获得最新的 REST Catalog fixture，而不必等待正式发布。多平台（amd64/arm64）构建在 nightly 任务中同样生效。

影响范围：仅 GitHub Actions 工作流配置，对仓库源码、构建产物逻辑、运行时行为均无影响。会增加 Docker Hub 上 `apache/iceberg-rest-fixture` 仓库的镜像更新频率（每日一次），以及对应的 CI 资源消耗（每天一次 ubuntu-latest 多架构构建）。

回迁到 1.4.x 分支的注意事项：1.4.x 是维护分支，其 `iceberg-rest-fixture` 工作流文件可能存在也可能不存在（取决于 1.4.x 时期是否已引入该 fixture 镜像）。回迁前需确认 1.4.x 中是否存在 `.github/workflows/publish-iceberg-rest-fixture-docker.yml`。若存在，则可套用本提交，让 1.4.x 的 `latest` 镜像也保持每日更新；但需评估 1.4.x 是否仍需要 nightly 更新（维护分支通常只在发布时更新镜像，nightly 可能意义不大，且会消耗 CI 配额）。此外，定时任务依赖 Docker Hub secrets（`DOCKERHUB_USER`/`DOCKERHUB_TOKEN`），需确认 1.4.x 分支的仓库 secrets 已配置。最后注意 GitHub Actions 的 schedule 触发有"可能在高峰期延迟甚至跳过"的官方说明，不要把它当作严格 SLA。
