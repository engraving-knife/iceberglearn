# 提交 3625：AWS: Fix stale LICENSE entry for Parquet, clarify failsafe attribution (#16179)

## 提交信息

- **序号**：3625 / 4088
- **哈希**：6ce50265b0b0b364491314f00606db1fe857ab15
- **短哈希**：6ce50265b
- **日期**：2026-04-30 14:43:35 -0700
- **作者**：Kevin Liu
- **提交说明**：AWS: Fix stale LICENSE entry for Parquet, clarify failsafe attribution (#16179)
- **PR/Issue**：#16179

## 总体目的

这个提交修复了 AWS Bundle 的 LICENSE 文件中关于 Parquet 的过时条目，并澄清了 failsafe 的来源归属。

AWS Bundle 是 Iceberg 的 AWS 集成 jar 包，包含了所有 AWS 相关的依赖。LICENSE 文件需要准确列出所有打包的第三方库及其许可证信息。之前有两个问题需要修复：

1. **Parquet 条目不完整**：Parquet 的 LICENSE 条目缺少版权信息和许可证声明，而且没有标注 Parquet 是由 AWS Analytics Accelerator S3 间接引入的。此外，Parquet 自身还引入了其他依赖（Thrift、JavaFastPFOR、fastutil、Zero-Allocation Hashing），这些也需要在 LICENSE 中声明。

2. **failsafe 归属不清**：failsafe 库是由 AWS Analytics Accelerator S3 间接引入的，但没有标注来源。

## 如何达成设计目的

1. 更新 Parquet 的 LICENSE 条目，添加版权信息、完整的许可证声明，并标注其由 AWS Analytics Accelerator S3 引入。
2. 添加 Parquet 间接引入的 4 个依赖的 LICENSE 条目。
3. 为 failsafe 条目添加来源标注。

## 修改详情

### `aws-bundle/LICENSE` (+35/-3 lines)

**修改目的**：修复 Parquet LICENSE 条目并添加 Parquet 的间接依赖声明。

**工作逻辑**：

1. **更新 Parquet 条目**：
```text
# 之前
This product bundles Apache Parquet.
Project URL: https://parquet.apache.org
License: Apache License, Version 2.0

# 之后
This product bundles Apache Parquet (bundled by AWS Analytics Accelerator S3).
Copyright: 2014-2024 The Apache Software Foundation
Project URL: https://parquet.apache.org/
License: Apache License, Version 2.0
```
添加了来源标注、版权信息和完整许可证 URL。

2. **添加 Parquet 间接引入的依赖**：
   - Apache Thrift (bundled by Parquet)
   - Daniel Lemire's JavaFastPFOR (bundled by Parquet)
   - fastutil (bundled by Parquet)
   - Zero-Allocation Hashing (bundled by Parquet)

3. **更新 failsafe 条目**：
```text
# 之前
This product bundles failsafe.

# 之后
This product bundles failsafe (bundled by AWS Analytics Accelerator S3).
```
添加了来源标注，表明 failsafe 是由 AWS Analytics Accelerator S3 间接引入的。

## 总结

这个提交修复了 AWS Bundle LICENSE 文件的准确性和完整性问题。通过为 Parquet 条目补充版权信息和许可证声明、添加 Parquet 间接引入的 4 个依赖的声明、以及为 failsafe 添加来源标注，使 LICENSE 文件更加准确地反映了实际打包的依赖关系。这是 Apache 项目法律合规性的重要维护工作。
