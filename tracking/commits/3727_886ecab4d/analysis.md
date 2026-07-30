# 提交 3727：Core: Fix NPE in RemoveOrphanFiles with prefix_listing for root table location (#16351)

## 提交信息

- **序号**：3727 / 4088
- **哈希**：886ecab4d6f72415fac69e1ad55f0bab9c25d18d
- **短哈希**：886ecab4d
- **日期**：2026-05-18 12:12:26 +0200
- **作者**：liuliquan-marshal
- **提交说明**：Core: Fix NPE in RemoveOrphanFiles with prefix_listing for root table location (#16351)
- **PR/Issue**：#16351

## 总体目的

本提交修复了在 `RemoveOrphanFiles` 过程中使用 `prefix_listing` 模式且表的 location 为存储根目录（如 S3 bucket 根 `s3://bucket/`）时发生的空指针异常（NPE）。

`FileSystemWalker.isHiddenPath()` 方法会从给定路径开始向上遍历父目录，以判断路径中是否包含被过滤（hidden）的目录。原实现使用 `while (currentPath.getParent().toString().contains(baseDir))` 作为循环条件，但当 `baseDir` 本身就是存储根目录（例如 `s3://bucket/`）时，路径的 `getParent()` 在到达根后会返回 `null`，此时调用 `null.toString()` 就会抛出 NPE，导致整个 `RemoveOrphanFiles` 操作失败。这种场景在使用对象存储（如 S3、OSS）且表 location 直接配置为 bucket 根时较为常见。

## 如何达成设计目的

修复思路是在循环条件中先取出 parent 并进行 null 检查，避免对 null 调用 `toString()`。具体地，将循环改为先获取 `parent = currentPath.getParent()`，然后判断 `parent != null && parent.toString().contains(baseDir)`，当 parent 为 null（到达存储根）时安全退出循环。同时将原先使用 `isHiddenPath` 标志变量 + break 的写法重构为直接 `return true` / `return false`，逻辑更清晰。

此外，新增了 4 个回归测试和一个 mock IO 工具类 `StaticPrefixFileIO`，覆盖 bucket 根目录、带/不带尾斜杠、隐藏目录过滤以及 null 文件位置等场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/FileSystemWalker.java` (+9/-6 lines)

**修改目的**：修复 `isHiddenPath` 在到达存储根时因 `getParent()` 返回 null 而抛出 NPE 的问题。

**工作逻辑**：
原实现：
```java
private static boolean isHiddenPath(String baseDir, Path path, PathFilter pathFilter) {
  boolean isHiddenPath = false;
  Path currentPath = path;
  while (currentPath.getParent().toString().contains(baseDir)) {  // NPE 风险
    if (!pathFilter.accept(currentPath)) {
      isHiddenPath = true;
      break;
    }
    currentPath = currentPath.getParent();
  }
  return isHiddenPath;
}
```
新实现：
```java
private static boolean isHiddenPath(String baseDir, Path path, PathFilter pathFilter) {
  Path currentPath = path;
  Path parent = currentPath.getParent();
  // Walk up the path hierarchy while the parent directory is still within baseDir.
  // Null-check the parent to avoid NPE when the walk reaches the storage root
  // (e.g., an S3 bucket root such as "s3://bucket/"), whose getParent() returns null.
  while (parent != null && parent.toString().contains(baseDir)) {
    if (!pathFilter.accept(currentPath)) {
      return true;
    }
    currentPath = parent;
    parent = currentPath.getParent();
  }
  return false;
}
```
关键变化：
- 在循环前先取出 `parent`，循环条件先判 `parent != null` 再调用 `toString()`，避免 NPE。
- 在循环体中将 `currentPath` 更新为 `parent`，并重新计算 `parent`，保持向上遍历。
- 用 `return true/false` 取代 `isHiddenPath` 标志变量 + break，简化控制流。

### `core/src/test/java/org/apache/iceberg/util/TestFileSystemWalker.java` (+92/-0 lines, 实为 +92/-0 新增内容)

**修改目的**：为修复添加回归测试覆盖，验证 bucket 根目录等场景下的正确性。

**工作逻辑**：
新增 4 个测试用例和 1 个辅助 mock IO 类：
- `testListDirRecursivelyWithFileIOBucketRootBaseDir`：baseDir 为 `s3://bucket/`，验证能正确列出 bucket 根及其子目录下的 parquet 文件。
- `testListDirRecursivelyWithFileIOBucketRootFiltersHiddenDir`：验证 bucket 根场景下 `_temporary/`、`.staging/` 等隐藏目录中的文件被正确过滤，且不发生 NPE。
- `testListDirRecursivelyWithFileIOBucketRootNoTrailingSlash`：baseDir 为 `s3://bucket`（无尾斜杠），验证仍能正常工作。
- `testListDirRecursivelyWithFileIONullFileLocation`：验证当 FileInfo 路径为 null 时抛出 `IllegalArgumentException`，包含消息 "Can not create a Path from a null string"。
- `listWithMockFileIO` 辅助方法：使用 `StaticPrefixFileIO`（一个实现了 `SupportsPrefixOperations` 的 mock）调用 `FileSystemWalker.listDirRecursivelyWithFileIO`，便于构造测试场景。
- `StaticPrefixFileIO` 内部类：实现 `SupportsPrefixOperations` 接口，`listPrefix` 返回构造时传入的固定 `Iterable<FileInfo>`，其余方法抛出 `UnsupportedOperationException`。

## 总结

本提交修复了 `FileSystemWalker.isHiddenPath()` 在表 location 为存储根目录（如 `s3://bucket/`）时因 `Path.getParent()` 返回 null 而抛出 NPE 的 Bug，使得 `RemoveOrphanFiles` 在 prefix_listing 模式下对对象存储根目录场景能正常工作。修复方式是引入 parent 的 null 检查并重构循环逻辑，同时新增多个回归测试覆盖 bucket 根、隐藏目录过滤、无尾斜杠和 null 路径等边界场景，保障修复的稳健性。
