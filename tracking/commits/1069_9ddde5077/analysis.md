# 提交 1069：Docs: Update MRAP endpoint and add notebook link (#9362)

## 提交信息

- **序号**：1069 / 4088
- **哈希**：9ddde5077b49e2f50d9824002f45c732c0fcbc7c
- **短哈希**：9ddde5077
- **日期**：2024-08-19 10:46:20 -0600
- **作者**：Prashant Singh <35593236+singhpk234@users.noreply.github.com>
- **提交说明**：Docs: Update MRAP endpoint and add notebook link (#9362)
- **PR/Issue**：#9362

## 总体目的

Iceberg 的 AWS 集成文档 `docs/docs/aws.md` 中有一节专门介绍如何通过 `s3.access-points.<bucket>` 配置项把 S3 操作指向 S3 Access Point（包括 Multi-Region Access Point，MRAP）。原示例代码中直接写死了一个具体的 ARN：`arn:aws:s3::123456789012:accesspoint:mfzwi23gnjvgw.mrap`，并在紧随其后的说明文字里再次引用了同一个具体 ARN。这种"写死真实账号 ID + 真实 access point 别名"的写法存在两个问题：

1. 该 ARN 是某个具体账号下的真实资源（账号 `123456789012`、别名 `mfzwi23gnjvgw.mrap`），把它作为示例公开在文档中容易让用户误以为可以直接复制使用，也可能泄露示例作者账号下的资源信息；
2. ARN 中使用了"冒号分隔"的旧式 access point ARN 写法 `accesspoint:mfzwi23gnjvgw.mrap`，而 MRAP 推荐的 ARN 格式实际上是用斜杠分隔的 `accesspoint/<MRAP_ALIAS>`，两种写法在 AWS SDK 行为上可能存在差异。

本提交的目的是把示例中的具体 ARN 改写为带占位符 `<ACCOUNT_ID>` 和 `<MRAP_ALIAS>` 的通用形式，并采用斜杠分隔的标准 MRAP ARN 格式，让示例更通用、更规范；同时在末尾的"参考链接"处追加一个示例 notebook 的 GitHub 链接，方便用户参考完整的实战代码。

## 如何达成设计目的

实现方式是直接修改 `docs/docs/aws.md` 中"S3 Access Points"小节里的 Spark SQL 配置示例与说明文字：

- 把两行 `s3.access-points.my-bucketN=...` 配置中的具体 ARN `arn:aws:s3::123456789012:accesspoint:mfzwi23gnjvgw.mrap` 替换为通用占位形式 `arn:aws:s3::<ACCOUNT_ID>:accesspoint/<MRAP_ALIAS>`；
- 把下方说明文字中重复出现的同一个具体 ARN 也替换为同样的占位形式；
- 把分隔符从 `accesspoint:`（冒号）改为 `accesspoint/`（斜杠），与 MRAP 的标准 ARN 格式保持一致；
- 在结尾"For more details ..."一行末尾追加 ` , [Sample notebook](https://github.com/aws-samples/quant-research/tree/main)`，指向 aws-samples 仓库中的一个示例 notebook，供用户参考完整可运行的端到端示例。

## 修改详情

### `docs/docs/aws.md`

**修改目的**：把 S3 Access Points / MRAP 示例从"写死具体账号与别名"改为"通用占位符 + 标准 MRAP ARN 格式"，并追加示例 notebook 链接。

**工作逻辑**：

修改前后的差异如下：

```diff
-    --conf spark.sql.catalog.my_catalog.s3.access-points.my-bucket1=arn:aws:s3::123456789012:accesspoint:mfzwi23gnjvgw.mrap \
-    --conf spark.sql.catalog.my_catalog.s3.access-points.my-bucket2=arn:aws:s3::123456789012:accesspoint:mfzwi23gnjvgw.mrap
+    --conf spark.sql.catalog.my_catalog.s3.access-points.my-bucket1=arn:aws:s3::<ACCOUNT_ID>:accesspoint/<MRAP_ALIAS> \
+    --conf spark.sql.catalog.my_catalog.s3.access-points.my-bucket2=arn:aws:s3::<ACCOUNT_ID>:accesspoint/<MRAP_ALIAS>
```

```diff
-For the above example, the objects in S3 on `my-bucket1` and `my-bucket2` buckets will use `arn:aws:s3::123456789012:accesspoint:mfzwi23gnjvgw.mrap`
+For the above example, the objects in S3 on `my-bucket1` and `my-bucket2` buckets will use `arn:aws:s3::<ACCOUNT_ID>:accesspoint/<MRAP_ALIAS>`
 access-point for all S3 operations.
```

```diff
-For more details on using access-points, please refer [Using access points with compatible Amazon S3 operations](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-points-usage-examples.html).
+For more details on using access-points, please refer [Using access points with compatible Amazon S3 operations](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-points-usage-examples.html), [Sample notebook](https://github.com/aws-samples/quant-research/tree/main) .
```

要点：

- 占位符 `<ACCOUNT_ID>` 与 `<MRAP_ALIAS>` 让用户清楚需要替换为自己的账号 ID 与 MRAP 别名；
- 分隔符由 `:` 改为 `/`，符合 AWS 对 MRAP ARN 的标准格式 `arn:aws:s3::<ACCOUNT_ID>:accesspoint/<MRAP_ALIAS>`；
- 追加的 Sample notebook 链接指向 `aws-samples/quant-research` 仓库，是一个端到端的量化研究示例，可作为 MRAP + Iceberg 的实战参考。

## 小结

- **成效**：把 MRAP 示例通用化（用占位符替换真实账号与别名）、规范化 ARN 格式（冒号分隔改为斜杠分隔），并补充了一个示例 notebook 链接，提升了文档的可读性与可复用性。
- **影响范围**：仅修改 `docs/docs/aws.md` 一个文件，4 处文本改动（其中 3 处是 ARN 替换、1 处是追加链接），无代码、配置或构建变更。
- **回迁到 1.4.x 的注意事项**：纯文档修改，回迁到 1.4.x 风险极低，可以直接 cherry-pick；需注意 1.4.x 分支的文档目录结构（`docs/aws.md` vs `docs/docs/aws.md`）与该提交当时的路径是否一致——Iceberg 文档目录在历史上有过重组，cherry-pick 时若路径不一致需要手工调整目标文件路径。该改动对功能无任何影响，仅是文档示例的规范化。
