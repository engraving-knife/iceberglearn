# 提交 1733：Docker: Pin QEMU version temporarily (#12262)

## 提交信息

- **序号**：1733 / 4088
- **哈希**：75d8e842294837c41c04c2abfcaf3314caf4879a
- **短哈希**：75d8e8422
- **日期**：2025-02-14 11:40:14 +0000
- **作者**：Yuya Ebihara
- **提交说明**：Docker: Pin QEMU version temporarily (#12262)
- **PR/Issue**：#12262

## 总体目的

Iceberg 的 REST Catalog Fixture Docker 镜像通过 GitHub Actions 进行多架构构建（multi-arch build），使用 QEMU 模拟不同 CPU 架构（如 ARM64）来交叉构建镜像。QEMU 通过 `docker/setup-qemu-action` GitHub Action 安装。

近期 QEMU 的最新版本存在一个 bug（参见 `docker/setup-qemu-action#198`），导致多架构 Docker 构建失败。本提交的目标是通过临时固定 QEMU 版本到一个已知可用的版本来解决 CI 构建中断问题，等待上游 QEMU 修复后再移除版本固定。

## 如何达成设计目的

提交在 `publish-iceberg-rest-fixture-docker.yml` GitHub Actions 工作流中，为 `docker/setup-qemu-action@v3` 步骤添加了 `with` 参数，指定使用 `tonistiigi/binfmt:qemu-v7.0.0-28` 镜像替代默认的最新版本。通过显式指定版本号，避免了自动拉取到有 bug 的最新版本。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml`（修改, +3 lines）

**修改目的**：临时固定 QEMU 版本以规避上游 bug。

**工作逻辑**：在 `Set up QEMU` 步骤中添加了 `with` 块，指定 `image: tonistiigi/binfmt:qemu-v7.0.0-28`。同时添加了注释说明这是临时措施，原因是指向 GitHub issue `docker/setup-qemu-action#198` 的 QEMU bug。

## 小结

- **成效**：通过固定 QEMU 到 `qemu-v7.0.0-28` 版本，临时解决了多架构 Docker 镜像构建失败的问题，恢复了 CI 流水线的正常运行。
- **影响范围**：仅影响 `publish-iceberg-rest-fixture-docker.yml` 工作流，即 REST Catalog Fixture Docker 镜像的发布流程。不影响任何 Iceberg 源代码或功能。
- **回迁到 1.4.x 的注意事项**：此提交仅修改 CI 工作流配置，回迁风险极低。如果 1.4.x 分支也有相同的 Docker 发布工作流且遇到同样的 QEMU bug，建议回迁。如果 1.4.x 的 CI 正常运行，则不一定需要回迁。这是一个临时性修复，后续可能已被移除或替换。
