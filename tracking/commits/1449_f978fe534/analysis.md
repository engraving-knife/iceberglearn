# 提交 1449：REST: Clean up `iceberg-rest-fixture` docker image naming (#11676)

## 提交信息

- **序号**：1449
- **哈希**：f978fe534447fd487a8d8d4c2d6c64404ac504f7
- **短哈希**：f978fe534
- **日期**：2024-12-01（Sun Dec 1 12:42:30 2024 +0530，提交时间 +0100）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：REST: Clean up `iceberg-rest-fixture` docker image naming (#11676)
- **PR/Issue**：#11676

## 总体目的

提交 #1442 引入了 REST Catalog 适配器的 Docker 镜像，目录名为 `docker/iceberg-rest-adapter-image/`，README 中本地构建的镜像名为 `apache/iceberg-rest-adapter`。随后 #1447 新增的 GitHub Actions 工作流 `publish-docker.yml` 把发布到 Docker Hub 的镜像名定为 `iceberg-rest-fixture`（而非 `iceberg-rest-adapter`）。这就造成了命名不一致：

- 本地构建：`apache/iceberg-rest-adapter`，目录 `docker/iceberg-rest-adapter-image/`
- CI 发布：`apache/iceberg-rest-fixture`

此外，#1447 的工作流文件名 `publish-docker.yml` 过于泛化，不能体现其发布的具体镜像；工作流中用 `${{ secrets.DOCKERHUB_USER }}` 作为镜像仓库前缀，意味着镜像仓库依赖于 Docker Hub 用户名 secret，不够明确（Apache 的 Docker Hub 仓库固定为 `apache`）；注释中还有一个小笔误（"apache-iceberg-1.7.0" 与输出 "1.7.1" 不一致）。

本提交统一命名并修复上述问题：

1. **目录重命名**：`docker/iceberg-rest-adapter-image/` → `docker/iceberg-rest-fixture/`，与 CI 发布的镜像名一致。
2. **工作流文件重命名**：`publish-docker.yml` → `publish-iceberg-rest-fixture-docker.yml`，名称更具描述性。
3. **工作流 name 字段更新**：`Build and Push Docker Image` → `Build and Push 'iceberg-rest-fixture' Docker Image`。
4. **引入 `DOCKER_REPOSITORY: apache` 环境变量**：替代 `${{ secrets.DOCKERHUB_USER }}` 作为镜像仓库前缀，使镜像仓库固定为 `apache`，不再依赖 secret 值。`docker login` 仍用 secret 凭据，但镜像 tag 用固定仓库名。
5. **修复注释笔误**：`apache-iceberg-1.7.0` → `apache-iceberg-1.7.1`，与示例输出 `1.7.1` 一致。
6. **更新 README**：本地构建命令中的镜像名和 Dockerfile 路径同步改为 `iceberg-rest-fixture`。

## 如何达成设计目的

通过 `git mv` 重命名目录和文件，并相应更新文件内容中的字符串引用：

1. `docker/iceberg-rest-adapter-image/Dockerfile` → `docker/iceberg-rest-fixture/Dockerfile`（内容无变化）。
2. `docker/iceberg-rest-adapter-image/README.md` → `docker/iceberg-rest-fixture/README.md`（更新构建命令中的镜像名和路径）。
3. `.github/workflows/publish-docker.yml` → `.github/workflows/publish-iceberg-rest-fixture-docker.yml`（更新 name、新增 DOCKER_REPOSITORY、替换 secret 引用、修复注释）。

## 修改详情

### `.github/workflows/publish-docker.yml` → `.github/workflows/publish-iceberg-rest-fixture-docker.yml`（重命名 + 修改）

**修改目的**：重命名工作流文件使其更具描述性，并统一镜像仓库为 `apache`。

**工作逻辑**：

- **name**：`Build and Push Docker Image` → `Build and Push 'iceberg-rest-fixture' Docker Image`。
- **env 新增**：`DOCKER_REPOSITORY: apache`。
- **Set the tagged version 步骤注释**：`for tag 'apache-iceberg-1.7.0', publish image 'apache/iceberg-rest-fixture:1.7.1'` → `for tag 'apache-iceberg-1.7.1', publish image 'apache/iceberg-rest-fixture:1.7.1'`（修复笔误，使输入 tag 与输出镜像 tag 一致）。
- **Build Docker Image 步骤**：
  ```bash
  # 修改前：
  docker build -t ${{ secrets.DOCKERHUB_USER }}/$DOCKER_IMAGE_TAG:$DOCKER_IMAGE_VERSION -f docker/iceberg-rest-adapter-image/Dockerfile .
  # 修改后：
  docker build -t $DOCKER_REPOSITORY/$DOCKER_IMAGE_TAG:$DOCKER_IMAGE_VERSION -f docker/iceberg-rest-fixture/Dockerfile .
  ```
- **Push Docker Image 步骤**：
  ```bash
  # 修改前：
  docker push ${{ secrets.DOCKERHUB_USER }}/$DOCKER_IMAGE_TAG:$DOCKER_IMAGE_VERSION
  # 修改后：
  docker push $DOCKER_REPOSITORY/$DOCKER_IMAGE_TAG:$DOCKER_IMAGE_VERSION
  ```

### `docker/iceberg-rest-adapter-image/Dockerfile` → `docker/iceberg-rest-fixture/Dockerfile`（仅重命名）

**修改目的**：目录名与镜像名统一。

**工作逻辑**：文件内容完全不变（similarity index 100%），仅路径重命名。

### `docker/iceberg-rest-adapter-image/README.md` → `docker/iceberg-rest-fixture/README.md`（重命名 + 修改）

**修改目的**：更新本地构建命令中的镜像名和 Dockerfile 路径。

**工作逻辑**：

```bash
# 修改前：
docker image rm -f apache/iceberg-rest-adapter && docker build -t apache/iceberg-rest-adapter -f docker/iceberg-rest-adapter-image/Dockerfile .
# 修改后：
docker image rm -f apache/iceberg-rest-fixture && docker build -t apache/iceberg-rest-fixture -f docker/iceberg-rest-fixture/Dockerfile .
```

## 小结

- **成效**：统一了 REST Catalog 适配器 Docker 镜像的命名——目录名、本地构建镜像名、CI 发布镜像名均为 `iceberg-rest-fixture`；工作流文件名更描述性；镜像仓库固定为 `apache` 不再依赖 secret；修复了注释笔误。这让本地构建与 CI 发布产物完全一致，减少用户混淆。
- **影响范围**：2 个文件重命名（Dockerfile、README.md），1 个文件重命名+修改（workflow yml），共 3 个文件、6 行新增、5 行删除。
- **回迁到 1.4.x 的注意事项**：这是 #1442 + #1447 的后续清理，属于 CI/工具基础设施改动，不影响产品运行时。1.4.x 上：
  1. 若已回迁 #1442 和 #1447，建议一并回迁本提交以保持命名一致。
  2. 若未回迁 #1442/#1447，则本提交无意义，可跳过。
  3. 回迁时注意：重命名操作在 cherry-pick 时可能需要手动处理（git rename detection），建议直接在 1.4.x 上 `git mv` 对应文件并修改内容。
  4. `DOCKER_REPOSITORY: apache` 这个变更意味着镜像一定会推送到 `apache` Docker Hub 组织下，需确保 Apache 的 Docker Hub 组织已有 `iceberg-rest-fixture` 仓库（或配置为自动创建），且 `DOCKERHUB_USER`/`DOCKERHUB_TOKEN` secret 对该仓库有写权限。
  5. 本提交与 #1447 在 workflow 文件上有叠加修改，回迁时需注意两者顺序：先 #1447 创建 `publish-docker.yml`，再 #1449 重命名为 `publish-iceberg-rest-fixture-docker.yml`。
