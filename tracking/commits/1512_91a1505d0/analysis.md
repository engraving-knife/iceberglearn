# 提交 1512 91a1505d0 分析

## 提交信息
- 哈希：91a1505d09cebcd1d088ac53cd42732c343883de
- 日期：2024-12-18（Wed Dec 18 16:55:05 2024 -0600）
- 作者：Marc Cenac <547446+mrcnc@users.noreply.github.com>
- 消息：Revert "Support WASB scheme in ADLSFileIO (#11504)" (#11812)

## 总体目的

本提交是对之前合并的 #11504（"Support WASB scheme in ADLSFileIO"）的完整回退。#11504 的目标是在 Azure Data Lake Storage (ADLS) Gen2 的 FileIO 中允许用户使用 `wasb`/`wasbs`（旧版 Blob Storage URI scheme）作为位置 URI，由 `ADLSFileIO` 兼容接收，但底层仍走 Data Lake Storage Gen2 的 REST API。

回退的根本原因可以从代码改动推断：原 PR 在 `ADLSLocation` 中将 location 的 `host` 用 `.` 切分，只取首段（`account`）作为 `storageAccount`，同时在 `AzureProperties.applyClientConfiguration` 中拼装 `https://{account}.dfs.core.windows.net` 作为 endpoint。这种"截取账户名再重新拼装默认 dfs endpoint"的实现假设了存储账户一定在公共 Azure 云（`.dfs.core.windows.net`）上，从而丢失了用户在 URI 中提供的原始 host 信息（如 sovereign cloud、`dfs.core.usgovcloudapi.net`、自定义 endpoint、emulator 等）。这破坏了用户使用非默认 host 的能力，是一个回归问题。

回退后，`ADLSLocation.storageAccount` 保留完整的 host（如 `account.dfs.core.windows.net`），`AzureProperties.applyClientConfiguration` 不再追加默认后缀，直接使用 `https://{storageAccount}` 作为 endpoint，把 host 信息原封不动地交给 Azure SDK 解析。`ResolvingFileIO` 也移除了对 `wasb`/`wasbs` scheme 的路由，回到只支持 `abfs`/`abfss` 的状态。

## 如何达成设计目的

通过 `git revert` 风格的纯反向 diff 回退 #11504 的所有改动，包括四类文件：

1. **`ADLSLocation.java`**：URI 解析逻辑回退——不再识别 `wasb`/`wasbs` scheme，不再用 `.` 切分 host 提取账户名，`storageAccount` 直接保留 authority 中 `@` 之后或整个 authority 字符串。
2. **`AzureProperties.java`**：endpoint 构造逻辑回退——不再追加 `.dfs.core.windows.net` 后缀，直接 `https://{account}`，并删除了被回退逻辑引入的 javadoc 注释。
3. **`ResolvingFileIO.java`**：scheme 路由表回退——移除 `wasb`/`wasbs` → `ADLS_FILE_IO_IMPL` 的映射。
4. **测试文件回退**：`AzurePropertiesTest` 和 `ADLSLocationTest` 中的测试用例恢复为回退前的版本，包括 `storageAccount` 期望值恢复为完整 host、移除 `testWasbLocatonParsing` 测试、连接字符串测试改回 `http://endpoint` 等。

### 修改详情

#### `azure/src/main/java/org/apache/iceberg/azure/AzureProperties.java`
- 删除了 `applyClientConfiguration` 方法上 13 行 javadoc 注释（该注释是 #11504 引入的）。
- endpoint 默认分支从 `builder.endpoint("https://" + account + ".dfs.core.windows.net")` 改回 `builder.endpoint("https://" + account)`。这样 `account` 参数实际传入的是完整 host（如 `account.dfs.core.windows.net`），由 SDK 而不是 Iceberg 来解析最终 endpoint。

#### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSLocation.java`
- URI 模式从 `^(abfss?|wasbs?)://([^/?#]+)(.*)?$` 改回 `^abfss?://([^/?#]+)(.*)?$`，只匹配 `abfs`/`abfss`。
- 类的 javadoc 中删除了 wasb scheme 的描述和 "For compatibility" 段落，恢复为单一 abfs URI 描述。
- 解析 authority 时不再用 `host.split("\\.", -1)[0]` 提取首段，而是将 `@` 之后或整个 authority 原样赋给 `storageAccount`。matcher group 索引相应从 2/3 改回 1/2。

#### `core/src/main/java/org/apache/iceberg/io/ResolvingFileIO.java`
- `SCHEME_TO_FILE_IO` 静态映射中移除 `wasb`、`wasbs` 两个键，只保留 `abfs`、`abfss` 指向 `ADLS_FILE_IO_IMPL`。意味着 `ResolvingFileIO` 不再会根据 wasb scheme 自动选择 `ADLSFileIO`。

#### `azure/src/test/java/org/apache/iceberg/azure/AzurePropertiesTest.java`
- `testWithConnectionString` 用例的连接字符串从 `https://account1.dfs.core.usgovcloudapi.net` 改回 `http://endpoint`，对应 verify 也改回 `http://endpoint`。这一改动验证了 endpoint 不再被重写——连接字符串原样作为 endpoint。
- 另两个用例中 endpoint 期望从 `https://account.dfs.core.windows.net` 改回 `https://account`（不带后缀）。

#### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/ADLSLocationTest.java`
- `testLocationParsing` 中 `storageAccount` 期望从 `"account"` 改回 `"account.dfs.core.windows.net"`。
- 删除 `testWasbLocatonParsing` 整个用例（不再支持 wasb scheme）。
- URL 编码 path、无 container、无 path 三个用例的 `storageAccount` 期望同样从 `"account"` 改回完整 host。

## 小结

- **成效**：修复了 #11504 引入的 host 信息丢失回归——回退后 `ADLSLocation` 保留用户提供的完整 host，`ADLSFileIO` 不再硬编码 `.dfs.core.windows.net` 后缀，使 sovereign cloud、自定义 host、emulator 等场景重新可用。代价是再次失去对 `wasb`/`wasbs` scheme 的兼容支持。
- **影响范围**：azure 模块（`AzureProperties`、`ADLSLocation` 及对应测试）+ core 模块（`ResolvingFileIO` 的 scheme 路由表）。共 5 个文件，+16/-50 行。
- **回退 vs. 修复的选择**：维护者选择直接 revert 而不是在原 PR 基础上修补，可能是因为 #11504 的设计本身就与保留 host 信息冲突（它假设 host 一定是 `<account>.dfs.core.windows.net` 形态），需要重新设计 wasb 兼容方案。
- **回迁到 1.4.x 的注意事项**：1.4.x 维护分支是否需要此回退取决于该分支是否已包含 #11504。若 1.4.x 仍处于 #11504 之前的版本，则无需操作；若 1.4.x 已含 #11504，则应回退此 PR 以避免 host 信息丢失的回归。鉴于 #11504 是较新的 feature，1.4.x 大概率未包含，故**通常无需回迁**。需在回迁前用 `git log 1.4.x --oneline | grep 11504` 确认。
