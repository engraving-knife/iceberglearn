# 提交 0785：AWS: Support S3 DSSE-KMS encryption (#8370)

## 提交信息

- **序号**：0785 / 4088
- **哈希**：311dbbb10dfa41fd22966ec87a03d449d0d84f96
- **短哈希**：311dbbb10
- **日期**：2024-05-25 01:23:02 +0900
- **作者**：Akira Ajisaka
- **提交说明**：AWS: Support S3 DSSE-KMS encryption (#8370)
- **PR/Issue**：#8370

## 总体目的

本提交为 Iceberg AWS 模块的 `S3FileIO` 增加对 S3 双层服务端加密 with AWS KMS keys（DSSE-KMS，Dual-layer Server-Side Encryption with AWS Key Management Service keys）的支持。此前 `S3FileIO` 已支持三种 S3 服务端加密：`none`（不加密）、`s3`（SSE-S3，AES256）、`kms`（SSE-KMS）、`custom`（SSE-C，客户自管密钥）。DSSE-KMS 是 AWS 于 2022 年推出的加密方式，与 SSE-KMS 类似但**对对象施加两层加密**，满足某些合规标准（如要求多层加密、完全自主管控密钥的监管场景）。本提交新增 `dsse-kms` 这一 `s3.sse.type` 取值，使用户在合规要求下可直接通过 Iceberg 配置启用 DSSE-KMS，无需绕过 Iceberg 自行实现。

## 如何达成设计目的

### 设计背景：S3 加密类型与 Iceberg 的抽象

`S3FileIO` 通过 `S3FileIOProperties` 持有加密相关配置：
- `s3.sse.type`：加密类型，取值为 `none`/`s3`/`kms`/`custom`（本提交新增 `dsse-kms`）。
- `s3.sse.key`：KMS Key ID/ARN（用于 `kms`/`dsse-kms`）或 base-64 AES256 对称密钥（用于 `custom`）。
- `s3.sse.md5`：仅 `custom` 类型需要的密钥 MD5 校验值。

`S3RequestUtil.configureEncryption` 是统一为各类 S3 请求（`PutObject`、`CreateMultipartUpload`、`UploadPart`、`GetObject`、`HeadObject`）设置加密参数的工具方法。它根据 `sseType` 做 `switch`（先 `toLowerCase(Locale.ENGLISH)` 归一化），对每种类型调用对应的 AWS SDK setter：
- `kms`：`serverSideEncryption = AWS_KMS`，`ssekmsKeyId = sseKey`。
- `s3`：`serverSideEncryption = AES256`。
- `custom`：`sseCustomerAlgorithm = AES256`，`sseCustomerKey = sseKey`，`sseCustomerKeyMD5 = sseMd5`。

AWS SDK 的 `ServerSideEncryption` 枚举已包含 `AWS_KMS_DSSE` 值（对应 DSSE-KMS），但 Iceberg 未暴露取用入口。

### 设计逻辑：新增 dsse-kms 类型，复用 kms 的密钥路径

DSSE-KMS 与 SSE-KMS 在配置层面几乎一致——都使用一个 KMS Key ID/ARN 作为密钥，区别仅在 S3 服务端应用几层加密。因此设计上：

1. **新增类型常量** `DSSE_TYPE_KMS = "dsse-kms"`，与既有 `SSE_TYPE_KMS = "kms"`、`SSE_TYPE_S3 = "s3"`、`SSE_TYPE_CUSTOM = "custom"` 并列。命名上用 `DSSE_` 前缀而非 `SSE_`，以区分"双层"语义。

2. **`s3.sse.key` 语义扩展**：将 `SSE_KEY` 的 javadoc 从"若 SSE-KMS，输入 KMS Key ID/ARN"扩展为"若 SSE-KMS **或 DSSE-KMS**，输入 KMS Key ID/ARN"。默认 key `aws/s3` 同样适用于两种类型。无需新增配置属性，复用既有 `s3.sse.key`，降低用户认知成本。

3. **`S3RequestUtil.configureEncryption` 新增 case 分支**：

```java
case S3FileIOProperties.DSSE_TYPE_KMS:
  encryptionSetter.apply(ServerSideEncryption.AWS_KMS_DSSE);
  kmsKeySetter.apply(s3FileIOProperties.sseKey());
  break;
```

与 `kms` 分支对称，仅 `ServerSideEncryption` 取值从 `AWS_KMS` 改为 `AWS_KMS_DSSE`，密钥设置逻辑完全相同（`kmsKeySetter.apply(sseKey)`）。`encryptionSetter` 与 `kmsKeySetter` 是 `Function` 类型的回调，由各请求 Builder 的方法引用提供（如 `requestBuilder::serverSideEncryption`、`requestBuilder::ssekmsKeyId`），因此同一套逻辑覆盖 `PutObject`、`CreateMultipartUpload` 等所有需要 SSE 的请求。对 `UploadPart`/`GetObject`/`HeadObject`，其 `encryptionSetter`/`kmsKeySetter` 是 `NULL_SSE_SETTER`/`NULL_STRING_SETTER`（这些请求不设置服务端加密参数，仅 SSE-C 的 customer key 在 part 请求中需要），DSSE-KMS 分支调用这些空 setter 不会产生副作用，行为正确。

4. **switch 的 fall-through 设计未变**：每个 case 都有 `break`，DSSE-KMS 独立分支不与 `kms` 合并，保持代码可读性与后续若需差异化处理的空间。

### 文档与测试

1. **`docs/docs/aws.md`**：在加密类型说明中新增 DSSE-KMS 段落（链接 AWS 官方 DSSE 文档，说明双层加密与合规用途），并在配置表 `s3.sse.type` 的取值列加入 `dsse-kms`，`s3.sse.key` 的说明同步为"对 `kms` 和 `dsse-kms` 类型有效"。

2. **单元测试 `TestS3RequestUtil.testConfigureDualLayerServerSideKmsEncryption`**：构造 `sseType=dsse-kms`、`sseKey=key` 的属性，调用 `configureEncryption`，断言 `serverSideEncryption = AWS_KMS_DSSE`、`kmsKeyId = key`、其余 custom 相关字段（`customAlgorithm`/`customKey`/`customMd5`）为 null。验证 setter 调用正确且未误触 custom 路径。

3. **集成测试 `TestS3FileIOIntegration.testDualLayerServerSideKmsEncryption`**：用 `DSSE_TYPE_KMS` + `kmsKeyArn` 创建 `S3FileIO`，`write` 写入文件，`validateRead` 读取校验，再用 `s3.getObject` 取响应头断言 `serverSideEncryption = AWS_KMS_DSSE` 且 `ssekmsKeyId = kmsKeyArn`。这是端到端验证，确认 S3 实际按 DSSE-KMS 加密存储。集成测试需真实 S3 环境与 KMS key（`kmsKeyArn` 由测试环境提供）。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java`

**修改目的**：新增 `DSSE_TYPE_KMS` 常量，扩展 `SSE_KEY` 文档。

**修改点 1**：新增常量（紧随 `SSE_TYPE_KMS = "kms"` 之后）：

```java
/**
 * S3 DSSE-KMS encryption.
 *
 * <p>For more details:
 * https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingDSSEncryption.html
 */
public static final String DSSE_TYPE_KMS = "dsse-kms";
```

**修改点 2**：`SSE_KEY` 的 javadoc 由"If S3 encryption type is SSE-KMS, input is a KMS Key ID or ARN..."改为"If S3 encryption type is SSE-KMS or DSSE-KMS, input is a KMS Key ID or ARN..."，明确两种类型共用此属性。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3RequestUtil.java`

**修改目的**：在加密 switch 中新增 `dsse-kms` 分支。

**修改内容**（`configureEncryption` 方法，`SSE_TYPE_KMS` 分支之后）：

```java
case S3FileIOProperties.DSSE_TYPE_KMS:
  encryptionSetter.apply(ServerSideEncryption.AWS_KMS_DSSE);
  kmsKeySetter.apply(s3FileIOProperties.sseKey());
  break;
```

`ServerSideEncryption.AWS_KMS_DSSE` 是 AWS SDK 既有的枚举值，无需额外依赖。其余分支（`s3`/`custom`/`default` 抛异常）不变。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3RequestUtil.java`

**修改目的**：单元测试覆盖 `dsse-kms` 分支。

**修改内容**：新增 `testConfigureDualLayerServerSideKmsEncryption`：设置 `sseType=DSSE_TYPE_KMS`、`sseKey="key"`，调用 `S3RequestUtil.configureEncryption`（传入各 setter 方法引用），断言 `serverSideEncryption=AWS_KMS_DSSE`、`kmsKeyId="key"`、`customAlgorithm/customKey/customMd5` 均 null。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIOIntegration.java`

**修改目的**：端到端集成测试验证 S3 实际按 DSSE-KMS 加密。

**修改内容**：新增 `testDualLayerServerSideKmsEncryption`：用 `DSSE_TYPE_KMS` + `kmsKeyArn` 构造 `S3FileIO`，`write`+`validateRead` 验证读写正确，再用 `s3.getObject` 断言响应头 `serverSideEncryption=AWS_KMS_DSSE` 与 `ssekmsKeyId=kmsKeyArn`。

### `docs/docs/aws.md`

**修改目的**：文档化新加密类型。

**修改内容**：
1. 加密类型说明段落新增 DSSE-KMS 条目，链接 https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingDSSEncryption.html，说明双层加密与合规用途。
2. 配置表 `s3.sse.type` 取值列由 `none`/`s3`/`kms`/`custom` 改为 `none`/`s3`/`kms`/`dsse-kms`/`custom`。
3. `s3.sse.key` 默认值列由 `aws/s3` for `kms` type 改为 `aws/s3` for `kms` and `dsse-kms` types；描述同步扩展。

## 小结

- **成效**：Iceberg `S3FileIO` 现支持 S3 DSSE-KMS 双层服务端加密，用户通过 `s3.sse.type=dsse-kms`（配合 `s3.sse.key` 指定 KMS Key ID/ARN）即可启用，满足需要多层加密的合规场景。改动复用了既有 SSE-KMS 的密钥配置路径（`s3.sse.key`），用户认知成本低；单元测试 + 集成测试 + 文档三件套齐全。
- **影响范围**：仅影响 AWS 模块（`aws/`）的 S3 加密配置路径。新增类型是纯增量（新 case 分支），对既有 `none`/`s3`/`kms`/`custom` 用户无任何行为变化。所有走 `S3FileIO` 的写入（PutObject、CreateMultipartUpload）与读取路径在用户配置 `dsse-kms` 时会正确设置 `AWS_KMS_DSSE`。`S3RequestUtil` 是 `S3FileIO` 内部工具，不影响其他 FileIO 实现（如 `GlueFileIO` 复用 `S3FileIO` 故同样受益）。
- **回迁注意事项**：
  1. 本提交依赖 AWS SDK 中 `ServerSideEncryption.AWS_KMS_DSSE` 枚举值的存在。该枚举在 AWS SDK 2.x 较早版本即有，1.4.x 分支的 AWS SDK 版本（结合 0781 升级到 2.25.57）必然支持，无依赖问题。
  2. `DSSE_TYPE_KMS = "dsse-kms"` 含连字符，而 `configureEncryption` 的 switch 先对 `sseType` 做 `toLowerCase(Locale.ENGLISH)` 归一化（不剔除连字符），所以用户写 `dsse-kms`/`DSSE-KMS`/`Dsse-Kms` 都能匹配。回迁后需确认 1.4.x 的 `S3FileIOProperties` 中 `sseType` 的 setter 未对取值做额外规整（如剔除连字符），否则可能失配。
  3. 集成测试 `testDualLayerServerSideKmsEncryption` 需要真实 S3 + KMS 环境（`kmsKeyArn`、`bucketName` 等由集成测试基类提供），回迁后默认不随单元测试运行，需在 CI 的 AWS 集成测试 profile 下执行。
  4. 文档 `docs/docs/aws.md` 的改动应一并回迁，避免 1.4.x 文档与代码脱节。
  5. 若 1.4.x 分支已有其他对 `S3RequestUtil.configureEncryption` switch 的改动（如新增其他加密类型），cherry-pick 时在 switch 处可能冲突，需手动合并保持各 case 独立 `break`。
