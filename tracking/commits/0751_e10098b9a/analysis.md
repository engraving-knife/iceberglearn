# 提交 0751：Docs: Add release notes for 1.5.2 (#10295)

## 提交信息

- **序号**：0751 / 4088
- **哈希**：e10098b9ab7cb532d2ca4876f00997102446e52d
- **短哈希**：e10098b9a
- **日期**：2024-05-09 14:06:31 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Docs: Add release notes for 1.5.2 (#10295)
- **PR/Issue**：#10295

## 总体目的

本提交为 Apache Iceberg 1.5.2 版本发布新增官方发布说明（release notes）。1.5.2 发布于 2024 年 5 月 9 日，其存在的主要原因是 1.5.1 版本的 Spark runtime 构件存在缺陷——部分构件使用了错误的 Scala 版本进行构建。因此 1.5.2 在变更内容上与 1.5.1 完全一致，仅是修复了构件问题后的重新发布。本提交在 `site/docs/releases.md` 文件中、位于 1.5.1 发布说明之前，新增了 1.5.2 的发布说明段落，明确告知用户 1.5.1 存在构件问题，并强烈建议使用 1.5.1 的系统升级到 1.5.2。

## 如何达成设计目的

发布说明文档以版本号倒序排列（最新版本在最上方）。本提交在 `releases.md` 中 Maven 依赖示例代码块之后、原 1.5.1 发布说明之前，插入了一个新的 `### 1.5.2 release` 三级标题段落。该段落包含三方面信息：发布日期（2024 年 5 月 9 日）、与 1.5.1 变更内容相同的说明、1.5.1 构件问题的描述（Spark runtime 构件使用了错误的 Scala 版本），以及对 1.5.1 用户的升级建议。通过在文档中保留 1.5.1 的发布说明（位于 1.5.2 下方），读者可以理解 1.5.2 相对于 1.5.0 的实际变更内容。

## 修改详情

### `site/docs/releases.md`

**修改目的**：新增 1.5.2 版本的发布说明。

**工作逻辑**：在 Maven 依赖示例代码块（`</dependencies>` 后的 ``` 之后）与 `### 1.5.1 release` 之间，新增 6 行内容：

1. `### 1.5.2 release`：三级标题，标识新版本段落。
2. `Apache Iceberg 1.5.2 was released on May 9, 2024.`：发布日期声明。
3. 空行。
4. `The 1.5.2 release has the same changes that the 1.5.1 release (see directly below) has.`：说明 1.5.2 与 1.5.1 变更内容一致，并引导读者查看下方 1.5.1 的详细说明。
5. `The 1.5.1 release had issues with the spark runtime artifacts; specifically certain artifacts were built with the wrong Scala version.`：解释 1.5.1 存在的构件缺陷（Spark runtime 构件使用了错误的 Scala 版本）。
6. `It is strongly recommended to upgrade to 1.5.2 for any systems that are using 1.5.1.`：对 1.5.1 用户的升级建议。

该段落放置在 1.5.1 段落之前（即更靠上的位置），符合发布说明按版本倒序排列的既有约定。

## 小结

- **成效**：为 1.5.2 版本提供了官方发布说明，使文档站点访客能了解该版本的存在、发布原因以及升级建议，避免了用户误用存在构件缺陷的 1.5.1 版本。
- **影响范围**：仅文档变更，无任何代码或配置改动，不影响运行时行为。影响对象为文档站点 `site/docs/releases.md` 的读者。
- **回迁注意事项**：此为纯文档提交，回迁到 1.4.x 分支时无需考虑兼容性问题。但需注意，1.4.x 分支是较早的维护分支，1.5.x 系列的发布说明本身与 1.4.x 无直接功能关联；若回迁仅为保持文档历史完整性，可直接 cherry-pick；若 1.4.x 分支的 `releases.md` 结构与 1.5.x 分支不同（例如缺少 1.5.1 段落），则需要调整插入位置上下文，确保引用的"1.5.1 release (see directly below)"在目标分支中确实存在。
