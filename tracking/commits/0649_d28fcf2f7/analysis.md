# 提交 0649：禁止 branch_ 标识符与 TIMESTAMP AS OF 组合使用

## 提交信息

- **序号**：0649 / 4088
- **哈希**：d28fcf2f746945d93d42d63268564d08c7b13a72
- **短哈希**：d28fcf2f7
- **日期**：2024-03-31
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Don't allow branch_ usage with TIMESTAMP AS OF (#10059)
- **PR/Issue**：PR #10059

## 总体目的

本提交修复 `SparkCatalog` 中一个被遗漏的校验缺口：当用户在表标识符里使用 `branch_<name>`（如 `db.table.branch_b1`）同时又使用 `TIMESTAMP AS OF` 子句时，Iceberg 没有拒绝这种组合，会导致语义混乱的时间旅行查询。

背景：`branch_` 前缀解析出一个指向分支 head 的可移动引用，而 `TIMESTAMP AS OF` 是按时间点回溯快照。把"可移动的分支 head 引用"和"按时间点回溯"叠在一起语义上自相矛盾——用户意图不明（是想查分支 head？还是想按时间查？）。先前 PR #9219（commit afe4aec4d，2023-12）已经为 `VERSION AS OF` 路径修复了同类问题，但**只改了 `loadTable(ident, String version)` 这一个重载**，遗漏了 `loadTable(ident, long timestamp)`（`TIMESTAMP AS OF` 走的重载）。本提交补齐这个遗漏，让两个重载行为一致。

## 如何达成设计目的

设计思路极简：把先前 PR #9219 在 String 重载里加的 `&& sparkTable.branch() == null` 条件，原样复制到 long（timestamp）重载的同一处 `Preconditions.checkArgument` 中。这样两个时间旅行重载都同时校验 `snapshotId` 和 `branch` 是否为空，任一非空都拒绝并抛出 `"Cannot do time-travel based on both table identifier and AS OF"`。

为什么 `branch_` 解析后 `branch()` 非空而 `snapshotId()` 为空？因为 `SparkCatalog.load(ident)` 对 `branch_(.*)` 模式调用 `new SparkTable(table, branch.group(1), !cacheEnabled)` 构造器，该构造器只设置 `branch` 字段，不设置 `snapshotId`（见 `SparkTable` 的 `(Table, String, boolean)` 构造器）。因此旧的对 `TIMESTAMP AS OF` 的校验 `snapshotId() == null` 对 `branch_` 场景恒为 true，无法拦截——必须额外检查 `branch()`。

修改覆盖 Spark 3.3、3.4、3.5 三个版本目录，保持一致。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`

**修改目的**：在 `TIMESTAMP AS OF` 走的 `loadTable(Identifier ident, long timestamp)` 重载中增加 `branch` 校验。

**工作逻辑**：该重载处理 `SELECT * FROM db.table TIMESTAMP AS OF <timestamp>`。流程是先 `loadTable(ident)` 拿到表（若 ident 含 `branch_b1`，返回的 SparkTable `branch="b1"`、`snapshotId=null`），再做时间旅行。修改把校验从

```java
Preconditions.checkArgument(
    sparkTable.snapshotId() == null,
    "Cannot do time-travel based on both table identifier and AS OF");
```

改为

```java
Preconditions.checkArgument(
    sparkTable.snapshotId() == null && sparkTable.branch() == null,
    "Cannot do time-travel based on both table identifier and AS OF");
```

这样 `branch_b1 TIMESTAMP AS OF now()` 会被拦截：`branch()` 返回 `"b1"`（非 null），校验失败抛 `IllegalArgumentException`。校验通过后，才用 `SnapshotUtil.snapshotIdAsOfTime(table, timestampMillis)` 查找时间点对应的 snapshotId 并 `copyWithSnapshotId` 返回。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`

**修改目的**：同 v3.3，为 v3.4 的 timestamp 重载补齐 branch 校验。改动一字不差。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`

**修改目的**：同上，为 v3.5 补齐。改动一字不差。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java`

**修改目的**：在已有测试 `testInvalidTimeTravelAgainstBranchIdentifierWithAsOf` 中追加 `TIMESTAMP AS OF` 场景的断言。

**工作逻辑**：该测试方法由 PR #9219 引入，原本只断言 `branch_b1 VERSION AS OF <snapshotId>` 抛错。本提交在其后追加：

```java
// using branch_b1 in the table identifier and TIMESTAMP AS OF
Assertions.assertThatThrownBy(
        () -> sql("SELECT * FROM %s.branch_b1 TIMESTAMP AS OF now()", tableName))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessage("Cannot do time-travel based on both table identifier and AS OF");
```

测试前置：创建分支 `b1`，再插一条数据产生第二个 snapshot，确保 `branch_b1` 引用有效。断言新场景与 `VERSION AS OF` 行为一致。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java`

**修改目的**：同 v3.3，为 v3.4 追加 `TIMESTAMP AS OF` 断言。改动一致（使用 `Assertions.assertThatThrownBy`）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java`

**修改目的**：同上，为 v3.5 追加。注意 v3.5 测试基类不同（`CatalogTestBase` 而非 `SparkCatalogTestBase`），使用静态导入的 `assertThatThrownBy`（非 `Assertions.assertThatThrownBy`），所以写法略有差异，但断言内容一致：

```java
// using branch_b1 in the table identifier and TIMESTAMP AS OF
assertThatThrownBy(() -> sql("SELECT * FROM %s.branch_b1 TIMESTAMP AS OF now()", tableName))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessage("Cannot do time-travel based on both table identifier and AS OF");
```

## 小结

- **成效**：本提交补齐了 PR #9219 遗漏的 `TIMESTAMP AS OF` 路径，使两个时间旅行重载对 `branch_` 标识符的拒绝行为完全一致，消除语义歧义。属于完整性修复，防止用户写出意图不明的查询。
- **影响范围**：仅影响使用 `branch_<name>` 标识符配合 `TIMESTAMP AS OF` 的查询场景，会从"静默执行（行为可能不符预期）"变为"显式报错"。对正常使用（`branch_` 单独用、或 `TIMESTAMP AS OF` 单独用、或 `tag_`/`snapshot_id_`/`at_timestamp_` 标识符）无影响。注意：`tag_` 与 `TIMESTAMP AS OF` 组合仍被允许，因为 tag 不可变指向固定 snapshot，语义上可叠加；这与 0647 文档阐明的"tag 用快照 schema"一致。
- **回迁到 1.4.x 的注意事项**：
  - 前置依赖：1.4.x 必须已回迁 PR #9219（commit afe4aec4d），否则 `VERSION AS OF` 路径仍漏。两个 PR 应成对回迁。若 1.4.x 已含 #9219，则本提交可直接回迁。
  - 三份 SparkCatalog.java 改动完全一致（单行 `&& sparkTable.branch() == null`），回迁时需确认 1.4.x 维护的 Spark 版本目录（可能是 3.3/3.4/3.5），逐版本补齐。
  - 测试回迁需注意 v3.5 与 v3.3/v3.4 的测试基类与断言 API 差异（`assertThatThrownBy` vs `Assertions.assertThatThrownBy`），不能盲目复制。
  - 该改动是严格的"收紧校验"——把原本静默通过的场景改为报错。回迁后若有用户依赖 `branch_` + `TIMESTAMP AS OF` 的旧行为，会收到错误。但这种用法本身语义错误，报错是正确做法，无需兼容。
