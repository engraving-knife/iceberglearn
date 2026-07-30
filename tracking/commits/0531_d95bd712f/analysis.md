# 提交 0531：通过符号链接将规范文档同步到站点

## 提交信息

- **序号**：0531 / 4088
- **哈希**：d95bd712ffbd3edf6a6bf9b9450a29bee2709818
- **短哈希**：d95bd712f
- **日期**：2024-02-24（Sat Feb 24 00:17:23 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Sync specs to site via symlinks (#9779)
- **PR/Issue**：#9779

## 总体目的

Iceberg 项目的规范文档（spec）此前在仓库中存在两份独立副本：一份位于 `format/` 目录（作为权威的规范源），另一份位于 `site/docs/` 目录（供 Hugo 站点渲染发布使用）。这种双副本维护方式带来了两个问题：

1. **同步成本高且易错**：每次规范更新都需要手动将 `format/` 的修改搬运到 `site/docs/`，容易出现两份内容不一致的情况，长期维护负担重。
2. **导航缺失**：AES GCM Stream 规范文档（`gcm-stream-spec.md`）虽然存在于 `format/` 目录，但未在站点导航 `site/nav.yml` 中登记，导致站点访问者无法通过导航进入该规范页面。

本提交通过引入符号链接（symlink）机制，将 `site/docs/` 下的规范文件改为指向 `format/` 中对应文件的软链接，从而消除重复副本，让规范文档的"单一事实源"落在 `format/`，站点自动跟随渲染，彻底避免同步漂移问题。

## 如何达成设计目的

整体设计思路是利用 Unix 符号链接在仓库内建立文件别名，使 Hugo 站点构建时读取到的 `site/docs/spec.md` 实际指向 `format/spec.md`，从而：

- 编辑者只需修改 `format/` 下的规范源文件，站点内容会自动更新，无需任何手动同步步骤；
- Git 会以 `120000` 模式记录符号链接（而非普通文件 `100644`），保留链接关系本身，不存储重复内容；
- Hugo 静态站点生成器在解析文档目录时会跟随符号链接读取真实文件，渲染行为与普通文件一致。

具体实现路径分三步：

1. **删除 `site/docs/` 下的规范副本文件**：移除 `spec.md`、`view-spec.md`、`puffin-spec.md`、`gcm-stream-spec.md` 四个普通文件（共删除约 1850 行重复内容）。
2. **以符号链接重建同名文件**：在 `site/docs/` 下创建四个符号链接，分别指向 `../../format/<spec>.md`，路径基于 `site/docs/` 相对定位到 `format/`。
3. **修正 Hugo 模板语法**：将 `format/spec.md` 中的 `{{% icebergVersion %}}` 改为 `{{ icebergVersion }}`，使站点渲染时变量插值正常工作。
4. **补全站点导航**：在 `site/nav.yml` 中新增 AES GCM Stream spec 的导航条目，使该规范可在站点侧边栏被访问。

## 修改详情

### `format/spec.md`

**修改目的**：修正 Hugo 模板变量插值语法，确保符号链接后的规范页面在站点构建时能正确渲染 Javadoc 链接中的版本号。

**工作逻辑**：仅两处单行改动，均位于"Tables"章节末尾的 Notes 部分。改动将 `{{% icebergVersion %}}` 替换为 `{{ icebergVersion }}`：

- 第 787 行附近：`HadoopTableOperations` 的 Javadoc 链接。
- 第 803 行附近：`BaseMetastoreTableOperations` 的 Javadoc 链接。

在 Hugo 中，`{{% %}}` 用于调用带 Markdown 处理的 shortcode，而 `{{ }}` 用于纯文本变量插值。`icebergVersion` 是一个在站点配置中定义的版本变量，本应使用 `{{ }}` 语法。此前 `{{% %}}` 写法属于误用，会在 shortcode 上下文中尝试解析变量导致渲染异常；本次顺带修正以保证符号链接方案落地后页面渲染正确。

### `site/docs/gcm-stream-spec.md`

**修改目的**：将独立的 AES GCM Stream 规范副本替换为指向源规范的符号链接，消除重复维护。

**工作逻辑**：删除原 85 行的普通文件内容（含 YAML front matter `title: "AES GCM Stream Spec"` 与 Apache 许可证头及规范正文），新建符号链接 `../../format/gcm-stream-spec.md`。Git 以 `120000` 模式记录，链接目标为相对于 `site/docs/` 向上两级进入 `format/` 目录的路径。

### `site/docs/puffin-spec.md`

**修改目的**：将独立的 Puffin 规范副本替换为指向源规范的符号链接，消除重复维护。

**工作逻辑**：删除原 144 行普通文件内容，新建符号链接 `../../format/puffin-spec.md`。链接目标解析逻辑与上文一致。

### `site/docs/spec.md`

**修改目的**：将主 Iceberg 规范副本替换为指向源规范的符号链接，消除重复维护。

**工作逻辑**：删除原 1294 行普通文件内容（最大的一处删除），新建符号链接 `../../format/spec.md`。这是规范文档的核心文件，删除后通过符号链接跟随 `format/spec.md` 的所有未来更新。

### `site/docs/view-spec.md`

**修改目的**：将 View 规范副本替换为指向源规范的符号链接，消除重复维护。

**工作逻辑**：删除原 328 行普通文件内容，新建符号链接 `../../format/view-spec.md`。

### `site/nav.yml`

**修改目的**：在站点导航中补登 AES GCM Stream spec 条目，使该规范页面可通过侧边栏访问。

**工作逻辑**：在 `nav` 配置的规范列表段（紧跟 `Spec`、`View spec`、`Puffin spec` 之后）插入一行：

```yaml
    - AES GCM Stream spec: gcm-stream-spec.md
```

该条目放在 `Puffin spec` 之后、`Multi-engine support` 之前，与现有规范条目保持相同的缩进与命名风格，Hugo 会据此在侧边栏生成对应链接，指向 `site/docs/gcm-stream-spec.md`（现为符号链接）。

## 小结

**成效**：本提交一次性消除了 4 份规范文档的重复副本（共减少约 1850 行重复内容），通过符号链接实现 `format/` 作为单一事实源、`site/docs/` 自动跟随的维护模式，从根本上解决了规范同步漂移问题；同时补全了 AES GCM Stream 规范的站点导航入口，并顺带修正了 Hugo 变量插值语法的误用。

**影响范围**：仅影响文档站点构建与规范文档维护流程，不涉及任何代码逻辑、API 或运行时行为，属于纯文档工程改动。

**回迁到 1.4.x 的注意事项**：
1. 符号链接在跨平台（尤其 Windows）检出时可能存在兼容性问题，回迁前需确认 1.4.x 分支的 CI 与本地开发环境能正确处理 `120000` 模式的 Git 链接（通常需启用 `core.symlinks=true`，Windows 上可能需要管理员权限或开发者模式）。
2. `format/spec.md` 中的 Hugo 语法修正（`{{% %}}` → `{{ }}`）需配合站点构建配置一并回迁，否则变量插值可能失败；若 1.4.x 分支站点配置不同，需验证渲染效果。
3. `site/nav.yml` 的导航条目新增是独立改动，可单独回迁而不引入符号链接，但建议与符号链接一并回迁以保持文档结构一致。
4. 该提交不依赖任何代码层面的前置改动，回迁冲突风险低，主要风险在符号链接的平台兼容性。
