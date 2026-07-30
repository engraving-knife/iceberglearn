# 提交 0866：Build: Bump mkdocs-material from 9.5.26 to 9.5.27 (#10555)

## 提交信息

- **序号**：0866 / 4088
- **哈希**：da6268ef192079b7dfc2d81b7ac7f606a88d7d02
- **短哈希**：da6268ef1
- **日期**：2024-06-24（Mon Jun 24 10:20:54 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material from 9.5.26 to 9.5.27 (#10555)
- **PR/Issue**：#10555

## 总体目的

Iceberg 项目网站（`site/` 目录下的 MkDocs 站点）使用 `mkdocs-material` 主题来构建文档站点。该主题在 9.5.26 之后发布了 9.5.27 patch 版本。本提交由 dependabot 自动生成，目的是把 `site/requirements.txt` 中锁定的 `mkdocs-material` 版本从 9.5.26 升到 9.5.27，跟进上游补丁修复（通常是 bug fix 或小改进，因为 semver patch 不含破坏性变更），保持文档站依赖的最新状态。

`mkdocs-material` 仅用于本地/CI 构建文档站，不进入 Iceberg 的发布产物，因此版本升级只影响文档站构建流程，对 Iceberg 本身的运行时行为无任何影响。

## 如何达成设计目的

实现方式非常直接：修改 `site/requirements.txt` 中 `mkdocs-material` 的版本钉，从 `mkdocs-material==9.5.26` 改为 `mkdocs-material==9.5.27`。该文件是 Python 依赖清单，MkDocs 构建时通过 `pip install -r requirements.txt` 安装指定版本。

## 修改详情

### `site/requirements.txt`

**修改目的**：把 `mkdocs-material` 文档主题从 9.5.26 升级到 9.5.27。

**工作逻辑**：仅修改一行，diff 如下：

```diff
 mkdocs-awesome-pages-plugin==2.9.2
 mkdocs-macros-plugin==1.0.5
-mkdocs-material==9.5.26
+mkdocs-material==9.5.27
 mkdocs-material-extensions==1.3.1
 mkdocs-monorepo-plugin @ git+https://github.com/bitsondatadev/mkdocs-monorepo-plugin@url-fix
```

其余 MkDocs 插件版本未变。

## 小结

- **成效**：把文档站构建依赖 `mkdocs-material` 升级到 9.5.27 patch 版本，跟进上游修复。
- **影响范围**：仅 `site/requirements.txt` 一个文件，1 行改动。不进入 Iceberg 发布产物，不影响运行时行为，仅影响文档站构建。
- **回迁到 1.4.x 的注意事项**：本提交是文档站依赖升级，**回迁优先级低**。如果 1.4.x 分支需要重建文档站，可以回迁以保持依赖最新；否则可以不回迁，9.5.26 与 9.5.27 之间是 patch 差异，对文档构建无功能性影响。回迁时需确认 1.4.x 分支的 `site/requirements.txt` 中 `mkdocs-material` 行位置未漂移；该改动不依赖任何其他提交，可独立 cherry-pick。
