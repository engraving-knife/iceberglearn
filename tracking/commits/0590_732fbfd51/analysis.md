# 提交 0590：Docs: Update site docs

## 提交信息

- **序号**：0590 / 4088
- **哈希**：732fbfd516a3dfb2028fd6795f8f564f70e44742
- **短哈希**：732fbfd51
- **日期**：2024-03-13 16:13:31 +0530
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Docs: Update site docs (#9946)
  - Resolve Nessie version for site docs
  - Add release notes link to template
- **PR/Issue**：#9946

## 总体目的

本提交是 1.5.0 发版流程中对 Iceberg 站点（`site/` 目录，MkDocs Material 构建）的两项配套完善：

1. **为站点配置补充 Nessie 版本变量**：MkDocs 的 `extra` 配置区块定义了可在文档页面中通过 Jinja2 模板语法（`{{ 变量名 }}`）引用的全局变量。此前站点已有 `icebergVersion: '1.5.0'`，被 `releases.md`、`how-to-release.md`、`multi-engine-support.md` 等页面大量用于动态填充下载链接、Maven 坐标、源码包名等。但 Nessie（Iceberg 集成的 Git-like 目录服务 catalog）的版本号此前未在站点配置中登记为变量，导致需要引用 Nessie 版本的页面只能硬编码。本提交新增 `nessieVersion: '0.77.1'`，与 `gradle/libs.versions.toml` 中的 `nessie = "0.77.1"`（1.5.0 发布说明中"Bump Nessie to 0.77.1"）保持一致，使站点文档可通过 `{{ nessieVersion }}` 统一引用。

2. **在发布通告邮件模板中加入发布说明链接**：`how-to-release.md` 中维护着一份发布通告邮件模板（一段 code block），供 release manager 在发版时套用。此前模板只包含下载链接与 Maven 制品说明，未包含指向站点发布说明页（`/releases/`）的链接。本提交在模板中加入 `Release notes: https://iceberg.apache.org/releases/#XYZ-release` 一行，使通告邮件可直接引导读者查看详细的发布说明。

## 如何达成设计目的

**Nessie 版本变量的设计**：沿用 `icebergVersion` 既有模式，在 `site/mkdocs.yml` 的 `extra:` 区块新增一行键值对 `nessieVersion: '0.77.1'`。MkDocs Material 会把 `extra` 下的键暴露为页面级 Jinja2 变量，文档中即可用 `{{ nessieVersion }}` 插值。版本值 `0.77.1` 取自构建侧 `gradle/libs.versions.toml` 的 `nessie = "0.77.1"`，确保站点展示的 Nessie 版本与实际发布的 Iceberg 制品依赖的 Nessie 版本一致。`how-to-release.md` 中也已有文档说明：发版时需把 Nessie 版本从 Iceberg 仓库的版本配置同步到 iceberg-docs 仓库的 `docs/config.toml`，本提交把这一版本也固化到站点 mkdocs 配置中，避免散落。

**发布说明链接的设计**：在 `how-to-release.md` 的通告模板 code block 中，于"This release can be downloaded from: ..."行之后、"Java artifacts are available from Maven Central."行之前，插入一行 `Release notes: https://iceberg.apache.org/releases/#XYZ-release`。其中 `XYZ` 是占位符，release manager 在套用模板时需替换为对应版本的锚点片段——`releases.md` 页面用 `### <版本> release` 作为各版本章节标题（如 `### 1.5.0 release`），MkDocs 会将其转为锚点 `#150-release`（小写化、去点、空格转连字符），因此 `XYZ` 应替换为去掉点号的版本号（如 1.5.0 → `150`）。这是模板惯例而非自动替换，符合 how-to-release 文档"手动套用模板"的定位。

## 修改详情

### `site/mkdocs.yml`

**修改目的**：在站点配置的 `extra` 区块新增 `nessieVersion` 变量，使文档页面可通过 `{{ nessieVersion }}` 引用 Nessie 版本。

**工作逻辑**：

修改前：
```yaml
extra:
  icebergVersion: '1.5.0'
  social:
    ...
```

修改后：
```yaml
extra:
  icebergVersion: '1.5.0'
  nessieVersion: '0.77.1'
  social:
    ...
```

`extra` 区块的键会被 MkDocs Material 注入为页面级 Jinja2 上下文变量。`icebergVersion` 已被广泛使用——例如 `releases.md` 中 `The latest version of Iceberg is [{{ icebergVersion }}](...)`、`how-to-release.md` 中 `apache-iceberg-{{ icebergVersion }}.tar.gz`、`multi-engine-support.md` 中 Maven 坐标 `iceberg-spark-runtime-3.5_2.12/{{ icebergVersion }}/...`。新增的 `nessieVersion` 以同样机制提供 Nessie 版本号 `0.77.1`，与 `gradle/libs.versions.toml` 中 `nessie = "0.77.1"` 对齐，供需要展示 Nessie 版本的文档页面引用。

### `site/docs/how-to-release.md`

**修改目的**：在发布通告邮件模板中增加一行"Release notes"链接，引导邮件读者前往站点查看完整发布说明。

**工作逻辑**：

在通告模板 code block 中插入一行：

```text
Release notes: https://iceberg.apache.org/releases/#XYZ-release
```

完整模板上下文（节选）：

```text
This release can be downloaded from: https://www.apache.org/dyn/closer.cgi/iceberg/<TARBALL NAME WITHOUT .tar.gz>/<TARBALL NAME>

Release notes: https://iceberg.apache.org/releases/#XYZ-release

Java artifacts are available from Maven Central.
```

`XYZ` 为占位符，发版时替换为目标版本对应的锚点。`releases.md` 中各版本章节标题为 `### <版本> release`（如 `### 1.5.0 release`、`### 1.4.3 Release`），MkDocs Material 自动生成锚点时会把标题小写、去点、空格转连字符，因此 1.5.0 的锚点为 `#150-release`，`XYZ` 应替换为 `150`。该链接指向站点 `/releases/` 页面中对应版本的章节锚点，让通告邮件收件人可一键跳转查看该版本的完整特性与修复列表。

## 小结

本提交是 1.5.0 发版流程的站点配套完善，包含两项小改动（共 3 行新增、0 行删除）：一是在 `site/mkdocs.yml` 的 `extra` 区块新增 `nessieVersion: '0.77.1'`，使文档页面可通过 `{{ nessieVersion }}` 模板变量引用 Nessie 版本（与构建侧 `gradle/libs.versions.toml` 的 `nessie = "0.77.1"` 对齐）；二是在 `how-to-release.md` 的发布通告邮件模板中加入"Release notes"链接行，引导读者前往站点发布说明页。两项改动均不影响代码与构建逻辑，仅改善站点文档的可维护性与发布流程的完整性。

回迁到 1.4.x 分支的注意事项：`nessieVersion` 变量值需与 1.4.x 分支实际依赖的 Nessie 版本对齐（1.4.x 的 `gradle/libs.versions.toml` 中 `nessie` 版本可能与 main 的 0.77.1 不同），不应盲目照搬 0.77.1。发布通告模板中的"Release notes"链接是通用改进，**可回迁**，但需确保 1.4.x 分支的 `releases.md` 中对应版本章节标题格式与锚点命名规则一致（`### <版本> release`）。由于这是 1.5.0 发版流程的产物，1.4.x 分支若不维护独立的站点发布说明，则无需回迁第一项（nessieVersion）；若 1.4.x 也维护站点文档，则按其实际 Nessie 版本配置。
