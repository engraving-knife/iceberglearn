# 提交 3571：Core: Use Idiomatic ThreadLocal cleanup in CommitMetadata (#15284) (#16031)

## 提交信息

- **序号**：3571 / 4088
- **哈希**：4a31e519b8782d1c5a98f2ad00edbe278cedc4a6
- **短哈希**：4a31e519b
- **日期**：2026-04-22 07:52:29 -0600
- **作者**：Anupam Yadav
- **提交说明**：Core: Use Idiomatic ThreadLocal cleanup in CommitMetadata (#15284) (#16031)
- **PR/Issue**：#16031（关联 #15284）

## 总体目的

该提交将 Spark `CommitMetadata` 类中 `ThreadLocal` 的清理方式从非惯用的 `set(ImmutableMap.of())` 改为推荐的 `remove()` 方法。根据 `ThreadLocal` 的 Javadoc，`remove()` 是清理线程本地值的推荐方式。

之前使用 `COMMIT_PROPERTIES.set(ImmutableMap.of())` 清理时，虽然值被重置为空 map，但 `ThreadLocal` 条目仍然保留在线程的 `ThreadLocalMap` 中。而 `remove()` 方法会完全移除该条目，有助于避免在线程池场景下的内存泄漏，特别是当线程长时间存活且 `ThreadLocal` 不再需要时。该提交覆盖 Spark 3.4、3.5、4.0、4.1 四个版本。

## 如何达成设计目的

在 `withCommitProperties` 方法的 `finally` 块中，将 `COMMIT_PROPERTIES.set(ImmutableMap.of())` 替换为 `COMMIT_PROPERTIES.remove()`。这是一个行为等价但更符合最佳实践的清理方式。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/CommitMetadata.java` (+1/-1 lines)

**修改目的**：使用推荐的 ThreadLocal 清理方式。

**工作逻辑**：
```java
} finally {
-  COMMIT_PROPERTIES.set(ImmutableMap.of());
+  COMMIT_PROPERTIES.remove();
}
```

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/CommitMetadata.java` (+1/-1 lines)

**修改目的**：同上，Spark 3.5 版本。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/CommitMetadata.java` (+1/-1 lines)

**修改目的**：同上，Spark 4.0 版本。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/CommitMetadata.java` (+1/-1 lines)

**修改目的**：同上，Spark 4.1 版本。

## 总结

这是一个代码质量改进提交，将 `ThreadLocal` 清理方式改为 Java 文档推荐的 `remove()` 方法。虽然功能上等价，但 `remove()` 能更好地避免线程池环境中的潜在内存泄漏，符合最佳实践。
