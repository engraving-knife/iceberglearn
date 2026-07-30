# 提交 0990：Build: Bump mkdocs-awesome-pages-plugin from 2.9.2 to 2.9.3 (#10795)

## 提交信息

- **序号**：0990 / 4088
- **哈希**：94d336ce01381116ce637121e8e59f440148847f
- **短哈希**：94d336ce0
- **日期**：2024-07-29 11:05:33 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-awesome-pages-plugin from 2.9.2 to 2.9.3 (#10795)
- **PR/Issue**：#10795

## 总体目的

Iceberg 文档站点除了 mkdocs-material 主题外，还使用 `mkdocs-awesome-pages-plugin` 来管理文档导航顺序与分组（通过 `.pages` 文件配置）。Dependabot 检测到该插件从 2.9.2 升到 2.9.3 的一次 patch 升级，本提交把它升级到最新版本，跟随上游修复。

## 如何达成设计目的

通过修改 `site/requirements.txt` 中该插件的版本锁定字符串即可。MkDocs 在构建时通过 pip 安装指定版本，不需要任何代码改动。

## 修改详情

### `site/requirements.txt`

**修改目的**：把 `mkdocs-awesome-pages-plugin` 从 2.9.2 升级到 2.9.3。

**工作逻辑**：仅修改一行：

```diff
-mkdocs-awesome-pages-plugin==2.9.2
+mkdocs-awesome-pages-plugin==2.9.3
```

## 小结

- **成效**：完成 mkdocs-awesome-pages-plugin 的一次 patch 级升级，跟随上游修复。
- **影响范围**：仅 `site/requirements.txt` 一行；只影响文档站点的导航生成行为，不影响任何 Java/Scala 代码与运行时行为。
- **回迁到 1.4.x 的注意事项**：纯文档构建依赖升级，回迁无任何风险。注意该提交与 0987（mkdocs-material 9.5.30）属于同一批 Dependabot 升级，回迁时建议一并带回以保持文档依赖一致性。
