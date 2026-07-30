# 提交 3768：Kafka Connect: Fix ConcurrentModificationException in IcebergSinkConfig.tableConfig (#16438)

## 提交信息

- **序号**：3768 / 4088
- **哈希**：26169f73695d75cf08b6ecd69ae3d4d8f82fef8f
- **短哈希**：26169f736
- **日期**：2026-05-22 11:39:44 +0200
- **作者**：Vova Kolmakov
- **提交说明**：Kafka Connect: Fix ConcurrentModificationException in IcebergSinkConfig.tableConfig (#16438)
- **PR/Issue**：#16438

## 总体目的

这个提交修复了 Kafka Connect Iceberg Sink 中的一个 `ConcurrentModificationException` 并发问题。`IcebergSinkConfig` 类中的 `tableConfigMap` 字段使用 `Maps.newHashMap()` 创建，这是一个非线程安全的 HashMap。在 Kafka Connect 的运行环境中，多个线程可能同时访问和修改这个 map（例如不同的 task 线程并发调用 `tableConfig` 方法），导致 `ConcurrentModificationException`。

## 如何达成设计目的

将 `tableConfigMap` 的初始化从 `Maps.newHashMap()` 改为 `Maps.newConcurrentMap()`，使用线程安全的并发 Map 实现来避免并发修改异常。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConfig.java` (+1/-1 lines)

**修改目的**：将 `tableConfigMap` 改为线程安全的并发 Map。

**工作逻辑**：
```java
// 修改前
private final Map<String, TableSinkConfig> tableConfigMap = Maps.newHashMap();
// 修改后
private final Map<String, TableSinkConfig> tableConfigMap = Maps.newConcurrentMap();
```

`Maps.newConcurrentMap()` 返回一个基于 `ConcurrentHashMap` 的实现，支持多个线程并发读写而不会抛出 `ConcurrentModificationException`。

## 总结

这是一个简洁的并发 bug 修复，通过将非线程安全的 HashMap 替换为 ConcurrentHashMap，解决了 Kafka Connect 多线程环境下 `tableConfigMap` 的并发修改问题。修改虽然只有一行，但对 Kafka Connect Sink 在生产环境中的稳定性很重要。
