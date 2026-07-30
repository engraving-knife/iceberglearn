# 提交 2097：GCP: Support multiple storage credential prefixes (#12881)

## 提交信息

- **序号**：2097 / 4088
- **哈希**：542a4d764683695217dc7741aec9887e1f947e8c
- **短哈希**：542a4d764
- **日期**：2025-05-07 17:35:04 +0200
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：GCP: Support multiple storage credential prefixes (#12881)
- **PR/Issue**：#12881

## 总体目的

此前 `GCSFileIO` 通过 `SupportsStorageCredentials` 接收 `StorageCredential` 列表，但在 `initialize` 中只允许存在至多一个 `gs` 前缀的凭证（`storageCredentialConfig()` 显式断言 `gcsCredentials.size() <= 1`），并把那一个凭证的 config 合并进全局 `GCPProperties`，构造单一的 `Storage` 客户端。这意味着无法对不同的 GCS 路径前缀（例如不同 bucket、不同项目）使用不同的凭证，限制了多租户/跨 bucket 场景的可用性。

本次提交重构 `GCSFileIO`，使其支持多个存储凭证前缀：把原本单一的 `Storage` 客户端与 `GCPProperties` 拆分为按前缀组织的 `PrefixedStorage` 集合，根据文件路径选择最长匹配前缀对应的客户端与配置。根前缀 `"gs"` 始终存在作为回退，用户通过 `setCredentials(...)` 注入的每个 `StorageCredential`（其 `prefix` 以 `gs` 开头）都会创建一个独立的 `PrefixedStorage`，各自持有独立的 `StorageOptions`/凭证与 `GCPProperties`。

## 如何达成设计目的

1. **新增 `PrefixedStorage` 类**：封装一个存储前缀对应的全部运行时状态——`storagePrefix`、`GCPProperties`、`SerializableSupplier<Storage>`、`OAuth2RefreshCredentialsHandler`、懒加载的 `storageClient`。构造时若未传入 supplier，则按属性构建 `StorageOptions`（projectId、host、clientLibToken、noAuth、oauth2 token、oauth2 refresh 等），与原 `GCSFileIO.initialize` 中的逻辑一致。`storage()` 双检锁懒加载，`close()` 关闭 refresh handler 并释放 supplier 引用。
2. **`GCSFileIO` 重构**：
   - 字段从 `GCPProperties gcpProperties` + `transient volatile Storage storage` + `OAuth2RefreshCredentialsHandler refreshHandler` 改为 `transient volatile Map<String, PrefixedStorage> storageByPrefix`；新增常量 `ROOT_STORAGE_PREFIX = "gs"`。
   - `initialize(props)` 只保存 `properties` 与初始化 metrics，不再立即构建 supplier（改为延迟到 `storageByPrefix()` 时）。
   - `storageByPrefix()` 双检锁懒加载：先放入 `ROOT_STORAGE_PREFIX` 的 `PrefixedStorage`（用全局 properties + 用户 supplier），再遍历 `storageCredentials` 中前缀以 `gs` 开头的凭证，对每个凭证合并 properties+credential.config（后者优先），创建独立 `PrefixedStorage`。
   - `clientForStoragePath(String storagePath)`：在 `storageByPrefix` 中找最长匹配前缀（`storagePath.startsWith(prefix)` 且长度更大者），找不到则回退到 `ROOT_STORAGE_PREFIX`，并断言非 null。
   - `newInputFile/newOutputFile/deleteFile/listPrefix` 等改为 `clientForStoragePath(path)` 取 `PrefixedStorage`，再 `.storage()` 取客户端、`.gcpProperties()` 取配置。
   - `close()` 遍历 `storageByPrefix.values()` 逐个 `PrefixedStorage::close`。
   - 删除 `storageCredentialConfig()` 与 "至多一个 GCS 凭证" 的限制。
3. **`GCSInputFile` / `GCSOutputFile`**：`fromLocation` 签名从 `(location, Storage, GCPProperties, metrics)` 改为 `(location, PrefixedStorage, metrics)`，内部用 `storage.storage()` 与 `storage.gcpProperties()` 拆开使用，使 InputFile/OutputFile 仍持有 `Storage` + `GCPProperties`，但来源是按前缀选定的。
4. **`GCPProperties`**：新增 `allProperties` 字段保存原始属性 map，并提供 `properties()` 访问器，供 `PrefixedStorage` 在合并凭证配置时获取基础属性。
5. **测试**：新增 `TestPrefixedStorage` 与大幅扩展的 `GCSFileIOTest`，覆盖多前缀凭证、最长前缀匹配、根前缀回退、按前缀选择客户端、批量删除按 blob 路由等；`TestS3FileIO` 有一处小适配（与 StorageCredential 接口变更相关）。

## 修改详情

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/PrefixedStorage.java` (新增, +121/-0 lines)

**修改目的**：封装单个存储前缀对应的客户端、配置与凭证生命周期。

**工作逻辑**：
- 构造函数 `(storagePrefix, properties, storage)`：校验前缀非空；保存 `gcpProperties = new GCPProperties(properties)`；若 `storage == null`，则构建一个按属性构造 `StorageOptions` 的 supplier——设置 projectId/clientLibToken/host，按 `noAuth`/`oauth2Token`/`oauth2RefreshCredentialsEnabled` 选择 `NoCredentials`、`OAuth2Credentials` 或 `OAuth2CredentialsWithRefresh`（后者附 `OAuth2RefreshCredentialsHandler`）。
- `storage()`：双检锁懒加载 `storageClient = storage.get()`。
- `close()`：关闭 refresh handler，置空 supplier 引用（GCS Storage 不可关闭）。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSFileIO.java` (修改, +95/-95 lines)

**修改目的**：从单客户端改为按前缀多客户端，支持多凭证。

**工作逻辑**：
- 字段：移除 `gcpProperties`、`storage`、`refreshHandler`；新增 `ROOT_STORAGE_PREFIX = "gs"` 与 `transient volatile Map<String, PrefixedStorage> storageByPrefix`。
- 构造函数：`GCSFileIO(supplier)` 与 `GCSFileIO(supplier, gcpProperties)` 都改为只保存 `properties`（后者从 `gcpProperties.properties()` 复制）。
- `client()` 仍返回根前缀的 `Storage`，`client(String storagePath)` 与 `clientForStoragePath(String)` 新增用于按路径选择。
- `clientForStoragePath`：遍历 `storageByPrefix().keySet()`，选 `storagePath.startsWith(prefix)` 中最长者，默认 `ROOT_STORAGE_PREFIX`，断言找到的 `PrefixedStorage` 非 null。
- `storageByPrefix()`：双检锁懒加载，先 put 根前缀的 `PrefixedStorage`，再对每个 `storageCredentials` 中前缀以 `gs` 开头者，合并 `properties + credential.config`（buildKeepingLast），put 一个独立 `PrefixedStorage`。
- `newInputFile/newInputFile(path,length)/newOutputFile/deleteFile/listPrefix/internalDeleteFiles`：均改为 `clientForStoragePath(path)` 取 `PrefixedStorage`，再 `.storage()`/`.gcpProperties()`。`internalDeleteFiles` 用 `clientForStoragePath(ROOT_STORAGE_PREFIX).gcpProperties().deleteBatchSize()` 取批大小，并按每个 batch 第一个 blob 的路径选择客户端删除。
- `initialize`：只保存 `properties` 与 `initMetrics`，不再构建 supplier。
- `close`：遍历 `storageByPrefix.values()` 逐个 close，再置空。
- 删除 `storageCredentialConfig()` 与"至多一个 GCS 凭证"断言。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSInputFile.java` (修改, +5/-9 lines)

**修改目的**：适配 `PrefixedStorage` 参数。

**工作逻辑**：`fromLocation` 签名从 `(location, Storage, GCPProperties, metrics)` 改为 `(location, PrefixedStorage, metrics)`；内部调用 `storage.storage()` 取 `Storage`、`storage.gcpProperties()` 取 `GCPProperties`。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSOutputFile.java` (修改, +3/-2 lines)

**修改目的**：同上，适配 `PrefixedStorage`。

**工作逻辑**：`fromLocation` 签名改为 `(location, PrefixedStorage, metrics)`，内部用 `storage.storage()` 与 `storage.gcpProperties()`。

### `gcp/src/main/java/org/apache/iceberg/gcp/GCPProperties.java` (修改, +8/-0 lines)

**修改目的**：保留原始属性 map 供 `PrefixedStorage` 合并凭证配置时使用。

**工作逻辑**：新增 `private final Map<String, String> allProperties` 字段；无参构造初始化为 `ImmutableMap.of()`，`Map` 构造复制为 `ImmutableMap.copyOf(properties)`；新增 `public Map<String,String> properties()` 访问器。

### 测试文件

- `gcp/src/test/java/org/apache/iceberg/gcp/gcs/TestPrefixedStorage.java`（新增, +58）：验证 `PrefixedStorage` 构造校验、懒加载、close 行为。
- `gcp/src/test/java/org/apache/iceberg/gcp/gcs/GCSFileIOTest.java`（修改, +159/-35）：新增多前缀凭证场景测试，验证不同前缀使用不同客户端、根前缀回退、最长前缀匹配、批量删除路由、`setCredentials` 后 `storageByPrefix` 重建等。
- `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIO.java`（修改, +3/-3）：适配 `StorageCredential` 接口变更（与 #12930 配套）。

## 总结

本次提交让 `GCSFileIO` 真正支持多个存储凭证前缀：通过引入 `PrefixedStorage` 把"前缀 + 客户端 + 配置 + 凭证"打包，`GCSFileIO` 维护一个 `Map<String, PrefixedStorage>`，按文件路径最长前缀匹配选择客户端，根前缀 `"gs"` 作为回退。这解除了原"至多一个 GCS 凭证"的限制，支持跨 bucket/跨项目的多租户场景，是与 S3/Azure 等其它 FileIO 多凭证能力对齐的重要改进。
