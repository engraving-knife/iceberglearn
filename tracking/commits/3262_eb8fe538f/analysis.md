# 提交 3262：Add Flink Quickstart docker image (#15124)

## 提交信息

- **序号**：3262 / 4088
- **哈希**：eb8fe538f0dcec74c9db5cb3c718185241836577
- **短哈希**：eb8fe538f
- **日期**：2026-02-16
- **作者**：Robin Moffatt
- **提交说明**：Add Flink Quickstart docker image (#15124)
- **PR/Issue**：#15124

## 总体目的

Iceberg 与 Flink 的集成是 Iceberg 最常用的引擎之一，但对于新用户而言，搭建一个可运行的 Iceberg-on-Flink 环境涉及多个繁琐步骤：需要准备 Flink 集群、下载并放置正确版本的 `iceberg-flink-runtime` JAR、AWS bundle JAR（用于 S3/Glue）、Hadoop 客户端 JAR，配置对象存储与 REST Catalog，还要处理版本兼容性。这些前置工作构成了较高的上手门槛，常常让初次体验者卡在环境搭建而非 Iceberg 本身的功能探索上。

本次提交在 `docker/iceberg-flink-quickstart/` 目录下新增了一套开箱即用的 Flink 快速启动 Docker 镜像与编排文件，让用户通过一条 `docker compose up` 命令即可拉起一个完整的、可立即用 SQL 客户端操作的 Iceberg on Flink 环境。该环境包含：Flink JobManager 与 TaskManager（预装 Iceberg Flink runtime、Iceberg AWS bundle、最小化 Hadoop 客户端依赖）、Iceberg REST Catalog（使用官方 `apache/iceberg-rest-fixture` 镜像）、MinIO（作为 S3 兼容的对象存储）以及一个自动创建 `warehouse` 存储桶的初始化服务。各服务之间通过健康检查与服务依赖（`depends_on` + `condition`）保证启动顺序与就绪状态，并通过自定义网络 `iceberg_net` 互联。

镜像构建采用参数化设计：通过 `ARG` 暴露 `FLINK_VERSION`（默认 2.0）、`ICEBERG_FLINK_RUNTIME_VERSION`（默认 2.0）、`ICEBERG_VERSION`（默认 1.10.1）、`HADOOP_VERSION`（默认 3.4.2），用户可在构建时按需覆盖版本。基础镜像选用 `apache/flink:${FLINK_VERSION}-java21`，以 `flink` 用户运行，从 Maven 中央仓库下载所需 JAR 并放入 `lib/iceberg` 与 `lib/hadoop` 子目录。这套快速启动环境既方便新手快速体验 Iceberg + Flink + S3 的完整流程，也适合开发者用作本地测试与演示的基准环境，显著降低了入门与原型验证成本。

## 如何达成设计目的

新增三个文件构成完整方案：`Dockerfile` 负责构建带 Iceberg 依赖的 Flink 镜像；`docker-compose.yml` 负责编排多服务全栈（Flink 集群、REST Catalog、MinIO 存储、桶初始化），通过健康检查与依赖条件串联启动顺序，并以环境变量统一配置 AWS 凭证与 S3 端点指向 MinIO；`README.md` 提供构建参数说明、构建命令与使用方法。三者配合实现"一条命令起环境、一条命令进 SQL 客户端"的体验。

## 修改详情

### `docker/iceberg-flink-quickstart/Dockerfile` (+49 lines)

**修改目的**：构建一个预装 Iceberg Flink runtime、AWS bundle 与 Hadoop 客户端依赖的 Flink 镜像。

**工作逻辑**：
镜像以 `apache/flink:${FLINK_VERSION}-java21`（默认 Flink 2.0 + Java 21）为基础。首先在 `FROM` 之前声明可被构建时覆盖的 `ARG FLINK_VERSION=2.0`；`FROM` 之后重新声明其余 `ARG`（`ICEBERG_FLINK_RUNTIME_VERSION=2.0`、`ICEBERG_VERSION=1.10.1`、`HADOOP_VERSION=3.4.2`），因为 `ARG` 跨 `FROM` 阶段需重新声明才能在后续层使用。切换到 `flink` 用户、设定 `WORKDIR /opt/flink` 后，分两个 `RUN` 阶段下载 JAR：

1. Iceberg 依赖：`mkdir -p ./lib/iceberg` 后用 `curl -fO` 从 Maven 中央仓库下载 `iceberg-flink-runtime-${ICEBERG_FLINK_RUNTIME_VERSION}-${ICEBERG_VERSION}.jar`（Flink 运行时 JAR，注意其坐标为 `iceberg-flink-runtime-<flinkMajor>`，制品名含 Flink 大版本）与 `iceberg-aws-bundle-${ICEBERG_VERSION}.jar`（AWS bundle，提供 S3/Glue 集成）。
2. Hadoop 依赖：`mkdir -p ./lib/hadoop` 后下载 `hadoop-client-api-${HADOOP_VERSION}.jar` 与 `hadoop-client-runtime-${HADOOP_VERSION}.jar`（API 与 shaded 运行时，提供 Flink 所需的最小化 Hadoop 客户端能力）。

`pushd $_`/`popd` 用于在新建目录中下载后回到原工作目录。这样 Flink 启动时会从 `lib/` 及其子目录加载这些 JAR，使 Iceberg catalog、S3 FileIO 等功能在 SQL 客户端中立即可用。

### `docker/iceberg-flink-quickstart/docker-compose.yml` (+142 lines)

**修改目的**：编排完整的 Iceberg on Flink 全栈环境，含 Flink 集群、REST Catalog、S3 兼容存储与桶初始化。

**工作逻辑**：
定义自定义网络 `iceberg_net`，所有服务接入该网络。共五个服务，通过 `depends_on` 的 `condition`（`service_healthy`/`service_completed_successfully`）严格保证启动顺序：

- **minio**：使用 `minio/minio` 镜像，以 `admin`/`password` 为根凭证启动 S3 兼容存储，开放 9000（S3 API）与 9001（控制台）端口。通过 `aliases: warehouse.minio` 为 MinIO 在网络中注册 `warehouse.minio` 别名，支持 path-style 的 S3 路径访问。健康检查使用 `mc ready local`。
- **create-bucket**：使用 `minio/mc` 镜像，依赖 minio 健康后运行 entrypoint 脚本：循环等待 `mc alias set minio http://minio:9000 admin password` 成功，随后 `mc rm -r --force minio/warehouse` 清理旧桶、`mc mb minio/warehouse` 创建 `warehouse` 桶、`mc policy set public minio/warehouse` 设为公开。该服务以 `service_completed_successfully` 作为下游就绪条件，即桶创建完成后才继续。
- **iceberg-rest**：使用官方 `apache/iceberg-rest-fixture` 镜像，依赖桶创建完成后启动。开放 8181 端口，通过环境变量配置 REST Catalog：`CATALOG_WAREHOUSE=s3://warehouse/`（仓库路径指向 MinIO 的 warehouse 桶）、`CATALOG_IO__IMPL=org.apache.iceberg.aws.s3.S3FileIO`（使用 S3FileIO）、`CATALOG_S3_ENDPOINT`/`CATALOG_S3_ACCESS__KEY__ID`/`CATALOG_S3_SECRET__ACCESS__KEY` 指向 MinIO。注意环境变量中双下划线 `__` 是 REST fixture 将其还原为属性层级分隔符的约定（如 `IO__IMPL` → `io-impl`）。健康检查访问 `/v1/config`。
- **jobmanager**：用本目录 `Dockerfile` 构建（`build: .`），依赖 iceberg-rest 健康后启动，开放 8081（Flink Web UI）。`command: jobmanager`，通过 `FLINK_PROPERTIES` 配置 `jobmanager.rpc.address: jobmanager`、`taskmanager.numberOfTaskSlots: 2`、`parallelism.default: 2`，并注入 `AWS_REGION`/`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`S3_ENDPOINT` 环境变量使 Flink 能访问 MinIO。
- **taskmanager**：同样 `build: .`，依赖 jobmanager 健康后启动，`command: taskmanager`，`deploy.replicas: 1`，复用相同的 Flink 属性与 AWS/S3 环境变量。

这种依赖链设计确保了从存储到目录再到计算引擎的按序就绪，避免 Flink 在 REST Catalog 或存储未就绪时启动失败。

### `docker/iceberg-flink-quickstart/README.md` (+79 lines)

**修改目的**：为快速启动镜像提供构建参数说明与使用指南。

**工作逻辑**：
README 分为 Overview、Build Arguments、Building Locally、Usage 几部分。Build Arguments 以表格列出四个 `ARG`（`FLINK_VERSION`/`ICEBERG_FLINK_RUNTIME_VERSION`/`ICEBERG_VERSION`/`HADOOP_VERSION`）及其默认值与含义。Building Locally 给出默认构建命令 `docker build -t apache/iceberg-flink-quickstart docker/iceberg-flink-quickstart/` 与带 `--build-arg` 覆盖版本的自定义构建示例。Usage 给出核心用法：从仓库根目录执行 `docker compose -f docker/iceberg-flink-quickstart/docker-compose.yml up -d --build` 拉起全栈，再用 `docker exec -it jobmanager ./bin/sql-client.sh` 进入 Flink SQL 客户端开始操作，最后用 `docker compose ... down` 停止。这些指引使新用户能零门槛上手。

## 总结

本次提交新增了一套完整的 Iceberg on Flink 快速启动 Docker 环境，包含参数化构建的 Dockerfile（预装 Iceberg Flink runtime、AWS bundle、Hadoop 客户端 JAR）、编排全栈的 docker-compose.yml（Flink 集群 + REST Catalog + MinIO + 桶初始化，带健康检查与依赖排序）以及使用指南 README。用户通过一条 `docker compose up` 即可获得可立即用 SQL 客户端操作的 Iceberg + Flink + S3 环境，大幅降低了入门与本地原型验证的门槛。
