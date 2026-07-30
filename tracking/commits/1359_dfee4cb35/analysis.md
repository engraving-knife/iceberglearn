# 提交 1359：Docs: Fixes Release Formatting for 1.7.0 Release Notes (#11499)

## 提交信息

- **序号**：1359 / 4088
- **哈希**：dfee4cb35e2d18ff22625d360ed8c4af499e694d
- **短哈希**：dfee4cb35
- **日期**：2024-11-08（Fri Nov 8 14:47:21 2024 -0600）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：Docs: Fixes Release Formatting for 1.7.0 Release Notes (#11499)
- **PR/Issue**：#11499

## 总体目的

提交 1355 在 `site/docs/releases.md` 中新增了 1.7.0 release notes，但格式存在两类小问题：(1) 部分条目使用 `*` 而非 `-` 作为列表标记，与同章节其他条目不一致；(2) PR 链接的 Markdown 语法写成了 `[\#10711](https://github.com/apache/iceberg/pull/10711))`，即左括号用 `[` 但右括号写成了 `)`（多了一个右圆括号、缺少右方括号），导致链接渲染异常。此外 "AWS" 前有一处缩进多余的 ` * AWS`。

本提交统一 1.7.0 章节的列表标记为 `-`，并把所有 PR 链接修正为标准 Markdown 形式 `([\#10711](https://github.com/apache/iceberg/pull/10711))`（外层圆括号包裹，内层为正确的方括号链接），同时修正 "AWS" 行的缩进。

## 如何达成设计目的

直接编辑 `site/docs/releases.md`，对 1.7.0 章节内的条目逐行做格式重写：

- 把 `* Java 8` / `* Apache Pig` 改为 `- Java 8` / `- Apache Pig`（统一为 `-`）。
- 把 ` * AWS` 改为 `* AWS`（去除多余前导空格）。
- 把所有 `[\#NNNNN](url))` 形式的链接改为 `([\#NNNNN](url))`。

共重写约 50 行（替换 50 行），无内容增删，纯格式修复。

## 修改详情

### `site/docs/releases.md`

**修改目的**：统一 1.7.0 release notes 的列表标记与 PR 链接语法。

**工作逻辑**：

1. **列表标记统一**：把 `Deprecation / End of Support` 分组下的 `* Java 8`、`* Apache Pig` 改为 `- Java 8`、`- Apache Pig`，与同章节其他分组（API、AWS、Build 等已用 `-`）保持一致。
2. **分组标记缩进修正**：把 ` * AWS` 改为 `* AWS`，去除多余前导空格，使其与其他顶层分组（`* Build`、`* Core` 等）对齐。
3. **PR 链接语法修正**：把所有形如

   ```
   - Add SupportsRecoveryOperations mixin for FileIO  [\#10711](https://github.com/apache/iceberg/pull/10711))
   ```

   的条目改为

   ```
   - Add SupportsRecoveryOperations mixin for FileIO  ([\#10711](https://github.com/apache/iceberg/pull/10711))
   ```

   即把错误的 `[\#...](url))`（左方括号 + 右圆括号收尾）改为 `([\#...](url))`（左圆括号起、右圆括号收，内层为标准 Markdown 链接 `[文本](url)`）。该修正覆盖 API、AWS、Build、Core、Flink、GCS、Hive、OpenAPI、Spark、Spec 等所有分组下的 PR 链接，共约 45 处。

修正后 Markdown 渲染器会把每条 PR 渲染为可点击的链接，外层圆括号作为普通文本显示，整体格式与 1.6.0、1.6.1 章节一致。

## 小结

- **成效**：1.7.0 release notes 的列表标记统一为 `-`，PR 链接语法修正为标准 Markdown 形式，渲染正常；"AWS" 分组缩进对齐。
- **影响范围**：仅 `site/docs/releases.md` 一个文件，修改 50 行（替换 50 行），无内容增删、无代码、构建或运行时变更。
- **回迁到 1.4.x 的注意事项**：这是 main 分支官网对 1.7.0 release notes 的格式修复，与 1.4.x 维护分支的发布产物无关。1.4.x 不维护 1.7.0 的 release notes，**无需回迁**。
