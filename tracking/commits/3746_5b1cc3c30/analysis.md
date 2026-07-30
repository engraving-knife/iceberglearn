# 提交 3746：Doap: Update DOAP to reference 1.11.0 (#16415)

## 提交信息

- **序号**：3746 / 4088
- **哈希**：5b1cc3c30d8e00b4b68f9e995e339ef9d4e8a0df
- **短哈希**：5b1cc3c30
- **日期**：2026-05-19 00:18:11 -0700
- **作者**：Aihua Xu
- **提交说明**：Doap: Update DOAP to reference 1.11.0 (#16415)
- **PR/Issue**：#16415

## 总体目的

本提交更新 Iceberg 项目的 DOAP（Description of a Project）文件，将所引用的最新发布版本从 1.10.2 更新为 1.11.0。

DOAP 是 Apache 软件基金会用于描述项目元数据的 RDF 文件（`doap.rdf`），其中 `<release>` 段落记录项目的最新发布版本信息。该信息会被 Apache 项目站点（projects.apache.org）用于展示项目的最新发布状态。随着 1.11.0 版本的发布（2026-05-18），需要将 DOAP 文件中的 release 引用从 1.10.2 更新为 1.11.0，使 Apache 项目目录正确反映最新发布版本。注意 1.10.2 和 1.11.0 的发布日期相同（2026-05-18），因此 `created` 字段保持不变。

## 如何达成设计目的

修改 `doap.rdf` 中 `<release>` 段落下的 `<Version>` 节点，将 `name` 和 `revision` 字段从 1.10.2 更新为 1.11.0，`created` 保持 `2026-05-18` 不变。

## 修改详情

### `doap.rdf` (+2/-2 lines)

**修改目的**：将 DOAP 文件中的最新发布版本引用更新为 1.11.0。

**工作逻辑**：
将 `<release>` 段落从：
```xml
<release>
  <Version>
    <name>1.10.2</name>
    <created>2026-05-18</created>
    <revision>1.10.2</revision>
  </Version>
</release>
```
更新为：
```xml
<release>
  <Version>
    <name>1.11.0</name>
    <created>2026-05-18</created>
    <revision>1.11.0</revision>
  </Version>
</release>
```
- `name` 从 `1.10.2` 改为 `1.11.0`
- `created` 保持 `2026-05-18`（两个版本同日发布）
- `revision` 从 `1.10.2` 改为 `1.11.0`

## 总结

本提交是 1.11.0 版本发布配套的元数据更新，将 DOAP 文件中的最新发布版本引用从 1.10.2 更新为 1.11.0（发布日期同为 2026-05-18），使 Apache 项目目录正确反映 Iceberg 的最新发布状态。改动仅涉及项目描述元数据，不影响产品代码。
