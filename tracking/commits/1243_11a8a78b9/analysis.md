# 提交 1243：Core, Azure: Support wasb[s] paths in ADLSFileIO (#11294)

## 提交信息

- **序号**：1243 / 4088
- **哈希**：11a8a78b951d6f83fbeff703ac4e1a4b7d3f3597
- **短哈希**：11a8a78b9
- **日期**：2024-10-16（Wed Oct 16 10:39:50 2024 -0500）
- **作者**：Marc Cenac <547446+mrcnc@users.noreply.github.com>
- **提交说明**：Core, Azure: Support wasb[s] paths in ADLSFileIO (#11294)
- **PR/Issue**：#11294

## 总体目的

让 `ADLSFileIO` 能够接受旧式 WASB/WASBS 路径（`wasb://`、`wasbs://`），不仅限于原生的 ABFS/ABFSS（`abfs://`、`abfss://`）路径。许多用户从 Hadoop 旧式 Azure Blob Storage（WASB）方案迁移到 Iceberg + ADLS Gen2，表元数据中残留的 `wasb://` 风格路径如果直接交给 Iceberg 会因 scheme 不匹配而失败。本提交让 Iceberg 在解析时识别 `wasb[s]` scheme，仍通过 ADLS Gen2（Data Lake Storage）API 访问，从而提供向后兼容。

同时，由于 `wasb` 路径的 host 形如 `account.blob.core.windows.net`（而非 `account.dfs.core.windows.net`），原 `ADLSLocation` 暴露的 `storageAccount()` 字段语义不准确。本次重写为 `storageEndpoint()`，返回完整的 host（含 `dfs.` 或 `blob.` 后缀），让 `ADLSFileIO` 在构建客户端时能正确以 endpoint 维度选择配置。

注意：此提交随后被 #11344（提交 1251）回退。

## 如何达成设计目的

1. 在 `ResolvingFileIO` 的 scheme → FileIO 实现映射表中追加 `wasb`、`wasbs` 两条映射，使其在运行时根据路径 scheme 路由到 `ADLS_FILE_IO_IMPL`。
2. 重写 `ADLSLocation`：
   - URI 正则从 `^abfss?://...` 扩展为 `^(abfss?|wasbs?)://...`，允许 wasb[s] scheme。
   - 不再手工 split authority，改用 JDK 的 `URI` 类解析 `userInfo`（container）和 `host`（endpoint），更稳健。
   - 把 `storageAccount` 字段重命名为 `storageEndpoint`，方法同步改名。
   - 异常类型由 `ValidationException` 改为 `IllegalArgumentException`。
3. `ADLSFileIO` 中调用点从 `location.storageAccount()` 改为 `location.storageEndpoint()`。
4. 单测 `ADLSLocationTest` 全面参数化，覆盖 `wasb`/`wasbs`/`abfs`/`abfss` 各种组合。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/ResolvingFileIO.java`

**修改目的**：让 `ResolvingFileIO` 根据 `wasb`/`wasbs` scheme 选用 `ADLSFileIO`。

**工作逻辑**：在静态 `IO_IMPL` 映射表中追加：

```java
"wasb", ADLS_FILE_IO_IMPL,
"wasbs", ADLS_FILE_IO_IMPL,
```

`ResolvingFileIO` 在 `resolveFileIO(String location)` 中按 location 的 scheme 查这张表，命中即返回缓存的 `ADLSFileIO` 实例。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSLocation.java`

**修改目的**：支持 `wasb[s]` scheme 并改用 `URI` 类解析。

**工作逻辑**：
- 正则更新为 `^(abfss?|wasbs?)://[^/?#]+.*$`，只用于校验整体格式。
- 构造函数中：先匹配正则，若不匹配则抛 `IllegalArgumentException("Invalid ADLS URI: %s")`；匹配则用 `new URI(location)` 解析，`container = uri.getUserInfo()`、`storageEndpoint = uri.getHost()`、`path = stripLeadingSlash(uri.getRawPath())`。`URISyntaxException` 被包装为 `IllegalArgumentException`。
- 新增私有静态方法 `stripLeadingSlash(String path)`，去除路径开头的 `/`。
- 字段 `storageAccount` → `storageEndpoint`，公共方法 `storageAccount()` → `storageEndpoint()`，并更新 Javadoc 说明支持的两种 URI 形式（abfs[s] 与 wasb[s]）以及 wasb 路径会被走 ADLS Gen2 API 而非 Blob API。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSFileIO.java`

**修改目的**：适配 `ADLSLocation` 字段改名。

**工作逻辑**：在 `client()` 方法中，调用从 `azureProperties.applyClientConfiguration(location.storageAccount(), clientBuilder)` 改为 `applyClientConfiguration(location.storageEndpoint(), clientBuilder)`。其余逻辑不变。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/ADLSLocationTest.java`

**修改目的**：覆盖 wasb[s] 解析与字段重命名。

**工作逻辑**：
- 把原先针对 `abfs`/`abfss` 的 `@ParameterizedTest` 扩展到 `wasb`/`wasbs`，多个用例（`testEncodedString`、`testNoContainer`、`testNoPath`、`testQueryAndFragment`、`testQueryAndFragmentNoPath`）改为参数化，对同一组断言用 abfs 与 wasb 两类路径分别验证。
- 新增 `testWasbLocationParsing`：专门校验 `wasb`/`wasbs` 路径的 endpoint 取 `account.blob.core.windows.net`。
- 新增 `testInvalidURI`：构造 `abfs://container@account.dfs.core.windows.net/#invalidPath#`，验证抛 `IllegalArgumentException`。
- 断言中的字段访问从 `storageAccount()` 改为 `storageEndpoint()`，异常断言从 `ValidationException` 改为 `IllegalArgumentException`。

## 小结

- **成效**：使 `ADLSFileIO` 接受 `wasb`/`wasbs` 路径，方便从旧 WASB 方案迁移的用户；并把 `ADLSLocation` 内部解析改为基于 `java.net.URI`，对编码字符、query/fragment 的处理更规范。
- **影响范围**：`core`（`ResolvingFileIO` 路由表）、`azure`（`ADLSLocation`、`ADLSFileIO` 及其单测），属于运行时行为变更。
- **回迁到 1.4.x 的注意事项**：
  - 此提交随后被 #11344（提交 1251）整体回退，原因是 wasb 路径走 ADLS Gen2 API 存在隐患。1.4.x 分支应**避免单独回迁此提交**，否则会引入已被社区认定需要回退的行为。
  - 若 1.4.x 上确实有 wasb 路径需求，建议跟踪 main 上后续替代方案（社区会另起 PR 解决），而不是直接回迁本提交。
  - 字段改名 `storageAccount()` → `storageEndpoint()` 是公开 API 上的可观察变化，依赖该方法的下游代码需要同步调整。
