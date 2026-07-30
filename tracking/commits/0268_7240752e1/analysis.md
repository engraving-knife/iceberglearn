# 提交 0268：Docs: Add spec-id for rewrite manifests (#9253)

## 提交信息

- **序号**：0268 / 4088
- **哈希**：7240752e18278b3454be141c2e4b121079004d8b
- **短哈希**：7240752e1
- **日期**：2023-12-13 00:48:29 -0800
- **作者**：Pucheng Yang <8072956+puchengy@users.noreply.github.com>
- **提交说明**：Docs: Add spec-id for rewrite manifests (#9253)
- **PR/Issue**：#9253

## 总体目的

本提交是一个纯文档提交，为 `rewrite_manifests` Spark 存储过程的用户文档补充此前遗漏的 `spec_id` 参数说明。在此之前，Spark 3.2/3.3/3.4/3.5 的 `RewriteManifestsProcedure` 已经分别在 PR #9243 和 #9242 中新增了可选的 `spec_id` 参数（对应跟踪序号 0241 和 0239），允许用户通过 `CALL system.rewrite_manifests(table => '...', spec_id => 0)` 精确指定只重写某个分区规约（partition spec）下的 manifest。但用户文档 `docs/spark-procedures.md` 中 `rewrite_manifests` 过程的 Usage 参数表并未同步更新，仍然只列出 `table` 和 `use_caching` 两个参数，导致用户无法从文档中获知这一新能力。

文档与代码不同步是开源项目中常见的问题。当功能 PR 合并后，如果文档没有同步更新，用户就无法发现和使用新功能，削弱了功能本身的价值。本提交正是补齐这一文档缺口，使 `rewrite_manifests` 过程的参数文档与实际实现保持一致。

从背景来看，`spec_id` 参数对经历了分区演进的表（通过 `ALTER TABLE ... ADD PARTITION FIELD` 引入新的分区规约）尤其有价值。当表存在多个 partition spec 时，不同 spec 的 manifest 会混合存在。默认情况下 `rewrite_manifests` 会处理所有 spec 的 manifest，而通过 `spec_id` 参数用户可以只重写某个特定 spec 的 manifest，避免无谓地重写其它 spec 的 manifest，从而节省写入与提交开销。文档补充这一参数说明后，用户能够更好地利用这一精细化的 manifest 运维能力。

## 如何达成设计目的

通过在 `docs/spark-procedures.md` 文件中 `rewrite_manifests` 过程的 Usage 参数表格里新增一行 `spec_id` 参数说明，并调整表格列宽以保持 Markdown 表格的对齐美观。改动极小但精准地补齐了功能文档。

## 修改详情

### `docs/spark-procedures.md`

**修改目的**：在 `rewrite_manifests` 存储过程的 Usage 参数表中新增 `spec_id` 参数的文档说明，并调整表格格式。

**工作逻辑**：

原 Usage 表格只有两行参数：
```
| Argument Name | Required? | Type | Description |
|---------------|-----------|------|-------------|
| `table`       | ✔️  | string | Name of the table to update |
| `use_caching` | ️   | boolean | Use Spark caching during operation (defaults to true) |
```

修改后新增了 `spec_id` 行，并对所有行的 Description 列做了等宽填充（padding），使表格在 Markdown 源码中保持整齐对齐：
```
| Argument Name | Required? | Type | Description                                                   |
|---------------|-----------|------|---------------------------------------------------------------|
| `table`       | ✔️  | string | Name of the table to update                                   |
| `use_caching` | ️   | boolean | Use Spark caching during operation (defaults to true)         |
| `spec_id`     | ️   | int | Spec id of the manifests to rewrite (defaults to current spec id) |
```

关键新增内容是第三行：
- **参数名**：`spec_id`
- **是否必填**：可选（与 `use_caching` 一样标记为非必填）
- **类型**：`int`（整型）
- **描述**：`Spec id of the manifests to rewrite (defaults to current spec id)`，即指定要重写的 manifest 所属的分区规约 ID，默认为当前 spec id。

这一描述准确反映了实现层的行为：当不传 `spec_id` 时，底层 `RewriteManifestsSparkAction` 不会调用 `specId(int)` 方法，从而处理所有 spec 的 manifest；当传入 `spec_id` 时，action 会校验该 spec id 存在（`table.specs().containsKey(specId)`），并在执行时通过 `manifest.partitionSpecId() == spec.specId()` 过滤只处理该 spec 的 manifest。

## 小结

本提交是功能 PR #9242/#9243 的文档收尾，确保 `rewrite_manifests` 存储过程的用户文档与代码实现同步。虽然改动量极小（仅 5 行新增、4 行删除的格式调整），但它让用户能够从官方文档中发现并正确使用 `spec_id` 参数，对多分区规约表的 manifest 运维体验有实际的文档支撑价值。这也提醒我们：功能开发应当在提交代码的同时同步更新文档，避免文档滞后于实现。
