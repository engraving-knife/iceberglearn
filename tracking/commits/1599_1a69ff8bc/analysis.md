# 提交 1599：Build: Bump mkdocs-material from 9.5.49 to 9.5.50 (#12005)

## 提交信息

- **序号**：1599 / 4088
- **哈希**：1a69ff8bcb97f449656ae9f9e31908994f3f8456
- **短哈希**：1a69ff8bc
- **日期**：2025-01-19（Sun Jan 19 10:24:33 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material from 9.5.49 to 9.5.50 (#12005)
- **PR/Issue**：#12005

## 总体目的

Iceberg 仓库使用 MkDocs Material 构建 `site/` 目录下的文档站点，依赖列表固定在 `site/requirements.txt` 中。dependabot 自动监控该 pip 依赖文件，发现 `mkdocs-material` 从 9.5.49 发布到 9.5.50（一个 patch 版本升级）。本提交是 dependabot 自动发起的版本升级 PR 合并后的提交，把文档构建依赖从 9.5.49 升级到 9.5.50，跟随上游 patch 修复（mkdocs-material 9.5.x 系列的 bug 修复与小幅改进）。

此类 dependabot 升级的目的：

1. 跟随上游补丁修复，避免积累技术债；
2. 保持文档构建链路在受支持版本上，便于安全 / 兼容性问题被及时修复；
3. 通过自动化（dependabot）减少人工维护成本。

由于是 semver-patch 升级，预期无 API 破坏，文档构建产物外观与功能保持稳定。

## 如何达成设计目的

直接修改 `site/requirements.txt` 中 `mkdocs-material` 的版本固定字符串：从 `mkdocs-material==9.5.49` 改为 `mkdocs-material==9.5.50`。其余依赖行不变。这是 Iceberg 文档站点构建环境的 pinning 文件，由 CI 在构建文档时通过 `pip install -r site/requirements.txt` 安装。

dependabot 在 PR 描述中提供了上游 release notes / changelog / commits 对比链接，便于评审者确认变更范围。本次升级只动一个版本号字符串，无代码逻辑变更。

### 修改详情

#### `site/requirements.txt`

**修改目的**：升级 mkdocs-material 至 9.5.50。

**工作逻辑**：

```diff
-mkdocs-material==9.5.49
+mkdocs-material==9.5.50
```

`==` 是 pip 的严格版本固定语法，确保 CI 与本地构建安装完全相同的版本，避免因浮动版本带来的构建不可重现问题。

## 小结

- **成效**：文档构建依赖 mkdocs-material 升级到 9.5.50，跟随上游 patch 修复。
- **影响范围**：仅 `site/requirements.txt` 一个文件，单行版本号变更。无产品代码 / 测试 / 表格式变更，对运行时产物（jar / 表格式规范）无任何影响。
- **回迁到 1.4.x 的注意事项**：纯文档构建依赖升级，与 1.4.x 发布产物无关。1.4.x 分支若维护独立的文档构建，可选择性同步此升级；不影响发布 jar 包。无需回迁。
