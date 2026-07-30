# 提交 0053：Docs: Remove AWS Version (#8842)

## 提交信息

- **序号**：0053 / 4088
- **哈希**：b47fb4eb6bcbe5d4ce9f5bc1224b85cc8ac559b2
- **短哈希**：b47fb4eb6
- **日期**：2023-10-16 07:49:24 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Docs: Remove AWS Version (#8842)
- **PR/Issue**：#8842

## 总体目的

本提交是一个文档维护性修改，目的是从 AWS 集成文档中移除硬编码的 AWS SDK 版本号，避免文档随依赖升级而过时。

背景与动机上，`docs/aws.md` 在介绍 Spark 集成示例时，文案中写死了 "AWS clients version 2.20.131（which is packaged in the `iceberg-aws-bundle`）"。Iceberg 项目的 `iceberg-aws-bundle` 实际打包的 AWS SDK 版本会随构建依赖（`software.amazon.awssdk:bom`）频繁升级（例如紧随其后的提交 0054 就把 AWS SDK 从 2.20.162 升到 2.21.0）。文档里写死具体版本号会带来两个问题：一是文档很快与实际打包版本脱节，误导用户；二是每次依赖升级都要同步改文档，增加维护负担。修复办法是直接在文案中不再提及具体版本号，只保留"AWS clients（which is packaged in the `iceberg-aws-bundle`）"的表述——版本信息由用户实际拉取的 bundle 决定，文档无需关心。

虽然改动极小（一行文案），但它体现了 Iceberg 文档维护的一个原则：把易变的版本信息从叙述性文字中剥离，让文档保持稳定。这对 Iceberg 这种多版本并行维护、依赖频繁升级的项目尤为重要。

## 如何达成设计目的

通过删除 `docs/aws.md` 中 Spark 示例段落的硬编码版本字符串 `version 2.20.131` 来达成。改动只涉及一行文字描述，未触碰命令行示例（命令行示例本身使用 `{{% icebergVersion %}}` 占位符，由文档构建时替换，所以不需要改）。

## 修改详情

### `docs/aws.md`

**修改目的**：移除 Spark 示例文案中硬编码的 AWS SDK 版本号，避免文档随依赖升级而过时。

**工作逻辑**：

修改前（位于 `### Spark` 小节开头）：
```
For example, to use AWS features with Spark 3.4 (with scala 2.12) and AWS clients version 2.20.131 (which is packaged in the `iceberg-aws-bundle`), you can start the Spark SQL shell with:
```

修改后：
```
For example, to use AWS features with Spark 3.4 (with scala 2.12) and AWS clients (which is packaged in the `iceberg-aws-bundle`), you can start the Spark SQL shell with:
```

即仅删除 `version 2.20.131` 这段文字。其余内容（包括 `--packages` 命令、`{{% icebergVersion %}}` 占位符、catalog 配置示例）均未改动。文档其余章节（`## Enabling AWS Integration`、`### Flink` 等）也未改动。

## 小结

本提交从 AWS 集成文档中移除硬编码的 AWS SDK 版本号，避免文档随 `iceberg-aws-bundle` 依赖升级而过时，是一次小但合理的文档维护性修改。
