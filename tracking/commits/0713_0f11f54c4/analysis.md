# 提交 0713：将站点文档更新到 1.5.1

## 提交信息
- **序号**：0713 / 4088
- **哈希**：0f11f54c438f946ccf4224f6da3613689f7821bf
- **短哈希**：0f11f54c4
- **日期**：2024-04-24
- **作者**：Amogh Jahagirdar
- **提交说明**：Update site to 1.5.1 docs (#10218)
- **PR/Issue**：#10218

## 总体目的

本提交将 Iceberg 官方文档站点（基于 MkDocs Material 构建）从 1.5.0 文档更新到 1.5.1，包括两处改动：

1. 把站点全局变量 `icebergVersion` 从 `1.5.0` 更新为 `1.5.1`。该变量在文档站点中用于显示当前推荐的 Iceberg 版本号（如安装指引、依赖示例中的版本占位符等）。

2. 在导航配置 `nav.yml` 中新增 `1.5.1` 文档入口，指向 `docs/docs/1.5.1/mkdocs.yml`。这样用户可以在文档站点的版本切换器中选择 1.5.1 文档，同时保留 1.5.0 及更早版本的可访问性。

这是 Iceberg 每次发版后的标准站点维护流程：发版时会在 `site/docs/docs/<VERSION>/` 下生成对应版本的文档快照，然后更新 `mkdocs.yml` 的版本变量和 `nav.yml` 的版本列表，使站点能展示并切换到新版本文档。

## 如何达成设计目的

策略是标准的版本递进更新：
- `mkdocs.yml` 中的 `extra.icebergVersion` 是站点级变量，被模板引用来渲染当前版本号。直接修改字符串值即可。
- `nav.yml` 中维护一个有序的文档版本列表，新版本插入到 `latest` 之后、上一版本（1.5.0）之前，保持"最新版本在上"的排列顺序。

两处改动配合，使站点既能展示 1.5.1 作为当前版本号，又能在版本切换器中访问到 1.5.1 的完整文档。

## 修改详情

### `site/mkdocs.yml`
**修改目的**：将站点全局版本变量更新为 1.5.1。
**工作逻辑**：

```yaml
extra:
  icebergVersion: '1.5.1'   # 原为 '1.5.0'
```

`icebergVersion` 被 MkDocs 模板用于在页面中渲染当前 Iceberg 版本号（例如在快速开始、依赖配置示例中显示 `<VERSION>` 占位符的实际值）。更新后所有引用该变量的页面都会显示 1.5.1。

### `site/nav.yml`
**修改目的**：在文档版本导航中新增 1.5.1 入口。
**工作逻辑**：

在 `Docs` 导航项下，`latest` 之后新增一行：
```yaml
nav:
  - Docs:
    - nightly: '!include docs/docs/nightly/mkdocs.yml'
    - latest: '!include docs/docs/latest/mkdocs.yml'
    - 1.5.1: '!include docs/docs/1.5.1/mkdocs.yml'    # 新增
    - 1.5.0: '!include docs/docs/1.5.0/mkdocs.yml'
    - 1.4.3: '!include docs/docs/1.4.3/mkdocs.yml'
    ...
```

通过 `!include` 指令引入 1.5.1 版本独立的 mkdocs 配置，使其文档页面被纳入站点构建并出现在版本切换列表中。插入位置在 `latest` 与 `1.5.0` 之间，保持版本号从新到旧的降序排列。

## 小结
- **成效**：成功将文档站点配置更新到 1.5.1，使站点能展示 1.5.1 版本号并提供 1.5.1 文档的访问入口。
- **影响范围**：仅影响 `site/mkdocs.yml` 和 `site/nav.yml` 两个站点配置文件，不影响 Iceberg 运行时代码。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支的站点配置应保持 1.4.x 系列的版本序列，不应 cherry-pick 此 1.5.1 更新。此改动属于 main 分支的发版维护，与 1.4.x 回迁无关。
