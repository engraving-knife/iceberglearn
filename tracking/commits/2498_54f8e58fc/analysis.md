# 提交 2498：Spark: Prune dead branch (#13808)

## 提交信息

- **序号**：2498 / 4088
- **哈希**：54f8e58fc845cb82af70750d748ac7b3dee25365
- **短哈希**：54f8e58fc
- **日期**：2025-08-14 09:28:04 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Spark: Prune dead branch (#13808)
- **PR/Issue**：#13808

## 总体目的

本提交移除了 `SparkParquetWriters` 中的一段死代码分支。在处理 Parquet BINARY/FIXED_LEN_BYTE_ARRAY 类型时，原代码中有一个检查 UUID 逻辑类型的分支：

```java
if (LogicalTypeAnnotation.uuidType().equals(primitive.getLogicalTypeAnnotation())) {
    return uuids(desc);
}
```

该分支在当前代码路径中实际上永远不会被执行到（dead code），因为 UUID 类型在进入该 switch 之前就已经被上层逻辑处理（在 `writeStruct` 方法的逻辑类型分发阶段就已被拦截）。保留这段死代码不仅增加了代码噪音，还可能误导开发者以为 UUID 类型会走到此路径。

该修改同时应用于 Spark 3.4、3.5 和 4.0 三个版本，保持一致性。

## 如何达成设计目的

直接删除 `case BINARY:` / `case FIXED_LEN_BYTE_ARRAY:` 下的 UUID 检查分支，使该 case 直接 fall through 到 `return byteArrays(desc)`。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java` (+0/-3 lines)

**修改目的**：移除死代码分支。

**工作逻辑**：在 `ParquetValueWriters` 的 `writePrimitive` 方法中，`case BINARY` 和 `case FIXED_LEN_BYTE_ARRAY` 下，删除 UUID 逻辑类型检查及对应的 `uuids(desc)` 返回分支。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java` (+0/-3 lines)

**修改目的**：与 v3.4 相同的死代码移除。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java` (+0/-3 lines)

**修改目的**：与 v3.4 相同的死代码移除。

## 总结

本提交是一个低风险的代码清理，移除了永远不会执行的死代码分支。这减少了代码噪音，使控制流更加清晰，避免了开发者对 UUID 处理路径的误解。三个 Spark 版本同步修改保持了一致性。
