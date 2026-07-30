# 提交 2943：Build: Apply spotless for scala code (#8023)

## 提交信息

- **序号**：2943 / 4088
- **哈希**：166a6ebb343cbad85e5cc892c4fe9e880bdc0fb7
- **短哈希**：166a6ebb3
- **日期**：2025-12-02
- **作者**：Xianyang Liu
- **提交说明**：Build: Apply spotless for scala code
- **PR/Issue**：#8023

## 总体目的

这是一个工程基础设施层面的提交，目的是把 Iceberg 项目中此前缺失的 Scala 代码自动格式化（spotless + scalafmt）能力引入并一次性应用到全部 Scala 源码。

Iceberg 仓库已经有 Java 侧的 `google-java-format` 格式化基线（在 `baseline.gradle` 中通过 `spotless` 插件配置 `java { ... }` 块），但 Scala 源码长期没有格式化工具约束。这导致：

1. **风格不一致**：Spark 扩展模块（`iceberg-spark-*`）下的 Scala 代码风格因不同贡献者而异，例如参数列表换行、import 排列、长行处理等没有统一标准；
2. **review 噪音**：开发者可能为了对齐风格引入大量无谓 diff，或反过来因为风格问题被反复打回；
3. **新贡献者门槛高**：没有明确的"正确风格"参考，IDE 默认格式与社区期望可能不一致。

本提交通过新增 scalafmt 配置、在 `baseline.gradle` 的 spotless 配置中为 Scala 源码挂上 scalafmt（针对 Scala 2.12 与 2.13 使用不同方言配置）、并在文档中提示开发者安装 scalafmt 插件，一次性把约 199 个 Scala 文件按统一规则重新格式化（统计：`spark/v3.4` 90 个、`spark/v3.5` 59 个、`spark/v4.0` 50 个）。此后 CI 的 spotless check 会强制所有 Scala 改动遵循该规则，从机制上锁定风格一致性。

值得注意的是，本次为纯格式化提交，无任何逻辑改动——所有 `.scala` 文件的 diff 都只是空格、换行、参数重排，语义上等价（虽然 diff 统计显示 +2168/-1672，但均为格式差异）。

## 如何达成设计目的

整体分三步落地：

1. **配置文件**：在 `.baseline/scala/` 下新增 `.scala212fmt.conf` 与 `.scala213fmt.conf` 两份 scalafmt 配置，分别对应 Scala 2.12 与 2.13 方言，其余规则一致（`version = 3.9.7`、`maxColumn = 100`、`align = none`、`importSelectors = "singleLine"`、`docstrings.style = Asterisk` 等）。
2. **构建脚本**：在 `baseline.gradle` 的 spotless 配置块中新增 `scala { ... }` 子块，根据 `project.name` 是否以 `iceberg-spark` 开头且以 `2.12`/`2.13` 结尾，分别挂载对应配置文件的 scalafmt，并附加 license header 检查。
3. **应用格式化**：执行 spotless apply，一次性把所有匹配的 Scala 源码按新规则重排，并更新 `site/docs/contribute.md` 指引贡献者安装 scalafmt 插件并指向 `.baseline/scala/` 配置。

## 修改详情

### `.baseline/scala/.scala212fmt.conf` (+32/-0 lines, new file) 与 `.baseline/scala/.scala213fmt.conf` (+32/-0 lines, new file)

**修改目的**：为 Scala 2.12 与 2.13 各提供一份 scalafmt 配置，唯一差异是 `runner.dialect`。

**工作逻辑**：
两份配置除 `runner.dialect`（`scala212` vs `scala213`）外完全相同，核心规则：

- `version = 3.9.7`：锁定 scalafmt 版本，避免不同版本规则差异导致格式漂移；
- `maxColumn = 100`：行宽上限 100 字符，超长自动换行；
- `align = none` + `align.openParenDefnSite = false` + `align.openParenCallSite = false` + `align.tokens = []`：关闭所有对齐（不对齐参数/赋值等），减少因对齐导致的无关 diff；
- `importSelectors = "singleLine"`：import 选择器单行排列；
- `optIn.configStyleArguments = false`：不强制"配置风格"参数换行（即不强制多参数时首尾括号单独成行）；
- `danglingParentheses.preset = false`：关闭悬空括号预设（括号不强制单独成行）；
- `docstrings.style = Asterisk` + `docstrings.wrap = false`：ScalaDoc 用 Asterisk 风格且不自动换行。

两份配置分别给 2.12 与 2.13 模块使用，避免 scalafmt 用错误方言解析导致语法歧义。

### `baseline.gradle` (+15/-0 lines)

**修改目的**：在 spotless 配置中为 `iceberg-spark` 系列模块的 Scala 源码挂载 scalafmt。

**工作逻辑**：
在已有的 `java { ... }` 块之后，新增按模块名分流的 `scala { ... }` 块：

```groovy
if (project.name.startsWith("iceberg-spark") && project.name.endsWith("2.13")) {
  scala {
    target 'src/**/*.scala'
    scalafmt("3.9.7").configFile("$rootDir/.baseline/scala/.scala213fmt.conf")
    licenseHeaderFile "$rootDir/.baseline/copyright/copyright-header-java.txt", "package"
  }
} else if (project.name.startsWith("iceberg-spark") && project.name.endsWith("2.12")) {
  scala {
    target 'src/**/*.scala'
    scalafmt("3.9.7").configFile("$rootDir/.baseline/scala/.scala212fmt.conf")
    licenseHeaderFile "$rootDir/.baseline/copyright/copyright-header-java.txt", "package"
  }
}
```

要点：
- 仅对 `iceberg-spark` 前缀且 `2.12`/`2.13` 后缀的模块生效（即各 Spark 版本的扩展模块），不波及非 Spark 模块；
- `target 'src/**/*.scala'` 限定主源码集，不格式化 generated/测试目录（取决于 spotless 默认 target，但这里显式指定）；
- 复用 Java 同款的 license header 文件，但通过第二个参数 `"package"` 告诉 spotless license header 后紧跟 `package` 声明（Scala 文件用 `package` 而非 Java 的 `package`/`import`）；
- scalafmt 版本与配置文件版本一致（3.9.7），保证构建期与 IDE 期格式一致。

### `site/docs/contribute.md` (+2/-0 lines)

**修改目的**：在贡献者文档中补充 scalafmt 插件安装指引。

**工作逻辑**：
在已有 google-java-format 安装指引段落后，追加：

```markdown
Follow the [instructions](https://scalameta.org/scalafmt/docs/installation.html) to install **scalafmt** plugin
and configure it to point to the configuration file located under the directory `.baseline/scala/`.
```

提示贡献者安装 scalafmt 插件并把 IDE 配置指向 `.baseline/scala/` 目录下的配置文件，从而本地编辑时即可获得与 CI 一致的格式化结果，避免本地格式与 CI check 不符导致反复失败。

### 199 个 Scala 源码文件（合计 +2019/-1672 lines）

**修改目的**：把所有 `iceberg-spark` 模块的 Scala 源码一次性按新 scalafmt 规则重新格式化。

**工作逻辑**：
受影响文件分布：`spark/v3.4` 90 个、`spark/v3.5` 59 个、`spark/v4.0` 50 个。所有改动均为格式化结果，无语义变化。典型格式化模式（以 `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/CheckViews.scala` 与 `execution/datasources/v2/CreateV2ViewExec.scala` 为代表）：

1. **删除 license header 后的空行**：原文件在 `*/` 注释结束与 `package` 之间有一个空行，scalafmt 删除之，使 header 紧贴 package；
2. **长参数列表换行**：原 `case CreateIcebergView(resolvedIdent@..., _, query, columnAliases, _, _, _, _, replace, _) =>` 一行放不下，被拆成每个参数独占一行并缩进对齐；
3. **长调用换行**：如 `SchemaUtils.checkColumnNameDuplication(...)`、`String.format(...)` 等超 100 列的调用被拆成多行，每个参数独占一行；
4. **类定义参数换行**：`case class CreateV2ViewExec(...)` 的 11 个构造参数从单行/紧凑排列改为每参数一行、`extends` 另起一行；
5. **删除多余空行**：如 package 与首个 import 之间、import 与 class 之间的多余空行被规范化；
6. **import 选择器单行化**：受 `importSelectors = "singleLine"` 影响，`scala.jdk.CollectionConverters._` 等保持单行。

这些改动均符合上述 `.scala*fmt.conf` 的规则定义，且与 IDE 装 scalafmt 插件后指向同一配置产生的结果一致。其余文件（如 `RewriteMergeIntoTable.scala`、`IcebergSqlExtensionsAstBuilder.scala`、`ExtendedSimplifyConditionalsInPredicate.scala`、`CreateOrReplaceBranchExec.scala`、`DropPartitionFieldExec.scala`、各 `*View*` 命令、`OrderAwareCoalesce` 系列等）的改动也均属同类格式化（参数换行、长行折行、空行清理、license header 后空行删除），不再逐文件展开。

## 总结

本次提交为 Iceberg 引入 Scala 代码的自动格式化基线：新增 scalafmt 3.9.7 配置（2.12/2.13 各一份）、在 `baseline.gradle` 的 spotless 配置中按模块名分流挂载、更新贡献者文档指引插件安装，并一次性把约 199 个 Spark 扩展模块的 Scala 源码按统一规则重新格式化。这是一次纯基础设施/风格统一提交，无任何逻辑改动，但为后续 Scala 代码贡献建立了可机器强制的一致性约束，显著降低风格相关的 review 噪音与本地与 CI 的格式偏差。
