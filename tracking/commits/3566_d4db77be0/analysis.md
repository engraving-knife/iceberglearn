# 提交 3566：Spark: Replace deprecated registerTempTable with createOrReplaceTempView (#16063)

## 提交信息

- **序号**：3566 / 4088
- **哈希**：d4db77be070a780cb54b84a6024d6e4ba095bc1e
- **短哈希**：d4db77be0
- **日期**：2026-04-21 12:38:48 +0200
- **作者**：drexler-sky
- **提交说明**：Spark: Replace deprecated registerTempTable with createOrReplaceTempView (#16063)
- **PR/Issue**：#16063

## 总体目的

该提交将 Spark 测试代码中已废弃的 `registerTempTable` 方法调用替换为推荐的 `createOrReplaceTempView` 方法。`registerTempTable` 在 Spark 2.0 中已被废弃，其语义是注册一个临时表，但在 Spark 后续版本中推荐使用 `createOrReplaceTempView`，后者语义更清晰（创建或替换临时视图），且行为一致。该提交覆盖 Spark 3.4、3.5、4.0、4.1 四个版本的测试代码。

## 如何达成设计目的

在 `TestCreateActions.java` 测试类中，将所有 `.registerTempTable("tempdata")` 调用替换为 `.createOrReplaceTempView("tempdata")`。两个方法在功能上等价（都注册一个临时视图供后续 SQL 查询使用），但 `createOrReplaceTempView` 是非废弃的推荐 API。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestCreateActions.java` (+1/-1 lines)

**修改目的**：替换废弃 API 调用。

**工作逻辑**：
```java
-.registerTempTable("tempdata");
+.createOrReplaceTempView("tempdata");
```
该调用在测试中用于创建临时视图 `tempdata`，随后通过 `INSERT INTO TABLE ... SELECT * FROM tempdata` 将数据插入 Iceberg 表。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestCreateActions.java` (+1/-1 lines)

**修改目的**：同上，Spark 3.5 版本的相同替换。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestCreateActions.java` (+1/-1 lines)

**修改目的**：同上，Spark 4.0 版本的相同替换。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestCreateActions.java` (+1/-1 lines)

**修改目的**：同上，Spark 4.1 版本的相同替换。

## 总结

这是一个代码清理提交，将四个 Spark 版本测试代码中的废弃 `registerTempTable` 调用替换为推荐的 `createOrReplaceTempView`。虽然两者功能等价，但使用非废弃 API 可以消除废弃警告并为未来 Spark 版本中可能移除 `registerTempTable` 做好准备。
