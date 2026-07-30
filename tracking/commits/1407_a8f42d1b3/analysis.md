# 提交 1407：docs: Add `iceberg-go` to doc site (#11607)

## 提交信息

- **序号**：1407 / 4088
- **哈希**：a8f42d1b3954fb53a40204f2aa3a99080d637201
- **短哈希**：a8f42d1b3
- **日期**：2024-11-20（Wed Nov 20 17:08:36 2024 -0500）
- **作者**：Matt Topol <zotthewizard@gmail.com>
- **提交说明**：docs: Add `iceberg-go` to doc site (#11607)
- **PR/Issue**：#11607

## 总体目的

Iceberg 官方文档站点（基于 mkdocs）在导航栏底部维护一组指向各语言生态的链接：Javadoc、PyIceberg、IcebergRust。Apache `iceberg-go` 项目（Go 语言实现）此前未在站点导航中露出，社区希望把它加入文档站，让 Go 用户也能从官方文档快速跳转到 `pkg.go.dev` 上的 `iceberg-go` 文档。

本提交的目的：在 mkdocs 导航 `nav` 列表末尾追加一项 `IcebergGo`，链接到 `https://pkg.go.dev/github.com/apache/iceberg-go/`，与已有 `PyIceberg`、`IcebergRust` 风格保持一致。

## 如何达成设计目的

直接编辑 `docs/mkdocs.yml`，在 `nav` 数组末尾新增一行 `- IcebergGo: https://pkg.go.dev/github.com/apache/iceberg-go/`。mkdocs 会把它渲染为站点顶部/侧边导航的一个外链条目，点击在新标签页打开 `pkg.go.dev` 上的 `iceberg-go` 包文档。

## 修改详情

### `docs/mkdocs.yml`

**修改目的**：在文档站导航中加入 iceberg-go 的外链。

**工作逻辑**：在 `nav` 列表底部追加：

```yaml
  - Javadoc: ../../javadoc/latest/
  - PyIceberg: https://py.iceberg.apache.org/
  - IcebergRust: https://rust.iceberg.apache.org/
  - IcebergGo: https://pkg.go.dev/github.com/apache/iceberg-go/   # 新增
```

这是纯 YAML 配置变更，不涉及 mkdocs 插件、主题或构建脚本改动，也不影响其它导航项。

## 小结

- **成效**：Iceberg 文档站点导航现在包含 `IcebergGo` 外链，Go 用户可从官方文档一键跳转到 `pkg.go.dev` 上的 `iceberg-go` 包文档，与 PyIceberg / IcebergRust 的露出方式一致。
- **影响范围**：仅 `docs/mkdocs.yml` 一行新增，无代码或构建产物变化。
- **回迁到 1.4.x 的注意事项**：这是文档站导航改进，与 1.4.x 发布产物无关，**无需回迁**。1.4.x 作为维护分支不重新构建文档站（文档站由 main 分支统一构建发布），即便回迁也不会影响 1.4.x 的 jar 产物或 API。
