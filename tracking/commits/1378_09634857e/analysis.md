# 提交 1378：Support WASB scheme in ADLSFileIO (#11504)

## 提交信息

- **序号**：1378 / 4088
- **哈希**：09634857e4a1333f5dc742d1dca3921e9a9f62dd
- **短哈希**：09634857e
- **日期**：2024-11-14（Thu Nov 14 08:38:04 2024 -0600）
- **作者**：Marc Cenac <547446+mrcnc@users.noreply.github.com>
- **提交说明**：Support WASB scheme in ADLSFileIO (#11504)
- **PR/Issue**：#11504

## 总体目的

Azure Blob Storage 历史上使用 `wasb://` / `wasbs://` 这两种 URI scheme（WebHDFS-style 的 Azure Blob 访问协议）来寻址对象，常见于旧版 Hadoop/HDInsight 部署。而 Azure Data Lake Storage Gen2（ADLS Gen2）使用 `abfs://` / `abfss://` scheme 与 dfs 端点（`account.dfs.core.windows.net`）。

Iceberg 的 `ADLSFileIO` 此前只识别 `abfs`/`abfss` scheme，遇到 `wasb`/`wasbs` URI 时无法路由到 ADLSFileIO（`ResolvingFileIO` 会回退到 Hadoop FileIO 或直接失败）。这对从旧版 HDInsight/WASB 存储迁移到 Iceberg 的用户是障碍——他们的表 location 仍是 `wasb://...` 形式。

本提交让 `ADLSFileIO` 同时接受 `wasb`/`wasbs` scheme：在 `ResolvingFileIO` 的 scheme 路由表中注册 wasb/wasbs → ADLSFileIO；在 `ADLSLocation` 的 URI 正则中匹配 wasb/wasbs；并重构 storageAccount 的解析逻辑，使其从 URI host 中只提取账户名（而非完整 host），再由 `AzureProperties` 显式拼出 dfs 端点。这样无论输入 URI 用的是 abfs（dfs host）还是 wasb（blob host），最终都通过 ADLS Gen2 的 dfs REST API 访问，保证功能一致。

## 如何达成设计目的

核心思路：**解耦"账户名"与"端点 host"**。WASB URI 的 host 是 `account.blob.core.windows.net`（blob 端点），ABFS URI 的 host 是 `account.dfs.core.windows.net`（dfs 端点）。如果直接用完整 host 作端点，WASB 会指向 blob 端点，而 ADLSFileIO 使用的是 Data Lake（dfs）REST API，二者不匹配。因此改为：

1. **ADLSLocation 解析时只取账户名**：把 host 按 `.` 分割取第一段作为 `storageAccount`（例如 `account.blob.core.windows.net` → `account`，`account.dfs.core.windows.net` → `account`）。
2. **AzureProperties 显式构造 dfs 端点**：当没有显式 connection-string 时，端点固定拼为 `https://{account}.dfs.core.windows.net`，确保无论原 URI 是 wasb 还是 abfs，都走 dfs 端点。

配合 scheme 路由注册与正则扩展，即实现 WASB URI 的透明支持。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/ResolvingFileIO.java`

修改目的：将 wasb/wasbs scheme 路由到 ADLSFileIO。

工作逻辑：在 scheme → FileIO 实现的静态映射表中新增两行：
```
"wasb", ADLS_FILE_IO_IMPL,
"wasbs", ADLS_FILE_IO_IMPL,
```
这样 `ResolvingFileIO` 在根据 URI scheme 选择 delegate 时，遇到 `wasb://` 或 `wasbs://` 会实例化 `ADLSFileIO`，与 `abfs`/`abfss` 一致。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSLocation.java`

修改目的：让 ADLSLocation 解析 wasb/wasbs URI，并改为只提取账户名。

工作逻辑：
- **正则扩展**：`URI_PATTERN` 从 `^abfss?://([^/?#]+)(.*)?$` 改为 `^(abfss?|wasbs?)://([^/?#]+)(.*)?$`。新增 `wasbs?` 分支匹配 wasb/wasbs。由于在 scheme 外加了一对捕获括号，原 group(1) authority 变为 group(2)，原 group(2) path 变为 group(3)，代码中 `matcher.group(1)` → `matcher.group(2)`、`matcher.group(2)` → `matcher.group(3)` 同步调整。
- **storageAccount 提取逻辑**：原代码把 authority 中 `@` 后的部分整体作为 `storageAccount`（如 `account.dfs.core.windows.net`）。改为取 host 按 `.` 分割后的第一段：`host.split("\\.", -1)[0]`（如 `account`）。无 `@` 的 authority（直接是 host）也同样取第一段。这样 storageAccount 始终是纯账户名。
- **Javadoc 更新**：补充 wasb/wasbs URI 形式说明，并注明 wasb scheme 出于兼容性接受，但底层仍使用 ADLS Gen2（dfs）REST API 而非 Blob Storage REST API。

### `azure/src/main/java/org/apache/iceberg/azure/AzureProperties.java`

修改目的：显式构造 dfs 端点，适配新的 storageAccount 语义。

工作逻辑：
- 为 `applyClientConfiguration` 补充 Javadoc，说明默认端点形式为 `https://{account}.dfs.core.windows.net`，默认凭据用 `DefaultAzureCredential`。
- **端点构造改动**：当既无 SAS token 也无 connection-string 时，原 `builder.endpoint("https://" + account)` 改为 `builder.endpoint("https://" + account + ".dfs.core.windows.net")`。由于此时 `account` 是纯账户名（不再是完整 host），必须显式拼上 `.dfs.core.windows.net` 才能得到合法 dfs 端点。SAS token 分支与 connection-string 分支不受影响（前者拼 SAS 后缀，后者直接用用户提供的 connection-string 作端点）。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/ADLSLocationTest.java`

修改目的：适配 storageAccount 新语义并覆盖 wasb 解析。

工作逻辑：
- 原 abfs 各测试用例的 `assertThat(location.storageAccount()).isEqualTo("account.dfs.core.windows.net")` 全部改为 `.isEqualTo("account")`（5 处）。
- 新增 `testWasbLocatonParsing` 参数化测试，用 `@ValueSource(strings = {"wasb", "wasbs"})` 覆盖两种 wasb scheme，验证 `wasb://container@account.blob.core.windows.net/path/to/file` 解析后 storageAccount=`account`、container=`container`、path=`path/to/file`。
- URL 编码路径用例、无 container 用例、无 path 用例的 storageAccount 断言同步改为纯账户名。

### `azure/src/test/java/org/apache/iceberg/azure/AzurePropertiesTest.java`

修改目的：适配端点构造改动。

工作逻辑：
- `testWithConnectionString`：connection-string 测试值从 `http://endpoint` 改为 `https://account1.dfs.core.usgovcloudapi.net`（用 US Gov 云的真实 dfs 端点形式），验证 connection-string 被原样用作 endpoint。
- `testWithSasToken`：endpoint 断言从 `https://account1` 改为 `https://account1.dfs.core.windows.net`。
- `testWithUserDelegationSas`：endpoint 断言从 `https://account` 改为 `https://account.dfs.core.windows.net`。

## 小结

- 成效：`ADLSFileIO` 现可透明处理 `wasb://` / `wasbs://` URI，方便从旧版 HDInsight/WASB 存储迁移到 Iceberg 的用户无需改写表 location 即可读写；同时重构了 storageAccount 与端点构造逻辑，使账户名与端点 host 解耦，端点显式拼为 dfs 形式，不再依赖 Azure SDK 对裸账户名的默认后缀推断，行为更确定。
- 影响范围：`ResolvingFileIO`（scheme 路由表）、`ADLSLocation`（URI 解析）、`AzureProperties`（端点构造）三个生产类，加 2 个测试类。**行为变化需注意**：storageAccount 字段语义从"完整 host"变为"纯账户名"——若有外部代码直接读取 `ADLSLocation.storageAccount()` 并依赖其返回完整 host，会受影响（但该字段包级可见 `String storageAccount()`，外部直接依赖的概率低）。端点构造从 `https://{account}`（依赖 SDK 默认后缀）变为 `https://{account}.dfs.core.windows.net`（显式），对原本用裸账户名作 storageAccount 的用户，端点会变得更明确（可能修复了之前在某些 Azure 云环境下 SDK 默认后缀不正确的问题）。
- 回迁到 1.4.x 的注意事项：**建议回迁**（若 1.4.x 包含 azure 模块且该模块在此区域与 main 结构一致）。理由：
  1. 这是面向用户的功能改进（支持 wasb scheme）+ 行为修复（显式 dfs 端点），对从 WASB 迁移的用户价值明确。
  2. 改动局部、清晰，配套测试充分（含 wasb 解析、端点构造、connection-string、SAS 等场景）。
  3. 风险点：storageAccount 语义变化与端点构造变化。回迁前需确认 1.4.x 的 azure 模块中 `ADLSLocation.storageAccount()` 的消费方（如 `ADLSFileIO` 内部如何使用该字段）是否会因从完整 host 变为账户名而出问题。从 diff 看，`applyClientConfiguration` 是 storageAccount 的主要消费方，而它已同步改为拼 `.dfs.core.windows.net`，因此内部一致。但应检查 1.4.x 是否有其它地方（如 SAS token 拼接、connection-string 查找）以 storageAccount 为 key，这些地方原来用完整 host 作 key、现在用账户名作 key，可能需要用户配置的 key 也相应调整（例如 `adls.connection-string.<account>` 之前可能要写完整 host，现在写账户名）。
  4. 若 1.4.x 已有用户用 `adls.connection-string.<full-host>` 形式配置，回迁后需在 release notes 中提示配置 key 改为账户名形式，避免连接字符串找不到。建议回迁时同时跑 `AzurePropertiesTest` 与 `ADLSLocationTest` 验证。
