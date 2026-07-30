# 提交 2490：Spark: Remove unused parameter in RewriteTablePathSparkAction#rewritePositionDeletes (#13792)

## 提交信息

- **序号**：2490 / 4088
- **哈希**：d38cdf38dd7aa4c3142405b4753d67883331ed17
- **短哈希**：d38cdf38d
- **日期**：2025-08-12 12:07:19 -0700
- **作者**：Manu Zhang
- **提交说明**：Spark: Remove unused parameter in RewriteTablePathSparkAction#rewritePositionDeletes (#13792)
- **PR/Issue**：#13792

## 总体目的

本提交是一个代码清理类的改动，移除了 `RewriteTablePathSparkAction` 中 `rewritePositionDeletes` 方法的一个未使用的参数 `TableMetadata metadata`。

在代码审查或开发过程中发现，`rewritePositionDeletes` 方法签名中声明了 `metadata` 参数，但方法体内从未使用该参数。这种未使用的参数属于代码噪音，可能误导开发者以为该参数在方法逻辑中发挥作用，也会增加维护成本。移除该参数可以使方法签名更加简洁明确，反映其真实依赖。

该改动同时应用于 Spark 3.4、3.5 和 4.0 三个版本的代码分支，保持三个分支的代码一致性。

## 如何达成设计目的

设计思路简单直接：
1. 修改 `rewritePositionDeletes` 方法的签名，移除 `TableMetadata metadata` 参数，只保留 `Set<DeleteFile> toRewrite` 参数。
2. 修改所有调用该方法的地方，移除传入的 `endMetadata` 实参。
3. 在三个 Spark 版本（v3.4、v3.5、v4.0）的对应文件中做相同修改。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+3/-3 lines)

**修改目的**：移除 `rewritePositionDeletes` 方法的未使用参数 `metadata`。

**工作逻辑**：
- 调用处由 `rewritePositionDeletes(endMetadata, deleteFiles)` 改为 `rewritePositionDeletes(deleteFiles)`。
- 方法定义由 `private void rewritePositionDeletes(TableMetadata metadata, Set<DeleteFile> toRewrite)` 改为 `private void rewritePositionDeletes(Set<DeleteFile> toRewrite)`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+3/-3 lines)

**修改目的**：与 v3.4 相同的参数移除，保持分支一致。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+3/-3 lines)

**修改目的**：与 v3.4 相同的参数移除，保持分支一致。

## 总结

本提交是一个低风险的代码清理改动，移除了未使用的方法参数，提升了代码可读性和可维护性。同时确保了三个 Spark 版本分支的一致性。由于只是移除未使用参数，不改变任何运行时逻辑，不会引入功能回归风险。
