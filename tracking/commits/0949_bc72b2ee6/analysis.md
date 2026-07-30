# 提交 0949：Docs: Fix link on Concepts page (#10718)

## 提交信息

- **序号**：0949 / 4088
- **哈希**：bc72b2ee6b14e83eff6a49bc664c09259e5bb1c8
- **短哈希**：bc72b2ee6
- **日期**：2024-07-18 16:58:05 +0200
- **作者**：gaborkaszab <gaborkaszab@cloudera.com>
- **提交说明**：Docs: Fix link on Concepts page (#10718)
- **PR/Issue**：#10718

## 总体目的

Iceberg 网站文档中 Concepts 页面 `site/docs/concepts/catalog.md` 在介绍 catalog 配置时，给出了一条指向"Iceberg 文档 / configuration / catalog-properties"的相对链接。该链接原本写为 `docs/latest/configuration.md#catalog-properties`，从 `concepts/catalog.md` 所在目录出发解析时路径不正确，导致页面上的" Iceberg documentation"超链接在渲染后指向不存在的页面（404），用户无法跳转到 catalog 属性配置说明。

本提交（PR #10718）修正该相对链接，加上 `../` 前缀使其相对于 `concepts/` 目录正确指向 `docs/latest/configuration.md#catalog-properties`，恢复文档间的正常导航。这是一处典型的文档链接修复。

## 如何达成设计目的

仅修改该行 Markdown 中的链接路径，将 `docs/latest/configuration.md#catalog-properties` 改为 `../docs/latest/configuration.md#catalog-properties`，其余文字与结构不动。修正后，从 `site/docs/concepts/catalog.md` 出发，相对路径 `../docs/latest/configuration.md` 正确解析到 `site/docs/latest/configuration.md`，锚点 `#catalog-properties` 指向其中的 catalog 属性章节。

## 修改详情

### `site/docs/concepts/catalog.md`

**修改目的**：修复 Concepts → catalog 页面中指向 configuration 文档 catalog-properties 章节的相对链接，使其能正确跳转。

**工作逻辑**：改动仅一处链接前缀：

```diff
-... refer to the [Iceberg documentation](docs/latest/configuration.md#catalog-properties) as well as ...
+... refer to the [Iceberg documentation](../docs/latest/configuration.md#catalog-properties) as well as ...
```

加上 `../` 后，相对路径从 `concepts/` 退回上一级 `docs/`，再进入 `docs/latest/configuration.md`，与站点实际目录结构一致。

## 小结

- **成效**：修复 Concepts 页面 catalog 文档中一个失效的相对链接，恢复指向 catalog-properties 配置章节的正常导航。
- **影响范围**：仅 `site/docs/concepts/catalog.md` 一个文件，1 行链接路径修正，无代码或功能变更。
- **回迁到 1.4.x 的注意事项**：纯文档链接修复，可安全回迁到 1.4.x；但需先确认 1.4.x 分支的 `site/docs/concepts/catalog.md` 中该链接是否存在同样问题，以及 1.4.x 文档目录结构是否与 main 一致。若 1.4.x 该文件结构与 main 相同，则直接 cherry-pick 即可；若目录结构有差异，应按 1.4.x 实际目录层次调整相对路径前缀。
