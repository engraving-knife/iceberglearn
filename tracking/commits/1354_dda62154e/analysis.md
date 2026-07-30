# 提交 1354：Infra: Update DOAP.RDF for Apache Iceberg 1.7.0 (#11492)

## 提交信息

- **序号**：1354 / 4088
- **哈希**：dda62154e69a27da1fa9e6cce59413f988a0e99b
- **短哈希**：dda62154e
- **日期**：2024-11-08（Fri Nov 8 13:05:42 2024 -0600）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：Infra: Update DOAP.RDF for Apache Iceberg 1.7.0 (#11492)
- **PR/Issue**：#11492

## 总体目的

DOAP（Description of a Project）是 Apache 软件基金会用于描述其托管项目的 RDF 规范，仓库根目录下的 `doap.rdf` 文件被 Apache 项目基础设施（projects.apache.org）自动抓取并展示在 Apache 项目目录页面上，用于公开项目的最新发布版本信息。该文件中的 `<release>` 区块记录当前最新发布版本的名称、创建日期与版本号。

Iceberg 1.7.0 即将/刚刚发布（2024-11-08），但 `doap.rdf` 仍指向上一版 1.6.1（创建日期 2024-08-27）。本提交将 `<release>` 区块更新为 1.7.0 与对应发布日期，使 Apache 项目目录与 Iceberg 实际最新发布保持一致。

## 如何达成设计目的

直接编辑 `doap.rdf`，将 `<Version>` 元素下的 `<name>`、`<created>`、`<revision>` 三个子元素的文本值从 `1.6.1` / `2024-08-27` / `1.6.1` 替换为 `1.7.0` / `2024-11-08` / `1.7.0`。这是纯元数据更新，无代码逻辑。

## 修改详情

### `doap.rdf`

**修改目的**：把 DOAP 文件中的最新发布版本从 1.6.1 更新为 1.7.0。

**工作逻辑**：将 `<release>` 区块下的版本信息整体替换：

```xml
    <release>
      <Version>
        <name>1.7.0</name>
        <created>2024-11-08</created>
        <revision>1.7.0</revision>
      </Version>
    </release>
```

Apache 基础设施会定时抓取此文件并更新 projects.apache.org 上 Iceberg 项目的展示页面，让外部用户看到最新发布版本。

## 小结

- **成效**：DOAP 文件现指向 1.7.0，Apache 项目目录页面将同步展示 Iceberg 最新发布版本。
- **影响范围**：仅 `doap.rdf` 一个文件，修改 3 行（替换 3 行），无代码、构建或运行时变更。
- **回迁到 1.4.x 的注意事项**：`doap.rdf` 由 main 分支统一维护，反映 Iceberg 项目整体最新发布版本，与各维护分支（1.4.x）的发布产物无关。1.4.x 不应回迁此改动——把 1.4.x 分支的 `doap.rdf` 改回 1.4.x 反而会让 Apache 项目目录显示过时版本。**无需回迁**。
