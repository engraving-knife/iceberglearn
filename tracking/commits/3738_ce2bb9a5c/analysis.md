# 提交 3738：Doap: Update Doap to reference 1.10.2 (#16405)

## 提交信息

- **序号**：3738 / 4088
- **哈希**：ce2bb9a5ca30afb55ee0cffd59f2af577e948cee
- **短哈希**：ce2bb9a5c
- **日期**：2026-05-18 16:52:11 -0700
- **作者**：Amogh Jahagirdar
- **提交说明**：Doap: Update Doap to reference 1.10.2 (#16405)
- **PR/Issue**：#16405

## 总体目的

本提交更新 Iceberg 项目的 DOAP（Description of a Project）文件，将所引用的最新发布版本从 1.10.1 更新为 1.10.2。

DOAP 是 Apache 软件基金会用于描述项目元数据的 RDF 文件（`doap.rdf`），其中 `<release>` 段落记录项目的最新发布版本信息，包括版本名、创建日期和修订号。该信息会被 Apache 项目站点（projects.apache.org）用于展示项目的最新发布状态。随着 1.10.2 版本的发布（2026-05-18），需要将 DOAP 文件中的 release 引用从 1.10.1 更新为 1.10.2，使 Apache 项目目录正确反映最新发布版本。

## 如何达成设计目的

修改 `doap.rdf` 中 `<release>` 段落下的 `<Version>` 节点，将 `name`、`created`、`revision` 三个字段从 1.10.1 对应的值更新为 1.10.2 对应的值。

## 修改详情

### `doap.rdf` (+2/-2 lines)

**修改目的**：将 DOAP 文件中的最新发布版本引用更新为 1.10.2。

**工作逻辑**：
将 `<release>` 段落从：
```xml
<release>
  <Version>
    <name>1.10.1</name>
    <created>2025-12-22</created>
    <revision>1.10.1</revision>
  </Version>
</release>
```
更新为：
```xml
<release>
  <Version>
    <name>1.10.2</name>
    <created>2026-05-18</created>
    <revision>1.10.2</revision>
  </Version>
</release>
```
- `name` 从 `1.10.1` 改为 `1.10.2`
- `created` 从 `2025-12-22` 改为 `2026-05-18`（1.10.2 的发布日期）
- `revision` 从 `1.10.1` 改为 `1.10.2`

## 总结

本提交是 1.10.2 版本发布配套的元数据更新，将 DOAP 文件中的最新发布版本引用从 1.10.1（2025-12-22）更新为 1.10.2（2026-05-18），使 Apache 项目目录正确反映 Iceberg 的最新发布状态。改动仅涉及项目描述元数据，不影响产品代码。
