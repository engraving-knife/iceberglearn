# 提交 1307：Core: use ManifestFiles.open when possible (#11414)

## 提交信息

- **序号**：1307 / 4088
- **哈希**：469c5560b26f8c0eb18bd04be8f4d8fb1fd87c20
- **短哈希**：469c5560b
- **日期**：2024-10-29（Tue Oct 29 15:03:38 2024 -0700）
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Core: use ManifestFiles.open when possible
- **PR/Issue**：#11414

## 总体目的

`BaseFilesTable.BaseManifestReader.files(Schema fileProjection)` 方法在读取 manifest 文件时，根据 `manifest.content()` 是 `DATA` 还是 `DELETES` 分别调用 `ManifestFiles.read(...)` 或 `ManifestFiles.readDeleteManifest(...)`，并在 `default` 分支抛出 `IllegalArgumentException("Unsupported manifest content type:...")`。

而 `ManifestFiles` 中其实早已存在一个包级私有的 `open(ManifestFile, FileIO, Map<Integer, PartitionSpec>)` 工具方法，做的就是完全相同的事情：

```java
static ManifestReader<?> open(ManifestFile manifest, FileIO io, Map<Integer, PartitionSpec> specsById) {
  switch (manifest.content()) {
    case DATA:
      return ManifestFiles.read(manifest, io, specsById);
    case DELETES:
      return ManifestFiles.readDeleteManifest(manifest, io, specsById);
  }
  throw new UnsupportedOperationException("Cannot read unknown manifest type: " + manifest.content());
}
```

本提交把 `BaseFilesTable.files` 中重复的 switch 逻辑替换为对 `ManifestFiles.open` 的调用，消除重复代码、统一行为，并让后续若新增 manifest content 类型时只需改一处。

## 如何达成设计目的

将 `files` 方法从 9 行的 switch 语句压缩为单行调用：

```java
return ManifestFiles.open(manifest, io, specsById).project(fileProjection);
```

`ManifestFiles.open` 内部已封装好按 content 类型分发到 `read`/`readDeleteManifest` 的逻辑，并对未知类型抛 `UnsupportedOperationException`，等价于原 `default` 分支的 `IllegalArgumentException`（异常类型略有差异，但语义一致：都是表示不支持的 manifest 类型）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseFilesTable.java`（修改，+1/-9 行）

**修改目的**：消除 `BaseFilesTable.BaseManifestReader.files` 中与 `ManifestFiles.open` 重复的 manifest content 分发逻辑。

**工作逻辑**：

修改前：

```java
private CloseableIterable<? extends ContentFile<?>> files(Schema fileProjection) {
  switch (manifest.content()) {
    case DATA:
      return ManifestFiles.read(manifest, io, specsById).project(fileProjection);
    case DELETES:
      return ManifestFiles.readDeleteManifest(manifest, io, specsById).project(fileProjection);
    default:
      throw new IllegalArgumentException(
          "Unsupported manifest content type:" + manifest.content());
  }
}
```

修改后：

```java
private CloseableIterable<? extends ContentFile<?>> files(Schema fileProjection) {
  return ManifestFiles.open(manifest, io, specsById).project(fileProjection);
}
```

`ManifestFiles.open` 返回 `ManifestReader<?>`（通配符泛型），后续 `.project(fileProjection)` 仍返回 `CloseableIterable<? extends ContentFile<?>>`，与方法签名兼容。运行时行为：

- DATA manifest → 走 `ManifestFiles.read`，返回 `ManifestReader<DataFile>`；
- DELETES manifest → 走 `ManifestFiles.readDeleteManifest`，返回 `ManifestReader<DeleteFile>`；
- 未知类型 → 抛 `UnsupportedOperationException`（原为 `IllegalArgumentException`，属于行为微调，但两者均为非受检异常且表示同一类错误，调用方一般不会捕获区分）。

## 小结

- **成效**：消除 `BaseFilesTable` 与 `ManifestFiles` 之间重复的 manifest content 分发逻辑，把读取入口统一到 `ManifestFiles.open`，代码更简洁、后续扩展更聚焦。这是一次纯粹的代码清理，无功能变化。
- **影响范围**：仅 `core` 模块的 `BaseFilesTable` 一个文件、一个方法、9 行变 1 行。`ManifestFiles.open` 是既有方法，未修改。所有数据文件表（`DataFilesTable`、`AllDataFilesTable`、`AllManifestFilesTable` 等继承自 `BaseFilesTable` 的元数据表）的 manifest 读取路径都受影响，但行为等价。
- **回迁到 1.4.x 的注意事项**：
  1. 1.4.x 分支的 `ManifestFiles.open` 应已存在（它是早期就引入的包级私有工具方法，`IncrementalFileCleanup` 等已在使用），可直接回迁；
  2. 异常类型从 `IllegalArgumentException` 改为 `UnsupportedOperationException`，若有调用方依赖具体异常类型做分支处理需注意（实际上 Iceberg 内部没有这种依赖）；
  3. 1.4.x 上 `BaseFilesTable` 的 `files` 方法实现可能与 main 略有差异（如本提交基于的版本），回迁时需以 1.4.x 当前实现为基准替换；
  4. 这是一次零风险重构，建议直接 cherry-pick。
