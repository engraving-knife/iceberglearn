# 提交 2998：REST: Implement Batch Scan for RESTTableScan (#14776)

## 提交信息

- **序号**：2998 / 4088
- **哈希**：23dc32e5f1130c9d79d71cbf0be5b85162d8976c
- **短哈希**：23dc32e5f
- **日期**：2025-12-10 23:20:46 -0800
- **作者**：Prashant Singh
- **提交说明**：REST: Implement Batch Scan for RESTTableScan (#14776)
- **PR/Issue**：#14776

## 总体目的

Iceberg 的 `Table` 接口同时提供 `newScan()`（返回 `TableScan`，面向单文件任务）和 `newBatchScan()`（返回 `BatchScan`，面向按组批量返回 `ScanTaskGroup` 的场景）两种扫描入口。`BatchScan` 在引擎层很重要：例如 Flink/Spark 在某些场景下需要以"任务组"为单位调度，而 `POSITION_DELETES` 这类元数据表只实现了 `BatchScan` 接口（不实现 `newScan()`），调用方只能通过 `newBatchScan()` 访问。

`RESTTable` 是 Iceberg REST Catalog 协议下的表实现——它通过 `RESTTableScan`（一个 `TableScan` 子类）把扫描计划下推到远端 REST 服务器，由服务端返回分页的 `ScanTask`。问题在于：`RESTTable` 此前只覆写了 `newScan()`，并没有覆写 `newBatchScan()`。`BaseTable` 中默认的 `newBatchScan()` 实现并不会走 REST 远端扫描计划，而是回退到本地基于 manifest 的扫描，导致：

1. 对接 REST Catalog 的用户调用 `table.newBatchScan().planFiles()` 时，无法享受 REST 服务端做扫描计划（remote scan planning）的能力，扫描计划在客户端完成，与服务端规划模式（`PlanningMode`）语义不一致。
2. `POSITION_DELETES` 元数据表在 REST Catalog 下调用 `newScan()` 会直接抛异常（"does not implement newScan() method"），而调用 `newBatchScan()` 又因为 `RESTTable` 未覆写而走错误路径，存在功能缺口。

本提交的目的就是为 `RESTTable` 实现 `newBatchScan()`，让 batch scan 也复用 `RESTTableScan` 的远端扫描计划能力，从而填补 REST Catalog 在 batch scan 上的功能缺口，并让依赖 `BatchScan` 的元数据表（如 `POSITION_DELETES`）在 REST Catalog 下可用。

## 如何达成设计目的

整体思路非常直接：`RESTTable` 已经有 `newScan()` 返回 `RESTTableScan`，把已有的 `TableScan` 适配为 `BatchScan` 即可——Iceberg 的 API 模块本来就提供了 `BatchScanAdapter`，它把任意 `TableScan` 包装成 `BatchScan`，所有 `planFiles()` 等调用都委托给底层 `TableScan`。因此 `RESTTable.newBatchScan()` 只需 `return new BatchScanAdapter(newScan());`。

为了让 `RESTTable`（位于 `core` 模块）能访问到 `BatchScanAdapter`（位于 `api` 模块），需要把 `BatchScanAdapter` 的类与构造器从包级可见改为 `public`。测试侧则新增 `scanPlanningWithBatchScan` 验证 batch scan 走 REST 规划能正确返回数据文件，并把 `metadataTablesWithRemotePlanning` 中关于 `POSITION_DELETES` 的 `assumeThat` 跳过逻辑改为"对该类型用 `newBatchScan()` 验证"，从而把原先被跳过的用例转成正向覆盖。

## 修改详情

### `api/src/main/java/org/apache/iceberg/BatchScanAdapter.java` (+2/-2 lines)

**修改目的**：把 `BatchScanAdapter` 及其构造器从包级可见改为 `public`，以便 `core` 模块的 `RESTTable` 使用。

**工作逻辑**：
`BatchScanAdapter` 此前声明为 `class BatchScanAdapter implements BatchScan`，构造器 `BatchScanAdapter(TableScan scan)` 也是包级。它位于 `org.apache.iceberg` 包（api 模块），原本只供同模块内 `Table` 实现使用。本提交把类声明改为 `public class BatchScanAdapter implements BatchScan`，构造器改为 `public BatchScanAdapter(TableScan scan)`，使其成为 api 模块对外暴露的适配工具。`BatchScanAdapter` 本身只是把 `TableScan` 委托为 `BatchScan` 接口的薄封装，无副作用，公开化是合理的。这样任何 `TableScan` 实现（包括 `RESTTableScan`）都可以被复用为 `BatchScan`，无需每个 `Table` 子类各自实现一遍 batch 扫描。

### `core/src/main/java/org/apache/iceberg/rest/RESTTable.java` (+7/-0 lines)

**修改目的**：为 `RESTTable` 覆写 `newBatchScan()`，返回基于 `RESTTableScan` 的 `BatchScanAdapter`。

**工作逻辑**：
新增两个 import：`org.apache.iceberg.BatchScan` 和 `org.apache.iceberg.BatchScanAdapter`，然后追加：

```java
@Override
public BatchScan newBatchScan() {
  return new BatchScanAdapter(newScan());
}
```

`newScan()` 返回 `RESTTableScan`（一个远端扫描计划实现），`BatchScanAdapter` 把它包装为 `BatchScan`。当调用方执行 `newBatchScan().planFiles()` 时，`BatchScanAdapter.planFiles()` 委托给 `RESTTableScan.planFiles()`，后者按 REST Catalog 协议向服务端请求扫描计划并分页返回 `ScanTask`。这样 batch scan 就完整继承了 REST 远端规划的所有能力（同步/异步 `PlanningMode`、分页、parser context 等），无需在 `RESTTable` 中重复实现一套 batch 扫描逻辑。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java` (+27/-9 lines)

**修改目的**：新增针对 `newBatchScan()` 的端到端测试，并把原先被跳过的 `POSITION_DELETES` 元数据表用例改为通过 batch scan 验证。

**工作逻辑**：

新增测试 `scanPlanningWithBatchScan`，使用 `@ParameterizedTest @EnumSource(PlanningMode.class)` 在不同规划模式下验证 batch scan：

```java
try (CloseableIterable<ScanTask> iterable = table.newBatchScan().planFiles()) {
  List<ScanTask> tasks = Lists.newArrayList(iterable);
  assertThat(tasks).hasSize(1);
  assertThat(tasks.get(0).asFileScanTask().file().location()).isEqualTo(FILE_A.location());
  assertThat(tasks.get(0).asFileScanTask().deletes()).isEmpty();
}
```

这直接验证：通过 `newBatchScan()` 走 REST 远端规划后，能正确返回 1 个 `ScanTask`，其文件位置等于 `FILE_A.location()`，且没有 delete 文件。新增 `import org.apache.iceberg.ScanTask;` 以支持类型声明。

`metadataTablesWithRemotePlanning` 中的改动更具结构性意义：原本对 `POSITION_DELETES` 用 `assumeThat(type).isNotEqualTo(MetadataTableType.POSITION_DELETES)` 直接跳过（注释为"POSITION_DELETES table does not implement newScan() method"），意味着该元数据表类型在 REST Catalog 下从未被真正测试过。本提交去掉 `assumeThat`、把方法签名上的 `throws IOException` 也移除（因为不再需要），改为按类型分流：

```java
if (type.equals(MetadataTableType.POSITION_DELETES)) {
  // Position deletes table only uses batch scan
  assertThat(metadataTableInstance.newBatchScan().planFiles()).isNotEmpty();
} else {
  assertThat(metadataTableInstance.newScan().planFiles()).isNotEmpty();
}
```

这样 `POSITION_DELETES` 在 REST Catalog 下走 `newBatchScan()`（也就是新实现的 `RESTTable.newBatchScan()` → `BatchScanAdapter(RESTTableScan)`），其余元数据表仍走 `newScan()`。这同时把"REST Catalog 下 `POSITION_DELETES` 不可用"的盲区变成正式覆盖。

## 总结

该提交为 `RESTTable` 补齐了 `newBatchScan()` 的实现，通过把 `RESTTableScan` 适配为 `BatchScan` 让 batch scan 也复用 REST Catalog 的远端扫描计划能力；为此把 `BatchScanAdapter` 公开为 api 模块公共工具。测试侧新增了 batch scan 的端到端验证，并把原先被 `assumeThat` 跳过的 `POSITION_DELETES` 元数据表用例改造为通过 batch scan 正向覆盖，填补了 REST Catalog 在 batch scan 与 position-deletes 元数据表上的功能与测试缺口。
