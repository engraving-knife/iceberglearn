# 提交 1481：Infra: Build Iceberg REST fixture docker image for `arm64` architecture (#11753)

## 提交信息

- **序号**：1481 / 4088
- **哈希**：fe2f593cd025223e4ab5ab41a296fb106ce3b1cf
- **短哈希**：fe2f593cd
- **日期**：2024-12-11（Wed Dec 11 21:45:52 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Infra: Build Iceberg REST fixture docker image for `arm64` architecture (#11753)
- **PR/Issue**：#11753

## 总体目的

Iceberg 在 `docker/iceberg-rest-fixture/Dockerfile` 维护了一个用于演示和测试的 REST Catalog fixture Docker 镜像，由 GitHub Actions workflow `.github/workflows/publish-iceberg-rest-fixture-docker.yml` 在打 tag 时构建并推送到 Docker Hub。这个镜像常被开发者本地拉起 Iceberg REST Catalog 做联调。

随着 Apple Silicon（M1/M2/M3 等 arm64 架构）成为开发者主流硬件，以及 AWS Graviton、Azure arm VM 等云上 arm 实例普及，越来越多的用户在 arm64 机器上运行容器。但原 workflow 只用 `docker build` 默认构建当前主机的架构——GitHub Actions runner 默认是 x86_64（amd64），所以镜像只发布了 `linux/amd64` 一个架构。

arm64 用户拉取该镜像时，Docker 会通过 QEMU 模拟运行 amd64 镜像，性能损失显著（Java 应用尤甚），且偶尔会遇到模拟器兼容性问题。本提交的目的：让 workflow 同时构建并发布 `linux/amd64` 和 `linux/arm64` 两个架构的镜像，作为 multi-arch image manifest 推送到 Docker Hub，让 arm64 用户能直接拉取原生 arm64 镜像。

## 如何达成设计目的

通过把原来的"原生 `docker build` + `docker push` 两步"改为使用 Docker 官方的 `docker/build-push-action` 这一 GitHub Action，配合 `docker/setup-qemu-action` 与 `docker/setup-buildx-action` 实现 multi-arch 构建。核心思路：

1. **QEMU 模拟器**：在 x86 runner 上安装 QEMU，让 Docker 能在 x86 上模拟 arm64 运行构建步骤（如 `apt-get install`、JRE 启动）。`docker/setup-qemu-action@v3` 是 Docker 官方提供的封装。
2. **Buildx**：启用 Docker 的 buildx 构建器，它是 multi-arch 构建的关键——能在一次构建中产出多架构镜像并以单一 manifest 推送。`docker/setup-buildx-action@v3` 是其封装。
3. **build-push-action**：用 `docker/build-push-action@v6` 替代手写 `docker build`/`docker push`，通过 `platforms: linux/amd64,linux/arm64` 参数一次性构建并推送两架构镜像。该 action 内部会处理 buildx 调用、缓存、tag 推送等细节。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml`

**修改目的**：把单架构构建改为 multi-arch 构建。

**工作逻辑**：把原来"Build Docker Image"和"Push Docker Image"两个 step 替换为三个 step + 一个 build-push step：

原代码：
```yaml
- name: Build Docker Image
  run: docker build -t $DOCKER_REPOSITORY/$DOCKER_IMAGE_TAG:$DOCKER_IMAGE_VERSION -f docker/iceberg-rest-fixture/Dockerfile .
- name: Push Docker Image
  run: |
    docker push $DOCKER_REPOSITORY/$DOCKER_IMAGE_TAG:$DOCKER_IMAGE_VERSION
```

新代码：
```yaml
- name: Set up QEMU
  uses: docker/setup-qemu-action@v3
- name: Set up Docker Buildx
  uses: docker/setup-buildx-action@v3
- name: Build and Push
  uses: docker/build-push-action@v6
  with:
    context: ./
    file: ./docker/iceberg-rest-fixture/Dockerfile
    platforms: linux/amd64,linux/arm64
    push: true
    tags: ${{ env.DOCKER_REPOSITORY }}/${{ env.DOCKER_IMAGE_TAG }}:${{ env.DOCKER_IMAGE_VERSION }}
```

要点：

1. **`docker/setup-qemu-action@v3`**：在 runner 上安装 QEMU user-space 模拟器，使 Docker 能模拟 arm64 等非本机架构运行容器。这是 multi-arch 构建在 x86 runner 上的必要前置条件。固定到 `@v3` 大版本，避免 breakage。

2. **`docker/setup-buildx-action@v3`**：创建并配置一个 Docker buildx builder 实例。buildx 是 Docker BuildKit 的扩展，支持多平台构建、高级缓存、并发生成等。这里使用默认创建的 builder 即可。

3. **`docker/build-push-action@v6`**：核心构建+推送 step。关键参数：
   - `context: ./`：构建上下文是仓库根目录（与原 `docker build ... .` 的 `.` 一致）。
   - `file: ./docker/iceberg-rest-fixture/Dockerfile`：Dockerfile 路径。
   - `platforms: linux/amd64,linux/arm64`：**multi-arch 的核心**——一次构建同时产出 amd64 和 arm64 两个架构的镜像，并以单一 multi-arch manifest 推送，用户 `docker pull` 时会自动获取匹配本机架构的版本。
   - `push: true`：构建后直接推送（原代码是 build 和 push 分两步，这里合并为一步，避免中间层冗余）。
   - `tags: ${{ env.DOCKER_REPOSITORY }}/${{ env.DOCKER_IMAGE_TAG }}:${{ env.DOCKER_IMAGE_VERSION }}`：使用 GitHub Actions 的 `${{ env.XXX }}` 语法引用前面 step 设置的环境变量（与原 `$DOCKER_REPOSITORY` shell 变量等价）。

4. **不再显式 `docker login`**：本提交 diff 只展示了改动部分；通常 multi-arch workflow 还会配合 `docker/login-action` 做认证（这部分应在文件未改动区域已存在）。

注意本提交只在 tag 触发的发布路径上改动，非 tag 的 PR/branch 触发路径（前面部分的 step）不受影响。

## 小结

- **成效**：发布到 Docker Hub 的 `iceberg-rest-fixture` 镜像现支持 `linux/amd64` 和 `linux/arm64` 双架构，arm64 用户（Apple Silicon、Graviton 等）能拉取原生 arm64 镜像，避免 QEMU 模拟的性能损失与兼容问题。同时构建从手写 `docker build`+`docker push` 升级为官方 `build-push-action`，更易维护。
- **影响范围**：仅 `.github/workflows/publish-iceberg-rest-fixture-docker.yml` 一个文件，新增 12 行、删除 5 行；不影响 Iceberg Java 代码与发布产物（除 Docker 镜像外）。
- **回迁到 1.4.x 的注意事项**：**可选回迁**。如果 1.4.x 分支也维护同样的发布 workflow 且有 arm64 用户需求，可考虑回迁。但需注意：1.4.x 的 workflow 文件结构可能与 main 不同（例如变量名、tag 触发条件、登录 step 位置），cherry-pick 后需手工核对。若 1.4.x 已停止维护或不再发布新版本，则无需回迁。基础设施改动对运行时无影响。
