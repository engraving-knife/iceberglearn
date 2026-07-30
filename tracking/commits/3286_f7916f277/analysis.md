# 提交 3286：Docker, Docs, Site: Add Flink quickstart (#15062)

## 提交信息

- **序号**：3286 / 4088
- **哈希**：f7916f2778866cff748a6c6a4118dde50c03181e
- **短哈希**：f7916f277
- **日期**：2026-02-19
- **作者**：Robin Moffatt
- **提交说明**：Docker, Docs, Site: Add Flink quickstart (#15062)
- **PR/Issue**：#15062

## 总体目的

本提交为 Apache Iceberg 项目补齐了 Flink 引擎的"快速上手"（quickstart）文档与配套环境，使新用户可以像 Spark/Hive quickstart 那样，通过 Docker Compose 一键拉起一套完整的 Flink + Iceberg 体验环境。

此前仓库内已经存在 `docker/iceberg-flink-quickstart/` 镜像与 compose 文件，以及 `site/docs/spark-quickstart.md`、`hive-quickstart.md` 等快速上手文档，但 Flink 一直缺少对应的站点文档，导致 Flink 用户只能查阅 `docs/docs/flink.md` 的相对偏 API/配置的说明，缺少一条端到端的"装环境 → 建表 → 写数据 → 读数据"入门路径。本提交通过新增 `site/docs/flink-quickstart.md` 填补这一空白，并把站点导航（`site/nav.yml`、`site/mkdocs-dev.yml`）与 `docs/docs/flink.md` 都接入了该页面，同时在 `docker/iceberg-flink-quickstart/` 目录中补上一个可执行的 `test.sql` 脚本，用于校验镜像本身是否正常工作。

此外，作者还对 Docker Compose 配置做了简化：移除了 jobmanager、iceberg-rest、minio 三个服务的对外端口映射（`8081`、`8181`、`9000/9001`），目的是让这套环境只通过 Flink SQL Client（`docker exec` 进入 jobmanager 容器）来访问，避免端口冲突并降低用户在本机同时运行多个 quickstart 时的干扰，符合 quickstart 以"内部网络通信为主、最小暴露面"的设计理念。

## 如何达成设计目的

整体思路是"文档 + 可执行脚本 + 导航接入"三位一体：新增 `site/docs/flink-quickstart.md` 作为面向用户的入门长文，内嵌完整的 SQL 示例（建 catalog、建库建表、开启 checkpoint、写数据、读数据、内联配置建表等）；新增 `docker/iceberg-flink-quickstart/test.sql` 作为镜像自检脚本，与文档示例保持一致；调整 `docker/iceberg-flink-quickstart/README.md` 指向新文档并描述测试脚本的预期行为；最后通过 `site/nav.yml` 与 `site/mkdocs-dev.yml` 把新页面挂到 Quickstart 导航下，并在 `docs/docs/flink.md` 顶部加入指向 quickstart 的提示块。涉及的目录包括 `docker/iceberg-flink-quickstart/`、`docs/docs/`、`site/docs/` 与 `site/` 配置文件。

## 修改详情

### `docker/iceberg-flink-quickstart/README.md` (+11/-6 lines)

**修改目的**：将 README 的"Usage"部分改为指向新的 quickstart 文档，并补充 `test.sql` 测试脚本的使用说明。

**工作逻辑**：
原 README 直接给出 `docker compose up` 与 `docker exec sql-client.sh` 的命令，本次改动把详细使用方式收敛到站点文档，README 仅保留启动容器与执行测试脚本的命令：`docker exec -i jobmanager ./bin/sql-client.sh < docker/iceberg-flink-quickstart/test.sql`。同时明确列出了测试脚本的预期行为（退出码 0、创建 1 catalog/1 database/1 table、插入 4 条记录、最终 `iceberg_catalog.nyc.taxis` 含 4 行），方便后续维护者把该脚本作为镜像的回归测试用例。此外在顶部与 Usage 部分都加了指向 `https://iceberg.apache.org/flink-quickstart/` 的链接。

### `docker/iceberg-flink-quickstart/docker-compose.yml` (+0/-7 lines)

**修改目的**：移除三个服务对外暴露的端口映射，简化 quickstart 环境的端口占用。

**工作逻辑**：
被移除的是 `jobmanager` 的 `8081:8081`（Flink Web UI）、`iceberg-rest` 的 `8181:8181`（REST Catalog）、`minio` 的 `9000:9000` 和 `9001:9001`（S3 API 与控制台）。这些端口在容器网络 `iceberg_net` 内部仍可通过服务名访问（如 `http://iceberg-rest:8181`、`http://minio:9000`），所以 Flink SQL Client 与各容器之间的通信不受影响，但用户无法再从宿主机直接访问 Flink Web UI 或 MinIO 控制台，从而减少了与本地其他 quickstart（如 Spark quickstart 同样使用 8081/8181/9000 端口）的冲突。

### `docker/iceberg-flink-quickstart/test.sql` (+78/-0 lines)

**修改目的**：新增一个端到端的 Flink SQL 测试脚本，用于验证 Iceberg-Flink 集成是否正常工作。

**工作逻辑**：
脚本按 7 个步骤组织：①创建 REST Catalog `iceberg_catalog`（指向 `iceberg-rest:8181`，仓库 `s3://warehouse/`，S3FileIO 走 MinIO 端点，path-style 访问）；②在 catalog 下建库 `nyc` 并建表 `taxis`（含 vendor_id/trip_id/trip_distance/fare_amount/store_and_fwd_flag 五列）；③设置 checkpoint 间隔为 10 秒（Iceberg 提交依赖 Flink checkpoint，这是文档反复强调的关键点）；④插入 4 条出租车记录；⑤以 tableau 模式查询全表；⑥通过 Iceberg 元数据表 `$snapshots`、`$files`、`$history` 检视快照、数据文件与历史；⑦可选清理（DROP TABLE / DROP DATABASE）。脚本内容与站点 quickstart 文档保持一致，既可作回归测试也可作教学示例。

### `docs/docs/flink.md` (+3/-2 lines)

**修改目的**：在 Flink 主文档顶部加入指向 quickstart 的提示块，并简化标题。

**工作逻辑**：
将 frontmatter 中的 `title` 从 `"Flink Getting Started"` 改为 `"Getting Started"`，避免与新的 quickstart 页面标题语义重复；同时把正文开头的 `# Flink` 标题替换为一个 `!!!tip` 提示块，引导用户先看 `/flink-quickstart`。这样既保留了 flink.md 作为"深入文档"入口的定位，又把入门流量导向新页面。

### `site/docs/flink-quickstart.md` (+174/-0 lines)

**修改目的**：新增面向终端用户的 Flink + Iceberg 快速上手长文档。

**工作逻辑**：
文档结构覆盖完整上手路径：先介绍 quickstart 环境的组成（Flink JobManager/TaskManager、Iceberg REST Catalog、MinIO），并配以架构图 `flink-quickstart.excalidraw.png`；接着给出 `git clone` + `docker compose up -d --build` + `docker exec -it jobmanager ./bin/sql-client.sh` 的启动流程；随后分小节演示 `CREATE CATALOG`（REST + S3 + MinIO）、`CREATE DATABASE`、`CREATE TABLE`、`SET 'execution.checkpointing.interval'='10s'`、`INSERT INTO ... VALUES`、`SELECT *`，并强调 checkpoint 对 Iceberg 提交的必要性；最后展示"内联配置建表"（不显式 `CREATE CATALOG`，而是在 `CREATE TABLE ... WITH` 中直接写 connector/catalog-type/uri/warehouse/io-impl/s3.* 等属性，注册到 Flink 默认内存 catalog 但仍落到同一个 REST Catalog + S3）。文末给出关闭环境的 `docker compose down` 与下一步学习链接。文档风格与现有 spark-quickstart/hive-quickstart 保持一致。

### `site/docs/assets/images/flink-quickstart.excalidraw.png` (Bin 0 -> 333949 bytes)

**修改目的**：为 quickstart 文档提供一张架构示意图，展示各容器之间的关系。

**工作逻辑**：
新增的二进制图片资源（Excalidraw 导出的 PNG），被 `site/docs/flink-quickstart.md` 通过 `/assets/images/flink-quickstart.excalidraw.png` 引用，用于直观呈现 Flink 集群、REST Catalog、MinIO 三者的交互关系。

### `site/mkdocs-dev.yml` (+1/-0 lines)

**修改目的**：在开发用 mkdocs 配置的导航中加入 Flink quickstart 页面。

**工作逻辑**：
在 `nav` 的 `Quickstart` 下、`Spark` 与 `Hive` 之间插入 `- Flink: flink-quickstart.md`，使开发预览站点也能看到新页面。

### `site/nav.yml` (+1/-0 lines)

**修改目的**：在生产站点导航中加入 Flink quickstart 页面。

**工作逻辑**：
与 `mkdocs-dev.yml` 同样的改动，在 `Quickstart` 节点下插入 `Flink: flink-quickstart.md`，与 Spark/Hive quickstart 并列。

## 总结

本提交为 Iceberg 的 Flink 生态补齐了与 Spark/Hive 对等的快速上手体验：新增 174 行的 quickstart 文档与配套测试脚本，并完成站点导航接入与端口配置精简。既降低了新用户的入门门槛，也为镜像维护提供了可执行的回归脚本，整体提升 Flink 集成的可发现性与可用性。
