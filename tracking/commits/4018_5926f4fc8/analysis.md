# 提交 4018：Build: Bump docker/setup-qemu-action from 4.1.0 to 4.2.0 (#17165)

## 提交信息

- **序号**：4018 / 4088
- **哈希**：5926f4fc8713ada3a44f008642b245f8b999b31c
- **短哈希**：5926f4fc8
- **日期**：2026-07-11 23:58:29 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump docker/setup-qemu-action from 4.1.0 to 4.2.0 (#17165)
- **PR/Issue**：#17165

## 总体目的

本提交是 Dependabot 自动生成的依赖升级，将 GitHub Action `docker/setup-qemu-action` 从 v4.1.0 升级到 v4.2.0。该 action 用于在 CI 中设置 QEMU，以支持多架构 Docker 镜像构建（如同时构建 amd64 和 arm64 镜像）。

## 如何达成设计目的

在 `.github/workflows/publish-iceberg-rest-fixture-docker.yml` 中更新 `setup-qemu-action` 的引用 commit hash 和版本注释。属于 minor 版本升级（4.1.0 → 4.2.0），可能包含新功能和改进。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：升级 setup-qemu-action 版本。

**工作逻辑**：
```yaml
# 修改前
uses: docker/setup-qemu-action@06116385d9baf250c9f4dcb4858b16962ea869c3 # v4.1.0
# 修改后
uses: docker/setup-qemu-action@96fe6ef7f33517b61c61be40b68a1882f3264fb8 # v4.2.0
```
该 action 用于 Iceberg REST fixture Docker 镜像发布工作流，支持多架构构建。

## 总结

这是一次常规的 Dependabot 依赖升级，Docker setup-qemu-action minor 版本更新（4.1.0 → 4.2.0），影响 REST fixture Docker 镜像发布工作流。无功能影响，保持 CI 工具链最新版本。
