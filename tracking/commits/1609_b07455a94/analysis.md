# 提交 1609 b07455a94 分析

## 提交信息
- 哈希：b07455a940538eda5f27bd4b1b87e49b121346e4
- 日期：2025-01-20 11:13:49 -0700
- 作者：Marc Cenac
- 消息：Azure: Support WASB scheme in ADLSFileIO (#11830)

## 总体目的

本次提交为 Iceberg 的 Azure ADLS Gen2 文件 IO 实现（`ADLSFileIO`）增加了对 WASB / WASBS URI scheme 的支持，使原本只识别 `abfs` / `abfss` scheme 的 `ADLSFileIO` 也能解析并处理 `wasb://` / `wasbs://` 形式的路径。

WASB（`wasb` / `wasbs`）是 Azure Blob Storage 的传统 URI scheme（对应 `*.blob.core.windows.net` 端点），而 ABFS（`abfs` / `abfss`）是 Azure Data Lake Storage Gen2 的 URI scheme（对应 `*.dfs.core.windows.net` 端点）。许多历史遗留的 Azure 数据湖数据仍以 WASB 路径形式存在于 Hive 元数据、作业配置或外部系统中。此前若把 WASB 路径交给 Iceberg，`ADLSLocation` 会因 URI 模式不匹配而抛出 `Invalid ADLS URI` 异常，导致无法读取或迁移这些数据。

本提交的设计取舍是：接受 WASB scheme 的输入路径用于“寻址”，但实际 IO 仍走 ADLS Gen2 的 REST API（`DataLakeFileSystemClient`），而非旧版 Blob Storage API。这是一种兼容性桥接策略——让用户无需迁移路径即可用 ADLS Gen2 客户端访问数据，前提是存储账号已启用分层命名空间（HNS）。

为支持这一桥接，提交还重构了 `ADLSLocation` 中 `storageAccount` 与新增 `host` 的语义分离：原先 `storageAccount()` 返回的是完整 host（如 `account.dfs.core.windows.net`），现在 `storageAccount()` 只返回存储账号名（如 `account`），完整 host 由新方法 `host()` 提供。这一区分对同时支持 `dfs.core.windows.net` 与 `blob.core.windows.net` 两类端点是必要的。

## 如何达成设计目的

设计思路分四步：

1. **放宽 URI 解析模式**：在 `ADLSLocation` 的正则中把 scheme 部分从 `abfss?` 扩展为 `abfss?|wasbs?`，使 `wasb` / `wasbs` 路径能被解析。
2. **分离账号名与 host**：在解析 authority 时，保留完整 host（用于端点寻址与 SAS token 查找），并从中切出账号名（用于其它需要纯账号名的场景）。
3. **用 host 而非账号名做客户端配置**：在 `ADLSFileIO.client()` 中改用 `location.host()` 调用 `applyClientConfiguration`，因为 SAS token 等凭据是以完整 host 为键存储的，且 `blob` 与 `dfs` 端点的 SAS token 可能不同。
4. **在 ResolvingFileIO 中路由 WASB scheme 到 ADLSFileIO**：让 `ResolvingFileIO` 遇到 `wasb` / `wasbs` scheme 时也实例化 `ADLSFileIO`，实现 scheme 到 IO 实现的自动解析。

### 修改详情

#### azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSLocation.java

这是本次提交的核心改动。

1. **URI 正则扩展**：
   ```java
   // 旧
   private static final Pattern URI_PATTERN = Pattern.compile("^abfss?://([^/?#]+)(.*)?$");
   // 新
   private static final Pattern URI_PATTERN = Pattern.compile("^(abfss?|wasbs?)://([^/?#]+)(.*)?$");
   ```
   新增了一个捕获组用于 scheme（`abfss?|wasbs?`），因此后续 group 编号整体后移一位：authority 由 `group(1)` 变为 `group(2)`，path 由 `group(2)` 变为 `group(3)`。

2. **新增 `host` 字段并分离 `storageAccount` 语义**：解析 authority（形如 `container@account.dfs.core.windows.net` 或 `account.dfs.core.windows.net`）时：
   ```java
   if (parts.length > 1) {
     this.container = parts[0];
     this.host = parts[1];                              // 完整 host
     this.storageAccount = host.split("\\.", -1)[0];    // 账号名 = host 第一段
   } else {
     this.container = null;
     this.host = authority;                             // 完整 host
     this.storageAccount = authority.split("\\.", -1)[0];
   }
   ```
   原先 `storageAccount` 直接等于 authority（完整 host），现在 `storageAccount` 只是 `account`，完整 host 存入新字段 `host`。`split("\\.", -1)` 中的 `-1` limit 保证尾部分隔符不丢失的稳健性。

3. **新增 `host()` 方法**：返回完整 host，供 `ADLSFileIO` 做客户端配置与凭据查找使用。

4. **更新类 Javadoc**：补充了 WASB scheme 的格式说明，并明确“WASB scheme 路径会被接受但实际使用 ADLS Gen2 REST API 而非 Blob Storage API”。

#### azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSFileIO.java

将客户端配置的入参从 `storageAccount()` 改为 `host()`：
```java
// 旧
azureProperties.applyClientConfiguration(location.storageAccount(), clientBuilder);
// 新
azureProperties.applyClientConfiguration(location.host(), clientBuilder);
```
这是配套 `ADLSLocation` 语义变更的必要修改：`applyClientConfiguration` 内部用传入的键去 `adlsSasTokens` map 中查找 SAS token，而该 map 的键是完整 host（如 `account.dfs.core.windows.net`）。若继续用 `storageAccount()`（现在只返回 `account`），SAS token 查找将失败。改用 `host()` 后，无论 `dfs` 还是 `blob` 端点的 SAS token 都能正确匹配。

#### azure/src/main/java/org/apache/iceberg/azure/AzureProperties.java

仅为 `applyClientConfiguration(String account, DataLakeFileSystemClientBuilder builder)` 方法补充 Javadoc，说明参数 `account` 是“用于取值的服务账号键（如 host 或 storage account key）”，并提及默认凭据通过 `DefaultAzureCredential` 提供。无逻辑变更。该 Javadoc 也澄清了参数 `account` 实际上是一个查找键（host），而非单纯的账号名。

#### core/src/main/java/org/apache/iceberg/io/ResolvingFileIO.java

在 scheme 到 FileIO 实现的静态映射中新增 `wasb` 与 `wasbs` 指向 `ADLS_FILE_IO_IMPL`：
```java
"abfs", ADLS_FILE_IO_IMPL,
"abfss", ADLS_FILE_IO_IMPL,
"wasb", ADLS_FILE_IO_IMPL,   // 新增
"wasbs", ADLS_FILE_IO_IMPL   // 新增
```
`ResolvingFileIO` 是一个根据 URI scheme 动态委派给具体 `DelegateFileIO` 的实现，此处新增使 `wasb://` / `wasbs://` 路径自动路由到 `ADLSFileIO`，与 `ADLSLocation` 的解析能力对齐。

#### azure/src/test/java/org/apache/iceberg/azure/adlsv2/ADLSLocationTest.java

1. 更新既有断言：由于 `storageAccount()` 语义变更，所有原本断言 `storageAccount()` 等于 `account.dfs.core.windows.net` 的地方改为断言等于 `account`。
2. 新增 `testWasbLocatonParsing`（参数化 `wasb` / `wasbs`）：验证 `wasb://container@account.blob.core.windows.net/path/to/file` 能被正确解析，`storageAccount()` 为 `account`、container 为 `container`、path 为 `path/to/file`。（注：测试方法名 `testWasbLocatonParsing` 中 "Locaton" 缺少字母 `i`，应为既有拼写疏漏。）
3. 新增参数化测试 `testHost`，用 `@CsvSource` 覆盖 abfs/abfss/wasb/wasbs、带或不带 container、以及 US Gov Cloud（`account.dfs.core.usgovcloudapi.net`）等多种 host 形式，验证 `host()` 返回完整 host。

#### azure/src/test/java/org/apache/iceberg/azure/adlsv2/ADLSFileIOTest.java

新增 `testApplyClientConfigurationWithSas`：构造一个以 `account.dfs.core.windows.net` 为键配置了 SAS token 的 `AzureProperties`，调用 `io.client(location)` 后用 Mockito `verify` 确认 `applyClientConfiguration` 是以完整 host `"account.dfs.core.windows.net"`（而非账号名 `"account"`）被调用的，从而锁定“用 host 做凭据查找”这一行为契约。

## 小结

本次提交成效在于：让 `ADLSFileIO` 兼容 WASB / WASBS 路径，用户无需迁移历史 WASB 路径即可通过 ADLS Gen2 客户端访问数据；同时通过分离 `storageAccount`（账号名）与 `host`（完整 host）语义，使凭据查找能正确区分 `dfs` 与 `blob` 端点。影响范围涉及 `azure` 模块的 `ADLSLocation`、`ADLSFileIO`、`AzureProperties` 以及 `core` 模块的 `ResolvingFileIO`，并有较完整的单元测试覆盖（含 US Gov Cloud 等边界）。

需要注意的语义变更：`ADLSLocation.storageAccount()` 的返回值从“完整 host”变为“纯账号名”。经核查，在该提交时点 `storageAccount()` 的唯一生产调用方 `ADLSFileIO.client()` 已同步改用 `host()`，其余调用仅在测试中，故语义变更不引入破坏性影响。

回迁到 1.4.x 分支的注意事项：
- 该提交是一个自洽的功能改动（含生产代码与测试），回迁风险中等。
- **行为契约变更**：`storageAccount()` 语义改变为返回纯账号名。回迁前需排查 1.4.x 中是否有其它模块或外部集成依赖 `ADLSLocation.storageAccount()` 返回完整 host 的旧行为；若有，需一并调整为使用 `host()`。
- **WASB 桥接前提**：WASB 路径走 ADLS Gen2 REST API 要求底层存储账号已启用分层命名空间（HNS）。回迁后若用于未启用 HNS 的纯 Blob 账号，可能因 DFS 端点不可用而失败，需在文档/测试中明确该前提。
- 回迁后建议运行 `ADLSLocationTest`、`ADLSFileIOTest`（含 Azurite 集成测试）以及 `ResolvingFileIO` 的 scheme 路由测试，确认 wasb/wasbs 路径解析与凭据查找行为正确。
