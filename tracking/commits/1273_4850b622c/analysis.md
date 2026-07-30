# 提交 1273：AWS: Support S3 directory bucket listing (#11021)

## 提交信息

- **序号**：1273 / 4088
- **哈希**：4850b622c778deb4b234880bfd7643070e0a5458
- **短哈希**：4850b622c
- **日期**：2024-10-24（Thu Oct 24 04:45:00 2024 +0100）
- **作者**：stubz151 <stubz151@gmail.com>
- **提交说明**：AWS: Support S3 directory bucket listing (#11021)
- **PR/Issue**：#11021

## 总体目的

AWS 在 2023 年底推出了 S3 Express One Zone（即"S3 目录桶 / directory bucket"），其桶名以 `--x-s3` 结尾（例如 `directory-bucket-usw2-az1--x-s3`）。与通用桶（general purpose bucket）相比，目录桶对 `ListObjectsV2` API 有一个特殊要求：当 prefix 不是一个"目录前缀"（即不以 `/` 结尾）时，行为不同，会要求 prefix 必须以 `/` 结尾才能正确列出该目录下的对象。

Iceberg 的 `S3FileIO.listPrefix(prefix)` 之前直接把传入的 prefix 透传给 `ListObjectsV2Request`，没有考虑目录桶这一特殊语义，因此当用户把 Iceberg 表存储在 S3 Express 目录桶上时，列举操作（如 `deletePrefix`、表元数据列举等）可能行为不符合预期甚至失败。

本提交让 S3FileIO 能够：
1. 自动识别 S3 目录桶（通过桶名 `--x-s3` 后缀）。
2. 当目标是目录桶且开关 `s3.directory-bucket.list-prefix-as-directory`（默认 true）打开时，自动把传入 prefix 转换为目录路径（追加 `/`），使 ListObjectsV2 行为符合目录桶语义。
3. 提供开关让用户在确需按非目录 prefix 列举（用于发现"目录"本身）时关闭自动补斜杠，从而让 S3 在不合法的列举请求上直接报错，保证正确性。
4. 同时重构集成测试工具，区分"通用桶清理"（按版本列举删除）与"目录桶清理"（仅按 ListObjectsV2 列举删除，目录桶不支持版本），并在所有 S3 Express 不支持的能力（KMS/SSE-C 加密、ACL、版本恢复、Access Point）上跳过对应集成测试。

## 如何达成设计目的

1. 在 `S3URI` 中新增静态常量 `S3_DIRECTORY_BUCKET_SUFFIX = "--x-s3"`，并新增 `useS3DirectoryBucket()` 与 `isS3DirectoryBucket(String)` 方法，通过桶名后缀判断是否为目录桶（仅做本地名称判断，不调用 S3 服务）。
2. 在 `S3URI` 中新增 `toDirectoryPath()` 方法，若 key 已以 `/` 结尾则返回自身，否则用 `scheme://bucket/key/` 格式构造新的 S3URI，保证 prefix 以 `/` 结尾。
3. 在 `S3FileIOProperties` 中新增配置项 `s3.directory-bucket.list-prefix-as-directory`（默认 true）及其字段、getter/setter，并从 properties 中解析。
4. 在 `S3FileIO.listPrefix` 中：
   - 先按原逻辑构造 `S3URI`；
   - 若该 URI 指向目录桶且开关打开，调用 `uri.toDirectoryPath()` 转换；
   - 用最终的 URI 构造 `ListObjectsV2Request`。
5. 在 `AwsIntegTestUtil` 中：
   - 将原 `cleanS3Bucket` 改名为 `cleanS3GeneralPurposeBucket`（仅语义更清晰，逻辑不变）；
   - 新增 `cleanS3DirectoryBucket`，使用 `ListObjectsV2Paginator` + 批量删除（不调用版本接口）；
   - 抽出 `deleteObjects` 私有方法。
6. 在 `TestS3FileIOIntegration` 中：根据 `S3URI.isS3DirectoryBucket(bucketName)` 在 setUp/tearDown 时分别走目录桶或通用桶的清理路径；并新增 `requireAccessPointSupport` / `requireKMSEncryptionSupport` / `requireVersioningSupport` / `requireACLSupport` 四个 `Assumptions.assumeThat` 守卫，在目录桶上跳过不兼容的测试。
7. 单测方面：
   - `TestS3URI` 新增 `testS3URIUseS3DirectoryBucket`、`testS3URIToDirectoryPath`；
   - `TestS3FileIOProperties` 新增 `testIsTreatS3DirectoryBucketListPrefixAsDirectoryEnabled`；
   - `TestS3FileIO` 新增 `testPrefixListWithExpressAddSlash`，通过 Mockito mock `S3Client` 验证目录桶场景下 `listPrefix` 真正以 `path/to/list/`（带尾斜杠）作为 prefix 发起请求，并校验返回的 `FileInfo` 数量、location、size 与 createdAtMillis。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3URI.java`

**修改目的**：让 S3URI 能识别目录桶并把 prefix 转换为目录路径。

**工作逻辑**：
- 新增常量 `S3_DIRECTORY_BUCKET_SUFFIX = "--x-s3"`。
- 新增 `public S3URI toDirectoryPath()`：若 `key` 已以 `PATH_DELIM`（`/`）结尾，返回 `this`；否则用 `String.format("%s://%s/%s/", scheme, bucket, key)` 构造新 S3URI。注意此方法不改变 bucket 与 scheme，只补尾斜杠。
- 新增 `public boolean useS3DirectoryBucket()`：基于解析后的最终 bucket 调用静态 `isS3DirectoryBucket`。
- 新增 `public static boolean isS3DirectoryBucket(String bucket)`：纯本地判断，`bucket.endsWith("--x-s3")`，注释明确不会调用 S3 服务。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java`

**修改目的**：新增目录桶列举行为开关。

**工作逻辑**：
- 新增配置 key `S3_DIRECTORY_BUCKET_LIST_PREFIX_AS_DIRECTORY = "s3.directory-bucket.list-prefix-as-directory"`，默认值 `true`。
- 新增字段 `private boolean s3DirectoryBucketListPrefixAsDirectory;`，在构造函数中初始化为默认值，并在 `applyProperty`（解析 properties）路径中通过 `PropertyUtil.propertyAsBoolean` 读取用户配置覆盖。
- 新增 getter/setter `isS3DirectoryBucketListPrefixAsDirectory()` 与 `setS3DirectoryBucketListPrefixAsDirectory(boolean)`。
- 配置项的 Javadoc 给出明确语义：默认 true 会在目录桶上自动补 `/`；设为 false 时如果用户列举的不是目录前缀，S3 会直接返回错误（用于检测/强制正确性场景）。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java`

**修改目的**：在 `listPrefix` 中根据桶类型与开关自动补斜杠。

**工作逻辑**：原方法直接 `S3URI s3uri = new S3URI(prefix, ...)`；现改为先 `S3URI uri = new S3URI(prefix, ...)`，若 `uri.useS3DirectoryBucket() && s3FileIOProperties.isS3DirectoryBucketListPrefixAsDirectory()` 则 `uri = uri.toDirectoryPath()`，再赋值给 `s3uri`。后续 `ListObjectsV2Request` 构造逻辑不变，使用最终的 `s3uri.bucket()` 与 `s3uri.key()`。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3URI.java`

**修改目的**：覆盖目录桶识别与目录路径转换。

**工作逻辑**：
- 定义测试桶名常量 `S3_DIRECTORY_BUCKET = "directory-bucket-usw2-az1--x-s3"`。
- `testS3URIUseS3DirectoryBucket`：验证直接 `s3://directory-bucket-usw2-az1--x-s3/...` 与通过 `bucketToAccessPointMapping` 把 `bucket` 映射到目录桶名两种情况下都返回 true；普通桶返回 false。
- `testS3URIToDirectoryPath`：验证无尾斜杠会补 `/`、已有尾斜杠保持不变、`s3a` 协议也支持、目录桶+映射组合都正确。

### `aws/src/test/java/org/apache/iceberg/aws/TestS3FileIOProperties.java`

**修改目的**：覆盖新开关可被 properties 关闭。

**工作逻辑**：`testIsTreatS3DirectoryBucketListPrefixAsDirectoryEnabled` 把 `s3.directory-bucket.list-prefix-as-directory` 设为 `"false"`，断言 `isS3DirectoryBucketListPrefixAsDirectory()` 返回 false（默认为 true 已被其他用例隐式覆盖）。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIO.java`

**修改目的**：通过 mock S3Client 验证 listPrefix 在目录桶上确实以带尾斜杠的 prefix 发起 ListObjectsV2 请求，并返回正确的 FileInfo。

**工作逻辑**：
- 引入 JUnit 4 的 `assertEquals`/`assertTrue`、Mockito、`S3Object`、`ListObjectsV2Request/Response`、`ListObjectsV2Iterable`、`FileInfo` 等。
- 定义常量 `S3_GENERAL_PURPOSE_BUCKET = "bucket"` 与 `S3_DIRECTORY_BUCKET = "directory-bucket-usw2-az1--x-s3"`，并把 `before()` 中创建的桶改用 `S3_GENERAL_PURPOSE_BUCKET` 常量。
- 新增 `testPrefixListWithExpressAddSlash`：分别在不显式设置和显式设置开关为 true 两种 properties 下调用 `assertPrefixIsAddedCorrectly`。
- `assertPrefixIsAddedCorrectly(String suffix, Map props)`：
  - 构造 `s3://<目录桶>/<suffix>` 作为 prefix；
  - mock 一个 `S3Client`，让其 `listObjectsV2Paginator` 在收到 `prefix="path/to/list/"`（带尾斜杠）、`bucket=<目录桶>` 的请求时返回两个 S3Object（file1.txt 1024B、file2.txt 2048B）；
  - 用该 mock 初始化 `S3FileIO` 并调用 `listPrefix`；
  - 收集返回的 `FileInfo` 列表，断言数量为 2，且 file1.txt 的 size=1024、createdAtMillis 在最近 120s 内，file2.txt 的 size=2048、createdAtMillis 早于 30s 前（即 lastModified 被正确转换为 createdAtMillis）。
- 注释说明：S3Mock 目前还不模拟 express 桶（createBucket 仍创建通用桶），因此必须用 Mockito 自行 mock，待 S3Mock 支持后可改造（代码中留有 TODO）。

### `aws/src/integration/java/org/apache/iceberg/aws/AwsIntegTestUtil.java`

**修改目的**：支持目录桶的清理。

**工作逻辑**：
- 新增常量 `BATCH_DELETION_SIZE = 1000`。
- 将原 `cleanS3Bucket` 改名为 `cleanS3GeneralPurposeBucket`，行为不变（按 `listObjectVersionsPaginator` 列举并按 1000 批量删除）。
- 新增 `cleanS3DirectoryBucket(S3Client, bucketName, prefix)`：先把 prefix 补上尾斜杠（若已结尾则不补），用 `ListObjectsV2Paginator` 列举对象，按 1000 批量调用 `deleteObjects`。
- 抽出私有 `deleteObjects(S3Client, bucket, List<ObjectIdentifier>)` 辅助方法。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/GlueTestBase.java`

**修改目的**：跟随工具方法改名。

**工作逻辑**：`afterClass` 中 `cleanS3Bucket` 调用替换为 `cleanS3GeneralPurposeBucket`。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIOIntegration.java`

**修改目的**：让 S3FileIO 集成测试能在目录桶上运行，并跳过目录桶不支持的能力。

**工作逻辑**：
- `beforeAll`：仅当 bucket 不是目录桶时才执行 `putBucketVersioning`、创建 Access Point（含跨区域）、获取 MRAP alias；目录桶不支持这些操作。
- `afterClass`：目录桶走 `cleanS3DirectoryBucket`（通过 `S3FileIO.client()` 取客户端）；通用桶走原 `cleanS3GeneralPurposeBucket` + 删除 Access Point + KMS key 调度删除。
- 多处 `cleanS3Bucket` 调用改名。
- 在以下测试方法首行新增守卫：
  - `requireAccessPointSupport()`：`testNewInputStreamWithAccessPoint`、`testNewInputStreamWithCrossRegionAccessPoint`、`testNewOutputStreamWithAccessPoint`、`testNewOutputStreamWithCrossRegionAccessPoint`、`testDeleteFilesMultipleBatchesWithAccessPoints`；
  - `requireKMSEncryptionSupport()`：`testServerSideKmsEncryption`、`testServerSideKmsEncryptionWithDefaultKey`、`testDualLayerServerSideKmsEncryption`、`testServerSideCustomEncryption`、`testDeleteFilesMultipleBatchesWithCrossRegionAccessPoints`（该方法实为依赖 KMS 加密的删除测试）；
  - `requireVersioningSupport()`：`testFileRecoveryHappyPath`、`testFileRecoveryFailsToRecover`；
  - `requireACLSupport()`：`testACL`。
- 四个 `requireXxx` 私有方法均使用 `Assumptions.assumeThat(S3URI.isS3DirectoryBucket(bucketName)).isFalse()`，在目录桶场景下让 JUnit 跳过该测试而非失败。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3MultipartUpload.java`

**修改目的**：跟随工具方法改名。

**工作逻辑**：`afterClass` 中 `cleanS3Bucket` 调用替换为 `cleanS3GeneralPurposeBucket`。

## 小结

- **成效**：S3FileIO 现在能够正确识别 S3 Express One Zone 目录桶，并在列举时按目录桶语义自动补全 prefix 尾斜杠；同时提供开关允许用户关闭自动行为以显式触发 S3 的目录校验错误。集成测试工具与测试用例也相应改造，使 Iceberg 的 AWS 集成测试矩阵可以覆盖目录桶。
- **影响范围**：仅 `aws/` 模块。主代码改动集中在 `S3URI`、`S3FileIOProperties`、`S3FileIO.listPrefix` 三处，向后兼容（默认行为对非目录桶无变化）。测试改动较多但都属增量。无 spec、API 或其他模块的破坏性变更。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 是维护分支，本身不会主动引入新功能；但 S3 Express One Zone 是 AWS 已正式发布且日益普及的存储类型，如果 1.4.x 用户在目录桶上跑 Iceberg，会遇到 `listPrefix` 行为不正确的问题（如 `deletePrefix` 漏删、表元数据列举异常）。此修复属于"适配云厂商新存储类型"的必要修复，**可考虑回迁**到 1.4.x。
  - 回迁时需把 `S3URI`、`S3FileIOProperties`、`S3FileIO` 三处主代码改动一并 port；测试改动可视情况取舍（目录桶集成测试需要真实目录桶环境，本地 unit 测试需要 Mockito 即可）。
  - 注意检查 1.4.x 中 `S3FileIO.listPrefix` 的实现是否与本提交所基于的版本一致；若 1.4.x 已有其他改动，需要合并而非直接套用。
  - 配置项 `s3.directory-bucket.list-prefix-as-directory` 是新增的，默认 true，对现有用户透明，不会破坏现有行为。
  - 若 1.4.x 不计划再发版，则无需回迁；若仍有 1.4.x 维护发版计划且用户有目录桶诉求，建议回迁。
