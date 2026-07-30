# 提交 1345：Fix ADLSLocation file parsing (#11395)

## 提交信息

- **序号**：1345 / 4088
- **哈希**：9be7f00dd6a9fb480a94c46d49473334908be859
- **短哈希**：9be7f00dd
- **日期**：2024-11-05（Tue Nov 5 17:55:56 2024 -0600）
- **作者**：Marc Cenac <547446+mrcnc@users.noreply.github.com>
- **提交说明**：Fix ADLSLocation file parsing (#11395)
- **PR/Issue**：#11395

## 总体目的

`ADLSLocation` 是 Iceberg Azure 模块用于解析 Azure Data Lake Storage Gen2 路径的工具类。它接受形如 `abfs[s]://[<container>@]<storage account host>/<file path>` 的 URI，并把 `path` 部分提取出来供后续 IO 操作使用。

此前的实现存在两个问题：
1. **错误地剥离 query 和 fragment**：原代码对解析出的 `uriPath` 调用 `split("\\?", -1)[0].split("#", -1)[0]`，即把 `?` 之后和 `#` 之后的内容都当作 query/fragment 丢弃。但 ADLS Gen2 的文件名（blob 名）本身允许包含 `?` 和 `#` 字符，这会导致诸如 `file?.txt` 这样的文件名被截断为 `file`，从而读不到正确文件，引发 IO 错误或读取错文件。
2. **Javadoc 引用偏离实际**：原 Javadoc 把 URI 描述为 "Hadoop's Azure support conventions"，引用 Hadoop 文档；但 Iceberg 实际只是借用这种 URI 形态，更准确的规范来源是 Azure Data Lake Storage 的官方 URI 语法。

本提交修复文件名中包含 `?`/`#` 字符时的解析错误，并更新 Javadoc 指向 Azure 官方文档；同时清理掉原先断言"丢弃 query/fragment"的两个测试用例，新增针对含 `?` 文件名的参数化测试。

## 如何达成设计目的

- **移除 query/fragment 剥离逻辑**：删除 `uriPath.split("\\?", -1)[0].split("#", -1)[0]` 这一行，让 path 保留原始字符。这样 `abfs://...file?.txt` 的 path 会正确变为 `file?.txt`，而不是 `file`。
- **更新 Javadoc**：把"This class represents a fully qualified location in Azure expressed as a URI"改为"This class represents a fully qualified location to a file or directory in Azure Data Lake Storage Gen2 storage"，并把 Hadoop 文档链接替换为 [Azure Data Lake Storage URI 语法文档](https://learn.microsoft.com/en-us/azure/storage/blobs/data-lake-storage-introduction-abfs-uri#uri-syntax)。
- **修正测试**：删除 `testQueryAndFragment`、`testQueryAndFragmentNoPath` 两个验证旧"剥离 query/fragment"行为的测试（这两个测试本身假设的行为就是错误的）；新增 `testQuestionMarkInFileName` 参数化测试，分别用 `file?.txt`（直接含 `?`）和 `file%3F.txt`（百分号编码的 `?`）作为文件名，断言 `location.path()` 包含完整的原文件名。
  - 对 `file?.txt`：修复后 path 为 `file?.txt`，包含原字符串。
  - 对 `file%3F.txt`：path 为 `file%3F.txt`，仍包含原字符串（Iceberg 不主动 percent-decode，保持 Azure 实际存储的 blob 名）。

## 修改详情

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSLocation.java`

**修改目的**：修复 path 解析误剥离 `?`/`#` 的问题，并更新 Javadoc。

**工作逻辑**：

Javadoc 部分改为：
```java
/**
 * This class represents a fully qualified location to a file or directory in Azure Data Lake
 * Storage Gen2 storage.
 *
 * <p>Locations follow a URI like structure to identify resources
 *
 * <pre>{@code abfs[s]://[<container>@]<storage account host>/<file path>}</pre>
 *
 * <p>See <a
 * href="https://learn.microsoft.com/en-us/azure/storage/blobs/data-lake-storage-introduction-abfs-uri#uri-syntax">Azure
 * Data Lake Storage URI</a>
 */
```

解析逻辑（构造函数中）原为：
```java
String uriPath = matcher.group(2);
uriPath = uriPath == null ? "" : uriPath.startsWith("/") ? uriPath.substring(1) : uriPath;
this.path = uriPath.split("\\?", -1)[0].split("#", -1)[0];
```
改为：
```java
String uriPath = matcher.group(2);
this.path = uriPath == null ? "" : uriPath.startsWith("/") ? uriPath.substring(1) : uriPath;
```
即只保留"去掉前导 `/`"的逻辑，不再对 `?`/`#` 进行切分。`URI_PATTERN = ^abfss?://([^/?#]+)(.*)?$` 中 authority 部分 `([^/?#]+)` 仍然正确地在第一个 `/`、`?`、`#` 处停止，因此 container@account 部分不受影响；改动只影响 `group(2)`（即 path 及之后部分）的处理。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/ADLSLocationTest.java`

**修改目的**：删除验证旧错误行为的测试，新增覆盖文件名含 `?` 字符的测试。

**工作逻辑**：

- 删除 `testQueryAndFragment`：原测试输入 `abfs://container@account.dfs.core.windows.net/path/to/file?query=foo#123`，断言 `path() == "path/to/file"`。这等价于要求把 `?query=foo#123` 当作 query/fragment 丢弃，是错误行为。
- 删除 `testQueryAndFragmentNoPath`：同理。
- 新增参数化测试：
  ```java
  @ParameterizedTest
  @ValueSource(strings = {"file?.txt", "file%3F.txt"})
  public void testQuestionMarkInFileName(String path) {
    String fullPath = String.format("abfs://container@account.dfs.core.windows.net/%s", path);
    ADLSLocation location = new ADLSLocation(fullPath);
    assertThat(location.path()).contains(path);
  }
  ```
  使用 `contains` 而非 `isEqualTo` 是因为对 `file?.txt` 输入，path 完全等于 `file?.txt`；对 `file%3F.txt`，path 也等于 `file%3F.txt`。`contains` 在两种情况下都成立且语义清晰。

## 小结

- **成效**：`ADLSLocation` 现可正确处理文件名中包含 `?`/`#` 字符的 ADLS 路径，不再误截断；Javadoc 准确指向 Azure 官方 URI 语法文档。
- **影响范围**：仅 azure 模块的 `ADLSLocation`（解析逻辑与 Javadoc）及对应测试。修复纯在 path 字符串处理层面，不涉及 IO、认证或 manifest 等其他流程。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个 bug 修复，影响 Azure ADLS Gen2 用户在文件名含特殊字符（`?`、`#`）时的正确性，**建议回迁**到 1.4.x。
  - 改动小且自包含，不依赖其他提交。只需保证 1.4.x 的 `ADLSLocation`、`ADLSLocationTest` 与本提交基线一致即可。
  - 注意 1.4.x 若已有相同文件，回迁时需保证测试中的 `@ParameterizedTest`、`@ValueSource` 等 JUnit 5 导入可用（azure 模块原本就用了 JUnit 5 参数化测试，应无问题）。
  - 若 1.4.x 的 `ADLSLocation` 与本提交基线有差异（例如后续有其他改动），需手动合并而非直接 cherry-pick。
