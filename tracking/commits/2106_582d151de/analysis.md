# 提交 2106：Update ADLS implementation status (#13020)

## 提交信息

- **序号**：2106 / 4088
- **哈希**：582d151de46d1a980e789b1746e97e05ebe3b618
- **短哈希**：582d151d
- **日期**：2025-05-09 17:15:55 -0400
- **作者**：Marc Cenac <547446+mrcnc@users.noreply.github.com>
- **提交说明**：Update ADLS implementation status (#13020)
- **PR/Issue**：#13020

## 总体目的

Iceberg 官方文档的 `site/docs/status.md` 页面用表格列出各语言实现（Java、PyIceberg、Rust、Go）对不同存储/FileIO 的支持状态。此前表格中只列了 Local Filesystem、Hadoop Filesystem、S3 Compatible、GCS Compatible 四类存储，缺少 Azure Data Lake Storage（ADLS）的条目，尽管 Java 实现已支持 ADLS（通过 `ADLSFileIO` 等）。

本次提交在 FileIO 表格中新增 "ADLS Compatible" 一行，标注各语言支持状态：Java=Y、PyIceberg=Y、Rust=N、Go=Y，使文档准确反映 ADLS 的实现现状，便于用户查阅。

## 如何达成设计目的

在 `site/docs/status.md` 的 `## File IO` 表格中，GCS Compatible 行之后新增一行 `| ADLS Compatible | Y | Y | N | Y |`，与既有行格式一致。

## 修改详情

### `site/docs/status.md` (修改, +1/-0 lines)

**修改目的**：在 FileIO 支持矩阵中加入 ADLS。

**工作逻辑**：在 File IO 表格中新增 `ADLS Compatible` 行，四列分别为 Java=Y、PyIceberg=Y、Rust=N、Go=Y。这反映：Java 与 PyIceberg、Go 已支持 ADLS，Rust 尚未支持。

## 总结

本次提交是纯文档维护：在 `status.md` 的 FileIO 支持矩阵中加入 ADLS Compatible 行（Java/PyIceberg/Go=Y，Rust=N），使文档与各语言实现现状一致。改动仅 1 行。
