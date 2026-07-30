# 提交 1251：Revert "Core, Azure: Support wasb[s] paths in ADLSFileIO (#11294)" (#11344)

## 提交信息

- **序号**：1251 / 4088
- **哈希**：fd064386fe637e504cb71e2cfa9ebd8f28b9ac6c
- **短哈希**：fd064386f
- **日期**：2024-10-18（Fri Oct 18 17:31:44 2024 +0800）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：Revert "Core, Azure: Support wasb[s] paths in ADLSFileIO (#11294)" (#11344)
- **PR/Issue**：#11344
- **回退目标**：11a8a78b951d6f83fbeff703ac4e1a4b7d3f3597（提交 1243）

## 总体目的

回退提交 1243（PR #11294）引入的"在 `ADLSFileIO` 中支持 `wasb`/`wasbs` 路径"功能。该功能合入后仅两天即被回退，原因是 wasb 路径在底层仍走 ADLS Gen2（Data Lake Storage）API 而非 Blob Storage API，存在语义不一致与潜在正确性风险——`wasb` 路径的 host 是 `account.blob.core.windows.net`（Blob 端点），但代码会把它交给 Data Lake 客户端处理，端点类型与 API 不匹配可能导致鉴权、命名空间或行为异常。社区决定先回退以保证稳定性，后续会另起 PR 设计更合理的 wasb 兼容方案。

回退操作把 `ADLSLocation`、`ADLSFileIO`、`ResolvingFileIO`、`ADLSLocationTest` 四个文件恢复到 1243 之前的状态：仅支持 `abfs`/`abfss` scheme，`ADLSLocation` 仍用原正则 + 手工 split authority 的方式解析，字段名恢复为 `storageAccount()`，异常类型恢复为 `ValidationException`。

## 如何达成设计目的

执行 `git revert 11a8a78b951d6f83fbeff703ac4e1a4b7d3f3597`，由 Git 自动生成反向 diff。回退涉及的文件与 1243 完全对应，改动方向相反：

1. `ResolvingFileIO` 的 scheme 映射表中移除 `wasb`、`wasbs` 两条。
2. `ADLSLocation` 的 URI 正则恢复为 `^abfss?://([^/?#]+)(.*)?$`，移除 `java.net.URI` 解析逻辑，恢复手工 `authority.split("@")` 解析；字段 `storageEndpoint` 改回 `storageAccount`，方法同步改名；异常类型从 `IllegalArgumentException` 改回 `ValidationException`；移除 `stripLeadingSlash` 辅助方法。
3. `ADLSFileIO.client()` 中 `location.storageEndpoint()` 改回 `location.storageAccount()`。
4. `ADLSLocationTest` 恢复为非参数化的 abfs/abfss 单测，移除 wasb 相关用例与 `testInvalidURI`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/ResolvingFileIO.java`

**修改目的**：移除 wasb/wasbs 路由。

**工作逻辑**：scheme → FileIO 映射表从

```java
"abfs", ADLS_FILE_IO_IMPL,
"abfss", ADLS_FILE_IO_IMPL,
"wasb", ADLS_FILE_IO_IMPL,
"wasbs", ADLS_FILE_IO_IMPL)
```

恢复为

```java
"abfs", ADLS_FILE_IO_IMPL,
"abfss", ADLS_FILE_IO_IMPL)
```

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSLocation.java`

**修改目的**：恢复仅支持 abfs/abfss 的解析逻辑。

**工作逻辑**：
- Javadoc 恢复为只描述 `abfs[s]://[<container>@]<storage account host>/<file path>` 形式，移除 wasb 说明。
- 正则恢复为 `^abfss?://([^/?#]+)(.*)?$`，捕获 authority 与剩余部分。
- 构造函数恢复为：用 `ValidationException.check(matcher.matches(), ...)` 校验；`authority = matcher.group(1)`，按 `@` split 得到 container 与 storageAccount；path 从 `matcher.group(2)` 取并去除前导 `/`、按 `?`/`#` split 去除 query/fragment。
- 字段 `storageEndpoint` 改回 `storageAccount`，方法 `storageEndpoint()` 改回 `storageAccount()`。
- 移除 `java.net.URI`/`URISyntaxException` 导入与 `stripLeadingSlash` 方法。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSFileIO.java`

**修改目的**：适配字段改名回退。

**工作逻辑**：`client()` 中 `azureProperties.applyClientConfiguration(location.storageEndpoint(), clientBuilder)` 改回 `location.storageAccount()`。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/ADLSLocationTest.java`

**修改目的**：恢复 abfs/abfss 单测，移除 wasb 覆盖。

**工作逻辑**：
- 导入从 `java.net.URI`/`URISyntaxException` 改回 `org.apache.iceberg.exceptions.ValidationException`。
- `testLocationParsing` 中字段访问从 `storageEndpoint()` 改回 `storageAccount()`。
- `testWasbLocationParsing`、`testInvalidURI` 用例被删除。
- `testEncodedString`、`testNoContainer`、`testNoPath`、`testQueryAndFragment`、`testQueryAndFragmentNoPath` 从参数化（同时覆盖 abfs 与 wasb）恢复为单一 abfs 路径的非参数化 `@Test`。
- `testMissingScheme`、`testInvalidScheme` 的异常断言从 `IllegalArgumentException` 改回 `ValidationException`。

## 小结

- **成效**：回退 wasb[s] 路径支持，恢复 `ADLSFileIO` 仅识别 abfs/abfss 的稳定行为，避免 wasb 路径走 ADLS Gen2 API 带来的潜在正确性风险。`ADLSLocation` 的 `storageAccount()` 公共方法恢复，依赖该方法的下游代码无需调整。
- **影响范围**：与 1243 相反，涉及 `core`（`ResolvingFileIO`）与 `azure`（`ADLSLocation`、`ADLSFileIO`、`ADLSLocationTest`）。属于运行时行为回退。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支应**保持与回退后状态一致**（即不引入 wasb 支持）。如果 1.4.x 上从未合入 1243，则**无需任何操作**——1.4.x 本就不支持 wasb。
  - 如果 1.4.x 上不慎合入了 1243（或等价改动），应同样回退，恢复 `storageAccount()` 命名与 abfs-only 解析。
  - 后续若 main 上有新的 wasb 兼容方案（替代 1243 的设计），1.4.x 可视情况选择性回迁那个新方案，而不是回迁本回退提交本身——因为本回退只是恢复原状。
  - 注意：1243 与 1251 是一对配对提交，回迁时必须成对考虑：要么都不回迁（1.4.x 保持原状），要么先回迁 1243 再回迁 1251（无意义），唯一有意义的是"只回迁 1243 而不回迁 1251"——但这会引入已被社区认定有问题的 wasb 行为，不建议。
