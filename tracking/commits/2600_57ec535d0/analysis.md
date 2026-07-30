# 提交 2600：Revert "[SPEC] Add implementation note about schema evolution (#13936)" (#14002)

## 提交信息

- **序号**：2600 / 4088
- **哈希**：57ec535d0ed749d93c93f8dba085ee70ad6eee84
- **短哈希**：57ec535d0
- **日期**：2025-09-05 13:00:07 -0700
- **作者**：emkornfield
- **提交说明**：Revert "[SPEC] Add implementation note about schema evolution (#13936)" (#14002)
- **PR/Issue**：#14002

## 总体目的

本次提交撤销了提交 2593（PR #13936）对 Iceberg 规范文档的修改，即移除了关于"Schema 演进与使用旧 schema 写入"的实现说明。

被 revert 的原始提交（c64c89fbb）在 `format/spec.md` 中添加了一个小节，描述了写入器应使用最新 schema、不使用旧 schema 写入可能导致的各类不一致问题（如 initial-default 被错误应用、write-default 值不正确、部分更新丢值等）。

虽然提交说明中没有详细说明 revert 的原因，但从时间线看，原始提交于 9 月 4 日合并，仅一天后（9 月 5 日）就被 revert。这通常意味着：
1. 该说明的措辞或内容在社区中引发了讨论或争议
2. 可能存在技术上的不准确之处需要进一步修正
3. 可能需要更广泛的社区讨论才能将此类指导写入规范

规范文档的修改通常需要谨慎，因为它是各引擎实现的依据。撤销后可以重新讨论更准确的表述方式。

## 如何达成设计目的

通过 `git revert` 操作，将 `format/spec.md` 中新增的 10 行内容完整移除，恢复到原始提交前的状态。

## 修改详情

### `format/spec.md` (+0/-10 lines)

**修改目的**：移除之前添加的 schema 演进实现说明。

**工作逻辑**：完整删除了提交 2593 添加的 `### Schema evolution and writing with old schemas` 小节，包括：
- 关于写入器必须使用最新 schema 的要求说明
- 三类潜在不一致问题的列表（全 null 列、write-default 变更、部分行更新）
- 关于列投影规则保证可读性的说明

文件恢复到添加该小节之前的状态。

## 总结

这是一个规范的回退操作。原始提交试图为 schema 演进场景提供实现指导，但可能因措辞或准确性问题被快速撤销。这体现了规范修改的审慎态度——规范是所有实现者的共识基础，需要充分讨论。后续可能会以更完善的形式重新引入类似说明。
