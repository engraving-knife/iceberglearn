# 提交 1237：[AWS] S3FileIO - Add Cross-Region Bucket Access (#11259)

## 提交信息

- **序号**：1237 / 4088
- **哈希**：3d9fc1dee1228e742e22234369498ee16b19f5a2
- **短哈希**：3d9fc1dee
- **日期**：2024-10-14（Mon Oct 14 22:37:44 2024 +0530）
- **作者**：S N Munendra <9696252+munendrasn@users.noreply.github.com>
- **提交说明**：[AWS] S3FileIO - Add Cross-Region Bucket Access (#11259)
- **PR/Issue**：#11259

## 总体目的

AWS S3 提供"跨区域访问桶"（Cross-Region bucket access）的能力：客户端可以在一个区域访问位于其他区域的桶，而不需要为每个区域单独配置客户端。AWS SDK for Java v2 在 S3ClientBuilder 上提供了 `crossRegionAccessEnabled(true)` 配置项来开启此行为。

Iceberg `S3FileIO` 之前已支持一系列 S3 服务级配置（`s3.dualstack-enabled`、`s3.use-arn-region-enabled`、`s3.path-style-access`、`s3.acceleration-enabled`），但缺少跨区域访问的开关。这意味着用户若使用部署在异区域的桶（例如 catalog 所在区域与数据桶区域不同），需要单独配置客户端或采用 access point 之类的变通方案。

本提交为 `S3FileIO` 新增 catalog 属性 `s3.cross-region-access-enabled`（默认 `false`），用户开启后，Iceberg 在构建 S3 客户端时会调用 `builder.crossRegionAccessEnabled(true)`，从而让 AWS SDK 自动处理跨区域访问。默认关闭是为了避免首次 S3 API 调用引入额外延迟（启用后 SDK 会发起跨区域探测）。

## 如何达成设计目的

1. 在 `S3FileIOProperties` 中：
   - 新增常量 `CROSS_REGION_ACCESS_ENABLED = "s3.cross-region-access-enabled"` 与默认值常量 `CROSS_REGION_ACCESS_ENABLED_DEFAULT = false`，附带 Javadoc 链接到 AWS 官方文档；
   - 新增字段 `private final boolean isCrossRegionAccessEnabled;`，在两个构造函数（无参默认与基于 properties 的）中分别初始化为默认值与从 properties 解析的值；
   - 新增 getter `isCrossRegionAccessEnabled()`；
   - 在 `applyServiceConfigurations(T builder)` 中追加 `builder.crossRegionAccessEnabled(isCrossRegionAccessEnabled)`，并更新方法 Javadoc 把 `crossRegionAccessEnabled` 加入支持项列表。
2. 在 `docs/docs/aws.md` 中新增 "S3 Cross-Region Access" 章节，说明用法、默认值与示例 spark-sql 启动命令，并附 AWS 官方文档链接。
3. 测试侧：
   - 在 `TestS3FileIOProperties` 单测中：默认值断言、`toString`/`properties` map 断言、`applyS3ServiceConfigurations` mock 验证均补上对 `CROSS_REGION_ACCESS_ENABLED` 的覆盖，包括 mock builder 的 `crossRegionAccessEnabled` 方法。
   - 在 `TestS3FileIOIntegration` 集成测试中：新增 `testCrossRegionAccessEnabled`，开启属性后用 `S3Client` 把对象写入 `crossRegionBucketName`（一个跨区域桶），再用 `S3FileIO` 读取该对象的 s3 URI 验证内容一致；并提取 `validateRead(S3FileIO, String s3Uri)` 重载方法复用现有逻辑。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java`

**修改目的**：新增跨区域访问的属性定义、字段、解析与对客户端的配置注入。

**工作逻辑**：
- 新增静态常量与 Javadoc：
  ```java
  public static final String CROSS_REGION_ACCESS_ENABLED = "s3.cross-region-access-enabled";
  public static final boolean CROSS_REGION_ACCESS_ENABLED_DEFAULT = false;
  ```
- 新增实例字段 `private final boolean isCrossRegionAccessEnabled;`，并在两个构造函数中赋值：无参构造置为默认 false；带 properties 的构造用 `PropertyUtil.propertyAsBoolean(properties, CROSS_REGION_ACCESS_ENABLED, CROSS_REGION_ACCESS_ENABLED_DEFAULT)` 解析。
- 新增 `public boolean isCrossRegionAccessEnabled()` getter。
- 在 `applyServiceConfigurations(T builder)` 中链式追加 `.crossRegionAccessEnabled(isCrossRegionAccessEnabled)`，与既有 `dualstackEnabled` 等并列；Javadoc 同步更新支持项。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIOProperties.java`

**修改目的**：单元测试覆盖新属性。

**工作逻辑**：
- 默认值测试：断言 `CROSS_REGION_ACCESS_ENABLED_DEFAULT == s3FileIOProperties.isCrossRegionAccessEnabled()`。
- `toString` 测试：在 properties map 中加入 `CROSS_REGION_ACCESS_ENABLED` 的对应条目断言。
- 构造覆盖测试（`testS3FileIOPropertiesWithDeserialization` 等）：在输入 map 中加入 `"s3.cross-region-access-enabled" -> "true"`。
- `testApplyS3ServiceConfigurations`：mock builder 增加 `Mockito.doReturn(mockA).when(mockA).crossRegionAccessEnabled(Mockito.anyBoolean());`，验证调用链确实传入了 `crossRegionAccessEnabled`。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIOIntegration.java`

**修改目的**：端到端验证开启跨区域访问后能正确读写异区域桶。

**工作逻辑**：
- 新增 `testCrossRegionAccessEnabled`：用 `clientFactory.initialize(...CROSS_REGION_ACCESS_ENABLED=true)`、获取 S3Client、向 `crossRegionBucketName` 写入随机 key 的对象；构造 `S3FileIO(s3FileIO, clientFactory::s3)`，调用 `validateRead(s3FileIO, crossBucketObjectUri)` 验证读取内容一致；finally 中清理对象。
- 重构 `validateRead`：抽出 `validateRead(S3FileIO, String s3Uri)` 重载，原 `validateRead(S3FileIO)` 委托给 `validateRead(s3FileIO, objectUri)`。

### `docs/docs/aws.md`

**修改目的**：用户文档新增 S3 跨区域访问章节。

**工作逻辑**：在 "S3 Access Grants" 之后、"S3 Acceleration" 之前插入 "S3 Cross-Region Access" 段落，说明：
- 属性名 `s3.cross-region-access-enabled`，默认 false（关闭以避免首次 API 调用增加延迟）；
- 给出 Spark 3.3 启动 spark-sql 的示例命令（包含 `--conf spark.sql.catalog.my_catalog.s3.cross-region-access-enabled=true`）；
- 附 AWS 官方文档链接 "Cross-Region access for Amazon S3"。

## 小结

- **成效**：`S3FileIO` 现支持 `s3.cross-region-access-enabled` 属性，开启后通过 AWS SDK 的 `crossRegionAccessEnabled(true)` 让 S3 客户端可访问异区域桶；默认关闭以避免首调延迟。文档与单测、集成测试同步覆盖。
- **影响范围**：仅 `aws` 模块与文档；新增一个对外 catalog 属性（默认 false，向后兼容）。`S3FileIOProperties.applyServiceConfigurations` 行为仅在用户显式开启时变化，对存量用户无影响。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个新功能（new feature），非 bug 修复。回迁需考虑 1.4.x 的 AWS SDK 版本是否支持 `S3ClientBuilder.crossRegionAccessEnabled(boolean)` 方法。该方法在 AWS SDK 2.x 较早版本就已存在，1.4.x 应已支持，但需确认 BOM 版本。
  - 文档（aws.md）与测试同步回迁。
  - 由于属性默认关闭，回迁不会改变存量用户行为；只有显式开启的用户会受影响，风险较低。
  - 注意集成测试依赖一个真实的 `crossRegionBucketName`（异区域桶），1.4.x CI 环境需要相应 AWS 凭证与桶配置才能跑通该集成测试。
