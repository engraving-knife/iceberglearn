# 提交 1442：REST: Docker file for REST Catalog Fixture (#11283)

## 提交信息

- **序号**：1442
- **哈希**：163e2068f96f139632488f36928bf443c9be326f
- **短哈希**：163e2068f
- **日期**：2024-11-28（Thu Nov 28 15:14:57 2024 +0530，提交时间 +0100）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：REST: Docker file for REST Catalog Fixture (#11283)
- **PR/Issue**：#11283

## 总体目的

Iceberg 的 `iceberg-open-api` 模块下提供了 REST Catalog 测试夹具（test fixture），用于把现有的各类 Catalog 后端（如 JDBC、Hadoop、云厂商 Catalog 等）包装成 REST 接口对外提供，方便集成测试和本地调试。此前这一夹具只能通过 `./gradlew :iceberg-open-api:shadowJar` 在本地启动，缺乏容器化方案，使用门槛较高。

本提交首次为该 REST Catalog 适配器引入官方 Docker 镜像构建方案：新增 `docker/iceberg-rest-adapter-image/` 目录，包含 `Dockerfile` 和说明文档 `README.md`，让用户可以通过 `docker build` 一行命令构建出可独立运行的 REST Catalog 容器镜像。镜像基于 `azul/zulu-openjdk:17-jre-headless`，内置默认的 JDBC（SQLite 内存模式）Catalog 配置，开箱即用。

配套地，为了让镜像能支持多种云存储后端（AWS、Azure、GCP），本提交在 `build.gradle` 中将 `iceberg-aws-bundle`、`iceberg-azure-bundle`、`iceberg-gcp-bundle` 三个 bundle 以 `testFixturesRuntimeOnly` 的形式引入 `iceberg-open-api` 模块的 test fixtures 运行时类路径，从而 shadowJar 打包出来的 fat jar 自动包含这些云 SDK，使得容器化后的 REST Catalog 能够直接访问 S3、Azure Blob、GCS 等对象存储。

同时，由于新增了大量 bundle 依赖，本提交同步更新了 `open-api/LICENSE` 与 `open-api/NOTICE` 文件，列出所有新增的第三方依赖（如 AWS SDK v2 系列、commons-configuration2、hadoop-auth 等）的版权与许可信息，以满足 Apache 发布合规要求。此外新增了一份 `log4j.properties` 资源，用于夹具运行时的日志输出配置。

## 如何达成设计目的

1. **Dockerfile 设计**：基于轻量级 JRE 镜像，创建非 root 用户 `iceberg`，将 `iceberg-open-api` 模块构建出的 `iceberg-open-api-test-fixtures-runtime-*.jar` 复制为 `iceberg-rest-adapter.jar`，通过环境变量预设默认 Catalog 实现（`JdbcCatalog` + SQLite 内存数据库），暴露 8181 端口，启动命令为 `java -jar iceberg-rest-adapter.jar`。
2. **build.gradle 调整**：在 `iceberg-open-api` 的 testFixtures 配置块中，新增对三个云 bundle 的 `testFixturesRuntimeOnly` 依赖；同时调整 hadoop-common 的 exclude 列表，移除 `hadoop-auth` 和 `commons-configuration2` 的排除（因为云 bundle 需要这些类），改为排除一些不必要的传递依赖（re2j、gson、jsch、jsr305、metrics-core、dnsjava、snappy-java、commons-cli、jersey-json），并新增 slf4j-api、slf4j-simple 的 testFixturesImplementation 依赖以支持日志输出。
3. **LICENSE / NOTICE 更新**：通过自动化 license 插件重新生成，新增 commons-configuration2 2.10.1、hadoop-auth 3.3.6 以及 AWS SDK v2 2.29.6 系列组件（annotations、apache-client、arns、auth、aws-core、aws-json-protocol、aws-query-protocol、aws-xml-protocol、checksums、checksums-spi 等数十个）的许可信息，均为 Apache License 2.0。
4. **log4j.properties**：配置 root logger 为 INFO 级别，输出到 stdout，使用 PatternLayout 格式化时间、级别、类名和消息。
5. **README.md**：提供本地构建镜像的命令示例（先 `./gradlew :iceberg-open-api:shadowJar` 再 `docker build`），以及使用 `pyiceberg` 连接 `http://localhost:8181` 浏览 Catalog、列出 namespace、描述 table 的实战示例。

## 修改详情

### `build.gradle`

**修改目的**：为 `iceberg-open-api` test fixtures 添加云 bundle 运行时依赖，并调整 hadoop-common 的 exclude 列表。

**工作逻辑**：

- 移除原有对 `org.apache.hadoop:hadoop-auth` 和 `org.apache.commons:commons-configuration2` 的 exclude（因为云 bundle 需要这两个模块）。
- 新增排除：`com.google.re2j:re2j`、`com.google.code.gson:gson`、`com.jcraft:jsch`、`com.google.code.findbugs:jsr305`、`io.dropwizard.metrics:metrics-core`、`dnsjava:dnsjava`、`org.xerial.snappy:snappy-java`、`commons-cli:commons-cli`、`com.github.pjfanning:jersey-json`。
- 新增 `testFixturesImplementation libs.slf4j.api` 和 `testFixturesImplementation libs.slf4j.simple`，用于 test fixtures 自身的日志输出。
- 新增三行 `testFixturesRuntimeOnly project(':iceberg-aws-bundle')`、`testFixturesRuntimeOnly project(':iceberg-azure-bundle')`、`testFixturesRuntimeOnly project(':iceberg-gcp-bundle')`，将云 bundle 引入运行时类路径，使 shadowJar 产物可访问云存储。

### `docker/iceberg-rest-adapter-image/Dockerfile`（新增）

**修改目的**：定义 REST Catalog 适配器容器镜像的构建方式。

**工作逻辑**：

- 基础镜像：`azul/zulu-openjdk:17-jre-headless`，提供 OpenJDK 17 运行时。
- 通过 `groupadd`/`useradd` 创建 `iceberg` 用户（uid/gid=1000），后续以非 root 身份运行。
- 工作目录：`/usr/lib/iceberg-rest`。
- 通过 `COPY --chown=iceberg:iceberg` 将宿主机 `open-api/build/libs/iceberg-open-api-test-fixtures-runtime-*.jar` 复制为容器内 `/usr/lib/iceberg-rest/iceberg-rest-adapter.jar`。
- 预设环境变量：`CATALOG_CATALOG__IMPL=org.apache.iceberg.jdbc.JdbcCatalog`、`CATALOG_URI=jdbc:sqlite::memory:`、`CATALOG_JDBC_USER=user`、`CATALOG_JDBC_PASSWORD=password`、`REST_PORT=8181`。
- `EXPOSE 8181`，切换到 `iceberg` 用户，设置 `LANG=en_US.UTF-8`。
- 启动命令：`java -jar iceberg-rest-adapter.jar`。

### `docker/iceberg-rest-adapter-image/README.md`（新增）

**修改目的**：说明如何构建和使用镜像。

**工作逻辑**：包含 ASF License 头；介绍"REST Catalog Adapter Test Fixture"用途；提供构建命令（先 `./gradlew :iceberg-open-api:shadowJar`，再 `docker build -t apache/iceberg-rest-adapter -f docker/iceberg-rest-adapter-image/Dockerfile .`）；展示用 `pyiceberg` 连接 `http://localhost:8181` 列出 namespace 与表、描述表 schema 的实战示例。

### `open-api/LICENSE`

**修改目的**：随依赖变化更新第三方许可清单。

**工作逻辑**：新增 `org.apache.commons:commons-configuration2:2.10.1`、`org.apache.hadoop:hadoop-auth:3.3.6`、以及 AWS SDK v2 2.29.6 系列（annotations、apache-client、arns、auth、aws-core、aws-json-protocol、aws-query-protocol、aws-xml-protocol、checksums、checksums-spi 等数十个）的 Group/Name/Version/Project URL/License 条目，均为 Apache License 2.0。

### `open-api/NOTICE`

**修改目的**：随依赖变化更新版权声明清单。

**工作逻辑**：在每个 Apache 组件的版权声明块前重复添加"This product includes software developed at The Apache Software Foundation (https://www.apache.org/)."声明；新增"Apache Commons Configuration Copyright 2001-2024 The Apache Software Foundation"条目。

### `open-api/src/testFixtures/resources/log4j.properties`（新增）

**修改目的**：为 test fixtures 运行时提供 log4j 配置。

**工作逻辑**：root logger 级别 INFO，stdout appender 输出到 System.out，PatternLayout 格式为 `%d{yyyy-MM-dd'T'HH:mm:ss.SSS} %-5p [%c] - %m%n`。注意文件末尾缺少换行符（`No newline at end of file`）。

## 小结

- **成效**：首次为 Iceberg REST Catalog 适配器引入官方 Docker 镜像构建方案，开箱即用，默认配置 SQLite 内存 JDBC Catalog，并通过云 bundle 支持访问 AWS/Azure/GCP 对象存储；同步完善了 LICENSE/NOTICE 合规文件和日志配置。
- **影响范围**：新增 `docker/iceberg-rest-adapter-image/Dockerfile` 和 `README.md`，新增 `log4j.properties`；修改 `build.gradle` 的 testFixtures 依赖；更新 `open-api/LICENSE` 和 `open-api/NOTICE`。共 6 个文件、约 537 行新增。
- **回迁到 1.4.x 的注意事项**：本提交属于工具/基础设施类改进，本身不改变 Iceberg 核心 API 或运行时行为。1.4.x 分支若无对应的 docker 构建需求或云 bundle 集成需求，可不必回迁。如确实需要在 1.4.x 上构建 REST Catalog docker 镜像，需要同时回迁 `build.gradle` 中关于云 bundle 的依赖与 exclude 调整、`Dockerfile`、`README.md`、`log4j.properties` 以及 LICENSE/NOTICE；并验证 1.4.x 当时所用 AWS SDK、Hadoop 等版本是否与 LICENSE 中列出的版本一致（本提交中 AWS SDK 为 2.29.6、Hadoop 3.3.6、commons-configuration2 2.10.1），如不一致需要重新生成 LICENSE/NOTICE。此外需关注 1.4.x 是否已有同名 docker 目录或冲突文件。
