# 提交 3081：manually update spark 3.4 (#14993)

## 提交信息

- **序号**：3081 / 4088
- **哈希**：daa3bb257bfc17ae7988dbff0a5379a16c1abcc9
- **短哈希**：daa3bb257
- **日期**：2026-01-07
- **作者**：Kevin Liu
- **提交说明**：manually update spark 3.4 (#14993)
- **PR/Issue**：#14993

## 总体目的

Iceberg 官网的"多引擎支持"文档页面（`multi-engine-support.md`）以表格形式列出了各 Spark 版本与 Iceberg 的兼容情况，包括每个 Spark 版本支持的最小和最大 Iceberg 版本，以及对应的运行时 jar 下载链接。Spark 3.4 此前在该表中标注的最大支持 Iceberg 版本为 `1.10.0`。

随着 Iceberg 1.10.1 的发布（这是一个 patch 版本），需要将该文档中 Spark 3.4 行的最大 Iceberg 版本从 `1.10.0` 更新为 `1.10.1`，同时更新对应的两个 Maven 下载链接（`iceberg-spark-runtime-3.4_2.12` 和 `iceberg-spark-runtime-3.4_2.13`）中的版本号，确保用户能从文档中找到最新版本的下载地址。

这是一个纯文档维护类改动，提交说明中的"manually"表明此更新是手动修改而非通过自动化脚本生成（文档中其他行如 Spark 3.5/4.0 使用 `{{ icebergVersion }}` 模板变量自动填充当前版本，而 Spark 3.4 已进入 Deprecated 阶段，其最大版本是固定的，需要手动维护）。

## 如何达成设计目的

直接编辑 `site/docs/multi-engine-support.md` 文件，将 Spark 3.4 行中的版本号 `1.10.0` 替换为 `1.10.1`，涉及该行的最大版本字段和两个 Maven jar 链接中的版本路径。

## 修改详情

### `site/docs/multi-engine-support.md` (+1/-1 lines)

**修改目的**：将 Spark 3.4 的最大支持 Iceberg 版本从 1.10.0 更新为 1.10.1。

**工作逻辑**：
Spark 3.4 行的状态为 "Deprecated"（已弃用但仍有维护），最小版本为 `1.3.0`。本次将最大版本从 `1.10.0` 改为 `1.10.1`，同时将两个下载链接中的版本路径从 `.../1.10.0/iceberg-spark-runtime-3.4_2.12-1.10.0.jar` 和 `.../1.10.1/iceberg-spark-runtime-3.4_2.13-1.10.1.jar` 同步更新。其他行（Spark 3.1/3.2/3.3/3.5/4.0）保持不变。

## 总结

本提交是一个简单的文档维护操作，将官网多引擎支持表中 Spark 3.4 的最大 Iceberg 版本更新为最新发布的 1.10.1 patch 版本并同步下载链接，确保文档与实际发布物保持一致。由于 Spark 3.4 处于 Deprecated 状态，其版本号不像 Maintained 版本那样使用模板变量自动填充，需要手动维护。
