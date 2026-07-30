# 提交 1956：Doc: Remove warning for issue resolved by #11147. (#12694)

## 提交信息

- **序号**：1956 / 4088
- **哈希**：14122cb736dcc0db52e48caf183d92eb1272da86
- **短哈希**：14122cb73
- **日期**：2025-04-02 17:58:55 +0200
- **作者**：slfan1989
- **提交说明**：Doc: Remove warning for issue resolved by #11147. (#12694)
- **PR/Issue**：#12694

## 总体目的

Iceberg 的 Spark 过程文档（`spark-procedures.md`）中，针对 `snapshot`、`migrate`、`add_files` 三个过程的 `parallelism` 参数，原本各有一处 `!!! warning` 提示，说明 `parallelism > 1` 存在已知问题（issue #11147），并称将在下一个版本修复。

由于该 issue 已经由 PR #11147 修复（即上一条提交 1955 回填的 #11157 的源头），这些警告已不再适用。本提交从文档中删除这三处过时的警告，使文档与当前代码实际行为保持一致。

## 如何达成设计目的

直接编辑 `docs/docs/spark-procedures.md`，在 `snapshot`、`migrate`、`add_files` 三个过程的参数表后，分别删除以 `!!! warning` 开头的、引用 issue #11147 的三段警告文本。

## 修改详情

### `docs/docs/spark-procedures.md` (修改, +0/-9 lines)

**修改目的**：移除已修复 issue 对应的过时警告。

**工作逻辑**：在三处（snapshot、migrate、add_files 过程的 `parallelism` 参数说明之后）各删除一段：
```
!!! warning
    There's a [known issue with `parallelism > 1`](https://github.com/apache/iceberg/issues/11147) that is scheduled to be fixed in the next release.
```
共删除 9 行（每处 3 行）。

## 总结

本提交清理 Spark 过程文档中关于 `parallelism > 1` 已知问题（#11147）的三处过时警告，因为该问题已被修复，文档不再需要提示。
