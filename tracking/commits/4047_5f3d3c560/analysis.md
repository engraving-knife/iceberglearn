# 提交 4047：Build: Bump docker/build-push-action from 7.2.0 to 7.3.0 (#17231)

## 提交信息

- **序号**：4047 / 4088
- **哈希**：5f3d3c560a956b8ca3e45c525a353c77f8a1f9b4
- **短哈希**：5f3d3c560
- **日期**：2026-07-15 18:53:02 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump docker/build-push-action from 7.2.0 to 7.3.0 (#17231)
- **PR/Issue**：#17231

## 总体目的

Dependabot 自动升级提交，将 `docker/build-push-action` 从 7.2.0 升级到 7.3.0（semver minor 版本升级）。`docker/build-push-action` 是用于构建并推送 Docker 镜像的官方 action，Iceberg 在 `publish-iceberg-rest-fixture-docker.yml` 工作流中使用它来构建和发布 Iceberg REST fixture 的 Docker 镜像。minor 版本升级通常包含新功能和向后兼容的改进。

## 如何达成设计目的

Dependabot 更新工作流中 `uses` 引用的 SHA 和版本注释，从 `f9f3042f7e2789586610d6e8b85c8f03e5195baf`（v7.2.0）改为 `53b7df96c91f9c12dcc8a07bcb9ccacbed38856a`（v7.3.0）。`with` 配置（context、file、push 等）保持不变。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：升级 Docker 镜像构建推送 action 到 7.3.0。

**工作逻辑**：
```yaml
- name: Build and Push
  uses: docker/build-push-action@53b7df96c91f9c12dcc8a07bcb9ccacbed38856a # v7.3.0
  with:
    context: ./
    file: ./docker/iceberg-rest-fixture/Dockerfile
```
仅更新 `uses` 行的 SHA 和版本注释，其余配置不变。

## 总结

常规的 CI 工具链维护升级，将 Docker 镜像构建推送 action 升级到 7.3.0 minor 版本，保持镜像发布流程基于最新版本。minor 级别升级风险较低。
