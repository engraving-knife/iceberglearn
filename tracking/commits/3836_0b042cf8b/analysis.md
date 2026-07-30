# 提交 3836：Build: Bump docker/setup-qemu-action from 4.0.0 to 4.1.0 (#16704)

## 提交信息

- **序号**：3836 / 4088
- **哈希**：0b042cf8b5a1391a02e471725dc58f3592304465
- **短哈希**：0b042cf8b
- **日期**：2026-06-07 09:41:21 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump docker/setup-qemu-action from 4.0.0 to 4.1.0 (#16704)
- **PR/Issue**：#16704

## 总体目的

这是 Dependabot 自动发起的依赖升级提交，用于将 GitHub Actions 工作流中使用的 `docker/setup-qemu-action` 从 4.0.0 版本升级到 4.1.0 版本。`docker/setup-qemu-action` 是 Docker 官方提供的 GitHub Action，用于在工作流中安装并配置 QEMU 多架构模拟环境，是构建多架构 Docker 镜像（如同时支持 amd64 和 arm64）的前置依赖。

Iceberg 项目在发布 REST Fixture Docker 镜像时需要支持多架构构建，因此使用该 Action 来启用 QEMU 模拟。Dependabot 监测到上游有新版本发布后，自动创建了本 PR 进行升级，属于例行维护工作，目的是获得新版本的修复与改进，同时保持依赖的最新状态。

## 如何达成设计目的

通过修改发布工作流 YAML 文件中引用 `docker/setup-qemu-action` 的版本号实现升级，将 action 的 commit SHA 引用从 `ce360397...`（对应 v4.0.0）更新为 `06116385...`（对应 v4.1.0）。采用 commit SHA 而非标签引用是出于安全考虑，可以防止供应链攻击。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：升级 QEMU setup action 版本。

**工作逻辑**：
将 `Set up QEMU` 步骤中引用的 action 版本从 v4.0.0 升级到 v4.1.0：
```yaml
- name: Set up QEMU
  uses: docker/setup-qemu-action@06116385d9baf250c9f4dcb4858b16962ea869c3 # v4.1.0
```
该步骤位于 `publish-iceberg-rest-fixture-docker.yml` 工作流中，用于在构建并发布 Iceberg REST Fixture Docker 镜像之前设置 QEMU 多架构模拟环境。

## 总结

这是一次例行的 CI 依赖升级，将 `docker/setup-qemu-action` 从 4.0.0 升级到 4.1.0（minor 版本更新）。影响范围仅限于 Iceberg REST Fixture Docker 镜像的发布工作流，对项目代码本身无任何影响，属于安全且低风险的维护性变更。
