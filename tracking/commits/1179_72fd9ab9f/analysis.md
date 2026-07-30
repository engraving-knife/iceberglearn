# 提交 1179：Docs: Document AWS Redshift and Amazon Data Firehose support (#11192)

## 提交信息

- **序号**：1179 / 4088
- **哈希**：72fd9ab9f44cfb3e4b08f849cf7b5132f84b9936
- **短哈希**：72fd9ab9f
- **日期**：2024-09-23（Mon Sep 23 17:41:39 2024 -0700）
- **作者**：Prashant Singh <35593236+singhpk234@users.noreply.github.com>
- **提交说明**：Docs: Document AWS Redshift and Amazon Data Firehose support (#11192)
- **PR/Issue**：#11192
- **共同作者**：Prashant Singh <psinghvk@amazon.com>

## 总体目的

Iceberg 文档站点（基于 mkdocs）的 `docs/docs/aws.md` 页面汇总了 AWS 生态对 Apache Iceberg 的支持情况，包括 Amazon Athena、Amazon EMR、Amazon Kinesis Data Analytics 等。该页面在 "Engines" 章节还通过 `docs/mkdocs.yml` 的 `nav` 维护一个外部引擎链接列表，方便用户跳转到各引擎官方文档。

本提交的目的是补全两个此前未在文档中明确提及的 AWS 服务：
1. **AWS Redshift**（含 Redshift Spectrum 与 Redshift Serverless）：支持查询 cataloged 在 AWS Glue Data Catalog 的 Iceberg 表。
2. **Amazon Data Firehose**：可直接把流式数据投递到 Amazon S3 上的 Apache Iceberg 表，支持从单一流路由到不同 Iceberg 表，并自动应用 insert/update/delete 操作；该功能要求使用 AWS Glue Data Catalog。

通过补充这两段文档与对应导航链接，让用户清楚知道这两个 AWS 服务对 Iceberg 的集成能力与官方文档入口。这是纯文档改动，无任何代码或构建逻辑变更。

## 如何达成设计目的

两处改动：
1. 在 `docs/docs/aws.md` 末尾追加两个小节（`### AWS Redshift`、`### Amazon Data Firehose`），简述各自对 Iceberg 的支持方式并附官方文档链接。
2. 在 `docs/mkdocs.yml` 的 `nav` 中 "Engines" 列表里（Amazon Athena、Amazon EMR 之后）追加 `Amazon Data Firehose` 与 `Amazon Redshift` 两个外链条目，让文档站点侧边栏可直接跳转。

## 修改详情

### `docs/docs/aws.md`

**修改目的**：在 AWS 文档页正文追加 Redshift 与 Firehose 两个支持小节。

**新增内容**（追加在文件末尾，原页面以 Kinesis Data Analytics 段结束）：

```markdown
### AWS Redshift
[AWS Redshift Spectrum or Redshift Serverless](https://docs.aws.amazon.com/redshift/latest/dg/querying-iceberg.html) supports querying Apache Iceberg tables cataloged in the AWS Glue Data Catalog.

### Amazon Data Firehose
You can use [Firehose](https://docs.aws.amazon.com/firehose/latest/dev/apache-iceberg-destination.html) to directly deliver streaming data to Apache Iceberg Tables in Amazon S3. With this feature, you can route records from a single stream into different Apache Iceberg Tables, and automatically apply insert, update, and delete operations to records in the Apache Iceberg Tables. This feature requires using the AWS Glue Data Catalog.
```

**工作逻辑**：
- Redshift 段说明 Redshift Spectrum（频谱查询，针对 S3 数据）与 Redshift Serverless（无服务器版本）均支持查询 Iceberg 表，前提是表通过 AWS Glue Data Catalog 注册，并给出官方查询 Iceberg 文档链接。
- Firehose 段说明 Firehose 可作为 Iceberg 的写入端：把流数据直接投递到 S3 上的 Iceberg 表，支持路由分发与 upsert/delete，且必须使用 Glue Data Catalog。给出 Firehose Iceberg destination 官方文档链接。

**注意**：本提交在文件末尾未补换行（diff 显示 `\ No newline at end of file`），这是个小瑕疵但 mkdocs 渲染不受影响。

### `docs/mkdocs.yml`

**修改目的**：在文档站导航的 Engines 列表追加两个外链。

**改动内容**（在 `Amazon EMR` 行之后、`Google BigQuery` 行之前插入两行）：

```yaml
  - Amazon Data Firehose: https://docs.aws.amazon.com/firehose/latest/dev/apache-iceberg-destination.html
  - Amazon Redshift: https://docs.aws.amazon.com/redshift/latest/dg/querying-iceberg.html
```

**工作逻辑**：mkdocs 的 `nav` 数组定义站点侧边栏顺序，外链条目用 `名称: URL` 形式，会在导航栏生成一个直接跳转到该 AWS 官方文档的链接项。两个新条目与已有的 Amazon Athena、Amazon EMR 风格一致，保持 AWS 服务聚在一起。

## 小结

- **成效**：文档站现明确列出 AWS Redshift 与 Amazon Data Firehose 对 Iceberg 的支持，正文给出能力说明，侧边栏提供跳转入口，用户可快速了解这两个 AWS 服务的 Iceberg 集成方式。
- **影响范围**：仅 `docs/docs/aws.md`（新增 6 行 + 末尾换行瑕疵）与 `docs/mkdocs.yml`（新增 2 行导航）两个文档文件，无代码、构建或运行时影响。
- **回迁到 1.4.x 的注意事项**：纯文档改进，与产品功能无关，对 1.4.x 运行时无任何影响。1.4.x 作为维护分支通常不必单独回迁文档增补。如希望 1.4.x 文档站也展示这两个 AWS 服务，可选回迁（改动小且纯增量）；**否则无需回迁**，跳过不会引发任何技术问题。
