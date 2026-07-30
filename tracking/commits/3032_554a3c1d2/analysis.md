# 提交 3032：GCP: Add service account impersonation support for BigQueryMetastoreCatalog (#14447)

## 提交信息

- **序号**：3032 / 4088
- **哈希**：554a3c1d2ad3faf1397f763c8ae9b1e69c9bb55d
- **短哈希**：554a3c1d2
- **日期**：2025-12-19
- **作者**：Joy Haldar
- **提交说明**：GCP: Add service account impersonation support for BigQueryMetastoreCatalog (#14447)
- **PR/Issue**：#14447

## 总体目的

本提交为 Iceberg 的 GCP BigQuery Metastore Catalog 和 GCS 存储层添加了服务账号模拟（service account impersonation）支持。在 GCP 企业环境中，出于安全与最小权限原则，应用程序通常不直接使用拥有全部权限的服务账号，而是以一个低权限的源身份运行，再通过模拟（impersonate）一个具有特定权限的目标服务账号来访问资源。这种模式可以实现权限的临时授予、审计追踪和跨项目访问控制。

在此提交之前，`BigQueryMetastoreCatalog` 只能使用 GCP 的应用默认凭证（Application Default Credentials, ADC）来创建 BigQuery 客户端，`GCPProperties` 和 `PrefixedStorage`（GCS 文件系统）也只支持 OAuth2 token 和无认证两种模式，缺乏模拟其他服务账号的能力。这意味着需要模拟场景的企业用户无法直接使用 Iceberg 的 BigQuery Catalog 和 GCS 存储。

本提交在两个层面引入了模拟支持：

1. **BigQuery Metastore Catalog 层面**：新增 `BigQueryProperties` 类集中管理 BigQuery 相关配置（包括原有的 project-id、location、list-all-tables 和新增的模拟属性），在构建 `BigQueryOptions` 时根据是否配置了模拟服务账号来选择构建 `ImpersonatedCredentials` 或应用默认凭证。
2. **GCS 存储层面**：在 `GCPProperties` 中新增模拟相关属性，在 `PrefixedStorage.getCredentials()` 中新增模拟分支，构建 `ImpersonatedCredentials` 用于 GCS 访问。

同时，本提交将 `BigQueryMetastoreCatalog` 中原有的属性常量（`PROJECT_ID`、`GCP_LOCATION`、`LIST_ALL_TABLES`）标记为 `@Deprecated`（自 1.11.0 起，1.12.0 移除），引导用户迁移到 `BigQueryProperties` 中的同名常量，并重构了 catalog 初始化逻辑使用 `BigQueryProperties`。

## 如何达成设计目的

改动分布在 `bigquery` 和 `gcp` 两个模块共 9 个文件。核心设计是：新增 `BigQueryProperties` 类封装 BigQuery 配置解析与凭证构建逻辑（包含模拟支持）；在 `GCPProperties` 中新增 GCS 模拟属性与解析逻辑；在 `PrefixedStorage` 中新增模拟凭证构建分支；将 `BigQueryMetastoreCatalog` 的属性常量标记为废弃并改为委托 `BigQueryProperties`；修改 `BigQueryMetastoreClientImpl` 使用 `BigQueryOptions` 中已配置的凭证而非自行创建。测试侧新增 `TestBigQueryProperties` 和扩展 `TestPrefixedStorage` 验证属性解析与 scope 展开逻辑。

## 修改详情

### `bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryProperties.java` (+197/-0 lines)

**修改目的**：新增 BigQuery 配置类，集中管理属性并支持服务账号模拟。

**工作逻辑**：
这是全新文件，定义了 `BigQueryProperties` 类（`Serializable`），包含：

- **原有属性常量**：`PROJECT_ID = "gcp.bigquery.project-id"`、`GCP_LOCATION = "gcp.bigquery.location"`、`LIST_ALL_TABLES = "gcp.bigquery.list-all-tables"`（从 `BigQueryMetastoreCatalog` 迁移而来）。
- **模拟属性常量**：`IMPERSONATE_SERVICE_ACCOUNT`、`IMPERSONATE_LIFETIME_SECONDS`、`IMPERSONATE_SCOPES`、`IMPERSONATE_DELEGATES`，前缀为 `gcp.bigquery.impersonate.*`。
- **默认值**：`DEFAULT_GCP_LOCATION = "us"`、`DEFAULT_LIFETIME_SECONDS = 3600`、`DEFAULT_SCOPES = ["https://www.googleapis.com/auth/cloud-platform"]`。
- **辅助方法**：`parseCommaSeparatedList(input, defaultValue)` 将逗号分隔字符串解析为去重列表，空值返回默认值；`expandScopes(inputScopes)` 将简写 scope（如 `"bigquery"`）展开为完整 URL（`"https://www.googleapis.com/auth/bigquery"`），已有的 http/https URL 保持/转换为 https。
- **构造函数**：从 properties 解析所有字段，`projectId` 非空校验，模拟属性可选。配置模拟时记录 INFO 日志。
- **凭证构建**：`metastoreOptions()` 构建 `BigQueryOptions`，若配置了模拟服务账号则调用 `buildImpersonatedCredentials()`（使用 `GoogleCredentials.getApplicationDefault()` 作为源凭证，`ImpersonatedCredentials.create()` 创建模拟凭证，并立即 `refresh()` 获取初始 token 验证有效性），否则调用 `buildApplicationDefaultCredentials()`（ADC + `BigqueryScopes.all()` scope）。

### `bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryMetastoreCatalog.java` (+23/-25 lines)

**修改目的**：重构 catalog 初始化，委托 `BigQueryProperties` 并废弃旧常量。

**工作逻辑**：
1. 将 `PROJECT_ID`、`GCP_LOCATION`、`LIST_ALL_TABLES` 三个常量标记为 `@Deprecated`（since 1.11.0, will be removed in 1.12.0），Javadoc 指向 `BigQueryProperties` 中的替代。
2. 移除 `DEFAULT_GCP_LOCATION` 常量（已迁移到 `BigQueryProperties`）。
3. `initialize()` 方法中移除了手动解析 `projectId`、`projectLocation`、`listAllTables` 以及手动构建 `BigQueryOptions` 的代码，改为 `BigQueryProperties bigQueryProperties = new BigQueryProperties(properties);`，然后 `this.projectId = bigQueryProperties.projectId();`、`this.projectLocation = bigQueryProperties.location();`、`this.listAllTables = bigQueryProperties.listAllTables();`，并通过 `bigQueryProperties.metastoreOptions()` 获取已配置凭证的 `BigQueryOptions` 传给 client。移除了移除对 `ServiceOptions` 的 import。
4. 移除了末尾 `this.listAllTables = Boolean.parseBoolean(...)` 的手动解析。

### `bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryMetastoreClientImpl.java` (+4/-4 lines)

**修改目的**：改为使用 `BigQueryOptions` 中已配置的凭证。

**工作逻辑**：
原实现在构造函数中自行调用 `GoogleCredentials.getApplicationDefault().createScoped(BigqueryScopes.all())` 创建凭证。修改后改为直接从传入的 `options.getCredentials()` 获取凭证（类型转换为 `GoogleCredentials`），这样模拟凭证（已在 `BigQueryProperties.metastoreOptions()` 中构建并设置到 options）能被正确传递。移除了对 `BigqueryScopes` 的 import。

### `gcp/src/main/java/org/apache/iceberg/gcp/GCPProperties.java` (+76/-3 lines)

**修改目的**：为 GCS 存储层添加服务账号模拟属性支持。

**工作逻辑**：
1. 新增模拟属性常量：`GCS_IMPERSONATE_SERVICE_ACCOUNT`、`GCS_IMPERSONATE_LIFETIME_SECONDS`、`GCS_IMPERSONATE_DELEGATES`、`GCS_IMPERSONATE_SCOPES`，前缀为 `gcs.impersonate.*`，以及默认值常量。
2. 新增字段：`gcsImpersonateServiceAccount`、`gcsImpersonateLifetimeSeconds`、`gcsImpersonateDelegates`、`gcsImpersonateScopes`。
3. 新增辅助方法 `parseCommaSeparatedList()` 和 `expandScopes()`（逻辑与 `BigQueryProperties` 中相同，用于 GCS 场景）。
4. `loadProperties()` 方法中新增模拟属性解析逻辑。
5. 新增 getter 方法：`impersonateServiceAccount()` 返回 `Optional<String>`，`impersonateLifetimeSeconds()`、`impersonateDelegates()`、`impersonateScopes()`。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/PrefixedStorage.java` (+24/-2 lines)

**修改目的**：在 GCS 凭证构建中支持模拟路径。

**工作逻辑**：
`getCredentials()` 方法中新增分支 `else if (properties.impersonateServiceAccount().isPresent()) { return buildImpersonatedCredentials(properties); }`，位于 `noAuth()` 分支之后、默认 null 分支之前。新增私有方法 `buildImpersonatedCredentials(GCPProperties)`：使用 `GoogleCredentials.getApplicationDefault()` 作为源凭证，调用 `ImpersonatedCredentials.create()` 传入目标服务账号、delegates、scopes、lifetime 创建模拟凭证，并 `refresh()` 获取初始 token。`IOException` 包装为 `UncheckedIOException`。

### `bigquery/src/test/java/org/apache/iceberg/gcp/bigquery/TestBigQueryProperties.java` (+95/-0 lines)

**修改目的**：验证 `BigQueryProperties` 的属性解析与辅助方法。

**工作逻辑**：
新增测试类，覆盖：合法属性初始化、默认 location、null properties 抛 NPE、缺少 project-id 抛 IllegalArgumentException、`expandScopes` 混合格式输入（简写、http、https）、`parseCommaSeparatedList` 正常与默认值场景。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/TestPrefixedStorage.java` (+41/-0 lines)

**修改目的**：验证 GCS 模拟属性的读取与默认值。

**工作逻辑**：
新增两个测试：`impersonationPropertiesAreRead()` 验证完整模拟配置（含 delegates、lifetime、scopes 简写展开为完整 URL）的解析；`impersonationPropertiesWithDefaults()` 验证仅设置服务账号时 delegates 为 null、lifetime 为默认 3600。

### `bigquery/src/test/java/org/apache/iceberg/gcp/bigquery/TestBigQueryCatalog.java` (+1/-1 lines)

**修改目的**：更新 import 引用迁移后的常量。

**工作逻辑**：
将 `import static BigQueryMetastoreCatalog.PROJECT_ID` 改为 `import static BigQueryProperties.PROJECT_ID`，因常量已迁移。

### `bigquery/src/test/java/org/apache/iceberg/gcp/bigquery/TestBigQueryTableOperations.java` (+1/-1 lines)

**修改目的**：更新 import 引用迁移后的常量。

**工作逻辑**：
同上，将 `PROJECT_ID` 的静态 import 从 `BigQueryMetastoreCatalog` 改为 `BigQueryProperties`。

## 总结

本提交为 Iceberg 的 GCP BigQuery Metastore Catalog 和 GCS 存储层全面引入了服务账号模拟支持，满足企业级安全场景下以低权限身份模拟目标服务账号访问资源的需求。同时通过新增 `BigQueryProperties` 类重构了 BigQuery 配置管理，将旧常量标记废弃，提升了代码组织与可维护性。两个模块的模拟逻辑独立但设计一致（相同的属性命名模式、scope 展开、凭证构建方式），并配有充分的单元测试覆盖。
