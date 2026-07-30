# 提交 3087：infra: add gradle cache to github workflows

## 提交信息

- **序号**：3087 / 4088
- **哈希**：a90848e21018d15aa6af12528b1aeef5e3365af5
- **短哈希**：a90848e21
- **日期**：2026-01-08
- **作者**：Kevin Liu
- **提交说明**：infra: add gradle cache to github workflows
- **PR/Issue**：无

## 总体目的

该提交为三个 GitHub Actions 工作流添加了 Gradle 缓存配置，以加速 CI 构建过程。Iceberg 是一个大型多模块 Gradle 项目，包含 core、spark（多版本）、flink（多版本）、kafka-connect 等众多子模块，每次 CI 构建都需要下载大量依赖并编译大量代码。在没有缓存的情况下，每次工作流运行都会重新下载所有 Gradle 依赖和 wrapper，耗时显著。

通过引入 `actions/cache@v5` Action，将 `~/.gradle/caches`（Gradle 依赖缓存目录）和 `~/.gradle/wrapper`（Gradle wrapper 缓存目录）缓存起来，后续构建可以复用已下载的依赖和 wrapper 发行包，大幅减少网络下载时间和构建时间。缓存键（key）基于操作系统和 Gradle 构建文件的哈希值（`hashFiles('**/*.gradle*', '**/gradle-wrapper.properties')`），当构建脚本或 wrapper 配置发生变化时会生成新的缓存键，同时通过 `restore-keys` 提供部分匹配的回退策略，即使精确键未命中也能利用已有缓存。

此改动涉及三个工作流：API 二进制兼容性检查（`api-binary-compatibility.yml`，运行 `revapi` 任务）、REST fixture Docker 镜像发布（`publish-iceberg-rest-fixture-docker.yml`，构建 open-api shadowJar）、以及快照发布（`publish-snapshot.yml`，发布到 Maven 仓库）。这三个工作流都使用 Java 17 或 21 并运行 Gradle 构建，是缓存收益最明显的场景。

## 如何达成设计目的

在三个工作流文件的 Java setup 步骤之后、Gradle 构建步骤之前，统一插入 `actions/cache@v5` 步骤，配置相同的缓存路径和键策略，实现依赖缓存的跨工作流运行复用。

## 修改详情

### `.github/workflows/api-binary-compatibility.yml` (+7/-0 lines)

**修改目的**：为 API 二进制兼容性检查工作流添加 Gradle 缓存。

**工作逻辑**：
在 `setup-java`（distribution: zulu, java-version: 17）步骤之后插入 `actions/cache@v5` 步骤。缓存路径为 `~/.gradle/caches` 和 `~/.gradle/wrapper`，键为 `${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}`，回退键为 `${{ runner.os }}-gradle-`。该工作流运行 `./gradlew revapi --rerun-tasks` 进行 API 兼容性检查，缓存可加速依赖解析和编译。

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+7/-0 lines)

**修改目的**：为 REST fixture Docker 镜像发布工作流添加 Gradle 缓存。

**工作逻辑**：
在 `setup-java`（distribution: zulu, java-version: 21）步骤之后插入相同的 `actions/cache@v5` 配置。该工作流运行 `./gradlew :iceberg-open-api:shadowJar` 构建 Open API 项目并发布 Docker 镜像，缓存可加速 shadowJar 构建过程中的依赖下载。

### `.github/workflows/publish-snapshot.yml` (+7/-0 lines)

**修改目的**：为快照发布工作流添加 Gradle 缓存。

**工作逻辑**：
在 `setup-java`（distribution: zulu, java-version: 17）步骤之后插入相同的 `actions/cache@v5` 配置。该工作流运行 `./gradlew printVersion` 和 `./gradlew -DallModules publishApachePublicationToMavenRepository` 发布所有模块到 Maven 仓库，这是最耗时的构建之一，缓存收益最大。

## 总结

该提交通过在三个关键 GitHub Actions 工作流中统一引入 Gradle 依赖和 wrapper 缓存，减少了 CI 构建中重复下载依赖的时间开销。三个工作流使用完全一致的缓存配置（路径、键策略、回退键），实现标准化管理。这是一个纯基础设施优化，不改变任何构建产物或代码逻辑，但能显著提升 CI 效率和开发者反馈速度。
