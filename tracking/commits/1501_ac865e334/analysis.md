# 提交 1501：REST: Use `apache/iceberg-rest-fixture` docker image (#11673)

## 提交信息

- **序号**：1501 / 4088
- **哈希**：ac865e334e143dfd9e33011d8cf710b46d91f1e5
- **短哈希**：ac865e334
- **日期**：2024-12-17（Tue Dec 17 13:52:36 2024 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：REST: Use `apache/iceberg-rest-fixture` docker image (#11673)
- **PR/Issue**：#11673

## 总体目的

Iceberg 提供了一个官方的 REST Catalog fixture Docker 镜像 `apache/iceberg-rest-fixture`，用于在文档示例、Kafka Connect 集成测试等场景中快速拉起一个 REST Catalog 服务端。此前仓库中两处文档/示例配置仍引用第三方镜像 `tabulario/iceberg-rest`（由 Tabulario 维护），这带来几个问题：

- 镜像来源不一致：官方仓库示例应优先使用官方镜像，避免对第三方维护者的依赖。
- `tabulario/iceberg-rest` 的默认行为与官方 fixture 可能不同（如 catalog 后端配置、是否需要显式指定 `CATALOG_URI` 等），导致示例无法直接复现。
- Kafka Connect 集成测试的 docker-compose 在使用 `tabulario/iceberg-rest` 时，需要额外配置 `CATALOG_URI` 才能让 REST 服务端用 sqlite 作为 catalog 后端（否则可能默认走内存或其它后端，行为不稳定）。

本提交将两处 `tabulario/iceberg-rest` 替换为 `apache/iceberg-rest-fixture`，并在 Kafka Connect 的 docker-compose 中补充 `CATALOG_URI=jdbc:sqlite:file:/tmp/iceberg_rest_mode=memory` 环境变量，使 fixture 以内存模式 sqlite 作为 catalog 后端，避免不同 JDBC 连接看到不一致的 catalog 状态（sqlite 内存库对每个连接独立可见的问题在提交 1498 的注释中也有提及）。

## 如何达成设计目的

1. 修改 `kafka-connect/kafka-connect-runtime/docker/docker-compose.yml`：把 `iceberg` 服务的 `image` 从 `tabulario/iceberg-rest` 改为 `apache/iceberg-rest-fixture`，并在 `environment` 中新增 `CATALOG_URI=jdbc:sqlite:file:/tmp/iceberg_rest_mode=memory`。
2. 修改 `site/docs/spark-quickstart.md`：把示例 docker-compose 中 `rest` 服务的 `image` 从 `tabulario/iceberg-rest` 改为 `apache/iceberg-rest-fixture`。

## 修改详情

### `kafka-connect/kafka-connect-runtime/docker/docker-compose.yml`

**修改目的**：让 Kafka Connect 集成测试使用官方 REST fixture 镜像，并配置 sqlite 内存模式 catalog 后端。

**工作逻辑**：

```yaml
   iceberg:
-    image: tabulario/iceberg-rest
+    image: apache/iceberg-rest-fixture
     depends_on:
       - create-bucket
     hostname: iceberg
     ports:
       - 8181:8181
     environment:
       - AWS_REGION=us-east-1
       - CATALOG_WAREHOUSE=s3://bucket/warehouse/
+      - CATALOG_URI=jdbc:sqlite:file:/tmp/iceberg_rest_mode=memory
       - CATALOG_IO__IMPL=org.apache.iceberg.aws.s3.S3FileIO
       - CATALOG_S3_ENDPOINT=http://minio:9000
       - CATALOG_S3_PATH__STYLE__ACCESS=true
```

关键点：

- `apache/iceberg-rest-fixture` 是官方维护的镜像，与仓库 `iceberg-open-api` 模块的 REST Catalog Server fixture 行为一致。
- `CATALOG_URI=jdbc:sqlite:file:/tmp/iceberg_rest_mode=memory`：使用 sqlite 文件路径形式但 `mode=memory` 参数让它以内存数据库方式运行（实际上 sqlite 的 `file::memory:?cache=shared` 才是真正的共享内存库；这里用 `/tmp/iceberg_rest_mode=memory` 路径配合 `mode=memory` 是 fixture 的约定写法）。其目的是让 REST 服务端用 JdbcCatalog 作为 catalog 实现，后端为 sqlite，便于测试。
- 注意：`CATALOG_IO__IMPL` 用双下划线 `__` 表示属性层级 `io.impl`，这是 Iceberg 配置在环境变量中的常见转义。

### `site/docs/spark-quickstart.md`

**修改目的**：让快速入门文档示例使用官方 REST fixture 镜像。

**工作逻辑**：

```yaml
   rest:
-    image: tabulario/iceberg-rest
+    image: apache/iceberg-rest-fixture
     container_name: iceberg-rest
     networks:
       iceberg_net:
```

仅镜像名替换，其余配置不变。文档示例通常不带 `CATALOG_URI`，依赖镜像默认配置即可启动一个可用于演示的 REST Catalog（默认可能使用内存 catalog 或 sqlite）。

## 小结

- **成效**：仓库内两处 REST Catalog docker 示例统一改用官方 `apache/iceberg-rest-fixture` 镜像，消除对第三方 `tabulario/iceberg-rest` 的依赖；Kafka Connect 集成测试补充 `CATALOG_URI` 显式指定 sqlite 内存模式 catalog 后端，保证测试环境行为稳定可复现。
- **影响范围**：2 个文件，3 行新增/2 行删除。仅文档与 docker-compose 配置，无 Java 代码改动。
- **回迁到 1.4.x 的注意事项**：这是文档与测试配置改动，与产品运行时无关。**可考虑回迁**到 1.4.x，但需注意：
  - `apache/iceberg-rest-fixture` 镜像的版本与 1.4.x 时期 Iceberg 的 REST 行为应匹配。若 1.4.x 时期该官方镜像尚未发布或行为不一致（例如默认 catalog 后端、配置项名称不同），则回迁后示例可能无法直接运行。
  - Kafka Connect 模块在 1.4.x 是否已存在？若 1.4.x 已有 `kafka-connect/kafka-connect-runtime/docker/docker-compose.yml`，可同步替换；否则只回迁 `site/docs/spark-quickstart.md` 部分。
  - `CATALOG_URI` 的 sqlite 内存模式写法依赖 fixture 镜像对 `mode=memory` 的支持，回迁前应确认 1.4.x 配套镜像版本支持该参数。
  - 若 1.4.x 的 spark-quickstart.md 已有不同结构（如端口、网络名不同），需手动核对后再替换镜像名。
  - 总体而言，这是低风险文档/配置同步，回迁优先级较低，仅当 1.4.x 仍维护 Kafka Connect 测试或文档示例时才有价值。
