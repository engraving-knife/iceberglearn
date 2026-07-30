# 提交 1424：Build: Bump mkdocs-material from 9.5.44 to 9.5.45 (#11641)

## 提交信息

- **序号**：1424 / 4088
- **哈希**：3aebcfeb8a12af11ca868968db206c4fdb7cce4d
- **短哈希**：3aebcfeb8
- **日期**：2024-11-25（Mon Nov 25 08:45:53 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material from 9.5.44 to 9.5.45 (#11641)
- **PR/Issue**：#11641

## 总体目的

由 dependabot 自动发起的依赖升级，将文档站点构建依赖 `mkdocs-material` 从 9.5.44 升级到 9.5.45。`mkdocs-material` 是 Iceberg 官方文档站点（位于 `site/` 目录）使用的 MkDocs 主题，提供文档渲染、搜索、导航等能力。9.5.45 是一个 patch 版本，包含主题层面的 bug 修复。本次升级用于跟随上游补丁，保证文档站点构建的稳定性和正确性。

## 如何达成设计目的

修改 `site/requirements.txt` 中 `mkdocs-material` 的版本号即可。MkDocs 主题是 Python 依赖，通过 `pip install -r site/requirements.txt` 安装，因此只需在该文件中改版本号即可。

## 修改详情

### `site/requirements.txt`

**修改目的**：升级 mkdocs-material 主题版本。

**工作逻辑**：

```
-mkdocs-material==9.5.44
+mkdocs-material==9.5.45
```

仅此一行。该文件锁定了文档站点所有 Python 依赖的精确版本，本次只升级 mkdocs-material 一个包。

## 小结

- **成效**：跟随上游 patch 版本，获得 9.5.45 的 bug 修复，文档站点构建更稳定。
- **影响范围**：仅 `site/requirements.txt` 一行，无源码、测试或运行时逻辑改动。
- **回迁到 1.4.x 的注意事项**：文档依赖升级与产品发布物无关，1.4.x 维护分支通常不单独维护文档站点的依赖版本（文档站点由 main 分支统一构建并发布到 iceberg.apache.org）。**一般无需回迁**到 1.4.x。即使 1.4.x 的 `site/requirements.txt` 与 main 不同，也不影响 1.4.x 的 jar/发布产物。如果 1.4.x 也要独立构建文档站点，则可顺手回迁，无破坏性风险。
