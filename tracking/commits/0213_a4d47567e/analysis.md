# 提交 0213：Core: Schema for a branch should return table schema (#9131)

## 提交信息

- **序号**：0213 / 4088
- **哈希**：a4d47567e1fef44f4443250537f09dc73a1f7583
- **短哈希**：a4d47567e
- **日期**：2023-12-05 12:03:43 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Schema for a branch should return table schema (#9131)
- **PR/Issue**：#9131

## 总体目的

本提交修复 `SnapshotUtil.schemaFor` 在分支（branch）场景下的语义错误。修复前，当向 `schemaFor(table, branch)` 传入一个已存在的 branch 名时，方法会返回该 branch 当前所指向 snapshot 的 schema（即历史 schema）；而正确语义应当是返回**表的当前 schema**（`table.schema()`）。只有当 ref 是 tag（不可移动的快照引用）时，才应返回该 tag 所指 snapshot 的 schema。

为什么 branch 必须返回表 schema 而非 snapshot schema？因为 Iceberg 中 branch 是一个"会向前演进"的引用：它指向某个 snapshot，但后续写入会更新 branch 的指向。读 branch 时的预期 schema，应当是该 branch **未来被写入时**所采用的 schema，也就是当前表 schema——而不是 branch 当前所指向历史快照的 schema。这一点尤其影响"在 branch 上写入新数据"的场景：写入器需要按表当前 schema 来构造数据，若返回历史 schema，写入会出错或读到不一致结果。对于 tag，因为它是不可变的固定指针，永远是某个历史快照的视图，所以返回 snapshot schema 才是正确的。

这是一个语义性 bug：在表 schema 演进（增删列）后，读 branch 数据会错误地按历史 schema 解析，导致列错位或读到不该出现的列。修复后，branch 读始终基于表当前 schema，与"branch 是会向前演进的引用"这一模型一致。

## 如何达成设计目的

核心改动在 `core/src/main/java/org/apache/iceberg/util/SnapshotUtil.java` 的两个 `schemaFor` 重载（一个接收 `Table`，一个接收 `TableMetadata`）。原实现根据 ref 是否存在来决定返回值，新实现根据 ref 是否是 **branch** 来决定：null 或不存在 → 表 schema；branch → 表 schema；tag → snapshot schema。参数名也从 `branch` 改为 `ref` 以反映它实际上接受任意 ref。同时新增单元测试 `TestSnapshotUtil` 的三个测试方法覆盖 null、不存在的 ref、main branch、普通 branch、tag 五种情况；并在 Spark v3.3/v3.4/v3.5 的 `TestSnapshotSelection` 中新增 `testWritingToBranchAfterSchemaChange` 端到端测试，验证 schema 演进后对 branch 的读写行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/SnapshotUtil.java`

**修改目的**：把 `schemaFor` 对 branch 的返回值从"branch 所指 snapshot 的 schema"改为"表当前 schema"，仅对 tag 返回 snapshot schema。

**工作逻辑**：

两个重载方法都做了相同的语义调整。

`schemaFor(Table table, String ref)` 修改前：

```java
public static Schema schemaFor(Table table, String branch) {
  if (branch == null || branch.equals(SnapshotRef.MAIN_BRANCH)) {
    return table.schema();
  }
  Snapshot ref = table.snapshot(branch);
  if (ref == null) {
    return table.schema();
  }
  return schemaFor(table, ref.snapshotId());
}
```

修改后：

```java
public static Schema schemaFor(Table table, String ref) {
  if (ref == null || ref.equals(SnapshotRef.MAIN_BRANCH)) {
    return table.schema();
  }
  SnapshotRef snapshotRef = table.refs().get(ref);
  if (null == snapshotRef || snapshotRef.isBranch()) {
    return table.schema();
  }
  return schemaFor(table, snapshotRef.snapshotId());
}
```

关键变化：

1. **数据源切换**：原代码用 `table.snapshot(branch)` 直接查 snapshot，无法区分 ref 是 branch 还是 tag（因为同一个 snapshotId 既可能被一个 branch 引用，也可能被一个 tag 引用）。新代码改用 `table.refs().get(ref)` 获取 `SnapshotRef` 对象，从而能调用 `snapshotRef.isBranch()` / `isTag()` 判断 ref 类型。

2. **判断条件反转**：原条件 `if (ref == null) return table.schema();` 仅当 ref 不存在时返回表 schema；新条件 `if (snapshotRef == null || snapshotRef.isBranch()) return table.schema();` 把"branch"也归入返回表 schema 的分支。这才是修复的核心——已存在的 branch 现在也走表 schema 路径。

3. **参数与文档**：参数名 `branch` → `ref`；Javadoc 从"If branch does not exist, the table schema is returned"改为"If the ref does not exist or the ref is a branch, the table schema is returned ... If the ref is a tag, then the snapshot schema is returned."。

`schemaFor(TableMetadata metadata, String ref)` 的修改完全对称：用 `metadata.ref(ref)` 取 `SnapshotRef`，判断 `snapshotRef == null || snapshotRef.isBranch()`，是 branch 则 `return metadata.schema()`；否则取 `metadata.snapshot(snapshotRef.snapshotId())` 并返回 `metadata.schemas().get(snapshot.schemaId())`。注意此处与 `Table` 重载略有差异——`Table` 重载通过 `schemaFor(table, snapshotRef.snapshotId())` 转发到另一个按 snapshotId 取 schema 的方法，而 `TableMetadata` 重载直接从 `metadata.schemas()` 取该 snapshot 的 schema。

### `core/src/test/java/org/apache/iceberg/util/TestSnapshotUtil.java`

**修改目的**：新增三个单元测试，分别覆盖 ref 不存在/null/main branch、普通 branch、tag 三类场景下 `schemaFor` 的返回值。

**工作逻辑**：

- `schemaForRef`：建表后验证 `schemaFor(table, null)`、`schemaFor(table, "non-existing-ref")`、`schemaFor(table, SnapshotRef.MAIN_BRANCH)` 都返回初始表 schema。
- `schemaForBranch`：创建 branch 后验证 `schemaFor(table, branch)` 返回表当前 schema；随后 `updateSchema().addColumn("zip", ...)` 修改表 schema，再次断言 `schemaFor(table, branch)` 返回**新的**表 schema（含 zip 列），而不是 branch 创建时快照的旧 schema。这条直接锁定本 PR 的修复语义。
- `schemaForTag`：创建 tag 后验证 `schemaFor(table, tag)` 返回表 schema（创建 tag 时表 schema 尚未演进）；随后 `updateSchema().addColumn("zip", ...)` 修改表 schema，断言 `schemaFor(table, tag)` 仍返回**旧的**初始 schema（与 branch 行为相反），验证 tag 走 snapshot schema 路径。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestSnapshotSelection.java`
### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSnapshotSelection.java`
### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSnapshotSelection.java`

**修改目的**：在 Spark 三个版本（v3.3、v3.4、v3.5）中分别扩展端到端测试，覆盖 schema 演进后对 branch 的读写行为，验证修复在 Spark 引擎层的正确性。

**工作逻辑**：三个文件的修改几乎完全相同（v3.3 与 v3.4/v3.5 仅在建表 API 上有细微差异——v3.3 用 `tables.create(SCHEMA, spec, tableLocation)`，v3.4/v3.5 用 `tables.create(SCHEMA, spec, properties, tableLocation)`）。

1. **修改既有测试** `testReadingFromBranchAfterSchemaChange`（原名为类似形态）：原测试在删除 `data` 列后断言"读 branch 仍能看到 data 列"（"The data should have the deleted column as it was captured in an earlier snapshot"）。修复后语义反转——断言"读 branch **不再**有被删的 data 列"，期望结果为只有 id 列的 3 行 `(1), (2), (3)`。然后再把 `data` 列加回来，验证"已删数据的列不会重新出现"——读 branch 得到 `(1, null), (2, null), (3, null)`。这条断言的变化直接体现了行为反转：branch 现在按表当前 schema 读，而不是历史 snapshot schema。

2. **新增测试** `testWritingToBranchAfterSchemaChange`：建表并写入第一批数据（id+data），创建 branch 指向当前快照；随后 `deleteColumn("data").addColumn("zip", IntegerType)` 替换列；先验证读 branch 时旧数据按新 schema 显示为 `(1, null), (2, null), (3, null)`（zip 为 null）；再用新 schema `(id, zip)` 向 branch 写入 3 行 `(4, 12345), (5, 54321), (6, 67890)`；最后读 branch 应得到 6 行，既包含历史数据（zip 为 null）也包含新写入的数据。这条测试覆盖了修复的核心动机：在 branch 上写入新数据必须按表当前 schema 进行，从而保证 branch 既能读历史又能按新 schema 接收新写入。

## 小结

本提交把 `SnapshotUtil.schemaFor` 对 branch 的语义从"返回 branch 所指历史 snapshot 的 schema"修正为"返回表当前 schema"，使 branch 作为可演进引用的读写都基于表 schema，仅 tag 仍返回固定 snapshot schema，从而修复了 schema 演进后 branch 读写不一致的语义 bug。
