# 提交 3602：Flink: RewriteDataFile support dynamic filter (#15865)

## 提交信息

- **序号**：3602 / 4088
- **哈希**：836bca9c7e840a0698fc828a1cb4658750b947fc
- **短哈希**：836bca9c7
- **日期**：2026-04-27 17:10:46 +0200
- **作者**：GuoYu
- **提交说明**：Flink: RewriteDataFile support dynamic filter (#15865)
- **PR/Issue**：#15865

## 总体目的

这个提交为 Flink 的 RewriteDataFiles（数据文件压缩/重写）操作添加了动态过滤器支持。

之前 Flink 的 RewriteDataFiles 操作只接受静态的 `Expression` 过滤器，过滤器在作业提交时就被固定下来。对于流式维护场景，重写操作会被周期性触发，而静态过滤器无法适应时间变化的需求。例如，用户希望"每次压缩只重写最近 3 天的数据文件"，使用静态过滤器无法实现，因为过滤器中的时间阈值在作业启动时就被固定了。

通过引入 `SerializableSupplier<Expression>`，过滤器可以在每次压缩触发时动态生成，使时间相对的过滤条件能够根据实际执行时间动态计算。

## 如何达成设计目的

实现方案：
1. 在 `RewriteDataFiles.Builder` 中将 `filter` 字段从 `Expression` 改为 `SerializableSupplier<Expression>`。
2. 新增 `filter(SerializableSupplier<Expression>)` 方法，将旧的 `filter(Expression)` 方法标记为 `@Deprecated`。
3. 在 `DataFileRewritePlanner` 中将 `filter` 字段改为 `filterSupplier`，在每次规划时调用 `filterSupplier.get()` 获取最新的过滤器。
4. 更新相关测试代码适配新的 API。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (+26/-2 lines)

**修改目的**：在 Builder 中支持动态过滤器。

**工作逻辑**：

1. 将字段从静态表达式改为 supplier：
```java
private SerializableSupplier<Expression> filterSupplier = Expressions::alwaysTrue;
```

2. 新增 `filter(SerializableSupplier<Expression>)` 方法，并标记旧方法为 `@Deprecated`：
```java
@Deprecated
public Builder filter(Expression newFilter) {
  this.filterSupplier = () -> newFilter;
  return this;
}

public Builder filter(SerializableSupplier<Expression> newFilterSupplier) {
  this.filterSupplier = newFilterSupplier;
  return this;
}
```
旧方法内部将静态表达式包装为返回固定值的 supplier，保持向后兼容。

3. 在构建 planner 时传入 `filterSupplier` 而非 `filter`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (+6/-3 lines)

**修改目的**：在 planner 中使用动态过滤器。

**工作逻辑**：
将 `filter` 字段改为 `filterSupplier`，在构造函数中接收 supplier。关键修改是在每次规划时调用 `filterSupplier.get()` 获取当前过滤器：
```java
BinPackRewriteFilePlanner planner =
    new BinPackRewriteFilePlanner(table, filterSupplier.get(), snapshot.snapshotId(), false);
```
这样每次压缩触发时都会调用 supplier 获取最新的过滤器表达式，实现动态过滤。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/RewriteUtil.java` (+1/-1 lines)

**修改目的**：适配测试工具类。

**工作逻辑**：
将 `Expressions.alwaysTrue()` 改为 `Expressions::alwaysTrue`（方法引用形式的 supplier）。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestRewriteDataFiles.java` (+52/-0 lines)

**修改目的**：新增动态过滤器测试。

**工作逻辑**：
添加测试验证使用 `SerializableSupplier` 的过滤器能在每次触发时动态生成新的表达式。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (+38/-0 lines)

**修改目的**：更新测试基类以支持动态过滤器测试。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewritePlanner.java` (+42/-3 lines)

**修改目的**：更新 planner 测试。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewriteRunner.java` (+1/-1 lines)

**修改目的**：适配 runner 测试。

## 总结

这个提交为 Flink 流式数据文件重写操作添加了动态过滤器支持，解决了静态过滤器无法适应时间变化需求的问题。通过 `SerializableSupplier<Expression>`，用户可以在每次压缩触发时动态生成过滤器表达式，例如实现"只重写最近 N 天的数据"这类时间相对的过滤逻辑。旧 API 被保留并标记为废弃，保持了向后兼容性。
