# 提交 4073：Build: Bump docker/setup-buildx-action from 4.1.0 to 4.2.0 (#17294)

## 提交信息

- **序号**：4073 / 4088
- **哈希**：e5bc1f15de48ff29638c90c8afe437c0095239a8
- **短哈希**：e5bc1f15d
- **日期**：2026-07-19 09:31:08 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump docker/setup-buildx-action from 4.1.0 to 4.2.0 (#17294)
- **PR/Issue**：#17294

## 总体目的

Dependabot 自动升级提交，将 `docker/setup-buildx-action` 从 4.1.0 升级到 4.2.0（semver minor 版本升级）。`docker/setup-buildx-action` 是用于在 GitHub Actions 中配置 Docker Buildx 的 action，Iceberg 在 `publish-iceberg-rest-fixture-docker.yml` 工作流中使用它来设置 Docker 构建环境（buildx），随后用 `docker/build-push-action` 构建和推送 REST fixture 镜像。minor 版本升级包含新功能和向后兼容的改进。

## 如何达成设计目的

更新工作流中 `uses` 引用的 SHA 和版本注释，从 `d7f5e7f509e45cec5c76c4d5afdd7de93d0b3df5`（v4.1.0）改为 `bb05f3f5519dd87d3ba754cc423b652a5edd6d2c`（v4.2.0）。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：升级 setup-buildx-action 到 4.2.0。

**工作逻辑**：
```yaml
uses: docker/setup-buildx-action@bb05f3f5519dd87d3ba754cc423b652a5edd6d2c # v4.2.0
```
将 SHA 和版本注释从 v4.1.0 更新为 v4.2.0。

## 总结

常规的 CI 工具链维护升级，将 Docker Buildx 配置 action 升级到 4.2.0 minor 版本，保持 Docker 镜像发布流程基于最新版本。minor 级别升级风险较低。
