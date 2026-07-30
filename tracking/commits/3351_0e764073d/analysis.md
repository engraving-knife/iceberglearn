# 提交 3351：Build: Bump docker/setup-buildx-action from 3 to 4 (#15533)

## 提交信息

- **序号**：3351 / 4088
- **哈希**：0e764073d6694debe20a50c7fa7700590e90e336
- **短哈希**：0e764073d
- **日期**：2026-03-07 23:15:19 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump docker/setup-buildx-action from 3 to 4 (#15533)
- **PR/Issue**：#15533

## 总体目的

`docker/setup-buildx-action` 是 Docker 官方维护的 GitHub Action，用于在 CI 中安装并配置 Docker Buildx 构建器（支持多架构、缓存驱动等高级构建能力）。Iceberg 在 `.github/workflows/publish-iceberg-rest-fixture-docker.yml` 工作流的 `Set up Docker Buildx` 步骤中用它为后续的 `docker/build-push-action` 准备构建器，用于构建并发布 `iceberg-rest-fixture` 镜像。

本次 dependabot 把该 action 从大版本 `v3` 升级到 `v4`（语义版本 semver-major 升级）。大版本升级通常伴随接口或默认行为的变更，主动跟进可获取上游修复与新特性、避免使用已 EOL 的旧版本。由于工作流中没有用到该 action 的高级输入参数，升级风险较低。

## 如何达成设计目的

仅需把工作流中 `uses:` 引用的版本标签从 `@v3` 改为 `@v4`，无需改动任何业务逻辑。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：把 setup-buildx-action 升级到 v4。

**工作逻辑**：`Set up Docker Buildx` 步骤由 `docker/setup-buildx-action@v3` 改为 `@v4`，无额外 `with` 参数。该步骤位于 `Set up QEMU`（v3）与 `Build and Push`（已是 v7，由 3350 提交升级）之间，v4 的 setup-buildx-action 负责为后续 v7 的 build-push-action 提供构建器，链路保持一致。

## 总结

本提交把构建 iceberg-rest-fixture 镜像前用于配置 Buildx 的 docker/setup-buildx-action 从 v3 升到 v4（semver-major），让 CI 镜像构建流水线跟上上游主版本。改动仅一行版本标签，与 3350 提交的 build-push-action v7 升级配套使用。
