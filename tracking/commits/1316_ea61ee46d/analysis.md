# 提交 1316：Docs: warn `parallelism > 1` doesn't work for migration procedures (#11417)

## 提交信息

- **序号**：1316 / 4088
- **哈希**：ea61ee46db17d94f22a5ef11fd913146557bdce7
- **短哈希**：ea61ee46d
- **日期**：2024-10-31（Fri Nov 1 04:48:45 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: warn `parallelism > 1` doesn't work for migration procedures (#11417)
- **PR/Issue**：#11417

## 总体目的

Iceberg 的 Spark 系统存储过程 `snapshot` / `migrate` / `add_files` 都接受一个 `parallelism` 参数（int，默认 1），文档描述为"Number of threads to use for file reading"，用于在迁移 Hive/Spark 表到 Iceberg 时并行读取多个数据文件的元数据以加速迁移。

但社区发现 `parallelism > 1` 时存在已知 bug（issue #11147）：并行读取文件元数据会出现问题（如某些文件元数据丢失或并发写入冲突），导致迁移结果不正确。该 bug 计划在下一版本修复，但当前发布版本仍存在该问题。

为避免用户在生产迁移场景中踩坑（迁移数据丢失/不完整是高风险事件），本提交在文档中三处涉及 `parallelism` 参数的存储过程说明里加上显式 `!!! warning` admonition，提醒用户存在已知问题、建议保持默认 `parallelism=1`，等下个版本修复后再使用大于 1 的值。

## 如何达成设计目的

在 `docs/docs/spark-procedures.md` 中三个存储过程的 `#### Usage` 小节（紧跟 `parallelism` 参数行后）分别插入一段 MkDocs Material 风格的 `!!! warning` 警告块，链接到 GitHub issue #11147 并说明"计划在下个版本修复"。

选择警告位置紧贴 `parallelism` 参数表的下方，确保用户在查阅参数用法时第一眼就能看到警告，而无需阅读整篇文档。

## 修改详情

### `docs/docs/spark-procedures.md`（修改，+9 行）

**修改目的**：在 `snapshot` / `migrate` / `add_files` 三个迁移类存储过程的参数表下方加上 `parallelism > 1` 已知问题警告。

**工作逻辑**：在三处分别插入完全相同的警告块：

```markdown
!!! warning
    There's a [known issue with `parallelism > 1`](https://github.com/apache/iceberg/issues/11147) that is scheduled to be fixed in the next release.
```

插入位置（三处）：

1. **`### snapshot`**：在 `#### Usage` 表格（含 `parallelism` 行）下方，`#### Output` 之前；
2. **`### migrate`**：在 `#### Usage` 表格（含 `parallelism` 行）下方，`#### Output` 之前；
3. **`### add_files`**：在 `#### Usage` 表格（含 `parallelism` 行）下方，"Warning : Schema is not validated..." 与 "Warning : Files added by this method..." 两段内联 Warning 之后、`#### Output` 之前。

警告块使用 MkDocs Material 的 admonition 语法 `!!! warning`，在文档站点上会渲染为醒目的黄色警告框，含超链接指向 issue #11147。

## 小结

- **成效**：在三个迁移类存储过程（`snapshot`/`migrate`/`add_files`）的文档中显式警告 `parallelism > 1` 存在已知 bug（#11147），避免用户在生产迁移中误用导致数据不完整。纯文档变更，零代码改动。
- **影响范围**：仅 `docs/docs/spark-procedures.md` 一个文档文件，9 行新增。不影响任何运行时行为或参数默认值（`parallelism` 默认仍是 1）。
- **回迁到 1.4.x 的注意事项**：纯文档变更，回迁零风险。需注意：
  1. 若 1.4.x 已修复 issue #11147，则该警告可不再回迁，或在回迁后调整为"已修复"措辞；
  2. 若 1.4.x 上的 `spark-procedures.md` 结构与 main 不同（如 `add_files` 的参数表已变更），需手动确认三处插入位置准确，避免插入到错误章节；
  3. 文档使用 MkDocs Material admonition 语法，1.4.x 的文档构建链应已支持（其它警告块已广泛使用），无新增依赖。
