# 提交 2527：Revert "Docker: Pin QEMU version temporarily (#12262)" (#13861)

## 提交信息

- **序号**：2527 / 4088
- **哈希**：c588d8d516500290a26619e5b48bdcab6ee37b6e
- **短哈希**：c588d8d51
- **日期**：2025-08-19 11:37:47 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Revert "Docker: Pin QEMU version temporarily (#12262)" (#13861)
- **PR/Issue**：#13861
- **回溯的提交**：75d8e842294837c41c04c2abfcaf3314caf4879a（PR #12262）

## 总体目的

此提交撤销了之前临时固定 QEMU 版本的变通方案，恢复使用 Docker setup-qemu-action 的默认 QEMU 版本。

在 PR #12262 中，由于 QEMU 存在一个已知 bug（参考 [docker/setup-qemu-action#198](https://github.com/docker/setup-qemu-action/issues/198)），Iceberg 项目临时将 Docker GitHub Actions 工作流中使用的 QEMU 版本固定为 `tonistiigi/binfmt:qemu-v7.0.0-28`。这是一个临时性的变通方案，目的是避免 QEMU bug 导致的多架构 Docker 构建（multi-arch build）失败。

随着 QEMU 上游修复了该 bug 或 setup-qemu-action 更新了默认版本，固定版本的变通方案不再需要。此回退提交移除了固定的 QEMU 版本配置，恢复使用 setup-qemu-action 的默认行为，确保使用最新的 QEMU 版本进行多架构 Docker 镜像构建。

## 如何达成设计目的

回退操作直接删除了 Docker workflow YAML 文件中 `Set up QEMU` 步骤的 `with` 配置块，使其回退到 setup-qemu-action@v3 的默认行为。

具体设计要点：
1. 移除 `image: tonistiigi/binfmt:qemu-v7.0-28` 配置
2. 移除相关注释说明
3. 保留 `uses: docker/setup-qemu-action@v3` 步骤本身，使用默认 QEMU 版本

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+0/-3 lines)

**修改目的**：移除固定的 QEMU 版本配置。

**工作逻辑**：将 `Set up QEMU` 步骤从：
```yaml
- name: Set up QEMU
  uses: docker/setup-qemu-action@v3
  with:
    ## Temporary due to bug in qemu:  https://github.com/docker/setup-qemu-action/issues/198
    image: tonistiigi/binfmt:qemu-v7.0.0-28
```
简化为：
```yaml
- name: Set up QEMU
  uses: docker/setup-qemu-action@v3
```

## 总结

此提交是一个常规的维护操作，移除了临时性的 QEMU 版本固定配置，恢复使用 Docker setup-qemu-action 的默认 QEMU 版本。这确保了多架构 Docker 镜像构建使用最新的 QEMU 版本，获取 bug 修复和性能改进。回退操作干净利落，仅删除了 3 行配置代码。
