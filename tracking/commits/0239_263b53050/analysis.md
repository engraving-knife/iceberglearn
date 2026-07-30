# 提交 0239：Spark 3.5: Support Specifying spec_id in RewriteManifestProcedure (#9242)

## 提交信息

- **序号**：0239 / 4088
- **哈希**：263b530502e5597b19b6b5e282917af8eede7600
- **短哈希**：263b53050
- **日期**：2023-12-07 15:34:27 -0600
- **作者**：Pucheng Yang
- **提交说明**：Spark 3.5: Support Specifying spec_id in RewriteManifestProcedure (#9242)
- **PR/Issue**：#9242

## 总体目的

本提交为 Spark 3.5 的 `system.rewrite_manifests` 存储过程新增 `spec_id` 可选参数，允许用户在执行 manifest 重写时只针对某个特定的分区规约（partition spec）重写其 manifest 文件。背景是 Iceberg 表支持多分区规约共存（通过 `ALTER TABLE ... ADD PARTITION FIELD` 演进分区策略），表的 `manifests` 中可能混合着不同 `partition_spec_id` 的 manifest。默认情况下 `rewrite_manifests` 会处理所有 spec 的 manifest，但在分区演进后用户往往只想重写旧 spec（或新 spec）下的 manifest，避免无谓地重写其它 spec 的 manifest，从而节省写入与提交开销。

底层 action [`RewriteManifestsSparkAction`](spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java) 早已暴露 `specId(int)` 方法（本提交未改动它，其内部会把 `spec` 切换为 `table.specs().get(specId)`，并在筛选 manifest 时按 `manifest.partitionSpecId() == spec.specId()` 过滤），但 Spark 侧的 procedure 包装层此前没有把这个能力透传给 SQL 用户。本提交正是补齐 procedure 层的参数透传，并在 Spark 3.5 extensions 测试中加入端到端用例验证。

## 如何达成设计目的

整体设计是"procedure 参数透传 + 测试覆盖"：在 `RewriteManifestsProcedure` 的 `PARAMETERS` 数组追加一个可选的 `spec_id` 整型参数，在 `call` 中读取并在非空时调用底层 action 的 `action.specId(specId)`。由于底层 action 已有完整实现，procedure 层改动极小（约 8 行新增）。配套测试通过"建表 → 插入数据 → 演进分区 → 调用带 spec_id 的 rewrite_manifests → 查询 `manifests` 元数据表验证 partition_spec_id 分布"的方式做端到端验证。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteManifestsProcedure.java`

**修改目的**：在 Spark procedure 的参数列表与 `call` 实现中新增 `spec_id` 可选参数并透传给底层 action。

**工作逻辑**：
- 在 `PARAMETERS` 数组中，紧跟 `table`（required）与 `use_caching`（optional）之后新增 `ProcedureParameter.optional("spec_id", DataTypes.IntegerType)`，使 SQL 用户可用命名参数 `spec_id => <int>` 或位置参数传入。
- 在 `call(InternalRow args)` 中，按位置读取第三个参数：`Integer specId = args.isNullAt(2) ? null : args.getInt(2);`，与 `use_caching` 的 nullable 读取模式一致。
- 在 `modifyIcebergTable` 回调里，紧跟 `use_caching` 的 option 设置之后，加入条件透传：

  ```java
  if (specId != null) {
    action.specId(specId);
  }
  ```

  仅在用户显式传入 `spec_id` 时才调用底层 `specId(int)`，保持默认行为（处理所有 spec）不变，向后兼容。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteManifestsProcedure.java`

**修改目的**：端到端验证新 `spec_id` 参数的行为，覆盖"仅重写指定 spec 的 manifest"场景。

**工作逻辑**：新增 `testWriteManifestWithSpecId` 测试方法，流程为：
1. 建表 `id int, dt string, hr string` 并按 `dt` 分区（spec_id=0），设置 `commit.manifest-merge.enabled = false` 使每次 append 产生独立 manifest。
2. 两次 `INSERT`，断言 `SELECT partition_spec_id FROM manifests` 返回两行 `0`，确认 spec_id=0 下有两份 manifest。
3. `ALTER TABLE ADD PARTITION FIELD hr` 演进分区，得到 spec_id=1；再 `INSERT`，断言 manifests 的 partition_spec_id 为 `(0, 0, 1)`，确认三份 manifest 跨两个 spec。
4. 先调用默认 `CALL system.rewrite_manifests('table')`，断言输出 `(0, 0)`（"Nothing should be rewritten"——因为默认会尝试所有 spec，但此处由于 merge 关闭且数据少，未触发实际重写，输出计数为 0/0）。
5. 关键步骤：调用 `CALL system.rewrite_manifests(table => '...', spec_id => 0)`，断言输出 `(2, 1)`（重写了 spec_id=0 的 2 份 manifest，写成 1 份新 manifest）。
6. 再次查询 `manifests`，断言 partition_spec_id 为 `(0, 1)`，即重写后 spec_id=0 只剩 1 份合并后的 manifest、spec_id=1 仍为 1 份，证明 `spec_id` 参数精准地把重写范围限定在 spec_id=0 的 manifest 上，未触碰 spec_id=1 的 manifest。

该测试既验证了新参数的功能语义（按 spec 过滤重写），也验证了默认行为不变（不带 `spec_id` 时仍是原有逻辑），是本提交的核心回归保护。

## 小结

本提交通过在 Spark 3.5 的 `RewriteManifestsProcedure` 中新增可选 `spec_id` 参数并透传给已有的底层 `RewriteManifestsSparkAction.specId(int)`，使用户能在分区演进后只重写特定分区规约的 manifest，避免无谓的全表 manifest 重写，是 procedure 层对底层 action 既有能力的功能性补齐。
