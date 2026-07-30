# 提交 3808：Build: Bump docker/setup-buildx-action from 4.0.0 to 4.1.0 (#16632)

## 提交信息

- **序号**：3808 / 4088
- **哈希**：16566de39e061cc2b3dcc50f4b94e90e2d56c16b
- **短哈希**：16566de39
- **日期**：2026-05-31 08:42:12 -0700
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump docker/setup-buildx-action from 4.0.0 to 4.1.0 (#16632)
- **PR/Issue**：#16632

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 仓库 `publish-iceberg-rest-fixture-docker.yml` 工作流中使用的 `docker/setup-buildx-action` 从 `4.0.0` 升级到 `4.1.0`。`docker/setup-buildx-action` 是 Docker 官方维护的 GitHub Action，用于在 CI 中安装并配置 Docker Buildx 构建器，通常作为 `docker/build-push-action` 的前置步骤，以支持多平台构建、缓存等高级特性。Iceberg 用它来准备 REST fixture Docker 镜像的构建环境。这是一个 minor 级升级（4.0.0 → 4.1.0），属于 CI/CD 基础设施的常规维护。

## 如何达成设计目的

Dependabot 检测到工作流中 `uses: docker/setup-buildx-action@<pin>` 的版本注释有新发布，自动创建 PR 升级 commit pin 和版本注释。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：升级 Docker setup-buildx Action 版本。

**工作逻辑**：
将 Action 引用从旧 commit pin（`4d04d5d9486b7bd6fa91e7baf45bbb4f8b9deedd # v4.0.0`）更新为新 commit pin（`d7f5e7f509e45cec5c76c4d5afdd7de93d0b3df5 # v4.1.0`）：
```yaml
-      uses: docker/setup-buildx-action@4d04d5d9486b7bd6fa91e7baf45bbb4f8b9deedd # v4.0.0
+      uses: docker/setup-buildx-action@d7f5e7f509e45cec5c76c4d5afdd7de93d0b3df5 # v4.1.0
```

## 总结

这是一次 CI/CD 基础设施的常规 minor 级升级，由 Dependabot 自动完成，风险低。升级后 Iceberg REST fixture Docker 镜像构建环境将使用 `docker/setup-buildx-action` 4.1.0，与上一个提交（build-push-action 7.2.0）共同保持 Docker 构建链路的新鲜。
