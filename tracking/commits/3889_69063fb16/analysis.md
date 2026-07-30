# 提交 3889：Spark: Reduce TestSparkDataFile row count to speed up partitioned cases (#16821)

## 提交信息

- **序号**：3889 / 4088
- **哈希**：69063fb162e835c3dc1ff7bbcf5526029771c6d0
- **短哈希**：69063fb16
- **日期**：2026-06-16 13:51:39 +0200
- **作者**：Sebastian Baunsgaard
- **提交说明**：Spark: Reduce TestSparkDataFile row count to speed up partitioned cases (#16821)
- **PR/Issue**：#16821

## 总体目的

解决 `TestSparkDataFile` 测试中分区场景的性能问题。该测试使用一个 14 字段的分区 spec 生成随机数据行，由于分区转换的组合使得几乎每行都落入独立的分区，因此每行都会产生一个独立的数据文件（加上每个数据文件对应一个位置删除文件）。测试成本随行数线性增长，而非随覆盖度增长——因为每一行生成时就已经覆盖了所有列类型和全部 14 种分区转换，且两种删除文件类型都会往返测试。

通过将生成行数从 200 减少到 40，在不降低测试覆盖的前提下显著加速测试套件。据提交说明，Spark 4.1 上本地运行时间从 27.4s 降至 11.5s（约 58% 的降幅），且通过/跳过计数完全一致。

## 如何达成设计目的

在 Spark 3.5、4.0、4.1 三个版本的 `TestSparkDataFile.java` 测试副本中，将 `RandomData.generateSpark` 调用的行数参数从 `200` 改为 `40`。由于数据生成是确定性的（使用固定种子 0），减少行数不影响测试覆盖的类型和转换组合，只减少了冗余的重复行。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkDataFile.java` (+1/-1 lines)
### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkDataFile.java` (+1/-1 lines)
### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkDataFile.java` (+1/-1 lines)

**修改目的**：减少测试数据生成行数以加速分区测试。

**工作逻辑**：
```java
-Iterable<InternalRow> rows = RandomData.generateSpark(table.schema(), 200, 0);
+Iterable<InternalRow> rows = RandomData.generateSpark(table.schema(), 40, 0);
```

将生成行数从 200 减至 40，种子保持为 0 确保确定性。每个文件各修改一处 `checkSparkContentFiles` 方法中的调用。

## 总结

将 `TestSparkDataFile` 测试的数据行数从 200 减少到 40，在不降低覆盖度的前提下将测试套件执行时间降低约 58%。这是一个纯测试性能优化，与提交 3887（降低 RandomData 集合大小上限）同属测试加速系列工作，三个 Spark 版本同步修改。
