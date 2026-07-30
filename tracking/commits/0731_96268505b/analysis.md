# 提交 0731：Release: add instruction to update doap.rdf file as part of release process

## 提交信息
- **序号**：0731 / 4088
- **哈希**：96268505b4b9db0df7f866a27c0c25ad0c665a54
- **短哈希**：96268505b
- **日期**：2024-04-30
- **作者**：JB Onofré
- **提交说明**：Release: add instruction to update doap.rdf file as part of release process (#9655)
- **PR/Issue**：#9655

## 总体目的

本提交向 Iceberg 项目的发布流程文档（`site/docs/how-to-release.md`）中补充一个新步骤：在每次发布后更新 ASF（Apache 软件基金会）项目描述文件 `doap.rdf`。这是一项流程类文档改进，目的是把"更新 DOAP 文件"这一原本可能被遗漏的步骤正式写入发布手册，确保发布流程的完整性，使 Apache Iceberg 项目在 ASF 项目门户（projects.apache.org）上展示的版本信息与实际发布保持同步。

**背景**：

1. **DOAP（Description of a Project）是 ASF 用来描述旗下项目的标准 RDF 格式文件**。每个 Apache 项目仓库根目录下通常有一个 `doap.rdf`，其中包含项目名称、描述、主页、邮件列表、版本控制地址、维护者以及发布版本（`<release>` 段）等信息。ASF 的项目聚合站点 `projects.apache.org` 会定时抓取各项目的 `doap.rdf`，从而在站点上展示项目的最新版本、发布日期等元数据。

2. **如果发布后忘记更新 `doap.rdf`**，则 ASF 项目门户上展示的版本信息就会停留在旧版本，外部用户、下游集成方通过 ASF 门户查询 Iceberg 版本时就会看到过期数据。在本次提交之前，发布手册里没有明确要求更新该文件，因此依赖发布经理的个人记忆或习惯，容易遗漏。

3. **本提交与 0733 号提交（`Docs: Update doap.rdf`）形成呼应**：0731 把步骤写进流程文档，0733 则是按照该步骤对 1.5.1 版本进行实际的 `doap.rdf` 更新，二者配合补齐了"流程规范 + 实际执行"的闭环。

## 如何达成设计目的

通过在 `how-to-release.md` 文档的"Post-release tasks"区段内、紧接在"Update Github release"等步骤之后，新增一个名为 `#### Update DOAP (ASF Project Description)` 的四级小节。该小节以简短的文字说明 + 一段 XML 代码示例的形式，告诉发布经理：

1. 需要创建一个 PR 来更新 `doap.rdf` 文件；
2. 更新位置是文件中的 `<release/>` 段；
3. 给出新增 `<release>` 块的 XML 模板，包含 `name`（版本号）、`created`（发布日期，格式 `yyyy-mm-dd`）、`revision`（版本号）三个字段。

这种"文字说明 + 可复制模板"的写法让发布经理无需去翻历史 PR 或 DOAP 规范即可完成操作，降低了出错概率，也让流程文档自包含、可执行。

## 修改详情

### `site/docs/how-to-release.md`
**修改目的**：在发布流程文档中新增"更新 DOAP 文件"步骤。

**修改位置**：在文档第 303 行附近、原有"Create a PR in the `iceberg` repo to add the new version to the github issue template"和"Draft a new release to update Github"两条之后，新增 14 行内容（纯新增，无删除）。

**新增内容**：
```markdown
#### Update DOAP (ASF Project Description)

- Create a PR to update the release version in [doap.rdf](https://github.com/apache/iceberg/blob/main/doap.rdf) file, in the `<release/>` section:

```xml
    <release>
      <Version>
        <name>x.y.z</name>
        <created>yyyy-mm-dd</created>
        <revision>x.y.z</revision>
      </Version>
    </release>
```
```

**工作逻辑**：
- 小节标题使用四级标题 `####`，与同区段内其它发布后任务（如"Update Github release"）保持层级一致；
- 文字说明明确指向 `doap.rdf` 文件在 main 分支的链接，便于直接跳转；
- XML 模板使用占位符 `x.y.z` 和 `yyyy-mm-dd`，发布经理按实际版本号和发布日期替换即可；
- 模板中 `<name>` 与 `<revision>` 都填版本号，`<created>` 填发布日期，符合 DOAP 规范与 Iceberg 既有 `doap.rdf` 的写法。

**未改动部分**：文档其余部分（包括前面的 release candidate 流程、投票流程、文档发布流程等）均未改动。新增内容紧接在主版本发布后的杂项任务列表之后，属于"Post-release tasks"的延伸，位置合理。

## 小结
- **成效**：成功将"更新 `doap.rdf`"这一容易被遗漏的步骤正式纳入发布流程文档，并提供了可直接复制的 XML 模板，降低了发布经理的操作门槛和遗漏风险。该步骤在随后的 1.5.1 发布中即被实际执行（见 0733 号提交）。
- **影响范围**：仅影响 `site/docs/how-to-release.md` 一个文档文件，不涉及任何代码、构建脚本或运行时行为。影响对象是发布流程的执行者（发布经理），对普通开发者无影响。
- **回迁到 1.4.x 的注意事项**：可直接回迁，无风险。该改动是纯文档增量，不依赖任何代码上下文。回迁时需注意：
  1. 确认 1.4.x 分支的 `how-to-release.md` 在对应位置（"Update Github release"步骤之后）有相同的插入点；
  2. 若 1.4.x 分支的发布文档结构已与 main 分化（例如行号不同），需根据实际位置插入，但内容可直接复用；
  3. 该步骤本身与具体发布版本无关，回迁后对 1.4.x 后续的 patch 发布（如 1.4.x）同样适用。
