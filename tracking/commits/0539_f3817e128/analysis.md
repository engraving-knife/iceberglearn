# 提交 0539：Docs: Sync contributing page / refer to website for contributing

## 提交信息

- **序号**：0539 / 4088
- **哈希**：f3817e12819784de279ad4e1a7e1f55e9e636c37
- **短哈希**：f3817e128
- **日期**：2024-02-26 12:38:07 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Docs: Sync contributing page / refer to website for contributing (#9776)
- **PR/Issue**：#9776

## 总体目的

本提交解决 Iceberg 贡献指南文档双份维护、易漂移的问题。此前仓库根目录的 `CONTRIBUTING.md` 维护了一份长达 330 余行的详细贡献指南（含 PR 流程、语义化版本、弃用规则、API 兼容性、代码风格、测试规范等），同时 `site/docs/contribute.md`（网站文档源）也维护了一份内容高度重叠但细节有差异的版本。两份文档长期并存导致内容不同步：例如 `CONTRIBUTING.md` 里有"在不破坏 API 的前提下新增功能"整节，而 `site/docs/contribute.md` 里缺失；网站文档里关于 `iceberg-docs` 仓库的说明也已过时。

本次提交确立"网站为唯一权威来源"的原则：把 `CONTRIBUTING.md` 精简为只保留一句话指引，指向网站 `https://iceberg.apache.org/contribute/`；同时把 `CONTRIBUTING.md` 中独有、网站缺失的内容（主要是"新增功能不破坏 API"一节和 AssertJ map 断言示例）迁移补入 `site/docs/contribute.md`，并清理网站文档中过时的"Website and Documentation Updates"章节。这样既消除了双份维护，又保证网站内容不丢失原有信息。

## 如何达成设计目的

采用"迁移 + 精简 + 清理"三步：
1. **迁移**：把 `CONTRIBUTING.md` 中独有、`site/docs/contribute.md` 缺失的内容（"Adding new functionality without breaking APIs"整节，含 Revapi 检查示例和 default 实现方案）原样搬到 `site/docs/contribute.md` 对应位置。
2. **精简**：把 `CONTRIBUTING.md` 全文替换为 3 行，仅说明"请参考网站的 contributing 章节"。
3. **清理**：删除 `site/docs/contribute.md` 末尾过时的"Website and Documentation Updates"章节（描述旧的 `iceberg-docs` 仓库本地运行流程，已不适用），并补充一个 AssertJ map 断言示例、修正若干 Markdown 列表缩进。

## 修改详情

### `CONTRIBUTING.md`

**修改目的**：把仓库内贡献指南精简为指向网站的指引，消除双份维护。

**工作逻辑**：删除从第 21 行起约 332 行的详细内容（PR 流程、本地构建、网站更新、语义化版本、弃用通知、API 兼容性、代码风格、测试规范等全部章节），替换为：
```
# Contributing

Please refer to the [contributing](https://iceberg.apache.org/contribute/) section for instructions
on how to contribute to Iceberg.
```
GitHub 在 PR/Issue 模板里会自动引用 `CONTRIBUTING.md`，保留这个文件并以指引形式存在，能让贡献者被正确导向网站，同时不丢失 GitHub 的自动关联。

### `site/docs/contribute.md`

**修改目的**：承接从 `CONTRIBUTING.md` 迁移来的内容，并清理过时章节。

**工作逻辑**：

- **新增"Adding new functionality without breaking APIs"章节**：内容来自 `CONTRIBUTING.md`，包含一个完整的 Revapi 工作流示例——假设要给 `ManageSnapshots` 接口加 `createBranch(String name)` 方法，演示：
  - 直接加方法会被 `./gradlew revapi` 检出 `java.method.addedToInterface` 违规（SOURCE: BREAKING）；
  - 解决方案是给接口方法加 `default` 实现并抛 `UnsupportedOperationException`，既新增能力又不破坏二进制兼容性。
  这一节对维护 Iceberg API 兼容性有实操指导价值。

- **新增 AssertJ map 断言示例**：补一个对比示例，说明逐个 `map.get("key")` 断言在失败时不会打印 map 内容，而用 `assertThat(map).containsEntry(...).hasEntrySatisfying(...)` 能在失败时展示完整 map，更易调试。

- **删除"Website and Documentation Updates"章节**：原章节描述如何 clone `iceberg-docs` 仓库、用 `hugo serve` 本地跑 landing-page 和 docs、以及如何从 Iceberg 仓库拷贝 docs 内容到 `iceberg-docs`。这套流程已过时（网站构建机制变更），删除避免误导。

- **Markdown 格式微调**：几处列表项缩进从 4 空格调整为 5 空格以符合网站渲染规范，并在若干段落间加空行提升可读性。

## 小结

本提交把 Iceberg 贡献指南统一收敛到网站 `site/docs/contribute.md`（渲染为 `https://iceberg.apache.org/contribute/`），仓库 `CONTRIBUTING.md` 退化为指引。这是文档治理上的正确决策：单一来源、避免漂移、网站渲染更友好。迁移过程中没有信息丢失——`CONTRIBUTING.md` 独有的 API 兼容性章节和 AssertJ 示例都补进了网站文档。

**回迁到 1.4.x 的注意事项**：纯文档变更，无代码影响，回迁零风险。但 1.4.x 若已有本地化的 `CONTRIBUTING.md` 改动，需注意合并冲突。另外需确认 1.4.x 对应的网站分支能同步接收到 `site/docs/contribute.md` 的变更，否则会出现"仓库指向网站、网站却没更新内容"的断链。
