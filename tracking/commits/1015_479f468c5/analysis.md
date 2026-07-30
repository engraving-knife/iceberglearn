# 提交 1015：Spec: Deprecate the file system table scheme (#10833)

## 提交信息

- **序号**：1015 / 4088
- **哈希**：479f468c5f389bd7a30938114f8e79445c48f179
- **短哈希**：479f468c5
- **日期**：2024-08-04 14:32:52 -0700
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：Spec: Deprecate the file system table scheme (#10833)
- **PR/Issue**：#10833

## 总体目的

Iceberg 规范（`format/spec.md`）的"Commit Conflict Resolution and Retry"章节中描述了一种基于文件系统原子 rename 的提交方案（File System Tables）：写入者把新版本的 metadata 文件命名为 `v<V+1>.metadata.json`，再依赖文件系统对 rename 的原子性保证来完成"原子交换"。原规范提到此机制"在支持原子 rename 的文件系统（如 HDFS 或大多数本地文件系统）中可实现"。

该方案存在两个实质问题：

1. **对象存储不安全**：S3、GCS、Azure Blob 等对象存储对 rename 的原子性保证不一致甚至完全不存在（很多对象存储的 rename 是 copy+delete，非原子），用文件系统 rename 方案在这些存储上会导致丢提交、重复提交或并发覆盖。即便在本地文件系统上，部分实现也不保证 rename 的原子性。
2. **已不再推荐**：Iceberg 实际生产中早已推荐通过 catalog（如 Hive Metastore、Nessie、JDBC catalog 等）来管理"当前 metadata 指针"，而非依赖文件系统 rename。文件系统方案保留在规范中主要是历史兼容原因。

本提交的目的是在规范层面正式将该方案标记为 **deprecated（弃用）**，明确警告它"不安全"且将在 spec version 4 中被移除，并修正原描述中"大多数本地文件系统"这一容易引起误解的措辞（去掉对本地文件系统的笼统背书，仅保留 HDFS 作为示例）。这是为后续 Iceberg 规范 v4 清理过时提交机制做铺垫，引导新实现不要采用该方案。

## 如何达成设计目的

实现方式是纯文档编辑：在 `format/spec.md` 的 "File System Tables" 小节开头插入一段 `_Note: ..._`（Markdown 斜体提示），明确标注该方案已 deprecated、将在 spec v4 移除、且在对象存储和本地文件系统中不安全；同时把紧随其后的"原子 rename 可实现"句子中"like HDFS or most local file systems"改为"like HDFS"，移除对本地文件系统的笼统背书。此外顺手修复了文件末尾缺少换行符的小问题（`\ No newline at end of file` 消失）。

## 修改详情

### `format/spec.md`

**修改目的**：在规范中正式弃用基于文件系统原子 rename 的提交方案，并修正对本地文件系统的误导性描述。

**工作逻辑**：
- 在 `#### File System Tables` 标题下、原首段之前，新增一段弃用提示：
  ```
  _Note: This file system based scheme to commit a metadata file is **deprecated** and will be removed in version 4 of this spec. The scheme is **unsafe** in object stores and local file systems._
  ```
  通过斜体 + 加粗 `deprecated`/`unsafe` 强调风险，并指明移除时间点（spec v4）。
- 紧随其后的原句：
  ```
  An atomic swap can be implemented using atomic rename in file systems that support it, like HDFS or most local file systems [1].
  ```
  改为：
  ```
  An atomic swap can be implemented using atomic rename in file systems that support it, like HDFS [1].
  ```
  去掉 `or most local file systems`，避免给读者留下"本地文件系统普遍安全"的印象——实际上本地文件系统对 rename 原子性的支持并不一致，规范不应笼统背书。
- 文件末尾补一个换行符（原本以 `\ No newline at end of file` 结束，现在正常换行）。这是无害的格式修正。

## 小结

- **成效**：在 Iceberg 规范文档中正式将"文件系统原子 rename 提交方案"标记为 deprecated，明确其不安全性（对象存储 + 本地文件系统）与移除时间点（spec v4），并修正了对本地文件系统的笼统背书。为后续规范版本清理做铺垫，引导实现者和使用者转向基于 catalog 的提交管理。
- **影响范围**：仅 `format/spec.md` 一个文件，4 行新增 / 2 行删除（含末尾换行修正）。不修改任何代码、构建、配置，纯规范文档变更。
- **回迁到 1.4.x 的注意事项**：本提交是规范文档变更，与代码版本无关。1.4.x 分支的 `format/spec.md` 若仍保留对该方案的非弃用描述，可回迁以保持文档与社区共识一致；但需注意 1.4.x 分支的 spec.md 可能整体版本较旧（spec v2/v3 之间），cherry-pick 时需确认上下文段落一致。规范层面的弃用声明不影响 1.4.x 现有代码行为（Iceberg 实现中基于 HDFS rename 的提交路径仍可工作，只是规范不再推荐新用途）。整体适合回迁，风险极低。
