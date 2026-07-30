# 提交 1802：Core: Code cleanup around TestTable and TestTableOperations (#12419)

## 提交信息

- **序号**：1802 / 4088
- **哈希**：b87edbb01f210ab2d7dd30325a376df0ef427e1d
- **短哈希**：b87edbb01
- **日期**：2025-02-28 12:39:47 +0100
- **作者**：gaborkaszab
- **提交说明**：Core: Code cleanup around TestTable and TestTableOperations (#12419)
- **PR/Issue**：#12419

## 总体目的

Iceberg core 模块测试辅助类 `TestTables` 中的 `TestTable` 和 `TestTableOperations` 存在两处代码冗余，影响可维护性：

1. `TestTableOperations` 已持有一个 `tableName` 成员字段，但 `TestTable` 的构造器却额外接收 `name` 参数并传给父类 `BaseTable`，造成同一个表名信息在两处重复维护，容易不一致。
2. `TestTableOperations` 的两个构造器（一个接收 `File`，另一个接收 `FileIO`）有大量重复的初始化代码（设置 tableName、metadata、创建目录、refresh、计算 lastSnapshotId 等），违反 DRY 原则。

本提交对这两处冗余进行清理，使代码更简洁、更易维护。

## 如何达成设计目的

1. 去除 `TestTable` 构造器中的 `name` 参数，改为从 `ops.tableName` 获取表名传给父类 `BaseTable`，消除重复。
2. 让 `TestTableOperations` 的单参数构造器（`tableName, File location`）委托调用双参数构造器（`tableName, File location, FileIO fileIO`），消除重复初始化逻辑。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestTables.java`（修改, +10 -21 lines）

**修改目的**：消除 `TestTable` 和 `TestTableOperations` 中的代码冗余。

**工作逻辑**：
- **TestTable 构造器简化**：
  - 原 `private TestTable(TestTableOperations ops, String name)` 改为 `private TestTable(TestTableOperations ops)`，内部调用 `super(ops, ops.tableName)`。
  - 原 `private TestTable(TestTableOperations ops, String name, MetricsReporter reporter)` 改为 `private TestTable(TestTableOperations ops, MetricsReporter reporter)`，内部调用 `super(ops, ops.tableName, reporter)`。
  - 所有调用 `new TestTable(ops, name)` 和 `new TestTable(ops, name, reporter)` 的地方相应改为 `new TestTable(ops)` 和 `new TestTable(ops, reporter)`。
- **TestTableOperations 构造器去重**：
  - 原 `public TestTableOperations(String tableName, File location)` 构造器内有一大段初始化代码（设置 tableName、metadata 目录、fileIO、mkdirs、refresh、计算 lastSnapshotId），全部删除，改为 `this(tableName, location, new LocalFileIO())`，委托给三参数构造器。
  - 三参数构造器 `public TestTableOperations(String tableName, File location, FileIO fileIO)` 保留原有的完整初始化逻辑，成为唯一的初始化入口。

## 小结

- **成效**：消除了 `TestTable` 中表名信息的重复传递，以及 `TestTableOperations` 构造器中的重复初始化代码，使测试辅助类更简洁、更易维护。
- **影响范围**：仅涉及 `core` 模块测试代码 `TestTables.java`，不影响生产代码。所有调用 `TestTable`/`TestTableOperations` 的测试用例行为不变（表名仍正确传递，初始化逻辑仍完整执行）。
- **回迁到 1.4.x 的注意事项**：纯测试代码重构，无风险，无前置依赖。但需注意 1.4.x 分支上 `TestTables.java` 的代码可能与 main 分支已有差异（如本批次中提交 1803 也修改了 `TestTables.java`）。回迁时需确保 1.4.x 上 `TestTableOperations` 确实有 `tableName` 字段且两个构造器存在，否则需调整。建议优先级低，可视 1.4.x 实际代码情况决定是否回迁。
