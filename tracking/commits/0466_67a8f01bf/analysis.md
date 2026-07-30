# 提交 0466：AWS: Add S3 Access Grants Documentation (#9590)

## 提交信息

- **序号**：0466
- **哈希**：67a8f01bff801f42be5cea303cf26667cecf4d79
- **短哈希**：67a8f01bf
- **日期**：2024-02-05 08:34:21 -0800
- **作者**：Adnan Hemani <ahemani@amazon.com>
- **提交说明**：AWS: Add S3 Access Grants Documentation (#9590)
- **PR/Issue**：#9590

## 总体目的

本提交是一个纯文档变更，目的是在 Iceberg 的 AWS 集成文档中补充 S3 Access Grants 这一访问控制机制的使用说明。S3 Access Grants 是 AWS 提供的、基于 IAM Principals 来授予 S3 数据访问权限的特性，与传统的直接通过 IAM 策略或 Bucket Policy 管理权限不同，它允许在更细粒度的对象/前缀层级上做权限管理，并通过统一的授权层将访问决策下放到 S3 Access Grants 服务。Iceberg 的 `S3FileIO` 通过 AWS SDK v2 的扩展机制接入这一能力，但相关配置和依赖项对用户而言并不直观，需要文档来引导。

在没有本提交之前，`docs/docs/aws.md` 中已经存在针对 access points、S3 Acceleration、客户端加密等特性的章节，但唯独缺少 S3 Access Grants 的说明。这意味着用户即便知道 Iceberg 支持 S3 Access Grants（通过 `s3.access-grants.enabled` 属性），也需要去翻阅 PR、源码或 AWS 文档才能拼凑出正确的使用方法，包括如何引入 S3 Access Grants Plugin jar、如何配置 `s3.access-grants.enabled` 与 `s3.access-grants.fallback-to-iam` 这两个 catalog 属性，以及 fallback-to-IAM 的语义。本提交正是填补这一文档空白。

从设计意图看，文档选择放在 `### S3 Access Grants` 子章节，并紧接在 `### Access Points` 之后、`### S3 Acceleration` 之前，把同类"S3 访问/授权增强"特性归并在一起，保持文档结构的连贯性。文档不仅给出了属性说明，还提供了一个可直接复制运行的 Spark 3.3 `spark-sql` 启动示例，让用户能快速验证集成效果，降低上手门槛。

## 如何达成设计目的

实现路径非常直接：在 `docs/docs/aws.md` 中 access points 小节末尾的"For more details ..."段落之后、`### S3 Acceleration` 标题之前，新增一段 24 行的 Markdown 文本，包含一段特性概述、两个 catalog 属性（`s3.access-grants.enabled` 与 `s3.access-grants.fallback-to-iam`）的说明、一个 Spark SQL 启动示例代码块，以及指向 AWS 官方文档的进一步阅读链接。没有修改任何代码、配置或构建脚本。

## 修改详情

### docs/docs/aws.md

**修改目的**：为 AWS 集成文档新增 S3 Access Grants 小节，说明如何在 Iceberg 中启用并配置该特性。

**工作逻辑**：新增内容位于 access points 小节之后（diff 上下文中的 `For more details on using access-points ...` 一行之后），共 24 行插入，无删除。新增内容结构如下：

1. **小节标题与特性概述**：以 `### S3 Access Grants` 开始，用一句话介绍 S3 Access Grants 的作用（"grant accesses to S3 data using IAM Principals"），并说明启用前置条件——将 `s3.access-grants.enabled` catalog 属性设为 `true`，并需要把 [S3 Access Grants Plugin jar](https://github.com/aws/aws-s3-accessgrants-plugin-java-v2) 加入 classpath。文中同时给出了该插件的 Maven Central 链接，方便用户直接引入依赖。

2. **fallback-to-IAM 配置说明**：解释 `s3.access-grants.fallback-to-iam` 布尔属性的作用——当 S3 Access Grants 无法授权某次 S3 调用时，回退到使用 IAM 角色及其权限集合直接访问数据。并明确该属性默认值为 `false`，即默认不回退，强制走 Access Grants 授权路径。

3. **Spark 3.3 启动示例**：给出一段带 `spark-sql` 命令的代码块，演示如何在 Spark SQL shell 启动时同时配置 GlueCatalog、S3FileIO 以及两个 S3 Access Grants 相关属性：
   ```
   spark-sql --conf spark.sql.catalog.my_catalog=org.apache.iceberg.spark.SparkCatalog \
       --conf spark.sql.catalog.my_catalog.warehouse=s3://my-bucket2/my/key/prefix \
       --conf spark.sql.catalog.my_catalog.catalog-impl=org.apache.iceberg.aws.glue.GlueCatalog \
       --conf spark.sql.catalog.my_catalog.io-impl=org.apache.iceberg.aws.s3.S3FileIO \
       --conf spark.sql.catalog.my_catalog.s3.access-grants.enabled=true \
       --conf spark.sql.catalog.my_catalog.s3.access-grants.fallback-to-iam=true
   ```
   示例同时把 `enabled` 与 `fallback-to-iam` 都设为 `true`，让用户在一个示例里看到两种配置的写法。

4. **进一步阅读链接**：在示例之后给出指向 AWS 官方文档 [Managing access with S3 Access Grants](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-grants.html) 的链接，便于用户深入了解 Access Grants 本身的模型与权限管理流程。

整体而言，新增内容遵循了 `aws.md` 中其它小节（如 Access Points、S3 Acceleration）统一的写作范式：先一句话定位特性 → 给出 Iceberg 侧的配置属性 → 提供可运行的示例 → 给出 AWS 官方延伸阅读链接，保持文档风格一致。

## 小结

本提交是 Iceberg 1.4.x 落后于 main 的一个文档补全类提交，通过在 `docs/docs/aws.md` 新增 24 行 Markdown，完整介绍了 S3 Access Grants 这一访问控制特性在 Iceberg 中的启用方式、两个相关 catalog 属性（`s3.access-grants.enabled`、`s3.access-grants.fallback-to-iam`）的语义、一个 Spark 3.3 启动示例以及 AWS 官方延伸阅读链接。改动不涉及任何代码逻辑，纯文档，风险极低，但对实际部署使用 S3 Access Grants 的用户而言能显著降低集成成本。
