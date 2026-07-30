# 提交 3694：Move all analyticscore references behind a AnalyticsCoreUtil class (#16258)

## 提交信息

- **序号**：3694 / 4088
- **哈希**：42f3d01e6ceaf84ae2810bf902d9cb7bcc4c4574
- **短哈希**：42f3d01e6
- **日期**：2026-05-12 13:00:41 -0700
- **作者**：Talat UYARER
- **提交说明**：Move all analyticscore references behind a AnalyticsCoreUtil class (#16258)
- **PR/Issue**：#16258

## 总体目的

这个提交将 GCS（Google Cloud Storage）模块中所有对 `com.google.cloud.gcs.analyticscore.*` 的引用集中到一个 `AnalyticsCoreUtil` 工具类中。`analyticscore` 是 Google Cloud Storage 的一个可选依赖，提供了 GCS 文件系统的高级功能（如优化的输入流），但并非所有用户都需要或能够使用此依赖。

此前，`analyticscore` 的类型引用散布在多个类中（`GCSInputFile`、`PrefixedStorage`、`BaseGCSFile` 等），这导致：
1. 类加载时需要 `analyticscore` 在 classpath 上，即使该功能未启用
2. 难以将 `analyticscore` 作为真正的可选依赖管理
3. 代码耦合度高，难以维护和测试

通过将所有引用集中到 `AnalyticsCoreUtil` 类中，可以利用 JVM 的延迟类加载特性——只有当 `AnalyticsCoreUtil` 被实际调用时（即 `GCS_ANALYTICS_CORE_ENABLED` 为 true 时），才会加载 `analyticscore` 相关类。这样其他类可以使用 `AutoCloseable` 等通用接口引用，避免直接依赖 `analyticscore` 类型。

## 如何达成设计目的

通过以下方式实现重构：
1. 新建 `AnalyticsCoreUtil` 类，包含所有 `analyticscore` 类型的引用和操作方法
2. 将 `BaseGCSFile`、`GCSInputFile`、`PrefixedStorage` 中的 `GcsFileSystem` 类型引用改为 `AutoCloseable`
3. 移除 `GCSInputFile` 中的 `analyticscore` 相关私有方法和导入
4. 移除 `PrefixedStorage` 中的 `gcsFileSystemSupplier` 方法和相关导入
5. 将 `GcsInputStreamWrapper` 类移入 `AnalyticsCoreUtil`（或作为内部类）
6. 更新测试类

## 修改详情

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/AnalyticsCoreUtil.java` (new file, +216 lines)

**修改目的**：集中所有 analyticscore 引用。

**工作逻辑**：

该类是 analyticscore 依赖的"网关"，所有对 `com.google.cloud.gcs.analyticscore.*` 类型的引用都被限制在此类中。提供以下静态方法：

- `createFileSystem(Map, Credentials)`：创建 GcsFileSystem 实例，返回 `AutoCloseable`
- `newStream(AutoCloseable, BlobId, Long, MetricsContext)`：创建 analyticscore 优化的输入流
- `close(AutoCloseable)`：关闭文件系统
- 私有辅助方法 `gcsItemId`、`gcsFileInfo`：构造 analyticscore 所需的数据结构

类注释明确说明："Gateway to the optional com.google.cloud.gcs.analyticscore.* dependency. All references to analytics-core types are confined to this class so that it is loaded only when GCS_ANALYTICS_CORE_ENABLED is true."

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/BaseGCSFile.java` (+4/-5 lines)

**修改目的**：将 GcsFileSystem 类型改为 AutoCloseable。

**工作逻辑**：

```java
-private final GcsFileSystem gcsFileSystem;
+// Using AutoCloseable avoids a runtime dependency on gcs-analytics-core. Cast via AnalyticsCoreUtil.
+private final AutoCloseable gcsFileSystem;
```

字段类型和构造函数参数类型都从 `GcsFileSystem` 改为 `AutoCloseable`，对应的 getter 方法返回类型也改为 `AutoCloseable`。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSInputFile.java` (+5/-44 lines)

**修改目的**：移除 analyticscore 直接引用，改用 AnalyticsCoreUtil。

**工作逻辑**：

移除了所有 `analyticscore` 相关导入和私有方法（`newGoogleCloudStorageInputStream`、`gcsItemId`、`gcsFileInfo`），将流创建委托给 `AnalyticsCoreUtil`：

```java
public SeekableInputStream newStream() {
  if (gcpProperties().isGcsAnalyticsCoreEnabled()) {
    try {
      return AnalyticsCoreUtil.newStream(gcsFileSystem(), blobId(), blobSize, metrics());
    } catch (IOException e) {
      // fallback to default
    }
  }
  return new GCSInputStream(storage(), blobId(), blobSize, gcpProperties(), metrics());
}
```

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/PrefixedStorage.java` (+33/-32 lines)

**修改目的**：重构 GcsFileSystem 的创建和生命周期管理。

**工作逻辑**：

1. 移除 `gcsFileSystemSupplier` 字段和方法，改为懒加载模式
2. `gcsFileSystem()` 方法检查 `isGcsAnalyticsCoreEnabled()`，仅在启用时通过 `AnalyticsCoreUtil.createFileSystem()` 创建
3. `close()` 方法重构，确保 `gcsFileSystem` 通过 `AnalyticsCoreUtil.close()` 正确关闭
4. 预先构建 `propertiesWithUserAgent`，避免每次创建文件系统时重复构建

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GcsInputStreamWrapper.java` (-143 lines)

**修改目的**：移除独立的 GcsInputStreamWrapper 类。

**工作逻辑**：该类的功能被移入 `AnalyticsCoreUtil` 中（作为内部类或内联代码），因为它包装的 `GoogleCloudStorageInputStream` 也是 analyticscore 类型。

### 测试文件更新

**修改目的**：更新测试以适配重构。

**工作逻辑**：新建 `TestAnalyticsCoreUtil.java`（+83 lines），移除 `TestGcsInputStreamWrapper.java`（-150 lines），更新 `TestGcsFileIO.java`、`TestGcsInputFile.java`、`TestPrefixedStorage.java`。

## 总结

这是一个重要的架构重构提交，通过将可选依赖 `analyticscore` 的所有引用集中到单一工具类中，实现了真正的可选依赖管理。利用 JVM 延迟类加载特性，确保未启用 analyticscore 功能的用户不需要在 classpath 上有此依赖。这种"网关模式"是管理可选依赖的最佳实践，提升了模块的解耦度和可维护性。
