# 提交 2917：Core: Support incremental Scan in RESTCatalogAdapter for RemoteScanPlanning (#14661)

## 提交信息

- **序号**：2917 / 4088
- **哈希**：f154dafb22676d69667f3b2380b2e9473601d4ae
- **短哈希**：f154dafb2
- **日期**：2025-11-24 15:59:04 -0800
- **作者**：Prashant Singh
- **提交说明**：Core: Support incremental Scan in RESTCatalogAdapter for RemoteScanPlanning
- **PR/Issue**：#14661

## 总体目的

在 Iceberg REST 协议中，`PlanTableScanRequest` 支持通过 `start-snapshot-id` 和 `end-snapshot-id` 参数进行增量扫描（incremental scan），即读取两个快照之间新增的数据文件。然而此前的服务端处理逻辑（`CatalogHandlers`）只支持普通的 `TableScan`，当请求包含 start/end snapshot ID 时，服务端仍使用 `table.newScan()` 创建普通扫描，无法正确处理增量扫描语义。

这意味着 REST 客户端无法通过远程扫描规划获取增量扫描结果，限制了增量扫描在 REST catalog 场景下的可用性。本提交修复了这一问题，使 `CatalogHandlers` 在检测到 start/end snapshot ID 时创建 `IncrementalAppendScan`，正确支持增量扫描的远程规划。

## 如何达成设计目的

设计上引入了通用的 `Scan<?, FileScanTask, ?>` 类型来统一处理 `TableScan` 和 `IncrementalAppendScan`，因为两者都继承自该泛型接口。将原先直接针对 `TableScan` 的配置逻辑抽取为通用的 `configureScan` 方法，通过泛型约束适用于所有 Scan 子类型。当请求同时包含 `startSnapshotId` 和 `endSnapshotId` 时创建增量扫描，否则创建普通扫描。`planFilesFor` 和 `asyncPlanFiles` 方法签名也改为接收通用 Scan 类型，并将 table uuid 作为独立参数传入（因为 Scan 接口不一定提供 table() 方法）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java` (+56/-23 lines)

**修改目的**：支持增量扫描的远程规划，重构扫描配置逻辑。

**工作逻辑**：
1. `planTableScan` 方法签名中，`shouldPlanAsync` 和 `tasksPerPlanTask` 的参数类型从 `TableScan` 改为 `Scan<?, FileScanTask, ?>`，以支持增量扫描。
2. 方法体内根据 `request.startSnapshotId()` 和 `request.endSnapshotId()` 是否同时非 null 来决定扫描类型：若同时存在则创建 `IncrementalAppendScan` 并调用 `fromSnapshotInclusive` 和 `toSnapshot`；否则创建普通 `TableScan` 并处理 `snapshotId`。
3. 新增私有静态方法 `configureScan`，通过泛型 `<T extends Scan<T, FileScanTask, ?>>` 统一应用 select、filter、statsFields、caseSensitive 配置，消除重复代码。
4. `planFilesFor` 和 `asyncPlanFiles` 方法签名从 `TableScan` 改为 `Scan<?, FileScanTask, ?>`，新增 `String tableId` 参数替代原先从 `tableScan.table().uuid()` 获取 uuid 的方式。
5. 在同步和异步规划调用处，将 `table.uuid().toString()` 作为 tableId 传入。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+4/-1 lines)

**修改目的**：更新测试适配器的接口方法签名以支持通用 Scan 类型。

**工作逻辑**：
将 `shouldPlanTableScanAsync` 方法的参数类型从 `TableScan` 改为 `Scan<?, FileScanTask, ?>`，并添加对应的 import。这使得测试适配器的异步规划判断逻辑同时适用于普通扫描和增量扫描。

## 总结

本提交为 REST catalog 的远程扫描规划增加了增量扫描支持。当请求包含 start/end snapshot ID 时，服务端现在正确创建 `IncrementalAppendScan` 而非普通 `TableScan`。通过引入通用泛型 `Scan<?, FileScanTask, ?>` 和抽取 `configureScan` 方法，代码在支持新功能的同时保持了整洁。这是一个功能补全性质的改动，使 REST catalog 与嵌入式 catalog 在增量扫描能力上保持一致。
