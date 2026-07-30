# 提交 3659：Spark: Fix LICENSE/NOTICE compliance for all versions of spark-runtime (v3.4, v3.5, v4.0, v4.1) (#16215)

## 提交信息

- **序号**：3659 / 4088
- **哈希**：86823e5a51b53f6154e824f24f3fe128e53f301f
- **短哈希**：86823e5a5
- **日期**：2026-05-06 14:13:29 -0700
- **作者**：Kevin Liu
- **提交说明**：Spark: Fix LICENSE/NOTICE compliance for all versions of spark-runtime (v3.4, v3.5, v4.0, v4.1) (#16215)
- **PR/Issue**：#16215

## 总体目的

这个提交修复了所有 Spark 版本（3.4、3.5、4.0、4.1）的 `spark-runtime` JAR 包的 LICENSE 和 NOTICE 合规性问题。

spark-runtime 是 Iceberg 为 Spark 提供的胖 JAR 包，打包了 Iceberg 运行所需的全部依赖。此前其 LICENSE/NOTICE 文件遗漏了多个传递依赖的许可证声明：FastDoubleParser（via Jackson）、fast_float、bigint、Mozilla Public Suffix List（via Apache HttpComponents）、JCTools（via Netty）。这些遗漏不符合 Apache 发布的许可证合规要求。本提交为四个 Spark 版本统一补全了缺失的许可证声明。

## 如何达成设计目的

在 spark/v3.4、v3.5、v4.0、v4.1 四个目录的 `spark-runtime/LICENSE` 和 `NOTICE` 文件中，统一新增相同的缺失依赖许可证声明。

## 修改详情

### `spark/v3.4/spark-runtime/LICENSE`、`spark/v3.5/spark-runtime/LICENSE`、`spark/v4.0/spark-runtime/LICENSE`、`spark/v4.1/spark-runtime/LICENSE` (各 +536/-2 lines)

**修改目的**：补全缺失的依赖许可证声明。

**工作逻辑**：四个版本各新增以下依赖的许可证文本：
- FastDoubleParser（via Jackson JSON Processor）— MIT
- fast_float（bundled by FastDoubleParser）— Apache 2.0 / BSL / MIT
- bigint（bundled by FastDoubleParser）— MIT
- Mozilla Public Suffix List（distributed by Apache HttpComponents）— MPL 2.0 全文
- JCTools（via Netty）— Apache 2.0

### `spark/v3.4/spark-runtime/NOTICE`、`spark/v3.5/spark-runtime/NOTICE`、`spark/v4.0/spark-runtime/NOTICE`、`spark/v4.1/spark-runtime/NOTICE` (各 +35 lines)

**修改目的**：补充相关依赖的 NOTICE 声明。

## 总结

这个提交修复了四个 Spark 版本（3.4/3.5/4.0/4.1）spark-runtime JAR 包的 LICENSE/NOTICE 合规性问题，补全了 FastDoubleParser、fast_float、bigint、Mozilla Public Suffix List、JCTools 五个传递依赖的许可证声明。四个版本的改动完全一致，确保所有发布版本都符合 Apache 许可证合规要求。
