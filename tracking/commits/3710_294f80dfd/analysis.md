# 提交 3710：Flink: Use native slot sharing group inheritance for maintenance tasks (#16329)

## 提交信息

- **序号**：3710 / 4088
- **哈希**：294f80dfd41c7919a9ed527d6838fa473897cf07
- **短哈希**：294f80dfd
- **日期**：2026-05-14 16:02:12 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Use native slot sharing group inheritance for maintenance tasks (#16329)
- **PR/Issue**：#16329

## 总体目的

这个提交重构了 Flink 2.1 模块中维护任务（maintenance tasks）的 slot sharing group 配置方式，从显式设置默认 slot sharing group 改为使用 Flink 原生的 slot sharing group 继承机制。

此前的实现中，`TableMaintenance` 和 `MaintenanceTaskBuilder` 会显式地为所有操作符设置一个默认的 slot sharing group（`StreamGraphGenerator.DEFAULT_SLOT_SHARING_GROUP`）。这种做法的问题是：当用户没有显式指定 slot sharing group 时，维护任务的操作符会被强制分配到默认组，而不是继承父流的 slot sharing group。这破坏了 Flink 的原生 slot sharing 继承机制，可能导致资源分配不符合用户预期。

修复方案是将默认 slot sharing group 从 `DEFAULT_SLOT_SHARING_GROUP` 改为 `null`，当为 null 时不显式设置 slot sharing group，让 Flink 的原生继承机制自动处理。仅当用户显式指定了 slot sharing group 时才设置。

## 如何达成设计目的

通过以下修改实现重构：
1. 将 `slotSharingGroup` 默认值从 `DEFAULT_SLOT_SHARING_GROUP` 改为 `null`
2. 新增 `setSlotSharingGroup` 辅助方法，仅在 slotSharingGroup 非 null 时设置
3. 将所有操作符的 `.slotSharingGroup(slotSharingGroup)` 调用替换为 `setSlotSharingGroup(...)` 包装
4. 在 `IcebergSink` 中，仅当配置的 slot sharing group 非 null 时才设置

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (+66/-66 lines)

**修改目的**：将默认 slot sharing group 改为 null，使用条件设置。

**工作逻辑**：

```java
-private String slotSharingGroup = StreamGraphGenerator.DEFAULT_SLOT_SHARING_GROUP;
+private String slotSharingGroup = null;
```

将所有操作符创建处的直接调用：
```java
-.slotSharingGroup(slotSharingGroup)
```

替换为通过 `setSlotSharingGroup` 包装：
```java
+setSlotSharingGroup(
+    triggers.transform(...)
+        .uid(...)
+        .forceNonParallel())
```

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/MaintenanceTaskBuilder.java` (+4 lines)

**修改目的**：新增 setSlotSharingGroup 辅助方法。

**工作逻辑**：

```java
<O> SingleOutputStreamOperator<O> setSlotSharingGroup(SingleOutputStreamOperator<O> operator) {
  return slotSharingGroup == null ? operator : operator.slotSharingGroup(slotSharingGroup);
}
```

当 slotSharingGroup 为 null 时直接返回操作符（不设置），否则设置指定的 slot sharing group。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+7/-3 lines)

**修改目的**：仅当配置非 null 时设置 slot sharing group。

**工作逻辑**：

```java
-      .slotSharingGroup(flinkMaintenanceConfig.slotSharingGroup())
-      .parallelism(flinkMaintenanceConfig.parallelism())
-      .append();
+      .parallelism(flinkMaintenanceConfig.parallelism());
+
+      String slotSharingGroup = flinkMaintenanceConfig.slotSharingGroup();
+      if (slotSharingGroup != null) {
+        builder.slotSharingGroup(slotSharingGroup);
+      }
+
+      builder.append();
```

### 其他维护任务文件

`DeleteOrphanFiles.java`、`ExpireSnapshots.java`、`RewriteDataFiles.java`、`FlinkMaintenanceConfig.java` 等文件进行相应的重构适配。

### 测试文件

更新相关测试以适配新的行为。

## 总结

这是一个 Flink 维护任务的重构提交，将 slot sharing group 的配置方式从显式设置默认值改为使用 Flink 原生的继承机制。当用户未显式指定 slot sharing group 时，操作符会继承父流的 slot sharing group，而非被强制分配到默认组。这更符合 Flink 的设计理念，使资源分配更灵活、更符合用户预期。
