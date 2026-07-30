# 提交 1447：Add GitHub Action to publish the `docker-rest-fixture` container (#11632)

## 提交信息

- **序号**：1447
- **哈希**：7c7b4bac9097217f5563338dbc4b30d1559b5d76
- **短哈希**：7c7b4bac9
- **日期**：2024-11-29（Fri Nov 29 04:39:24 2024 -0500，提交时间 +0100）
- **作者**：Sung Yun <107272191+sungwy@users.noreply.github.com>
- **提交说明**：Add GitHub Action to publish the `docker-rest-fixture` container (#11632)
- **PR/Issue**：#11632

## 总体目的

提交 #1442 在 `docker/iceberg-rest-adapter-image/Dockerfile` 中引入了 REST Catalog 适配器的 Docker 镜像构建方案，但当时只能通过本地 `docker build` 手动构建。为了让社区能直接拉取官方预构建镜像（避免每个用户自行构建），本提交新增一个 GitHub Actions 工作流 `.github/workflows/publish-docker.yml`，在两种触发条件下自动构建并推送镜像到 Docker Hub：

1. **打 tag 触发**：当仓库推送形如 `apache-iceberg-X.Y.Z` 的 release tag 时，自动构建镜像并以该版本号（如 `1.7.0`）作为 tag 推送到 Docker Hub。
2. **手动触发**：通过 `workflow_dispatch` 手动触发，此时使用 `latest` 作为镜像 tag。

镜像名固定为 `iceberg-rest-fixture`（注意：与 #1442 中 `Dockerfile` 所在目录名 `iceberg-rest-adapter-image` 不同，也与 #1449 后续重命名有关），镜像仓库为 `${{ secrets.DOCKERHUB_USER }}`（即 Apache 在 Docker Hub 的用户名，通常为 `apache`）。

## 如何达成设计目的

1. **触发条件**：`on.push.tags` 匹配 `apache-iceberg-[0-9]+.[0-9]+.[0-9]+` 正则；同时支持 `workflow_dispatch` 手动触发。
2. **环境变量**：预设 `DOCKER_IMAGE_TAG=iceberg-rest-fixture`、`DOCKER_IMAGE_VERSION=latest`。
3. **构建步骤**：
   - `actions/checkout@v3` 拉取代码。
   - `actions/setup-java@v4` 配置 Zulu JDK 21（用于运行 Gradle 构建，注意：运行时容器使用 JRE 17，但构建机器用 JDK 21 以支持最新 Gradle）。
   - `./gradlew :iceberg-open-api:shadowJar` 构建 fat jar，产物位于 `open-api/build/libs/iceberg-open-api-test-fixtures-runtime-*.jar`，即 `Dockerfile` 中 `COPY` 的源文件。
   - `docker login` 用 `secrets.DOCKERHUB_USER` 和 `secrets.DOCKERHUB_TOKEN` 登录 Docker Hub。
   - **版本号提取**（仅 tag 触发时）：`echo "DOCKER_IMAGE_VERSION=`echo ${{ github.ref }} | tr -d -c 0-9.`" >> "$GITHUB_ENV"`——从 `refs/tags/apache-iceberg-1.7.0` 中删除所有非数字和非点字符，得到 `1.7.0`，覆盖默认的 `latest`。
   - `docker build -t ${{ secrets.DOCKERHUB_USER }}/iceberg-rest-fixture:$DOCKER_IMAGE_VERSION -f docker/iceberg-rest-adapter-image/Dockerfile .` 构建镜像。
   - `docker push` 推送镜像。
4. **安全**：Docker Hub 凭据通过 GitHub Secrets 注入，不暴露在代码中。

## 修改详情

### `.github/workflows/publish-docker.yml`（新增）

**修改目的**：定义自动构建并推送 `iceberg-rest-fixture` Docker 镜像的 CI 工作流。

**工作逻辑**：文件共 55 行，包含 ASF License 头。关键内容：

- **name**：`Build and Push Docker Image`
- **触发**：
  ```yaml
  on:
    push:
      tags:
        - 'apache-iceberg-[0-9]+.[0-9]+.[0-9]+'
    workflow_dispatch:
  ```
- **环境变量**：
  ```yaml
  env:
    DOCKER_IMAGE_TAG: iceberg-rest-fixture
    DOCKER_IMAGE_VERSION: latest
  ```
- **作业**：`build`，运行于 `ubuntu-latest`。
- **步骤**：
  1. `uses: actions/checkout@v3`
  2. `uses: actions/setup-java@v4`，distribution=zulu，java-version=21。
  3. `./gradlew :iceberg-open-api:shadowJar`。
  4. `docker login -u ${{ secrets.DOCKERHUB_USER }} -p ${{ secrets.DOCKERHUB_TOKEN }}`。
  5. **Set the tagged version**（条件：`github.event_name == 'push' && contains(github.ref, 'refs/tags/')`）：从 `github.ref` 中提取版本号写入 `DOCKER_IMAGE_VERSION`。注释举例："for tag 'apache-iceberg-1.7.0', publish image 'apache/iceberg-rest-fixture:1.7.1'"（注：注释中的 `1.7.1` 与 `1.7.0` 不一致，应为笔误，实际逻辑会提取出 `1.7.0`）。
  6. `docker build -t ${{ secrets.DOCKERHUB_USER }}/$DOCKER_IMAGE_TAG:$DOCKER_IMAGE_VERSION -f docker/iceberg-rest-adapter-image/Dockerfile .`。
  7. `docker push ${{ secrets.DOCKERHUB_USER }}/$DOCKER_IMAGE_TAG:$DOCKER_IMAGE_VERSION`。

## 小结

- **成效**：为 #1442 引入的 REST Catalog 适配器 Docker 镜像建立了官方 CI 发布流水线，release tag 时自动构建并以版本号推送到 Docker Hub，手动触发时推送 `latest` tag。社区用户可直接 `docker pull apache/iceberg-rest-fixture:<version>` 使用，无需本地构建。
- **影响范围**：仅新增 `.github/workflows/publish-docker.yml` 一个文件、55 行。无代码、构建或文档逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是 CI 基础设施改动，与产品运行时无关。1.4.x 作为维护分支：
  1. **一般无需回迁**——CI 工作流由 main 分支统一维护并作用于整个仓库的 tag 推送。即使 1.4.x 上没有这个 workflow 文件，只要 main 上有，打 `apache-iceberg-1.4.x` tag 时仍会触发该 workflow（GitHub Actions 工作流在 tag 推送时使用 tag 所在分支的 workflow 文件版本，但通常 release tag 是在 main 上打的）。
  2. 若 1.4.x 需要独立打 tag 并发布 docker 镜像，需确保 1.4.x 分支上同时存在 #1442 的 `Dockerfile` 和本 workflow 文件，且 `Dockerfile` 中 `COPY` 的 jar 路径与 1.4.x 的 Gradle 输出一致。
  3. 需注意 Docker Hub 凭据（`secrets.DOCKERHUB_USER`、`secrets.DOCKERHUB_TOKEN`）需在 Apache 的 GitHub 组织 Secrets 中预先配置好，否则 login 步骤会失败。
  4. 本提交中镜像名为 `iceberg-rest-fixture`，与 #1442 中目录名 `iceberg-rest-adapter-image` 不一致，后续 #1449 会清理这个命名不一致问题。回迁时需注意与 #1449 的协调。
