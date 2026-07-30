# 提交 0010：Labeler: Add Specification label (#8700)

## 提交信息

- **序号**：0010 / 4088
- **哈希**：c862b9177af8e2d83122220764a056f3b96fd00c
- **短哈希**：c862b9177
- **日期**：2023-10-02 13:56:58 -0700
- **作者**：Fokko Driesprong
- **提交说明**：Labeler: Add Specification label (#8700)
- **PR/Issue**：#8700

## 总体目的

这个提交是纯仓库维护类改动，目的是为 GitHub 的自动标签机器人（Labeler）新增一个 "SPECIFICATION" 标签规则，使修改 Iceberg 表格式规范（spec）相关文件的 PR 能被自动打上该标签。

Iceberg 项目使用 GitHub 的 [Labeler Action](https://github.com/actions/labeler) 配合 `.github/labeler.yml` 配置文件实现自动标签：当 PR 修改了配置中匹配的文件路径时，机器人会自动给 PR 加上对应标签。仓库已存在多个标签规则，例如 `DOCS`（匹配 README、CHANGELOG、CONTRIBUTING 等）、`EXAMPLES`（匹配 `examples/**/*`）、`COMMON` 等模块标签。这些标签帮助维护者快速分类和路由 PR。

Iceberg 的核心价值之一是其跨引擎、跨语言的**表格式规范（Specification）**——规范定义了元数据、快照、Manifest、分区等核心概念。仓库中 `format/` 目录下存放的就是规范文档（如 `Spec.md`、`Binary.md` 等）。在此提交之前，修改规范文档的 PR 不会被自动打上任何专门的标签，维护者难以一眼识别"这是动规范"的 PR——而规范改动通常影响面广、需要更谨慎的评审。通过新增 `SPECIFICATION` 标签规则匹配 `format/*` 路径，这类 PR 将被自动标记，便于维护者优先关注和规范评审流程。这是一个提升项目治理效率的小型基础设施改进。

## 如何达成设计目的

设计思路很简单：在 `.github/labeler.yml` 配置文件中，紧接已有的 `DOCS` 规则块之后，新增一个 `SPECIFICATION` 规则块，匹配 `format/*` 路径下的所有文件。这样 Labeler Action 在运行时会扫描 PR 改动文件，若命中 `format/` 目录下的文件，就自动给 PR 打上 `SPECIFICATION` 标签。改动仅 2 行新增，无删除，结构上与既有的 `DOCS`、`EXAMPLES`、`COMMON` 等规则块格式完全一致。

## 修改详情

### `.github/labeler.yml`

**修改目的**：新增 `SPECIFICATION` 标签规则，使修改 `format/` 目录下文件的 PR 自动获得该标签。

**工作逻辑**：在 `DOCS` 规则块（匹配 `**/*CHANGELOG.md`、`**/*README.md`、`**/*CONTRIBUTING.md` 等）之后，新增如下规则块：

```yaml
SPECIFICATION:
  - "format/*"
```

`format/*` 是 Labeler 使用的 glob 匹配模式，表示仓库根目录下 `format/` 目录中的任意文件（含子目录中的文件，取决于 Labeler 的匹配语义；该配置使用的是单层 `*`，配合 Labeler 的路径匹配规则覆盖 format 目录下的规范文档）。当 PR 改动命中此模式时，Labeler Action 会自动给 PR 添加 `SPECIFICATION` 标签。规则块的缩进和格式与上方 `DOCS`、下方 `EXAMPLES` 完全一致，保持配置风格统一。

## 小结

该提交通过在 `.github/labeler.yml` 新增 `SPECIFICATION` 标签规则匹配 `format/*`，使修改 Iceberg 表格式规范文档的 PR 能被自动打标签，便于维护者快速识别和优先评审规范类改动，提升了项目治理效率。
