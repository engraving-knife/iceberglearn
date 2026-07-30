# 提交 3629：AWS: remove extra/staled LICENSE entry bundled by Parquet (#16180)

## 提交信息

- **序号**：3629 / 4088
- **哈希**：128f656c0ecc908aaf6d08744f887cdf2f7d3876
- **短哈希**：128f656c0
- **日期**：2026-05-02 11:31:47 -0700
- **作者**：Kevin Liu
- **提交说明**：AWS: remove extra/staled LICENSE entry bundled by Parquet (#16180)
- **PR/Issue**：#16180

## 总体目的

这个提交从 AWS Bundle 的 LICENSE 文件中移除了三个由 Parquet 间接引入但实际并未打包到 AWS Bundle 中的依赖条目。

在提交 3625 中，为 AWS Bundle 的 LICENSE 文件添加了 Parquet 间接引入的依赖声明（JavaFastPFOR、fastutil、Zero-Allocation Hashing）。然而，经过进一步检查发现，这些依赖实际上并没有被打包到 AWS Bundle 中。AWS Bundle 中的 Parquet 是由 AWS Analytics Accelerator S3 引入的，而 Analytics Accelerator 可能已经排除了这些传递依赖，或者这些依赖的版本不包含在最终的 bundle 中。

保留不存在的依赖条目会误导用户和审计人员，因此需要移除这些过时的条目。

## 如何达成设计目的

从 `aws-bundle/LICENSE` 文件中移除三个不存在的依赖条目。注意保留了 Apache Thrift (bundled by Parquet) 条目，因为该依赖确实存在于 bundle 中。

## 修改详情

### `aws-bundle/LICENSE` (+0/-23 lines)

**修改目的**：移除不在 bundle 中的 Parquet 间接依赖条目。

**工作逻辑**：
移除了以下三个条目：
1. `Daniel Lemire's JavaFastPFOR project (bundled by Parquet)` - 不在 AWS Bundle 中
2. `fastutil (bundled by Parquet)` - 不在 AWS Bundle 中
3. `Zero-Allocation Hashing (bundled by Parquet)` - 不在 AWS Bundle 中

注意：`Apache Thrift (bundled by Parquet)` 条目被保留，因为该依赖确实存在于 bundle 中。

## 总结

这个提交修正了提交 3625 中的过度声明，移除了三个实际不存在于 AWS Bundle 中的 Parquet 间接依赖条目。这确保了 LICENSE 文件的准确性，只声明实际打包的依赖，避免误导。这提醒了在添加 LICENSE 条目时需要验证依赖是否实际存在于 bundle 中。
