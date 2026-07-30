# 提交 3379：API, Core: Add FileIO to Scan API (#15561)

## 提交信息

- **序号**：3379 / 4088
- **哈希**：cf6f83550afc9081232b437c58730048aad51425
- **短哈希**：cf6f83550
- **日期**：2026-03-13
- **作者**：Eduard Tudenhoefner
- **提交说明**：API, Core: Add FileIO to Scan API (#15561)
- **PR/Issue**：#15561

## 总体目的

本提交在 `Scan` 公共 API 中新增 `io()` 方法，使调用方能够获取与特定 Scan 实例关联的 `FileIO`，尤其是 REST 表在远程扫描规划（distributed scan planning）场景下生成的、携带了 plan-scoped 存储凭证的 FileIO 实例。这解决了此前 REST 扫描规划产生的临时 FileIO 无法被外部访问的关键缺口。

背景是 Iceberg 的 REST Catalog 支持"服务端扫描规划"（server-side scan planning）。当服务端规划扫描时，会返回带有效期（plan-scoped）的存储凭证（storage credentials），客户端据此构造一个临时的、绑定了该 planId 的 FileIO 用于读取数据文件。此前这个 FileIO 被封装在 `RESTTableScan` 内部的 `fileIOForPlanId` 字段中，外部无法获取，导致需要读取文件内容的下游消费者（如 Spark task）无法直接复用这个带凭证的 FileIO，只能退回使用表级别的 FileIO（可能缺少服务端下发的短期凭证）。

更深层的问题在于原设计的状态管理混乱：`RESTTableScan` 同时维护 `tableIO`（表级 FileIO）和 `fileIOForPlanId`（plan 级 FileIO）两个字段，`io()` 方法的返回值取决于 `fileIOForPlanId` 是否已设置，而该字段在 `planFiles()` 调用前后会变化，这种隐式时序依赖使得行为难以预测。本提交通过将 `io()` 提升为公共 API 并简化内部状态来一并解决这些问题。

## 如何达成设计目的

整体设计分三步：一是在 `Scan` 接口新增 `default FileIO io()` 方法（默认抛出 `UnsupportedOperationException`，标注 "added in 1.11.0" 以表明 API 兼容性），在 `BatchScan` 中提供默认实现返回 `table().io()`；二是在 `BaseScan` 中将原有的 `protected io()` 改为 `public` 并加 `@Override`；三是重构 `RESTTableScan`，移除独立的 `table` 和 `tableIO` 字段，统一用 `scanFileIO` 字段表示当前 scan 可用的 FileIO，并强制要求 `planFiles()` 必须先调用——未调用时 `io()` 抛出 `IllegalStateException`，使时序依赖显式化。同时在 `RESTTable` 构造 `RESTTableScan` 时不再传入 `io()`，因为 scan 可通过 `table().io()` 获取表级 FileIO。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Scan.java` (+6/-0 lines)

**修改目的**：在 Scan 接口新增 `io()` 方法。

**工作逻辑**：
新增 `default FileIO io()` 方法，默认实现抛出 `UnsupportedOperationException`，消息为 `"io() is not implemented: added in 1.11.0"`。使用 `default` 方法保证对现有 Scan 实现的二进制兼容性，未覆盖该方法的实现会在调用时明确报错而非返回 null。

### `api/src/main/java/org/apache/iceberg/BatchScan.java` (+8/-0 lines)

**修改目的**：为 BatchScan 接口提供 `io()` 默认实现。

**工作逻辑**：
新增 `@Override default FileIO io()`，返回 `table().io()`。BatchScan 作为最常见的扫描接口，其 FileIO 默认来源于表本身，符合多数引擎（非 REST 分布式规划）场景的预期。

### `api/src/main/java/org/apache/iceberg/BatchScanAdapter.java` (+6/-0 lines)

**修改目的**：在适配器中委托 `io()` 给被包装的 scan。

**工作逻辑**：
新增 `@Override public FileIO io()` 返回 `scan.io()`。`BatchScanAdapter` 将 `TableScan` 适配为 `BatchScan`，需将 `io()` 调用委托给内部持有的 `TableScan`，确保适配后的 scan 行为一致。

### `core/src/main/java/org/apache/iceberg/BaseScan.java` (+2/-1 lines)

**修改目的**：将 `BaseScan` 的 `io()` 从 protected 提升为 public。

**工作逻辑**：
原有 `protected FileIO io()` 改为 `@Override public FileIO io()`，实现仍为 `return table.io()`。`BaseScan` 是 `DataTableScan`、`RESTTableScan` 等的基类，提升可见性使其满足新的 `Scan.io()` 接口契约。

### `core/src/main/java/org/apache/iceberg/rest/RESTTable.java` (+0/-1 lines)

**修改目的**：构造 `RESTTableScan` 时不再显式传入 FileIO。

**工作逻辑**：
移除传给 `RESTTableScan` 构造器的 `io()` 参数。因为 scan 现在可以通过 `table().io()` 获取表级 FileIO，无需在构造时单独传入，减少了字段冗余。

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+27/-19 lines)

**修改目的**：重构 RESTTableScan 的 FileIO 状态管理，实现新的 `io()` 契约。

**工作逻辑**：
这是本提交的核心改动。首先移除了 `table`、`tableIO` 两个字段及构造器中对应的 `tableIO` 参数，scan 通过继承自 `DataTableScan` 的 `table()` 方法获取表。将原 `fileIOForPlanId` 字段重命名为 `scanFileIO`，语义更清晰。

`io()` 方法从 `protected` 改为 `public`，并加入前置条件检查：`Preconditions.checkState(null != scanFileIO, "FileIO is not available: planFiles() must be called first")`。这强制调用方必须先执行 `planFiles()` 才能获取 FileIO，将原本隐式的时序依赖变为显式契约——若未规划就调用 `io()`，会立即得到明确的错误而非静默返回错误的 FileIO。

`planFiles()` 与 `fetchPlanResults()` 中，原 `fileIOForPlanId` 仅在 `planId != null` 且凭证非空时设置的逻辑，改为：`this.scanFileIO = !response.credentials().isEmpty() ? scanFileIO(response.credentials()) : table().io()`。即当服务端返回凭证时构造带凭证的 plan-scoped FileIO，否则退回使用表级 FileIO。`scanFileIO(...)` 方法（原 `fileIOForPlanId`）也做了改进：构造 FileIO 配置属性时，仅在 `planId != null` 时才把 `REST_SCAN_PLAN_ID` 放入属性，避免 null 值被写入。

`cleanupPlanResources()` 简化为直接置空 `scanFileIO` 并调用 `FILEIO_TRACKER.invalidate(this)`，移除了 null 检查分支。此外，`copy()` 方法中传给新 scan 的参数也相应移除了 `io()`，且一处 `table.currentSnapshot()` 改为 `table().currentSnapshot()`（因 `table` 字段已移除）。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java` (+111/-0 lines)

**修改目的**：验证 plan-scoped FileIO 的传播与隔离。

**工作逻辑**：
新增参数化测试 `fileIOForRemotePlanningIsPropagated`（覆盖所有 `PlanningMode` 枚举值）。测试通过 Mockito spy 包装 `RESTCatalogAdapter`，在响应中注入虚拟 storage credential（`maybeAddStorageCredential` 方法对 `PlanTableScanResponse` 和 `FetchPlanningResultResponse` 在 `COMPLETED` 状态时追加一个 dummy credential）。

测试断言：表级 `table.io()` 的属性不含 `REST_SCAN_PLAN_ID`；新创建的 scan 在 `planFiles()` 之前调用 `io()` 抛出 `IllegalStateException`（"FileIO is not available: planFiles() must be called first"）；调用 `planFiles()` 后，`scan.io()` 的属性包含 `REST_SCAN_PLAN_ID`；再次创建新 scan 规划后，两个 scan 的 planId 不同（验证每次规划独立）。`maybeAddStorageCredential` 辅助方法确保响应携带凭证，从而触发 plan-scoped FileIO 的构造路径。

## 总结

本提交将 `FileIO` 获取能力提升为 `Scan` 公共 API 的一部分，使调用方能拿到与特定 scan 绑定的、可能携带短期服务端凭证的 FileIO 实例。同时重构了 `RESTTableScan` 内部的 FileIO 状态管理，将隐式时序依赖变为显式契约（必须先 `planFiles()` 才能调 `io()`），消除了 `tableIO`/`fileIOForPlanId` 双字段的混乱，为 REST 分布式扫描规划场景下的文件读取提供了清晰、安全的能力暴露路径。
