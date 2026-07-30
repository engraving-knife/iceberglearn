# 提交 1952：Core: Pass storage credentials from LoadTableResponse to FileIO (#12591)

## 提交信息

- **序号**：1952 / 4088
- **哈希**：817dc35a924b403716d2eb899aba46f3398a5ca9
- **短哈希**：817dc35a9
- **日期**：2025-04-02 11:55:12 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Pass storage credentials from LoadTableResponse to FileIO (#12591)
- **PR/Issue**：#12591

## 总体目的

在 REST Catalog 架构中，当客户端通过 REST API 加载表时，服务端可以在 `LoadTableResponse` 中返回表级别的短期存储凭证（storage credentials），以便客户端用这些凭证访问底层对象存储（如 S3、GCS）。然而此前 Iceberg 的 FileIO 实现并没有一个标准机制来接收并使用这些从 REST 响应中下发的凭证，导致基于 REST Catalog 的短期/临时凭证下发流程无法落地到具体的 FileIO。

本提交引入了一套通用的"存储凭证传递"机制：定义 `StorageCredential` 值对象与 `SupportsStorageCredentials` 接口，让 `S3FileIO`、`GCSFileIO`、`ResolvingFileIO` 实现该接口，并在 `CatalogUtil.loadFileIO` 与 `RESTSessionCatalog` 中把 `LoadTableResponse.credentials()` 转换为 `StorageCredential` 列表后传入 FileIO，从而使 FileIO 在初始化时能够把这些凭证合并到自己的配置中用于构建客户端。

这样做的动机是支持 REST Catalog 的 v1 规范中表加载响应携带凭证的能力，使短期凭证、vended credentials 等场景在客户端侧得到正确消费，提升安全性与多租户场景下的可用性。

## 如何达成设计目的

整体设计思路是引入一个可选的、向后兼容的扩展接口，让 FileIO 实现自行决定是否接收存储凭证，并在加载/初始化 FileIO 的统一入口处做条件性注入：

1. **`StorageCredential`**：不可变值对象（基于 Immutables），包含 `prefix`（凭证适用的存储前缀，如 `s3`、`gs`）和 `config`（凭证配置键值对），并做非空校验。
2. **`SupportsStorageCredentials`**：FileIO 扩展接口，提供 `setCredentials(List<StorageCredential>)` 与 `credentials()`，用于注入和读取凭证。
3. **`CatalogUtil.loadFileIO`**：新增重载方法，接收 `List<StorageCredential>`；在实例化 FileIO 后，若该 FileIO 实现了 `SupportsStorageCredentials`，则调用 `setCredentials` 注入凭证，再调用 `initialize`。原有无凭证的重载方法保留并向新方法委托（传空列表）。
4. **`ResolvingFileIO`**：实现 `SupportsStorageCredentials`，保存凭证列表；在解析底层 delegate FileIO 时把凭证透传给 delegate（通过 `CatalogUtil.loadFileIO(..., storageCredentials)`），并在已缓存 delegate 上检测凭证是否一致以决定是否更新。
5. **`S3FileIO` / `GCSFileIO`**：实现 `SupportsStorageCredentials`，保存凭证；在 `initialize` 中将自身 properties 与从凭证中筛选出的对应存储（`s3` 前缀 / `gs` 前缀）配置合并（`buildKeepingLast`，凭证优先），再构造 `S3FileIOProperties` / `GCPProperties`。约束每个存储最多一个凭证。
6. **`RESTSessionCatalog`**：把原本传给 `tableFileIO` 的 `response.config()` 改为 `response.credentials()`（即从表配置改为表凭证），并新增带凭证的 `newFileIO` 重载，把 REST 的 `Credential`（`prefix`+`config`）转换为 `StorageCredential` 后通过 `CatalogUtil.loadFileIO` 传入；同时调整复用 FileIO 的条件（凭证为空时才复用共享 io）。
7. **测试**：为 `StorageCredential`、`CatalogUtil`、`ResolvingFileIO`、`S3FileIO`、`GCSFileIO` 新增单元/集成测试，覆盖凭证注入、合并、前缀过滤、重复凭证约束等。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/StorageCredential.java` (新增, +42/-0 lines)

**修改目的**：定义不可变的存储凭证值对象。

**工作逻辑**：基于 `@Value.Immutable` 的接口，包含 `prefix()` 与 `config()` 两个字段；`@Value.Check` 的 `validate()` 校验 prefix 和 config 非空；提供静态工厂 `create(prefix, config)` 构造 `ImmutableStorageCredential`。实现 `Serializable` 以支持序列化。

### `core/src/main/java/org/apache/iceberg/io/SupportsStorageCredentials.java` (新增, +32/-0 lines)

**修改目的**：定义 FileIO 的扩展接口，声明可提供/获取存储凭证的能力。

**工作逻辑**：接口包含 `setCredentials(List<StorageCredential>)` 与 `List<StorageCredential> credentials()` 两个方法，供 `CatalogUtil` 与具体 FileIO 实现协作。

### `core/src/main/java/org/apache/iceberg/CatalogUtil.java` (修改, +33/-0 lines)

**修改目的**：扩展 `loadFileIO` 以支持注入存储凭证。

**工作逻辑**：
- 原 `loadFileIO(impl, properties, hadoopConf)` 改为委托给新重载 `loadFileIO(impl, properties, hadoopConf, storageCredentials)`，传空列表。
- 新重载在实例化 FileIO、调用 `configureHadoopConf` 之后，判断 `fileIO instanceof SupportsStorageCredentials`，若是则调用 `setCredentials(storageCredentials)`，最后调用 `fileIO.initialize(properties)`。这样凭证在 initialize 之前就已就位，便于 FileIO 在 initialize 中合并使用。

### `core/src/main/java/org/apache/iceberg/io/ResolvingFileIO.java` (修改, +27/-1 lines)

**修改目的**：让 ResolvingFileIO 支持存储凭证，并在解析 delegate 时透传。

**工作逻辑**：
- 类声明增加 `implements SupportsStorageCredentials`，新增字段 `storageCredentials`（默认空列表）。
- 实现 `setCredentials`（拷贝到可变集合以兼容 Kryo 序列化）与 `credentials()`（返回不可变副本）。
- 在 `io(String prefix)` 方法中：当从缓存取到 delegate 时，若 delegate 也实现了 `SupportsStorageCredentials` 且其凭证与当前不一致，则更新之；当新建 delegate 时（包括正常路径和 fallback 路径），调用 `CatalogUtil.loadFileIO(key, props, conf, storageCredentials)` 把凭证传下去。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (修改, +35/-15 lines)

**修改目的**：将 LoadTableResponse 中的凭证传递到 FileIO。

**工作逻辑**：
- 新增带 `List<Credential> storageCredentials` 参数的 `newFileIO` 重载，把 REST 的 `Credential`（含 `prefix`/`config`）映射为 `StorageCredential` 后调用 `CatalogUtil.loadFileIO(..., storageCredentials)`。
- 原 `newFileIO(context, properties)` 委托新重载（传空凭证列表）。
- `tableFileIO(context, config, storageCredentials)`：当 config 为空、无 ioBuilder 且凭证为空时复用共享 `io`；否则合并 properties 与 config 后调用带凭证的 `newFileIO`。
- 多处 `tableFileIO(context, response.config())` 改为 `tableFileIO(context, tableConf, response.credentials())`，即把响应中的 credentials（而非 config）作为 FileIO 凭证来源传入。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java` (修改, +44/-2 lines)

**修改目的**：让 S3FileIO 接收并消费存储凭证。

**工作逻辑**：
- 类声明增加 `implements SupportsStorageCredentials`，新增 `storageCredentials` 字段。
- `initialize` 中将 `properties` 与 `storageCredentialConfig()` 合并（`buildKeepingLast`，凭证优先），再构造 `S3FileIOProperties`。
- `storageCredentialConfig()`：从凭证列表中筛选 `prefix` 以 `s3` 开头的凭证，校验最多一个，返回其 config（无则为空 map）。
- 实现 `setCredentials` / `credentials()`。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSFileIO.java` (修改, +44/-2 lines)

**修改目的**：让 GCSFileIO 接收并消费存储凭证。

**工作逻辑**：
- 类声明增加 `implements SupportsStorageCredentials`，新增 `storageCredentials` 字段。
- `initialize` 中将 `properties` 与 `storageCredentialConfig()` 合并后构造 `GCPProperties`。
- `storageCredentialConfig()`：筛选 `prefix` 以 `gs` 开头的凭证，校验最多一个，返回其 config。
- 实现 `setCredentials` / `credentials()`。

### 测试文件 (新增多个测试方法)

**修改目的**：覆盖凭证传递、合并、前缀过滤等行为。

涉及文件：`core/src/test/.../TestCatalogUtil.java`、`core/src/test/.../io/TestResolvingIO.java`、`core/src/test/.../io/TestStorageCredential.java`、`aws/src/test/.../s3/TestS3FileIO.java`、`gcp/src/test/.../gcs/GCSFileIOTest.java`，分别验证 `loadFileIO` 对 `SupportsStorageCredentials` 的注入、`ResolvingFileIO` 的凭证透传、`StorageCredential` 校验、S3/GCS FileIO 的凭证合并与前缀过滤等。

## 总结

本提交为 Iceberg 引入了从 REST Catalog 的 `LoadTableResponse` 向 FileIO 传递短期存储凭证的通用机制。核心是新增 `StorageCredential` 值对象与 `SupportsStorageCredentials` 扩展接口，让 `S3FileIO`、`GCSFileIO`、`ResolvingFileIO` 实现该接口，并在 `CatalogUtil.loadFileIO` 与 `RESTSessionCatalog` 中完成凭证的转换与注入，最终在 FileIO 初始化时把凭证合并进配置以构建存储客户端。这为 REST Catalog 的 vended credentials 场景提供了客户端侧支持。
