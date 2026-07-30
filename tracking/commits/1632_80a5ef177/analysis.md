# 提交 1632 80a5ef177 分析

## 提交信息
- 哈希：80a5ef17733e209909f780d6149ae01e26c0cd36
- 日期：2025-01-24 10:55:04 +0100
- 作者：Eduard Tudenhoefner
- 消息：Spark: Disable rewriting position deletes for V3 tables (#12048)

## 总体目的

本提交针对 Spark 3.5 模块中的 `RewritePositionDeleteFilesSparkAction`（重写位置删除文件的动作）增加了一道前置校验：当目标表的格式版本（format version）为 V3 时，禁止执行重写位置删除文件操作，并直接抛出 `IllegalArgumentException`。

V3 表引入了新的行级血缘（row lineage）以及与 V2 不同的删除文件语义，原有的"重写位置删除文件"逻辑在 V3 表上尚不完全兼容，贸然执行可能产生不符合 V3 规范的删除文件或破坏数据正确性。因此在 V3 的位置删除重写能力完善之前，本提交采用"显式拦截"的策略，避免用户在 V3 表上误用该动作。

这是一个保护性（guard rail）变更，目的不是新增功能，而是为 V3 表的早期使用者提供一个清晰、明确的错误信息，而不是让动作在内部某个不兼容的环节以难以诊断的方式失败。

## 如何达成设计目的

设计思路非常直接：在执行动作前进行"前置条件检查（precondition check）"，即在动作真正开始工作之前就失败。具体做法是在 `RewritePositionDeleteFilesSparkAction` 中调用 `TableUtil.formatVersion(table)` 读取表的真实格式版本，再通过 `Preconditions.checkArgument` 限定 `formatVersion <= 2`。

之所以使用 `TableUtil.formatVersion(table)` 而不是直接读取表属性，是因为该工具方法能够正确处理 Spark 动作中常见的 `SerializableTable`（被序列化后传输到 executor 的表对象），也能回退到 `HasTableOperations` 的常规表实现，从而在驱动端和 executor 端都能拿到准确的格式版本。

### 修改详情

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java

新增对 `org.apache.iceberg.TableUtil` 的 import，并在动作执行链路中加入版本校验。

关键修改点位于执行（execute）相关方法中，紧随 `PARTIAL_PROGRESS_MAX_COMMITS` / `PARTIAL_PROGRESS_ENABLED` 等部分进度参数配置之后：

```java
Preconditions.checkArgument(
    TableUtil.formatVersion(table) <= 2, "Cannot rewrite position deletes for V3 table");
```

工作逻辑：在动作真正分发任务、扫描位置删除文件之前，先用 `TableUtil.formatVersion(table)` 取得格式版本（兼容 `SerializableTable` 与 `HasTableOperations` 两种表形态），再断言其不超过 2。若表为 V3，则立即抛出 `IllegalArgumentException`，消息为 "Cannot rewrite position deletes for V3 table"，动作不会进行任何文件扫描或重写。

#### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java

新增测试 `testRewritePositionDeletesForV3TableFails`，并重构了表属性构造工具方法以支持指定格式版本。

1. 新增测试方法 `testRewritePositionDeletesForV3TableFails`：使用 `tableProperties(3)` 创建一张 V3 表，写入记录后断言 `SparkActions.get(spark).rewritePositionDeletes(table).execute()` 抛出 `IllegalArgumentException` 且消息为 "Cannot rewrite position deletes for V3 table"。这直接覆盖了新增的校验逻辑。

2. 重构 `tableProperties()` 工具方法：
   - 原来的无参 `tableProperties()` 现在委托给 `tableProperties(2)`，保持默认行为不变（向后兼容）。
   - 新增带参 `tableProperties(int formatVersion)`，将原来硬编码的 `"2"` 替换为 `String.valueOf(formatVersion)`，使测试能够按需构造不同格式版本的表。

这种重构方式既让新测试能显式指定 V3，又没有破坏既有测试对 V2 的依赖，改动最小且聚焦。

## 小结

- 成效：本提交以最小代价（4 行产品代码 + 22 行测试）为 V3 表显式禁用了"重写位置删除文件"动作，给出了清晰的错误信息，避免用户在尚未支持的场景下误用。
- 影响范围：仅影响 Spark 3.5 模块的 `RewritePositionDeleteFilesSparkAction`，不影响其它模块或读/写路径。V1/V2 表的行为完全不变。
- 回迁到 1.4.x 的注意事项：本提交依赖 `core` 模块中的 `TableUtil.formatVersion(Table)` 工具方法。回迁前需确认 1.4.x 分支是否已包含 `TableUtil`（该类较新）。若 1.4.x 尚无 `TableUtil`，则需同时回迁 `TableUtil`，或改用 `table.operations().current().formatVersion()` 等既有方式获取格式版本（但需注意 `SerializableTable` 场景）。此外，1.4.x 默认不支持 V3，若 1.4.x 完全不接受 V3 表，则该校验可视为额外加固，回迁风险很低；但需确认 1.4.x 的测试基类 `CatalogTestBase` 等能够构造 V3 表，否则测试需相应调整。
