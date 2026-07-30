# 提交 3906：Core, AWS, GCP, Dell, Hive: Fix FileIO leaks and standardize close() across Catalog implementations (#16862)

## 提交信息

- **序号**：3906 / 4088
- **哈希**：0ddbeead67275dc785ec2a905f14eb8847d75f98
- **短哈希**：0ddbeead6
- **日期**：2026-06-19 07:11:01 +0200
- **作者**：Mateus Aubin
- **提交说明**：Core, AWS, GCP, Dell, Hive: Fix FileIO leaks and standardize close() across Catalog implementations (#16862)
- **PR/Issue**：#16862

## 总体目的

修复各 Catalog 实现中的 FileIO 资源泄漏问题，并统一 `close()` 方法的实现模式。此前，各 Catalog 对 `Closeable` 资源的管理方式不统一：有些资源直接关闭，有些通过 `super.close()` 关闭，有些根本未被关闭。这种不一致导致了资源泄漏，特别是 FileIO 实例（持有底层文件系统连接、HTTP 客户端等资源）在 Catalog 关闭时未被释放。

具体泄漏和问题：
- `HadoopCatalog` 和 `InMemoryCatalog` 未将 FileIO 注册到 CloseableGroup，导致 FileIO 泄漏
- `BigQueryMetastoreCatalog` 完全没有 `close()` 方法，FileIO 和 metrics reporter 均泄漏
- `HiveCatalog` 手动关闭 keyManagementClient 而非通过 CloseableGroup，且 FileIO 未被关闭
- 多个 Catalog 在 `initialize()` 之前调用 `close()` 会 NPE

## 如何达成设计目的

通过统一使用 `CloseableGroup` 模式管理所有 Closeable 资源：将 FileIO、key management client、metrics reporter 等资源注册到 CloseableGroup，`close()` 方法只需关闭 CloseableGroup。添加 null 检查确保 `close()` 在 `initialize()` 之前调用是安全的。不改变任何公共 API 或 close() 方法签名。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/dynamodb/DynamoDbCatalog.java` (+3/-1 lines)
### `aws/src/main/java/org/apache/iceberg/aws/glue/GlueCatalog.java` (+3/-1 lines)
### `dell/src/main/java/org/apache/iceberg/dell/ecs/EcsCatalog.java` (+3/-1 lines)

**修改目的**：添加 null 检查使 close() 在 initialize() 前安全调用。

```java
@Override
public void close() throws IOException {
-  closeableGroup.close();
+  if (closeableGroup != null) {
+    closeableGroup.close();
+  }
}
```

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopCatalog.java` (+4/-1 lines)

**修改目的**：注册 FileIO 到 CloseableGroup 并添加 null 检查。

```java
this.closeableGroup = new CloseableGroup();
closeableGroup.addCloseable(lockManager);
+closeableGroup.addCloseable(fileIO);  // 修复泄漏
closeableGroup.addCloseable(metricsReporter());
closeableGroup.setSuppressCloseFailure(true);
```

### `core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java` (+4/-1 lines)

**修改目的**：注册 FileIO 到 CloseableGroup 并添加 null 检查。

```java
this.closeableGroup = new CloseableGroup();
+closeableGroup.addCloseable(io);  // 修复泄漏
closeableGroup.addCloseable(metricsReporter());
```

### `bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryMetastoreCatalog.java` (+14/-0 lines)

**修改目的**：新增 CloseableGroup 和 close() 方法。

```java
private CloseableGroup closeableGroup;
// 在 initialize() 中：
this.closeableGroup = new CloseableGroup();
closeableGroup.addCloseable(fileIO);
closeableGroup.addCloseable(metricsReporter());
closeableGroup.setSuppressCloseFailure(true);
// 新增 close()：
@Override
public void close() throws IOException {
  if (closeableGroup != null) {
    closeableGroup.close();
  }
}
```

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java` (+10/-4 lines)

**修改目的**：用 CloseableGroup 替代手动关闭，修复 FileIO 泄漏。

```java
this.closeableGroup = new CloseableGroup();
closeableGroup.addCloseable(fileIO);
closeableGroup.addCloseable(keyManagementClient);
closeableGroup.addCloseable(metricsReporter());
closeableGroup.setSuppressCloseFailure(true);

@Override
public void close() throws IOException {
-  super.close();
-  if (keyManagementClient != null) {
-    keyManagementClient.close();
-  }
+  if (closeableGroup != null) {
+    closeableGroup.close();
+  }
}
```
注意：共享的进程级 `CachedClientPool` 故意不纳入关闭，因为关闭它会破坏其他使用它的 Catalog。

## 总结

统一了 7 个 Catalog 实现的 close() 模式，修复了 FileIO 资源泄漏问题。所有 Catalog 现在通过 CloseableGroup 统一管理 FileIO、key management client 和 metrics reporter 等资源，并添加了 null 检查确保 close() 在 initialize() 前安全调用。该修复不改变公共 API，但显著改善了资源管理的一致性和可靠性。
