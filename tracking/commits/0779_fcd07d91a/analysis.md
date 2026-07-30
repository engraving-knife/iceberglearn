# 提交 0779：Build: Bump mkdocs-material from 9.5.21 to 9.5.23 (#10353)

## 提交信息

- **序号**：0779 / 4088
- **哈希**：fcd07d91aac2c0851e9174c0e0bd84b1a529856c
- **短哈希**：fcd07d91a
- **日期**：2024-05-23 09:17:55 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.5.21 to 9.5.23 (#10353)
- **PR/Issue**：#10353

## 总体目的

本提交由 Dependabot 自动生成，将文档站点构建依赖 `mkdocs-material` 从 `9.5.21` 升级到 `9.5.23`，属于一次补丁版本（semver-patch）升级。`mkdocs-material` 是 Iceberg 文档站点（`site/` 目录）所用的 MkDocs 主题，提供站点外观与交互组件。升级目的是跟进上游修复与小幅改进，保持文档主题为较新版本，避免落后积累过多补丁。Dependabot 在提交体中标注 `update-type: version-update:semver-patch`，表明仅涉及补丁号递增，无破坏性变更。

## 如何达成设计目的

整体思路是直接修改文档站点的 Python 依赖清单 `site/requirements.txt` 中 `mkdocs-material` 的版本钉死（pin），从 `9.5.21` 改为 `9.5.23`。不涉及任何代码逻辑、构建脚本或文档内容改动，纯粹是依赖版本号的递增。MkDocs 主题作为构建期依赖，仅在本地/CI 构建文档站点时拉取，不进入 Iceberg 的 Java 产物，因此对运行时行为零影响。

## 修改详情

### `site/requirements.txt`

**修改目的**：将 `mkdocs-material` 版本钉从 9.5.21 提升到 9.5.23。

**工作逻辑**：

```diff
-mkdocs-material==9.5.21
+mkdocs-material==9.5.23
```

该文件以 `==` 精确钉死文档站点各 Python 依赖版本（如 `mkdocs-awesome-pages-plugin==2.9.2`、`mkdocs-macros-plugin==1.0.5`、`mkdocs-material-extensions==1.3.1`、`mkdocs-redirects==1.2.1` 等）。本次仅改动 `mkdocs-material` 一行，跨两个补丁版本（9.5.21 → 9.5.22 → 9.5.23），按上游 CHANGELOG 属于常规缺陷修复与小幅增强，不含破坏性变更，主题渲染行为保持兼容。

## 小结

- **成效**：文档站点主题依赖向前推进两个补丁版本，纳入上游修复，降低长期未升级带来的积压风险。
- **影响范围**：仅 `site/requirements.txt` 单行，属构建/文档期依赖，不影响 Java 产物与运行时行为。
- **回迁注意事项**：回迁到 1.4.x 无风险，直接套用即可。需确认 1.4.x 的 `site/requirements.txt` 结构一致（同样以 `==` 钉死版本）。若 1.4.x 上 `mkdocs-material` 版本已高于 9.5.23（例如已被其它提交升级），则无需回迁此 patch。
