# 提交 3660：Flink: Fix LICENSE/NOTICE compliance for all versions of flink-runtime (1.20, 2.0, 2.1) (#16216)

## 提交信息

- **序号**：3660 / 4088
- **哈希**：0f657edf12dc29f8487a679bfdd4210e9588d014
- **短哈希**：0f657edf1
- **日期**：2026-05-06 14:13:50 -0700
- **作者**：Kevin Liu
- **提交说明**：Flink: Fix LICENSE/NOTICE compliance for all versions of flink-runtime (1.20, 2.0, 2.1) (#16216)
- **PR/Issue**：#16216

## 总体目的

这个提交修复了所有 Flink 版本（1.20、2.0、2.1）的 `flink-runtime` JAR 包的 LICENSE 合规性问题。

flink-runtime 是 Iceberg 为 Flink 提供的胖 JAR 包，打包了 Iceberg 运行所需的全部依赖。此前其 LICENSE 文件遗漏了多个传递依赖的许可证声明：FastDoubleParser（via Jackson）、fast_float、bigint、fastutil（bundled by Parquet）、Eclipse MicroProfile OpenAPI、Mozilla Public Suffix List（via Apache HttpComponents）。这些遗漏不符合 Apache 发布的许可证合规要求。本提交为三个 Flink 版本统一补全了缺失的许可证声明。

## 如何达成设计目的

在 flink/v1.20、v2.0、v2.1 三个目录的 `flink-runtime/LICENSE` 文件中，统一新增相同的缺失依赖许可证声明。

## 修改详情

### `flink/v1.20/flink-runtime/LICENSE`、`flink/v2.0/flink-runtime/LICENSE`、`flink/v2.1/flink-runtime/LICENSE` (各 +505/-3 lines)

**修改目的**：补全缺失的依赖许可证声明。

**工作逻辑**：三个版本各新增以下依赖的许可证文本：
- FastDoubleParser（via Jackson JSON Processor）— MIT
- fast_float（bundled by FastDoubleParser）— Apache 2.0 / BSL / MIT
- bigint（bundled by FastDoubleParser）— MIT
- fastutil（bundled by Parquet）— Apache 2.0
- Eclipse MicroProfile OpenAPI — Apache 2.0
- Mozilla Public Suffix List（distributed by Apache HttpComponents）— MPL 2.0 全文

## 总结

这个提交修复了三个 Flink 版本（1.20/2.0/2.1）flink-runtime JAR 包的 LICENSE 合规性问题，补全了 FastDoubleParser、fast_float、bigint、fastutil、Eclipse MicroProfile OpenAPI、Mozilla Public Suffix List 六个传递依赖的许可证声明。三个版本的改动完全一致，确保所有发布版本都符合 Apache 许可证合规要求。注意本次仅修改了 LICENSE，未涉及 NOTICE 文件。
