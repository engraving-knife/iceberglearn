# 提交分析：Catalog: Add BigQuery Metastore Catalog Support

## 提交信息

| 字段 | 值 |
|------|-----|
| 提交号 | 2118 |
| 短哈希 | `8facdafc3` |
| 完整哈希 | `8facdafc37ab586459ce7d7f57d9135cb4177765` |
| 作者 | Talat UYARER |
| 邮箱 | talat@apache.org |
| 日期 | 2025-05-13 09:44:40 2025 -0700 |
| 提交信息 | Catalog: Add BigQuery Metastore Catalog Support (#12808) |

## 总体目的

本提交为 Iceberg 添加了全新的 BigQuery Metastore Catalog 支持。这是一个新模块（`bigquery`），允许 Iceberg 使用 Google BigQuery 的元数据存储来管理 Iceberg 表的元数据。BigQuery Metastore 作为 Iceberg 的 Catalog 实现，将表元数据存储在 BigQuery 的 Dataset 和 Table 中，使用 BigQuery API 进行元数据操作。

## 设计目的的实现方式

1. **创建新模块**：在项目中新建 `bigquery` 模块，包含完整的 Catalog 实现。

2. **分层架构设计**：
   - `BigQueryMetastoreCatalog`：Catalog 主类，继承 `BaseMetastoreCatalog`，实现 `SupportsNamespaces`
   - `BigQueryMetastoreClient`：客户端接口，定义 BigQuery API 操作
   - `BigQueryMetastoreClientImpl`：客户端实现，封装 BigQuery API 调用
   - `BigQueryTableOperations`：表操作实现，管理表元数据文件的读写
   - `BigQueryMetastoreUtils`：工具类，处理标识符转换等

3. **集成到 CatalogUtil**：在 `CatalogUtil` 中注册 `bigquery` 类型，使用户可以通过 `type=bigquery` 配置使用此 Catalog。

4. **测试基础设施**：提供 `FakeBigQueryMetastoreClient` 用于测试，无需真实 BigQuery 连接。

5. **构建配置**：在 `build.gradle` 和 `settings.gradle` 中注册新模块。

## 修改详情

### 1. 新增 `BigQueryMetastoreCatalog.java`（349 行）

**文件**：`bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryMetastoreCatalog.java`

**目的**：BigQuery Metastore Catalog 的主实现类。继承 `BaseMetastoreCatalog`，实现 `SupportsNamespaces` 和 `Configurable` 接口。

**工作逻辑**：
- 初始化时创建 `BigQueryMetastoreClient`，配置项目 ID 和可选的 FileIO
- 命名空间映射到 BigQuery Dataset
- 表标识符映射到 BigQuery Table（使用 `ExternalCatalogDatasetOptions` 存储表属性）
- 实现 `createTable`、`dropTable`、`renameTable`、`tableExists` 等方法
- 实现 `createNamespace`、`dropNamespace`、`listNamespaces`、`loadNamespaceMetadata`、`setNamespaceProperties` 等命名空间管理方法
- 表元数据位置存储在 BigQuery Table 的属性中

### 2. 新增 `BigQueryMetastoreClient.java`（127 行）

**文件**：`bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryMetastoreClient.java`

**目的**：定义 BigQuery Metastore 客户端接口。

**工作逻辑**：定义以下操作：
- `create(Dataset)`：创建 Dataset
- `load(DatasetReference)`：加载 Dataset
- `delete(DatasetReference)`：删除 Dataset
- `setParameters(DatasetReference, Map)`：设置 Dataset 参数（仅在参数变化时更新）
- `removeParameters(DatasetReference, Set)`：移除 Dataset 参数
- `listAllDatasets(String)`：列出所有 Dataset
- `create(Table)`：创建 Table
- `load(TableReference)`：加载 Table
- `delete(TableReference)`：删除 Table
- `listAllTables(DatasetReference)`：列出 Dataset 下所有 Table
- `setTableParameters(TableReference, Map, boolean)`：设置 Table 参数
- `removeTableParameters(TableReference, Set)`：移除 Table 参数

### 3. 新增 `BigQueryMetastoreClientImpl.java`（660 行）

**文件**：`bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryMetastoreClientImpl.java`

**目的**：`BigQueryMetastoreClient` 接口的实现类，封装 Google BigQuery API 调用。

**工作逻辑**：
- 使用 `BigQuery` 客户端对象与 BigQuery 服务通信
- Dataset 操作：创建、加载、删除、列出 Dataset
- Table 操作：创建、加载、删除、列出 Table
- 参数管理：通过 `setParameters`/`removeParameters` 方法管理 Dataset 和 Table 的属性，仅在参数实际变化时执行更新以避免不必要的 API 调用

### 4. 新增 `BigQueryTableOperations.java`（259 行）

**文件**：`bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryTableOperations.java`

**目的**：实现 `TableOperations` 接口，管理 Iceberg 表的元数据文件操作。

**工作逻辑**：
- 从 BigQuery Table 属性中读取元数据指针（metadata location）
- 使用 `FileIO` 读写元数据 JSON 文件
- 提交时将新的元数据位置写入 BigQuery Table 属性
- 支持表属性的读写

### 5. 新增 `BigQueryMetastoreUtils.java`（75 行）

**文件**：`bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryMetastoreUtils.java`

**目的**：工具类，处理 Iceberg `TableIdentifier`/`Namespace` 与 BigQuery `TableReference`/`DatasetReference` 之间的转换。

### 6. 新增 `FakeBigQueryMetastoreClient.java`（254 行）

**文件**：`bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/FakeBigQueryMetastoreClient.java`

**目的**：`BigQueryMetastoreClient` 的假实现，用于测试。使用内存数据结构模拟 BigQuery API 行为。

### 7. 新增 `TestBigQueryCatalog.java`（174 行）

**文件**：`bigquery/src/test/java/org/apache/iceberg/gcp/bigquery/TestBigQueryCatalog.java`

**目的**：测试 BigQuery Catalog 的各种操作，包括表的 CRUD、命名空间管理等。

### 8. 新增 `TestBigQueryTableOperations.java`（293 行）

**文件**：`bigquery/src/test/java/org/apache/iceberg/gcp/bigquery/TestBigQueryTableOperations.java`

**目的**：测试表操作的正确性，包括元数据读写、提交等。

### 9. 修改 `CatalogUtil.java`

**文件**：`core/src/main/java/org/apache/iceberg/CatalogUtil.java`

**修改内容**：
- 新增常量 `ICEBERG_CATALOG_TYPE_BIGQUERY = "bigquery"`
- 新增常量 `ICEBERG_CATALOG_BIGQUERY = "org.apache.iceberg.gcp.bigquery.BigQueryMetastoreCatalog"`
- 在 `loadCatalog` 的 switch 语句中添加 `bigquery` 类型分支

**目的**：将 BigQuery Catalog 注册到 Iceberg 的 Catalog 工厂中，使用户可以通过 `type=bigquery` 配置使用此 Catalog。

### 10. 修改 `build.gradle`

**修改内容**：新增 `iceberg-bigquery` 项目定义，包含依赖：
- `iceberg-api`、`iceberg-common`、`iceberg-core`
- `google-cloud-bigquery`、`google-cloud-core`、`google-cloud-storage`
- 测试依赖 `iceberg-core` 和 `iceberg-api` 的测试构件

### 11. 修改 `settings.gradle`

**修改内容**：添加 `include 'bigquery'` 和 `project(':bigquery').name = 'iceberg-bigquery'`。

### 12. 修改 `.github/labeler.yml`

**修改内容**：在 GCP 标签的文件匹配模式中添加 `bigquery/**/*`。

## 总结

本提交为 Iceberg 添加了完整的 BigQuery Metastore Catalog 支持，是一个大型功能提交。核心变更包括：

1. **新模块**：创建 `bigquery` 模块，包含 Catalog 主类、客户端接口和实现、表操作、工具类、假客户端和测试
2. **分层架构**：Catalog → Client → BigQuery API 的清晰分层，客户端接口允许测试时使用假实现
3. **元数据映射**：Iceberg 命名空间映射到 BigQuery Dataset，表标识符映射到 BigQuery Table，表元数据位置存储在 Table 属性中
4. **集成注册**：在 `CatalogUtil` 中注册 `bigquery` 类型，在构建系统中注册新模块
5. **优化设计**：参数更新仅在变化时执行，避免不必要的 API 调用

共修改 12 个文件，新增 2230 行，删除 1 行。这是 Iceberg 多 Catalog 生态的重要扩展，使用户可以利用 BigQuery 作为元数据存储。
