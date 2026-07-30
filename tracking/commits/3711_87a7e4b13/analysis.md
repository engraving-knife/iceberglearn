# 提交 3711：Spark: Also disable min/max aggregation push down for binary (#16328)

## 提交信息

- **序号**：3711 / 4088
- **哈希**：87a7e4b13470b238e86872e5b194b80c3a89a80f
- **短哈希**：87a7e4b13
- **日期**：2026-05-14 08:35:00 -0700
- **作者**：Dong Wang
- **提交说明**：Spark: Also disable min/max aggregation push down for binary (#16328)
- **PR/Issue**：#16328

## 总体目的

这个提交是提交 3705 的补充，将二进制（BINARY）类型的 min/max 聚合下推也在所有模式下禁用。提交 3705 已经禁用了字符串（STRING）类型的 min/max 下推，理由是数据文件的 lower_bounds 和 upper_bounds 可能在历史某时刻被截断过，即使当前模式不截断也无法保证统计信息的完整性。

同样的逻辑也适用于 BINARY 类型——BINARY 类型的 lower_bounds 和 upper_bounds 同样可能被截断，因此基于截断后的统计信息进行 min/max 下推也可能产生错误结果。此提交将 BINARY 类型加入到与 STRING 类型相同的禁用条件中。

## 如何达成设计目的

通过修改 `SparkScanBuilder` 中的聚合下推判断条件，在原有的 STRING 类型检查旁边添加 BINARY 类型检查。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+2/-1 lines)

**修改目的**：将 BINARY 类型加入 min/max 下推禁用范围。

**工作逻辑**：

```java
-        } else if (aggregate.type().typeId() == Type.TypeID.STRING) {
+        } else if (aggregate.type().typeId() == Type.TypeID.STRING
+            || aggregate.type().typeId() == Type.TypeID.BINARY) {
           // lower_bounds and upper_bounds may have been truncated before, so disable push down
           // regardless of the current mode
```

在原有的 STRING 类型条件中添加 `|| aggregate.type().typeId() == Type.TypeID.BINARY`，使 BINARY 类型也享受同样的保护。

### 其他 Spark 版本模块

同样的修改应用到 `spark/v3.4`、`spark/v4.0`、`spark/v4.1` 模块。

### 测试文件 (各 +72/-14 lines)

**修改目的**：添加 BINARY 类型的聚合下推测试。

## 总结

这是提交 3705 的补充修复，将 BINARY 类型纳入 min/max 聚合下推的禁用范围。BINARY 类型与 STRING 类型一样，其 lower_bounds 和 upper_bounds 可能被截断，因此需要同样的保护。这一修复确保二进制数据类型的 min/max 聚合查询正确性。
