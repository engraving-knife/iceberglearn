# 提交 3560：Build: Bump docker/build-push-action from 7.0.0 to 7.1.0 (#16041)

## 提交信息

- **序号**：3560 / 4088
- **哈希**：55f892319457e569c36cec2e72321a2f89745250
- **短哈希**：55f892319
- **日期**：2026-04-19 07:17:11 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump docker/build-push-action from 7.0.0 to 7.1.0 (#16041)
- **PR/Issue**：#16041

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 GitHub Actions 中用于构建和推送 Docker 镜像的 `docker/build-push-action` 从版本 7.0.0 升级到 7.1.0。该 Action 用于 Iceberg 项目的 CI/CD 流水线中构建 REST fixture 的 Docker 镜像。这是一个 semver-minor（次版本）升级。

## 如何达成设计目的

Dependabot 自动扫描 GitHub Actions 工作流文件中引用的 Action 版本，发现 `docker/build-push-action` 有新版本可用，自动创建 PR 升级版本引用。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：更新 Docker 构建推送 Action 的版本引用。

**工作逻辑**：
将工作流中 `docker/build-push-action` 的版本从 `@v7.0.0`（或对应 commit）升级到 `@v7.1.0`。升级后该工作流在构建 Iceberg REST fixture Docker 镜像时会使用 7.1.0 版本的 Action，获取新版本中的改进和 bug 修复。

## 总结

这是一个 CI/CD 依赖维护提交，通过升级 Docker build-push Action 获取 7.1.0 版本的改进，保持镜像构建流水线的最新状态。
