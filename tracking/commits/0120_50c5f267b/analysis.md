# 提交 0120：Core, Spark: Avoid extra copies of manifests while optimizing V2 tables (#8928)

## 提交信息

- **序号**：0120 / 4088
- **哈希**：50c5f267b7f2919fec77f8f690412c0cb24b0de5
- **短哈希**：50c5f267b
- **日期**：2023-10-31
- **作者**：Anton Okolnychyi
- **提交说明**：Core, Spark: Avoid extra copies of manifests while optimizing V2 tables (#8928)
- **PR/Issue**：#8928

## 总体目的

Iceberg 的 manifest 文件可以带或不带 snapshot ID。不带 snapshot ID 的 manifest 会"继承"引用它的那次 commit 的 snapshot ID。这一特性（snapshot-id-inheritance）允许 `RewriteManifests` 这类操作在 commit 之前就写好新 manifest，然后直接以原文件参与 commit，而无需在 commit 时为了打上 snapshot ID 再把整个 manifest 重写一遍（重写意味着把 manifest 里每一条 DataFile 元数据读出来、再以新 snapshot ID 写回，代价不低）。

对 V1 表，snapshot-id-inheritance 默认关闭，由表属性 `compatibility.snapshot-id-inheritance.enabled` 控制（默认 `false`）。但对 V2 表，规范层面 manifest 本就不再依赖 snapshot ID 做引用，snapshot-id-inheritance 实际上始终成立——无论该表属性是否设为 true。然而本提交之前的代码在 `BaseRewriteManifests` 与 `RewriteManifestsSparkAction` 中只读取表属性 `snapshot-id-inheritance.enabled` 来判断，没有考虑 format version。结果是：对 V2 表（默认该属性为 false），`RewriteManifests` 会在 commit 阶段把事先写好的 manifest 再重写一遍以打上 snapshot ID，产生不必要的额外拷贝；Spark action 在 commit 后还会把这些"重写前"的 manifest 文件当作孤儿删掉，进一步加重了 I/O 浪费。

本提交修复这个缺陷：在 `SnapshotProducer`（`BaseRewriteManifests` 的父类）中新增 `canInheritSnapshotId` 字段，其值为 `formatVersion > 1 || snapshotIdInheritanceEnabled`，即 V2+ 表永远视为可继承 snapshot ID；`BaseRewriteManifests` 与各 Spark 版本的 `RewriteManifestsSparkAction` 都据此调整判断条件，使 V2 表在 rewrite manifests 时跳过额外的 manifest 拷贝。这是一个实质性的性能优化，对频繁执行 `RewriteManifests`（如 manifest 合并、重写）的 V2 表可显著减少 I/O 与 commit 耗时。

## 如何达成设计目的

整体设计思路是把"是否可继承 snapshot ID"的判定从子类（`BaseRewriteManifests` / `RewriteManifestsSparkAction`）上移到公共父类 `SnapshotProducer`，并使其同时考虑 format version 与表属性。具体改动结构：

1. 在 `SnapshotProducer` 中新增 `canInheritSnapshotId` 字段与 `canInheritSnapshotId()` 方法，构造时按 `formatVersion > 1 || snapshotIdInheritanceEnabled` 计算。
2. 在 `BaseRewriteManifests` 中删除自有的 `snapshotIdInheritanceEnabled` 字段，把 `addManifest` 中的判断改为调用父类的 `canInheritSnapshotId()`。
3. 在 Spark v3.2/v3.3/v3.4/v3.5 四个版本的 `RewriteManifestsSparkAction` 中，把 commit 后清理新 manifest 的条件从 `!snapshotIdInheritanceEnabled` 收紧为 `formatVersion == 1 && !snapshotIdInheritanceEnabled`，即仅 V1 表且未启用继承时才需要删除重写前的 manifest。
4. 同步更新 `docs/configuration.md` 与 `docs/spark-procedures.md`，说明该属性对 V2+ 表始终为 true。
5. 在 core 与 Spark 测试中新增 format version 维度，断言 V2 表下 manifest 路径与写入时一致（未被重写），V1 表下则不一致（被重写）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`

**修改目的**：在公共父类中统一"是否可继承 snapshot ID"的判定逻辑，使其考虑 format version。

**工作逻辑**：
- 新增 import：`SNAPSHOT_ID_INHERITANCE_ENABLED` 与 `SNAPSHOT_ID_INHERITANCE_ENABLED_DEFAULT`。
- 新增字段 `private final boolean canInheritSnapshotId;`。
- 在构造函数中读取表属性 `snapshot-id-inheritance.enabled`，并计算 `this.canInheritSnapshotId = ops.current().formatVersion() > 1 || snapshotIdInheritanceEnabled;`。关键语义：V2+ 表恒为 true，V1 表取决于表属性。
- 新增 `protected boolean canInheritSnapshotId() { return canInheritSnapshotId; }`，供子类（如 `BaseRewriteManifests`）调用。

### `core/src/main/java/org/apache/iceberg/BaseRewriteManifests.java`

**修改目的**：让 manifest 重写判断使用父类的 `canInheritSnapshotId()`，从而对 V2 表跳过不必要的 manifest 重写。

**工作逻辑**：
- 删除 `snapshotIdInheritanceEnabled` 字段及其在构造函数中的初始化（含相关 import），改为继承父类的判定。
- 在 `addManifest(ManifestFile manifest)` 中，原判断 `if (snapshotIdInheritanceEnabled && manifest.snapshotId() == null)` 改为 `if (canInheritSnapshotId() && manifest.snapshotId() == null)`。当条件成立时，manifest 直接加入 `addedManifests`（保留 null snapshot ID，commit 时继承）；否则进入 else 分支，把 manifest 重写为带本次 commit snapshot ID 的新文件。对 V2 表，由于 `canInheritSnapshotId()` 恒为 true，只要 manifest 没带 snapshot ID 就直接复用，避免重写。

### `spark/v3.2|v3.3|v3.4|v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

**修改目的**：调整 commit 后清理新 manifest 文件的条件，使 V2 表不再误删已提交的 manifest。

**工作逻辑**：在 `replaceManifests` 方法中，原条件 `if (!snapshotIdInheritanceEnabled)` 改为 `if (formatVersion == 1 && !snapshotIdInheritanceEnabled)`。语义说明：当 V1 表且未启用 snapshot-id-inheritance 时，`BaseRewriteManifests.addManifest` 会把事先写好的 manifest 重写为带 snapshot ID 的新文件，那些"重写前"的 manifest 文件成了孤儿，需要 `deleteFiles` 清理；而对 V2 表（或启用继承的 V1 表），manifest 未被重写，事先写好的文件就是 commit 引用的文件，不能删。此处的 `formatVersion` 字段在类中已存在（`this.formatVersion = ops.current().formatVersion();`），无需新增。四个 Spark 版本的改动完全一致。

### `core/src/test/java/org/apache/iceberg/TestRewriteManifests.java`

**修改目的**：验证 V1 与 V2 表在 rewrite manifests 后 manifest 路径的差异行为。

**工作逻辑**：在两处测试中新增路径断言：
- 第一处（参数化测试，覆盖 V1/V2）：若 `formatVersion == 1`，断言 commit 后的 manifest 路径与事先写入的 `firstNewManifest`/`secondNewManifest` 路径**不相等**（因为 V1 默认未启用继承，manifest 被重写）；否则（V2）断言路径**相等**（manifest 未被重写，直接复用）。
- 第二处：直接断言路径相等，对应启用了继承的场景。新增 `import static org.assertj.core.api.Assertions.assertThat;`。

### `spark/v3.2|v3.3|v3.4|v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`

**修改目的**：把 format version 加入参数化测试维度，覆盖 V1 与 V2 两种场景。

**工作逻辑**：
- 参数化测试矩阵从 `{snapshotIdInheritanceEnabled, useCaching}` 两维扩展为三维 `{snapshotIdInheritanceEnabled, useCaching, formatVersion}`，四组参数调整为 `{"true","true",1}`、`{"false","true",1}`、`{"true","false",2}`、`{"false","false",2}`，即 V1 与 V2 各两组。
- 构造函数新增 `formatVersion` 字段。
- 在所有建表用例（`testRewriteManifestsEmptyTable`、`testRewriteSmallManifestsNonPartitionedTable`、`testRewriteManifestsWithCommitStateUnknownException`、`testRewriteSmallManifestsPartitionedTable`、`testRewriteImportedManifests`、`testRewriteLargeManifestsPartitionedTable`、`testRewriteManifestsWithPredicate`）的 options 中新增 `options.put(TableProperties.FORMAT_VERSION, String.valueOf(formatVersion));`，使参数化的 format version 生效。
- `testRewriteSmallManifestsNonPartitionedV2Table` 是专测 V2 的用例，在开头加 `assumeThat(formatVersion).isGreaterThan(1);`，当参数化 formatVersion 为 1 时跳过该用例，避免与参数化建表逻辑冲突。
- 新增 `import static org.assertj.core.api.Assumptions.assumeThat;` 与 `import org.junit.runners.Parameterized.Parameters;`。四个 Spark 版本的改动完全一致。

### `docs/configuration.md`

**修改目的**：说明 `compatibility.snapshot-id-inheritance.enabled` 对 V2+ 表始终为 true。

**工作逻辑**：将该属性的描述从"Enables committing snapshots without explicit snapshot IDs"改为"Enables committing snapshots without explicit snapshot IDs (always true if the format version is > 1)"。

### `docs/spark-procedures.md`

**修改目的**：同步 `changed_partition_count` 的 warning 提示，补充 V2+ 情形。

**工作逻辑**：在 `RewriteManifests` 过程的 `changed_partition_count` 说明 hint 中，原"changed_partition_count will be 0 when table property `compatibility.snapshot-id-inheritance.enabled` is set to true"补充为"... is set to true or if the table format version is > 1."。

## 小结

本提交通过在 `SnapshotProducer` 中引入 `canInheritSnapshotId`（对 V2+ 表恒为 true），让 `RewriteManifests` 与 Spark `RewriteManifestsSparkAction` 在 V2 表上跳过不必要的 manifest 重写与孤儿文件清理，是一次针对 V2 表 manifest 优化的实质性性能改进，并同步完善了文档与多版本测试覆盖。
