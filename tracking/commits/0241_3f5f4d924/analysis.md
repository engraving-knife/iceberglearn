# 提交 0241：Spark 3.2, 3.3, 3.4: Support specifying spec_id in RewriteManifestProcedure (#9243)(#9242)

## 提交信息

- **序号**：0241 / 4088
- **哈希**：3f5f4d924be94c6f06793a3667c8da95c52c4064
- **短哈希**：3f5f4d924
- **日期**：2023-12-08 09:20:26 -0600
- **作者**：Pucheng Yang
- **提交说明**：Spark 3.2, 3.3, 3.4: Support specifying spec_id in RewriteManifestProcedure (#9243)(#9242)
- **PR/Issue**：#9243, #9242

## 总体目的

Iceberg 的 `RewriteManifests` 动作（用于合并 / 重写 manifest 文件以优化读取性能）在底层 `RewriteManifestsSparkAction` 中早已支持通过 `specId(int)` 指定只针对某个 partition spec 的 manifest 进行重写。然而在 Spark 侧的 SQL 存储过程入口 `system.rewrite_manifests` 中，调用方只能传 `table` 和 `use_caching` 两个参数，无法把 `spec_id` 透传到底层 action，导致用户在表经历过分区演进（`ALTER TABLE ... ADD PARTITION FIELD`）后、想要精确地只重写某个 spec 的 manifest 时缺少 SQL 入口。

当一个表存在多个 partition spec（例如先按 `dt` 分区，后来又 `ADD PARTITION FIELD hr`），它的 manifest 会分散在不同 spec id 下。如果不允许指定 spec id，`rewrite_manifests` 会对所有 spec 进行重写，这可能不是用户期望的行为（例如只想合并旧 spec 下的碎小 manifest）。本提交为 Spark 3.2 / 3.3 / 3.4 三个版本的 `RewriteManifestsProcedure` 增加可选的 `spec_id` 参数，使得用户可以通过 `CALL system.rewrite_manifests(table => '...', spec_id => 0)` 精确控制重写范围。这是把底层 action 能力补齐到 SQL 调用层的一步，对多 spec 表的运维体验有直接价值。

## 如何达成设计目的

整体设计非常直接：在 `RewriteManifestsProcedure` 的 `PARAMETERS` 数组中追加一个可选的 `spec_id`（`IntegerType`）参数，在 `call` 方法中从 `InternalRow` 读出该参数，当其非空时调用底层 `action.specId(specId)` 把 spec 透传给 `RewriteManifestsSparkAction`。三个 Spark 版本（3.2/3.3/3.4）做完全相同的改动，并在每个版本的 `TestRewriteManifestsProcedure` 中新增 `testWriteManifestWithSpecId` 测试覆盖该流程。

## 修改详情

### `spark/v3.2/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteManifestsProcedure.java`、`spark/v3.3/.../RewriteManifestsProcedure.java`、`spark/v3.4/.../RewriteManifestsProcedure.java`

**修改目的**：在 `rewrite_manifests` 存储过程中新增可选 `spec_id` 参数并透传给底层 action。

**工作逻辑**：

- 在 `PARAMETERS` 数组中把原来 `optional("use_caching", BooleanType)` 之后追加 `ProcedureParameter.optional("spec_id", DataTypes.IntegerType)`，使 SQL 调用方可以通过命名参数 `spec_id => 0` 传入。
- 在 `call(InternalRow args)` 中新增 `Integer specId = args.isNullAt(2) ? null : args.getInt(2);`，从第三个槽位读取该参数（与 `PARAMETERS` 顺序一致），未传时为 null。
- 在 `modifyIcebergTable` 的回调中，紧跟 `use_caching` 处理之后新增 `if (specId != null) { action.specId(specId); }`，仅当用户显式指定 spec id 时才设置，避免影响默认行为（不指定时 action 会处理所有 spec）。
- 底层 `RewriteManifestsSparkAction.specId(int)` 会校验 `table.specs().containsKey(specId)`，并在执行时通过 `manifest -> manifest.partitionSpecId() == spec.specId()` 过滤只处理该 spec 的 manifest，因此无需在过程层做重复校验。

三个 Spark 版本的修改逐字节相同，仅文件路径中的版本号不同。

### `spark/v3.2/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteManifestsProcedure.java`、`spark/v3.3/.../TestRewriteManifestsProcedure.java`、`spark/v3.4/.../TestRewriteManifestsProcedure.java`

**修改目的**：新增 `testWriteManifestWithSpecId` 验证 `spec_id` 参数在多 spec 表上的行为。

**工作逻辑**：

- 建表 `PARTITIONED BY (dt)`，并设置 `commit.manifest-merge.enabled = false` 以避免 manifest 自动合并，便于观察每次 insert 产生的 manifest 数量。
- 两次 insert 产生两个 spec id = 0 的 manifest，通过 `SELECT partition_spec_id FROM table.manifests` 断言有两个 spec id 为 0 的 manifest。
- `ALTER TABLE ADD PARTITION FIELD hr` 引入 spec id = 1，再 insert 一条，断言此时 manifests 表中 partition_spec_id 为 `[0, 0, 1]`。
- 先调用不带 `spec_id` 的 `rewrite_manifests`，断言返回 `row(0, 0)`（即 rewritten=0, added=0，因为没有其他过滤条件且 manifest 数量未触发重写阈值，什么都没被重写）。
- 再调用 `rewrite_manifests(table => '...', spec_id => 0)`，断言返回 `row(2, 1)`：即重写了 spec id = 0 下的 2 个 manifest 并合并为 1 个新 manifest；同时验证 manifests 表中 partition_spec_id 变为 `[0, 1]`，确认只有 spec 0 的 manifest 被合并，spec 1 的 manifest 原封不动。这精确地验证了 `spec_id` 参数的语义：限定重写范围到指定 spec。

## 小结

本提交把底层 `RewriteManifestsSparkAction.specId()` 的能力透传到 Spark SQL 存储过程入口，使多 spec 表的 manifest 重写可以精确限定到某个 partition spec，补齐了 Spark 3.2/3.3/3.4 在 manifest 运维上的 SQL 调用面。
