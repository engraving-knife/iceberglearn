# 提交 1095：AWS: Include http-auth-aws-crt module into iceberg-aws-bundle (#10972)

## 提交信息

- **序号**：1095 / 4088
- **哈希**：b9a6645a53a1a7a5143a58b23adaae1ea0af99d4
- **短哈希**：b9a6645a5
- **日期**：2024-08-25 11:47:35 +0900（commit 信息中标注 -0600 为作者本地时区显示差异）
- **作者**：Akira Ajisaka
- **提交说明**：AWS: Include http-auth-aws-crt module into iceberg-aws-bundle (#10972)
- **PR/Issue**：#10972

## 总体目的

本提交将 AWS SDK 的 `http-auth-aws-crt` 模块纳入 Iceberg 的 AWS 相关构建（`iceberg-aws`、`iceberg-aws-bundle`、`iceberg-kafka-connect-runtime`），并新增 S3 Multi-Region Access Point（多区域访问点）的集成测试覆盖。

AWS SDK 2.x 中，`http-auth-aws-crt` 模块提供了基于 AWS CRT（Common Runtime）的 HTTP 认证签名能力。当使用 S3 Multi-Region Access Point（MRAP）等需要 SigV4A 签名的场景时，需要该模块提供 SigV4A 签名实现。此前 Iceberg 的 `iceberg-aws-bundle` 未包含该模块，导致 bundle 用户在使用 S3 MRAP 时会因缺少 SigV4A 签名器而失败。

为此本提交在三个构建文件中显式声明对 `software.amazon.awssdk:http-auth-aws-crt` 的依赖（`iceberg-aws` 为 `compileOnly`，`iceberg-aws-bundle` 与 `iceberg-kafka-connect-runtime` 为 `implementation`），并新增 `testMultiRegionAccessPointAlias` 测试工具方法及两个 MRAP 集成测试用例，验证通过 MRAP ARN 读写对象的正确性。同时顺带将测试中多处 `InputStream`/`OutputStream` 的手动 `close()` 改为 try-with-resources，提升资源管理健壮性。

## 如何达成设计目的

1. **依赖声明**：在 `build.gradle`（`iceberg-aws` 项目）中以 `compileOnly` 加入 `http-auth-aws-crt`（编译期需要，运行期由下游/bundle 提供）；在 `aws-bundle/build.gradle` 与 `kafka-connect/build.gradle` 中以 `implementation` 加入该模块（打包进 bundle 供运行期使用）。

2. **集成测试支持**：在 `AwsIntegTestUtil` 新增 `testMultiRegionAccessPointAlias()` 读取环境变量 `AWS_TEST_MULTI_REGION_ACCESS_POINT_ALIAS`；在 `TestS3FileIOIntegration` 中新增 `multiRegionS3Control` 等字段、`testMultiRegionAccessPointARN(...)` 工具方法（构造 MRAP ARN：`arn:<partition>:s3::<account-id>:accesspoint/<alias>`），并新增两个测试 `testNewInputStreamWithMultiRegionAccessPoint` 与 `testNewOutputStreamWithMultiRegionAccessPoint`，通过 `Assumptions.assumeThat(alias).isNotEmpty()` 在未配置 MRAP alias 时跳过。

3. **资源管理清理**：将 `write`、`validateRead`、`testNewOutputStream`、`testNewOutputStreamWithAccessPoint`、`testNewOutputStreamWithCrossRegionAccessPoint` 等方法中的流操作改为 try-with-resources。

4. **LICENSE/NOTICE 更新**：因新增 `http-auth-aws-crt` 依赖，同步更新 `aws-bundle/LICENSE` 与 `NOTICE` 文件以反映该模块的许可证信息。

## 修改详情

### `build.gradle`

**修改目的**：为 `iceberg-aws` 项目添加 `http-auth-aws-crt` 编译期依赖。

**工作逻辑**：在 `project(':iceberg-aws')` 的 dependencies 块中，于 `compileOnly("software.amazon.awssdk:auth")` 之后新增 `compileOnly("software.amazon.awssdk:http-auth-aws-crt")`。使用 `compileOnly` 表明该模块在编译期需要（用于 SigV4A 签名相关 API），运行期由 bundle 或下游用户提供。

### `aws-bundle/build.gradle`

**修改目的**：将 `http-auth-aws-crt` 打入 `iceberg-aws-bundle`。

**工作逻辑**：在 `project(":iceberg-aws-bundle")` 的 dependencies 中，于 `implementation "software.amazon.awssdk:auth"` 之后新增 `implementation "software.amazon.awssdk:http-auth-aws-crt"`，使该模块被打包进 bundle 的 shaded jar，供 bundle 用户直接使用 SigV4A 签名能力。

### `kafka-connect/build.gradle`

**修改目的**：将 `http-auth-aws-crt` 纳入 kafka-connect runtime 依赖。

**工作逻辑**：在 `project(':iceberg-kafka-connect:iceberg-kafka-connect-runtime')` 的 dependencies 中，于 `implementation 'software.amazon.awssdk:auth'` 之后新增 `implementation "software.amazon.awssdk:http-auth-aws-crt"`，使 kafka-connect runtime 也具备 SigV4A 能力。

### `aws/src/integration/java/org/apache/iceberg/aws/AwsIntegTestUtil.java`

**修改目的**：提供 MRAP alias 环境变量读取方法。

**工作逻辑**：新增 `public static String testMultiRegionAccessPointAlias()`，返回环境变量 `AWS_TEST_MULTI_REGION_ACCESS_POINT_ALIAS`。注释说明开发者需在运行集成测试前预先创建 MRAP（创建耗时数分钟）。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIOIntegration.java`

**修改目的**：新增 MRAP 读写集成测试，并将流操作改为 try-with-resources。

**工作逻辑**：
- 新增字段 `multiRegionS3Control` 与 `multiRegionAccessPointAlias`；在 `@BeforeAll` 中通过 `AwsIntegTestUtil.testMultiRegionAccessPointAlias()` 读取 alias。
- 新增 `testMultiRegionAccessPointARN(region, alias)`：构造 MRAP ARN `arn:<partition>:s3::<accountId>:accesspoint/<alias>`。
- 新增 `testNewInputStreamWithMultiRegionAccessPoint`：初始化 `USE_ARN_REGION_ENABLED=true`，用原生 S3Client 上传对象，再用 `S3FileIO`（配置 MRAP ARN 作为 access point）读取并验证内容一致。
- 新增 `testNewOutputStreamWithMultiRegionAccessPoint`：类似地通过 `S3FileIO` 写入 MRAP 再用原生 S3 读取验证。
- 两个 MRAP 测试均用 `Assumptions.assumeThat(multiRegionAccessPointAlias).isNotEmpty()` 跳过未配置环境。
- 将 `write(S3FileIO, String)`、`validateRead(S3FileIO)`、`testNewOutputStream`、`testNewOutputStreamWithAccessPoint`、`testNewOutputStreamWithCrossRegionAccessPoint` 中的 `InputStream`/`OutputStream` 手动 close 改为 try-with-resources，避免资源泄漏。

### `aws-bundle/LICENSE` 与 `aws-bundle/NOTICE`

**修改目的**：因引入 `http-auth-aws-crt` 模块，同步更新 bundle 的 LICENSE 与 NOTICE 文件，反映该模块及其依赖的许可证信息。

**工作逻辑**：机械更新许可证文件内容，添加 `http-auth-aws-crt` 相关条目并调整现有条目顺序/格式以保持一致性（LICENSE 改动约 140 行，NOTICE 改动约 67 行）。

## 小结

- **成效**：使 `iceberg-aws-bundle` 与 kafka-connect runtime 包含 `http-auth-aws-crt` 模块，从而支持 S3 Multi-Region Access Point 所需的 SigV4A 签名；新增 MRAP 集成测试覆盖读写路径；顺带用 try-with-resources 提升测试资源管理健壮性。
- **影响范围**：涉及 `build.gradle`、`aws-bundle/build.gradle`、`kafka-connect/build.gradle` 三个构建文件，`aws/src/integration/` 下两个集成测试文件，以及 `aws-bundle/LICENSE`、`aws-bundle/NOTICE` 两个许可证文件，共 7 个文件。
- **回迁到 1.4.x 的注意事项**：此改动修复了 bundle 对 S3 MRAP 的支持缺失，属于功能性 bug 修复，**可考虑回迁到 1.4.x**（若 1.4.x 用户需要 MRAP 支持）。回迁时需注意：1.4.x 的 AWS SDK 版本可能不同，需确认对应版本的 `http-auth-aws-crt` 模块坐标与 SigV4A API 是否一致；LICENSE/NOTICE 的更新需随依赖一并回迁以保持合规。集成测试为可选回迁项（依赖 MRAP 环境变量，不回迁不影响功能）。
