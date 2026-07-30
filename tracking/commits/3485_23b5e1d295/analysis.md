# 提交 3485：Fix position delete rewrite option validation (#15828)

## 提交信息

- **序号**：3485 / 4088
- **哈希**：23b5e1d295e0a89d8aeeb49483c2f863fcd9142b
- **短哈希**：23b5e1d295
- **日期**：2026-03-30 13:42:37 -0700
- **作者**：Naama Maoz
- **提交说明**：Fix position delete rewrite option validation (#15828)
- **PR/Issue**：#15828

## 总体目的

修复 `RewritePositionDeleteFilesSparkAction` 中 `validateAndInitOptions` 方法的选项验证逻辑错误。原代码在初始化 `validOptions` 集合时使用了 `planner.validOptions()`，但实际上应该使用 `runner.validOptions()`。这导致来自 runner 的有效选项未被正确纳入初始验证集合，可能错误地拒绝合法的用户选项。

## 如何达成设计目的

将 `validOptions` 初始化从 `Sets.newHashSet(planner.validOptions())` 改为 `Sets.newHashSet(runner.validOptions())`。后续代码仍然会 `addAll(planner.validOptions())` 和 `addAll(VALID_OPTIONS)`，因此最终集合包含 runner、planner 和内置选项的并集。这个修改在四个 Spark 版本（3.4、3.5、4.0、4.1）的对应文件中同步应用。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java` (+1/-1 line)

**修改目的**：修正 validOptions 初始化使用 runner 而非 planner。

**工作逻辑**：
```java
-    Set<String> validOptions = Sets.newHashSet(planner.validOptions());
+    Set<String> validOptions = Sets.newHashSet(runner.validOptions());
     validOptions.addAll(VALID_OPTIONS);
     validOptions.addAll(planner.validOptions());
```

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java` (+1/-1 line)

**修改目的**：同上，Spark 3.5 版本的同步修改。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java` (+1/-1 line)

**修改目的**：同上，Spark 4.0 版本的同步修改。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java` (+1/-1 line)

**修改目的**：同上，Spark 4.1 版本的同步修改。

## 总结

修复了 position delete 重写动作的选项验证 bug。`validOptions` 集合初始化时错误地使用了 `planner.validOptions()` 而非 `runner.validOptions()`，导致 runner 特有的选项在初始集合中缺失。虽然后续会 addAll planner 选项，但初始集合来源的错误可能导致某些验证逻辑异常。修复在四个 Spark 版本中同步应用。
