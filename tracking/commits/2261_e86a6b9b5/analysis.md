# 提交 2261：Docs: Fix description of min-input-files option of Spark rewrite_data_files procedure (#13355)

## 提交信息

- **序号**：2261 / 4088
- **哈希**：e86a6b9b5a8ec1f29f49548ee572079d2d496228
- **短哈希**：e86a6b9b5
- **日期**：2025-06-20 17:41:33 +0200
- **作者**：Manu Zhang
- **提交说明**：Docs: Fix description of min-input-files option of Spark rewrite_data_files procedure
- **PR/Issue**：#13355

## 总体目的

本提交修复了 Spark `rewrite_data_files` 存储过程中 `min-input-files` 选项的文档描述。原有的描述"Any file group exceeding this number of files will be rewritten regardless of other criteria"（超过此文件数的文件组将被重写）不够准确，因为实际的逻辑是文件组达到此数量或更多时就会被重写，且文件组至少需要有两个文件。

准确的文档描述对于用户正确配置数据文件压缩（compaction）行为至关重要。`min-input-files` 是一个重要的调优参数，它控制何时触发文件重写——即使文件大小未达到阈值，只要文件组中的文件数量达到此值就会触发重写。不准确的描述可能导致用户误解参数行为并做出错误的配置决策。

## 如何达成设计目的

- 修改 `spark-procedures.md` 文档中 `min-input-files` 的描述，使其准确反映实际行为。

## 修改详情

### `docs/docs/spark-procedures.md` (修改, +1/-1 lines)

**修改目的**：修正 `min-input-files` 选项的描述。

**工作逻辑**：将描述从 "Any file group exceeding this number of files will be rewritten regardless of other criteria" 修改为 "Any file group with this number of files or more will be rewritten regardless of other criteria (the file group should have at least two files)"。修改了两点：(1) "exceeding" 改为 "with this number of files or more"，明确包含等于阈值的情况；(2) 新增 "the file group should have at least two files" 说明文件组至少需要两个文件。

## 总结

本提交是一个纯文档修复，修正了 Spark `rewrite_data_files` 过程中 `min-input-files` 参数的描述，使其更准确地反映实际的文件组重写触发条件。修改了 1 行文档，澄清了"达到或超过"而非"超过"的行为，以及文件组至少需要两个文件的前提条件。
