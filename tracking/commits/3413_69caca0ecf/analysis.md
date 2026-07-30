# 提交 3413：API, Core: Use Supplier for FileIO on Scan (#15646)

## 提交信息

- **序号**：3413 / 4088
- **哈希**：69caca0ecfe1b2f4a60b4164c24ea65853c23857
- **短哈希**：69caca0ecf
- **日期**：2026-03-18 09:38:02 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：API, Core: Use Supplier for FileIO on Scan (#15646)
- **PR/Issue**：#15646

## 总体目的

将 `Scan` 接口中的 `io()` 方法改为返回 `Supplier<FileIO>` 而非直接返回 `FileIO`。这一改动是为了支持延迟加载 FileIO 的场景，特别是 REST 扫描规划中 FileIO 需要在 `planFiles()` 调用后才可用。通过返回 Supplier，可以将 FileIO 的获取延迟到实际需要时，避免在扫描配置阶段就要求 FileIO 可用。

## 如何达成设计目的

1. 在 `Scan` 接口中将 `io()` 方法替换为 `fileIO()`，返回 `Supplier<FileIO>`
2. 在 `BatchScan` 和 `BatchScanAdapter` 中同步更新
3. 在 `BaseScan` 中将 `io()` 标记为 `@Deprecated` 并改为 protected，新增 `fileIO()` 返回 `table::io`
4. 在 `RESTTableScan` 中将 `fileIO()` 返回一个 lambda，在 get() 时检查 scanFileIO 是否可用
5. 更新测试使用 `fileIO().get()` 替代直接调用 `io()`

## 修改详情

### `api/src/main/java/org/apache/iceberg/Scan.java` (+5/-2 lines)

**修改目的**：将 `io()` 方法替换为 `fileIO()`。

**工作逻辑**：
- 移除 `default FileIO io()` 方法
- 新增 `default Supplier<FileIO> fileIO()` 方法，默认抛出 `UnsupportedOperationException`，说明 "fileIO() is not implemented: added in 1.11.0"

### `api/src/main/java/org/apache/iceberg/BatchScan.java` (+5/-2 lines)

**修改目的**：更新 BatchScan 的默认实现。

**工作逻辑**：
- 将 `default FileIO io()` 替换为 `default Supplier<FileIO> fileIO()`，返回 `table()::io`

### `api/src/main/java/org/apache/iceberg/BatchScanAdapter.java` (+5/-2 lines)

**修改目的**：更新适配器实现。

**工作逻辑**：
- `io()` 替换为 `fileIO()`，返回 `scan.fileIO()`

### `core/src/main/java/org/apache/iceberg/BaseScan.java` (+13/-4 lines)

**修改目的**：更新 BaseScan 实现。

**工作逻辑**：
- 将原有 `public FileIO io()` 标记为 `@Deprecated`，改为 `protected`，说明将在 1.12.0 移除
- 新增 `public Supplier<FileIO> fileIO()` 方法，返回 `table::io` 方法引用

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+11/-4 lines)

**修改目的**：更新 RESTTableScan 实现以支持延迟获取 FileIO。

**工作逻辑**：
- 将 `public FileIO io()` 替换为 `public Supplier<FileIO> fileIO()`
- 返回一个 lambda 表达式 `() -> { Preconditions.checkState(...); return scanFileIO; }`
- 在调用 `get()` 时才检查 `scanFileIO` 是否可用（需要先调用 `planFiles()`）

### `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java` (+15/-6 lines)

**修改目的**：更新测试使用 Supplier 接口。

**工作逻辑**：
- 将 `tableScan::io` 改为 `() -> tableScan.fileIO().get()`
- 将 `tableScan.io().properties()` 改为 `tableScan.fileIO().get().properties()`
- 将 `newScan::io` 改为 `() -> newScan.fileIO().get()`

## 总结

本提交将 `Scan` 接口的 `io()` 方法改为 `fileIO()` 返回 `Supplier<FileIO>`，支持 FileIO 的延迟获取。这对 REST 扫描规划尤为重要，因为 RESTTableScan 的 FileIO 需要在 `planFiles()` 调用后才可用。旧方法被弃用并改为 protected，计划在 1.12.0 移除。
