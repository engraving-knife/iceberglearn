# 提交 3739：Docs: Add release notes for 1.10.2 (#16406)

## 提交信息

- **序号**：3739 / 4088
- **哈希**：1802dbfada36bc281ce70669d88c6e00c85e70fc
- **短哈希**：1802dbfa
- **日期**：2026-05-18 17:18:32 -0700
- **作者**：Amogh Jahagirdar
- **提交说明**：Docs: Add release notes for 1.10.2 (#16406)
- **PR/Issue**：#16406

## 总体目的

本提交为 Iceberg 1.10.2 版本添加发布说明（release notes），并同步更新文档站点的版本相关配置，使文档站点正确反映 1.10.2 为最新发布版本。

1.10.2 是一个 bug 修复和安全修复版本，于 2026-05-18 发布。发布说明需要列出本次版本包含的所有重要修复，按模块（Core、Flink、Hive、Spark、AWS、Azure、GCP、Build）分类，并附上对应 PR 链接，便于用户了解该版本相对前一版本（1.10.1）的改动。同时，文档站点的 `icebergVersion` 变量和导航中的版本标签也需要从 1.10.1 更新为 1.10.2，并将 1.10.1 添加到 Previous 历史版本列表。

## 如何达成设计目的

通过三个文件的修改实现：
1. 在 `site/docs/releases.md` 中新增 1.10.2 的发布说明段落，并将 "Past releases" 标题移到 1.10.2 之后、1.10.1 之前，使 1.10.2 成为当前发布版本。
2. 在 `site/mkdocs.yml` 中将 `icebergVersion` 变量从 `1.10.1` 更新为 `1.10.2`，该变量用于文档中展示当前版本号。
3. 在 `site/nav.yml` 中将 Latest 标签从 `1.10.1` 更新为 `1.10.2`，并在 Previous 列表中新增 `1.10.1` 条目。

## 修改详情

### `site/docs/releases.md` (+33/-2 lines)

**修改目的**：新增 1.10.2 发布说明，并调整 Past releases 标题位置。

**工作逻辑**：
- 在 1.10.1 发布说明之前新增 1.10.2 发布说明段落：
```markdown
### 1.10.2 release

Apache Iceberg 1.10.2 was released on May 18, 2026.

The 1.10.2 release contains bug fixes and security fixes. For full release notes visit [Github](https://github.com/apache/iceberg/releases/tag/apache-iceberg-1.10.2)
```
随后按模块列出重要修复（含 PR 链接）：
  - **Core**：equality deletes schema 排序修复（#15605）、commit 后加载 snapshot 防止误清理（#15650）、commit 路径合并 deletion vectors（#15654）、CREATE 事务 503 时不清理文件（#15662）、v2 deletes 并发格式升级校验（#16161）、manifest merge 时 EXISTING 条目 row ID 修复（#16304）
  - **Flink**：DynamicIcebergSink 操作符 UID 非确定性修复（#15738）、LICENSE/NOTICE 修复（#16175）
  - **Hive**：HMS 数据库路径尾斜杠修复（#16010）
  - **Spark**：LICENSE/NOTICE 修复（#16255）
  - **AWS / Azure / GCP**：LICENSE/NOTICE 修复（#16236/#16242/#16244）
  - **Build**：CVE-2025-67721 修复（#15829）、Jackson 升级修复 GHSA-72hv-8253-57qq（#15847）、Avro 升级（#15607）
- 将 `## Past releases` 标题从 1.10.0 之前移到 1.10.2 之后、1.10.1 之前，使 1.10.2 归为当前发布、1.10.1 及更早版本归为历史发布。

### `site/mkdocs.yml` (+1/-1 lines)

**修改目的**：更新文档站点使用的 iceberg 版本变量。

**工作逻辑**：
将 `extra.icebergVersion` 从 `'1.10.1'` 更新为 `'1.10.2'`，该变量在文档页面中用于展示当前 Iceberg 版本号（如安装说明中的依赖版本）。

### `site/nav.yml` (+2/-1 lines)

**修改目的**：更新导航中的版本标签和历史版本列表。

**工作逻辑**：
- 将 Latest 标签从 `Latest (1.10.1)` 更新为 `Latest (1.10.2)`。
- 在 Previous 列表顶部新增 `1.10.1` 条目，指向 `docs/docs/1.10.1/mkdocs.yml`，使 1.10.1 成为可访问的历史版本。

## 总结

本提交为 Iceberg 1.10.2 版本添加了完整的发布说明，列出了按模块分类的重要 bug 修复和安全修复（含 PR 链接），并同步更新了文档站点的 `icebergVersion` 变量和导航版本标签（Latest 改为 1.10.2，Previous 新增 1.10.1）。这是 1.10.2 版本发布流程的文档配套部分，便于用户了解该版本的改动内容并访问对应版本的文档。
