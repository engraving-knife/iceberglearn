# 提交 3358：Build: Bump docker/setup-qemu-action from 3 to 4 (#15534)

## 提交信息

- **序号**：3358 / 4088
- **哈希**：e94775075b4049dbf3f49c93708f9c7277d14761
- **短哈希**：e94775075
- **日期**：2026-03-08
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump docker/setup-qemu-action from 3 to 4 (#15534)
- **PR/Issue**：#15534

## 总体目的

这是由 Dependabot 自动生成的 GitHub Actions 依赖升级提交，用于将 `docker/setup-qemu-action` 从 v3 升级到 v4。该 Action 用于在 GitHub Actions CI 流水线中安装和配置 QEMU，以便支持多架构 Docker 镜像构建（如同时构建 amd64 和 arm64 镜像）。

Iceberg 项目通过 `.github/workflows/publish-iceberg-rest-fixture-docker.yml` 工作流发布 Iceberg REST Catalog 的 Docker 镜像。该工作流使用 `docker/setup-qemu-action` 来启用 QEMU 模拟，随后配合 `docker/setup-buildx-action` 和 Docker Buildx 构建多平台镜像。从 v3 到 v4 属于主版本（semver-major）升级，Dependabot 标注为 `update-type: version-update:semver-major`，意味着可能存在不兼容的行为变更。不过由于该 Action 仅用于设置 QEMU 环境这一单一职责，主版本升级通常涉及底层 Node.js 运行时或输入参数的调整，对工作流本身的调用方式影响有限。

升级到 v4 可使 CI 流水线使用最新维护的 Action 版本，避免旧版本停止维护带来的安全风险，并获取新版本中的缺陷修复与运行时改进。

## 如何达成设计目的

通过修改 GitHub Actions 工作流文件中 `Set up QEMU` 步骤引用的 Action 版本，将 `docker/setup-qemu-action@v3` 更新为 `docker/setup-qemu-action@v4`，使后续 CI 构建使用新版 Action。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：将 `docker/setup-qemu-action` 从 v3 升级到 v4。

**工作逻辑**：
该工作流负责构建并发布 Iceberg REST Catalog 的 Docker 镜像。在构建多架构镜像前，需要先通过 QEMU 设置步骤启用跨架构模拟。本次仅修改该步骤的 Action 版本引用：

```
-      uses: docker/setup-qemu-action@v3
+      uses: docker/setup-qemu-action@v4
```

紧随其后的 `docker/setup-buildx-action@v4`（已是 v4）以及 `Build and Push` 步骤保持不变。升级后 QEMU 设置步骤将使用 v4 版本的 Action 实现，该版本通常更新了 Node.js 运行时并可能调整了默认行为，但核心功能（安装 QEMU 以支持多架构构建）不变。

## 总结

本次为 GitHub Actions 中 `docker/setup-qemu-action` 的主版本升级（v3 → v4），用于保持多架构 Docker 镜像构建流水线使用最新维护的 Action 版本。虽然属于主版本升级，但该 Action 职责单一，对 Iceberg REST Docker 镜像发布流程的影响可控，主要目的是跟进上游维护与安全更新。
