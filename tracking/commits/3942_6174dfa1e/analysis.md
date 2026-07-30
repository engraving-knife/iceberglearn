# 提交 3942：Revert "Core: Migrate switch statements to switch expressions (#16881)" (#16953)

## 提交信息

- **序号**：3942 / 4088
- **哈希**：6174dfa1e1825601384874d5a693b9219cf459e6
- **短哈希**：6174dfa1e
- **日期**：2026-06-24 10:22:26 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：Revert "Core: Migrate switch statements to switch expressions (#16881)" (#16953)
- **PR/Issue**：#16953（回退 #16881，即提交 3938）

## 总体目的

这次提交回退了 #16881（提交 3938），该 PR 将 core 模块中 84 个文件的传统 switch 语句迁移为 Java 14+ 的 switch 表达式。

回退的原因虽然没有在提交信息中详细说明，但通常大规模的纯语法重构 PR 被回退可能是因为：
1. 在 1.4.x 分支上引发了未预期的编译或行为问题。
2. 与其他并行开发的 PR 产生了合并冲突。
3. 需要更谨慎的评审或分批进行，而非一次性 84 个文件的大规模变更。
4. 可能存在某些 switch 表达式转换在特定 JVM 版本或编译器上的边界情况问题。

从 diff 统计来看，回退操作恢复了 2636 行、删除了 1766 行（与原 PR 的增删完全对称），确认这是一次完整的 `git revert`。

## 如何达成设计目的

通过 `git revert` 操作自动生成反向 diff，撤销 #16881 对 84 个 core 模块文件的所有 switch 表达式转换，恢复为传统的 switch 语句形式。

## 修改详情

### 84 个 core 模块文件 (+2636/-1766 lines)

**修改目的**：回退 switch 表达式迁移，恢复传统 switch 语句。

**工作逻辑**：将每个文件中的 `case L ->` 箭头标签 switch 表达式恢复为 `case L:` 冒号标签 switch 语句，恢复 `break` 语句和临时变量赋值。涉及的文件列表与提交 3938 完全一致，包括 `BaseFile.java`、`GenericManifestFile.java`、`V1Metadata.java`-`V4Metadata.java`、`AvroSchemaUtil.java`、`ErrorHandlers.java` 等全部 84 个文件。

## 总结

这次提交完整回退了 #16881 的 switch 表达式迁移，将 core 模块 84 个文件恢复为传统 switch 语句。这表明大规模语法重构需要更谨慎的处理方式——可能需要分批进行或等待更合适的时机。回退操作本身不影响任何功能行为，仅恢复代码语法形式。
