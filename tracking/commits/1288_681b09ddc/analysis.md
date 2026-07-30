# 提交 1288：Core: Move Javadoc about commit retries to SnapshotProducer (#10995)

## 提交信息

- **序号**：1288 / 4088
- **哈希**：681b09ddc95e66b056aa7954c6606c5ff329cb24
- **短哈希**：681b09ddc
- **日期**：2024-10-28（Mon Oct 28 10:13:50 2024 +0100）
- **作者**：gaborkaszab <gaborkaszab@cloudera.com>
- **提交说明**：Core: Move Javadoc about commit retries to SnapshotProducer (#10995)
- **PR/Issue**：#10995

## 总体目的

Iceberg 中所有产生新快照的写操作（`AppendFiles`、`DeleteFiles`、`OverwriteData`、`RewriteFiles` 等）最终都经由 `SnapshotProducer` 抽象基类提交。提交失败时框架会按配置重试，重试次数由表属性 `commit.retry.num-retries`（`TableProperties.COMMIT_NUM_RETRIES`，默认 `COMMIT_NUM_RETRIES_DEFAULT = 4`）控制。

旧代码中，三个 `SnapshotProducer` 子类——`FastAppend`、`MergeAppend`、`StreamingDelete`——各自在类级 Javadoc 里写了**重复且不准确的**一句话："This implementation will attempt to commit 5 times before throwing `CommitFailedException`."。问题有二：

1. **重复**：同一段说明被复制到三个子类，维护成本高、易不一致。
2. **不准确**：重试次数并非固定 5 次，而是由 `commit.retry.num-retries` 属性控制（默认 4 次重试 + 1 次初始尝试 = 5 次，但属性可改）。

本提交把这段说明**移到基类 `SnapshotProducer`** 的 Javadoc，并改为正确引用 `TableProperties.COMMIT_NUM_RETRIES` / `COMMIT_NUM_RETRIES_DEFAULT` 两个属性；同时移除三个子类中重复的 Javadoc 段落及其不再需要的 `CommitFailedException` import，并新增测试验证重试次数确实可由该属性调整。

## 如何达成设计目的

1. **下沉说明到基类**：在 `SnapshotProducer` 类声明上加 Javadoc，说明"重试次数由 `commit.retry.num-retries` 控制，默认见 `COMMIT_NUM_RETRIES_DEFAULT`"，一处说明、所有子类继承可见。
2. **清理子类**：`FastAppend`/`MergeAppend`/`StreamingDelete` 的类 Javadoc 仅保留对该实现的一句话简介（"adds a new manifest file for the write" 等），删除重试说明段落，并移除因此不再被引用的 `org.apache.iceberg.exceptions.CommitFailedException` import。
3. **新增测试**：在 `TestFastAppend` 中新增 `testIncreaseNumRetries()`，验证把 `commit.retry.num-retries` 设为 `COMMIT_NUM_RETRIES_DEFAULT + 1` 后，原本会失败的提交（注入 `COMMIT_NUM_RETRIES_DEFAULT + 1` 次失败）能成功，从而证明重试次数可配置——这与新 Javadoc 的描述一致。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`

**修改目的**：在基类上集中说明提交重试行为。

**工作逻辑**：在 `class SnapshotProducer<ThisT>` 声明前新增 Javadoc：

```java
/**
 * Keeps common functionality to create a new snapshot.
 *
 * <p>The number of attempted commits is controlled by {@link TableProperties#COMMIT_NUM_RETRIES}
 * and {@link TableProperties#COMMIT_NUM_RETRIES_DEFAULT} properties.
 */
```

### `core/src/main/java/org/apache/iceberg/FastAppend.java`

**修改目的**：去除重复/不准确的 Javadoc 与无用 import。

**工作逻辑**：

- 移除 import `org.apache.iceberg.exceptions.CommitFailedException`；
- 类 Javadoc 由含重试说明的多行改为单行：`/** {@link AppendFiles Append} implementation that adds a new manifest file for the write. */`。

### `core/src/main/java/org/apache/iceberg/MergeAppend.java`

**修改目的**：同上。

**工作逻辑**：

- 移除 import `CommitFailedException`；
- 类 Javadoc 改为 `/** {@link AppendFiles Append} implementation that produces a minimal number of manifest files. */`（顺带补上 `{@link AppendFiles}` 链接）。

### `core/src/main/java/org/apache/iceberg/StreamingDelete.java`

**修改目的**：同上。

**工作逻辑**：

- 移除 import `CommitFailedException`；
- 类 Javadoc 改为 `/** {@link DeleteFiles Delete} implementation that avoids loading full manifests in memory. */`。

### `core/src/test/java/org/apache/iceberg/TestFastAppend.java`

**修改目的**：验证 `commit.retry.num-retries` 可调整提交重试次数。

**工作逻辑**：新增 `@TestTemplate testIncreaseNumRetries()`：

1. 取 `table.ops()` 为 `TestTables.TestTableOperations`，调用 `ops.failCommits(TableProperties.COMMIT_NUM_RETRIES_DEFAULT + 1)` 注入"比默认重试多一次"的失败；
2. 用默认属性执行 `table.newFastAppend().appendFile(FILE_B)` 并断言 `commit()` 抛 `CommitFailedException`（消息 `"Injected failure"`）——证明默认重试次数下提交失败；
3. 通过 `table.updateProperties().set(COMMIT_NUM_RETRIES, String.valueOf(COMMIT_NUM_RETRIES_DEFAULT + 1)).commit()` 把重试次数加一；
4. 再次 `append.commit()`，断言成功，并 `validateSnapshot(null, readMetadata().currentSnapshot(), FILE_B)` 校验数据落地。

测试引入了对 `CommitFailedException`、`TableProperties`、`assertThatThrownBy` 的引用（测试本就需要这些）。

## 小结

- **成效**：提交重试行为的文档集中到 `SnapshotProducer` 基类并修正为引用正确的配置属性，消除三处重复且不准确的 Javadoc（"5 次"误导），并新增测试证明重试次数可由 `commit.retry.num-retries` 配置——文档与代码行为一致。
- **影响范围**：改动 5 个文件，新增 34 行、删除 21 行。涉及 `core` 的 `SnapshotProducer` 与三个子类（纯 Javadoc/import 清理）及 `TestFastAppend`（新增测试）。无运行时逻辑变更，仅文档与测试。
- **回迁到 1.4.x 的注意事项**：
  - **低风险、建议回迁**：纯文档修正 + 测试增强，不改运行时行为，可安全回迁；1.4.x 的 `SnapshotProducer`/`FastAppend`/`MergeAppend`/`StreamingDelete` 结构与 main 一致，预期可直接 cherry-pick。
  - **测试前置**：`testIncreaseNumRetries` 依赖 `TestTables.TestTableOperations.failCommits(...)` 与 `validateSnapshot`，回迁前确认 1.4.x 的 `TestFastAppend`/`TestTables` 已具备这些辅助方法。
  - **收益**：修正文档误导，避免使用者误以为重试次数固定不可调。
