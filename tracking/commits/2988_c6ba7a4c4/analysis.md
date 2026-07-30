# 提交 2988：GCS: Integrate GCSAnalyticsCore Library (#14333)

## 提交信息

- **序号**：2988 / 4088
- **哈希**：c6ba7a4c48cf1280eeff35829492fd7e6a5f6787
- **短哈希**：c6ba7a4c4
- **日期**：2025-12-09
- **作者**：Beerelly Prudhvi Maharishi
- **提交说明**：GCS: Integrate GCSAnalyticsCore Library (#14333)
- **PR/Issue**：#14333

## 总体目的

Google Cloud Storage（GCS）是 Iceberg 在 GCP 上最主要的底层存储。Iceberg 现有的 GCS 读取链路基于 `com.google.cloud:google-cloud-storage` 客户端，通过 `GCSInputStream` 实现按需定位读取（seek + range read）。但对于分析型工作负载，Google 提供了一个专门的优化库 `gcs-analytics-core`，它在底层做了更激进的预取、向量化读取、并发分片拉取以及针对分析查询的请求合并优化，能够显著提升大规模扫描吞吐量。

本提交的目标是在不破坏现有读取链路的前提下，将 `gcs-analytics-core` 库集成进 Iceberg 的 GCP 模块，作为可选的输入流实现。集成通过一个开关属性 `gcs.analytics-core.enabled`（默认关闭）控制，仅在用户显式启用时才走新的 `GoogleCloudStorageInputStream` 路径；若该路径创建失败则自动回退到原有 `GCSInputStream`，保证向后兼容与容错。

除了核心集成代码，本提交还配套引入了基于 fake-gcs-server（testcontainers）的集成测试、针对 `GCSInputFile` 与 `GcsInputStreamWrapper` 的单元测试，以及对 `PrefixedStorage` 凭证处理逻辑的重构。同时更新了 gcp-bundle 的 LICENSE/NOTICE 以符合依赖合规要求。

## 如何达成设计目的

整体设计分四层：依赖与构建（`build.gradle`、`libs.versions.toml`、`gcp-bundle`）引入 `gcs-analytics-core` 1.2.1；属性层在 `GCPProperties` 新增 `gcs.analytics-core.enabled` 开关；核心 IO 层在 `BaseGCSFile`/`GCSInputFile`/`GCSOutputFile`/`PrefixedStorage` 中贯穿 `GcsFileSystem` 实例，并在 `GCSInputFile.newStream()` 中按开关选择创建 `GoogleCloudStorageInputStream`，通过新建的 `GcsInputStreamWrapper` 适配为 Iceberg 的 `SeekableInputStream`（含 `RangeReadable`）；测试层通过 mock 单元测试与 fake-gcs-server 集成测试验证端到端正确性。

## 修改详情

### `build.gradle` (+3/-0 lines)

**修改目的**：为 iceberg-gcp 模块引入 gcs-analytics-core 依赖及测试用 testcontainers。

**工作逻辑**：在 `:iceberg-gcp` 的 `dependencies` 中新增 `compileOnly(libs.gcs.analytics.core)`（运行时由 bundle 提供），并为测试新增 `testImplementation libs.testcontainers` 与 `testImplementation libs.testcontainers.junit.jupiter`，用于支撑基于 fake-gcs-server 容器的集成测试。

### `gradle/libs.versions.toml` (+2/-0 lines)

**修改目的**：登记 gcs-analytics-core 的版本与坐标。

**工作逻辑**：新增版本变量 `gcs-analytics-core = "1.2.1"` 与库坐标 `gcs-analytics-core = { module = "com.google.cloud.gcs.analytics:gcs-analytics-core", version.ref = "gcs-analytics-core" }`，供 build.gradle 引用。注意 LICENSE 中记录的是 1.1.2，而实际引入版本为 1.2.1（LICENSE/NOTICE 文本为较早版本所写，存在轻微不一致）。

### `gcp-bundle/build.gradle` (+1/-0 lines)

**修改目的**：将 gcs-analytics-core 打入 gcp-bundle 运行时胖包。

**工作逻辑**：在 `:iceberg-gcp-bundle` 的 `dependencies` 中新增 `implementation libs.gcs.analytics.core`，使 shadowJar 最终包含该库，保证用户使用 bundle 时开箱即用。

### `gcp-bundle/LICENSE` (+6/-0 lines) 与 `gcp-bundle/NOTICE` (+23/-0 lines)

**修改目的**：补充新增依赖的许可证与声明信息以满足发布合规。

**工作逻辑**：LICENSE 新增 `com.google.cloud.gcs.analytics:gcs-analytics-core:1.1.2` 条目（Apache 2.0）；NOTICE 新增 GCS Analytics Core 项目的版权声明文本。两者均为 Apache 2.0 许可，与项目自身许可兼容。

### `gcp/src/main/java/org/apache/iceberg/gcp/GCPProperties.java` (+10/-0 lines)

**修改目的**：新增 analytics core 开关属性。

**工作逻辑**：新增常量 `GCS_ANALYTICS_CORE_ENABLED = "gcs.analytics-core.enabled"`、字段 `gcsAnalyticsCoreEnabled`，在构造器中通过 `PropertyUtil.propertyAsBoolean(properties, GCS_ANALYTICS_CORE_ENABLED, false)` 解析（默认关闭），并暴露 `isGcsAnalyticsCoreEnabled()` 读方法供 `GCSInputFile` 判断是否启用新读取路径。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/BaseGCSFile.java` (+14/-5 lines)

**修改目的**：在文件抽象基类中贯穿 `GcsFileSystem` 实例。

**工作逻辑**：`BaseGCSFile` 新增 `GcsFileSystem gcsFileSystem` 字段，构造器增加该参数并新增 `gcsFileSystem()` 访问器。`GCSInputFile`/`GCSOutputFile` 在构造时需要传入由 `PrefixedStorage` 懒加载创建的 `GcsFileSystem`，从而让输入流能够使用 analytics core 的读取能力。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSInputFile.java` (+57/-4 lines)

**修改目的**：在输入流创建处按开关选择 analytics core 路径。

**工作逻辑**：

`newStream()` 先判断 `gcpProperties().isGcsAnalyticsCoreEnabled()`，若启用则调用 `newGoogleCloudStorageInputStream()` 创建基于 `GoogleCloudStorageInputStream` 的包装流；若创建过程抛 `IOException`，则记录 ERROR 日志并回退到原有 `new GCSInputStream(...)` 路径，保证容错。

`newGoogleCloudStorageInputStream()` 根据 `blobSize` 是否已知分两种方式创建：当 `blobSize` 为 null（长度未知）时用 `GoogleCloudStorageInputStream.create(gcsFileSystem(), gcsItemId())`，仅提供 bucket/object/generation 标识；当已知长度时用 `gcsFileInfo()` 构造 `GcsFileInfo`（含 size、uri、空 attributes）再创建，使 analytics core 可基于已知大小做更优的预取。`gcsItemId()` 将 Iceberg 的 `BlobId` 转换为 analytics core 的 `GcsItemId`，并按需带上 `contentGeneration`。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSOutputFile.java` (+17/-3 lines)

**修改目的**：输出文件构造链路同步传递 `GcsFileSystem`。

**工作逻辑**：`fromLocation` 与构造器均新增 `GcsFileSystem gcsFileSystem` 参数并透传给 `BaseGCSFile`；`toInputFile()` 在构造对应 `GCSInputFile` 时也传入 `gcsFileSystem()`，确保由输出文件转换得到的输入文件同样具备 analytics core 能力。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GcsInputStreamWrapper.java` (+112/-0 lines, 新增)

**修改目的**：将 analytics core 的 `GoogleCloudStorageInputStream` 适配为 Iceberg 的 `SeekableInputStream`。

**工作逻辑**：

新类 `GcsInputStreamWrapper extends SeekableInputStream implements RangeReadable`，构造时持有底层 `GoogleCloudStorageInputStream` 与 `MetricsContext`，并初始化 `readBytes`/`readOperations` 两个计数器。

- `getPos()`/`seek()` 直接委托底层流。
- `read()`/`read(byte[])`/`read(byte[],int,int)` 委托底层并在每次读后递增读字节与读操作计数器，用于指标上报。
- 实现 `RangeReadable`：`readFully(position,buffer,offset,length)`、`readTail(buffer,offset,length)` 直接委托；`readVectored(ranges,allocate)` 将 Iceberg 的 `FileRange` 列表转换为 analytics core 的 `GcsObjectRange`（携带 offset、length、`byteBufferFuture`），再调用底层 `readVectored`，从而支持向量化区间读，与 Iceberg 的 co-read 机制对接。
- `close()` 委托底层流。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/PrefixedStorage.java` (+69/-14 lines)

**修改目的**：创建并懒加载 `GcsFileSystem`，同时重构凭证处理。

**工作逻辑**：

新增字段 `gcsFileSystemSupplier`（`SerializableSupplier<GcsFileSystem>`）与懒加载字段 `gcsFileSystem`。`gcsFileSystem()` 采用双重检查锁懒加载，首次取用时调用 supplier 创建实例并加入 `closeableGroup` 以保证资源释放。

新增 `credentials(GCPProperties)` 方法将原本散落在 Storage 构造中的凭证选择逻辑抽出：有 oauth2 token 用 `GCPAuthUtils.oauth2CredentialsFromGcpProperties`，否则 noAuth 时用 `NoCredentials`，其余返回 null（交由 SDK 自动发现）。该统一方法同时服务于 Storage 构建与 `GcsFileSystem` 构建，保证两者使用一致凭证。

`gcsFileSystemSupplier(properties)` 构造一个 supplier：在原始属性上叠加 `gcs.user-agent`，构造 `GcsAnalyticsCoreOptions("gcs.", ...)` 并取 `GcsFileSystemOptions`；凭证为 null 时用 `new GcsFileSystemImpl(fileSystemOptions)`，否则用 `new GcsFileSystemImpl(credentials, fileSystemOptions)`。同时把 `closeableGroup` 的初始化提前到构造器主体，修正了原先仅在 oauth2 分支才初始化 closeableGroup 的潜在空指针隐患。

### `gcp/src/integration/java/org/apache/iceberg/gcp/gcs/TestGcsFileIO.java` (+246/-0 lines, 新增)

**修改目的**：基于 fake-gcs-server 容器做端到端集成测试。

**工作逻辑**：使用 testcontainers 启动 `fsouza/fake-gcs-server:latest` 镜像并绑定 4443 端口，通过 `NoCredentials` 与自定义 host 构建 `GCSFileIO`。覆盖读、写、删除、列举、range 读、向量化读等场景，验证 analytics core 开关开启/关闭两种路径下的端到端正确性。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/TestGcsInputFile.java` (+270/-0 lines, 新增)

**修改目的**：单元测试 `GCSInputFile` 在 analytics core 启用时的流创建与回退逻辑。

**工作逻辑**：使用 Mockito 的 `mockStatic`/`mockConstruction` mock `GoogleCloudStorageInputStream.create` 与相关 builder，验证：开启开关且 blobSize 已知/未知时分别用 `GcsFileInfo`/`GcsItemId` 创建包装流；创建抛 IOException 时回退到 `GCSInputStream`；开关关闭时直接走原路径。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/TestGcsInputStreamWrapper.java` (+146/-0 lines, 新增)

**修改目的**：单元测试 `GcsInputStreamWrapper` 的委托与指标计数行为。

**工作逻辑**：用 `@Mock` mock `GoogleCloudStorageInputStream`，验证 `read`/`seek`/`readFully`/`readTail`/`readVectored` 的委托调用与 `GcsObjectRange` 转换是否正确，并校验读字节/读操作计数器递增。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/TestPrefixedStorage.java` (+40/-0 lines, 新增)

**修改目的**：测试 `PrefixedStorage` 参数校验与 `GcsFileSystem` 构造。

**工作逻辑**：验证非法参数（null prefix、null properties）抛 `IllegalArgumentException`，以及 `gcsFileSystem()` 懒加载与 `GcsFileSystemOptions` 构造路径。

## 总结

本提交将 Google 的 `gcs-analytics-core` 分析优化库以可选开关的形式集成进 Iceberg GCP 模块，在不破坏原有读取链路的前提下为 GCS 上的大规模分析扫描提供了更高吞吐的读取路径，并通过 `GcsInputStreamWrapper` 对接 Iceberg 的 `RangeReadable`/向量化读接口。配套的容器集成测试与 mock 单元测试保证了正确性，对凭证逻辑的统一重构也顺手修复了 closeableGroup 初始化的隐患，是一次质量较高的功能集成。
