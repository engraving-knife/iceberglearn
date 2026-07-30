# 提交 3542：Update Rust status on the site (#15709)

## 提交信息

- **序号**：3542 / 4088
- **哈希**：20ca6c3f88fc237d4af48db4295a9915fb3e958f
- **短哈希**：20ca6c3f8
- **日期**：2026-04-16 09:02:01 +0800
- **作者**：Shawn Chang
- **提交说明**：Update Rust status on the site (#15709)
- **PR/Issue**：#15709

## 总体目的

Iceberg 官方网站的 `status.md` 文档以表格形式跟踪各语言实现（Java、PyIceberg、Rust、Go、C++）对 Iceberg 规范各项操作的支持情况。随着 `iceberg-rust` 项目的进展，Rust 实现已支持更多操作，但网站表格中的 Rust 列还是旧的 "N"（不支持）状态，未及时更新。

本提交将 `status.md` 中 Rust 列的多项操作状态从 "N" 更新为 "Y"，反映 Rust 实现的最新能力，让用户准确了解 Rust 实现的成熟度。

## 如何达成设计目的

逐项更新 `status.md` 中多个表格的 Rust 列，把已支持的操作从 "N" 改为 "Y"。更新覆盖多个类别：表元数据操作、数据文件追加、读取规划、删除文件处理、表写操作、以及多个 catalog（REST、Glue、Hive Metastore）的命名空间操作。

## 修改详情

### `site/docs/status.md` (+27/-27 lines)

**修改目的**：更新 Rust 实现的支持状态。

**工作逻辑**：以下操作的 Rust 列从 N 改为 Y：

**表元数据操作**（两个表格，v1 和 v2）：
- Replace sort order
- Update table location
- Update statistics

**数据文件追加**：
- Append data files（两个表格）

**读取规划**：
- Plan with position deletes
- Plan with equality deletes
- Read with position deletes
- Read with equality deletes

**表写操作**：
- Write equality deletes

**REST Catalog 命名空间操作**：
- listNamespaces
- createNamespace
- namespaceExists
- loadNamespaceMetadata

**Glue Catalog 命名空间操作**：
- listNamespaces
- createNamespace
- dropNamespace
- namespaceExists
- loadNamespaceMetadata

**Hive Metastore Catalog 命名空间操作**：
- listNamespaces
- createNamespace
- dropNamespace
- loadNamespaceMetadata

每处改动都是表格单元格中 Rust 列从 `N` 改为 `Y`，共 27 处。

## 总结

本提交将 Iceberg 官方网站 `status.md` 中 Rust 实现的 27 项操作支持状态从 "N" 更新为 "Y"，覆盖表元数据操作、数据文件追加、读取规划（position/equality deletes）、写 equality deletes、以及 REST/Glue/Hive Metastore 三个 catalog 的命名空间操作。属于文档维护，反映 iceberg-rust 项目的进展，让用户准确了解 Rust 实现的能力。
