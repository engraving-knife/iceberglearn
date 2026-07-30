# 提交 0710：Docs: Don't check links on Release page

## 提交信息
- **序号**：0710 / 4088
- **哈希**：34e181b288464e9ede176b87ee1ecb27f3d3ac78
- **短哈希**：34e181b28
- **日期**：2024-04-24
- **作者**：Eduard Tudenhoefner
- **提交说明**：Docs: Don't check links on Release page (#10212)
- **PR/Issue**：#10212

## 总体目的

Iceberg 项目的文档站点使用 `markdown-link-check` 工具来校验 Markdown 文档中的链接是否有效。该工具通过 HTML 注释形式的指令 `<!-- markdown-link-check-enable -->` 和 `<!-- markdown-link-check-disable -->` 来控制对哪些区段进行链接校验。

Release 页面（`site/docs/releases.md`）列出了所有历史版本的下载链接，包括指向 Maven Central 上各版本 JAR 包的 `search.maven.org/remotecontent` 链接以及 GitHub Release 标签链接。这些链接会随着时间推移而变化，部分旧版本链接可能失效或返回非 200 状态，导致 CI 中的链接校验任务失败。

本次提交将 `<!-- markdown-link-check-enable -->` 指令从文档开头（第 42 行附近，下载链接列表之前）移动到了文档末尾，从而让整个 Release 页面的链接都不再被校验，避免因历史版本下载链接失效而阻断 CI 流程。

## 如何达成设计目的

通过调整 `markdown-link-check-enable` 指令的位置来改变链接校验的作用范围。原本该指令位于下载链接列表之后，意味着从该位置往后的内容会被链接校验覆盖。将其移到文件最末尾后，整个文件（包含所有历史版本下载链接）实际上都被排除在链接校验之外。

## 修改详情

### `site/docs/releases.md`
**修改目的**：将 `markdown-link-check-enable` 指令从文档中部移到文档末尾，使整个 Release 页面的链接不再被链接校验工具检查。

**工作逻辑**：
- 删除了位于下载链接列表之后（约第 42 行）的 `<!-- markdown-link-check-enable -->` 注释。
- 在文件末尾（最后版本 0.7.0 条目之后）新增了 `<!-- markdown-link-check-enable -->` 注释。

这样，从文档开始到末尾指令之间的所有链接（即全部 Release 下载链接）都不会被链接校验工具扫描，只有末尾指令之后的内容（目前为空）才会被校验。

## 小结
- **成效**：成功将 Release 页面排除在链接校验之外，避免历史版本下载链接失效导致的 CI 失败。
- **影响范围**：仅影响文档站点的链接校验 CI 配置，不影响任何代码逻辑。
- **回迁到 1.4.x 的注意事项**：无特殊注意点。可直接应用。需确认 1.4.x 分支的 `releases.md` 中该指令的位置与 main 分支改动前的状态一致，否则需手动调整。
