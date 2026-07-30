# 提交 0362：Build: Add `iceberg-bom` artifact (#8065)

## 提交信息

- **序号**：0362
- **哈希**：13e108b370716f1589f7df1177ac3c88415ddbf9
- **短哈希**：13e108b37
- **日期**：2024-01-16 09:52:22 +0100
- **作者**：Robert Stupp
- **提交说明**：Build: Add `iceberg-bom` artifact (#8065)
- **PR/Issue**：#8065
- **作者注**：BOMs are pretty useful to align dependencies of a project. Their usage is optional though.

## 总体目的

本提交为 Iceberg 构建系统新增一个 `iceberg-bom`（Bill of Materials）工件，让下游用户在依赖 Iceberg 时只需在 Maven `dependencyManagement` 中导入一个 BOM 工件，即可自动对齐所有 Iceberg 模块的版本，避免逐个声明版本号导致的版本错配。BOM 是 Maven 生态中的一种特殊 POM 工件，本身不包含任何代码/JAR，只在 `<dependencyManagement>` 段列出"一组受管理的依赖坐标 + 版本"；下游在 `pom.xml` 中以 `<scope>import</scope>` 引入该 BOM 后，即可在自己的 `<dependencies>` 中省略各 Iceberg 模块的 `<version>`，由 BOM 统一裁决版本。在 Gradle 侧，等价机制是 `java-platform` 插件，它产出 `*.pom`（而非 `*.jar`），其中以 `<dependencyManagement>` 形式声明对其他工件的版本约束（constraint）。本提交的核心就是把 `iceberg-bom` 作为一个新的 Gradle 子项目加入构建，应用 `java-platform` 插件，枚举所有 Iceberg 工件作为约束，并通过 Maven 发布流程把它发布为 `iceberg-bom` 工件。

Iceberg 工件矩阵有一个独特的复杂性：Spark 适配工件按"Spark 版本 × Scala 版本"双维度切分，例如 `iceberg-spark-3.4_2.12` 与 `iceberg-spark-3.4_2.13` 是两个独立工件。然而 Iceberg 的 Gradle 构建是按单一 Scala 版本运行的（通过 `-PscalaVersion=2.12` 或 `2.13` 切换），一次构建只产出一组 Spark/Scala 工件。若 BOM 简单地把当前构建的工件列表写入约束，发布出去的 BOM 就只会引用当前构建所对应的单一 Scala 版本，下游用 2.13 的项目导入该 BOM 时若 BOM 是用 2.12 构建的，就找不到对应工件。本提交通过在 `build.gradle` 中硬编码 Spark/Scala 版本矩阵（`3.3`/`3.4`/`3.5` × `2.12`/`2.13`），并在生成 BOM 约束时用正则解析当前 Spark 工件名、对每个 Spark 版本同时为两个 Scala 版本合成约束坐标，从而让"一次构建产出的 BOM"覆盖所有 Spark/Scala 组合。这是一种"在发布时虚构未实际构建的工件坐标"的手法——BOM 只是坐标清单，不需要这些工件在当前构建中真实存在，只要它们在 Maven Central 上（由其他构建运行发布）存在即可。

附带地，由于 `iceberg-bom` 是一个"无源码"项目（只有 `java-platform` 插件，没有 `java-library`、没有 source set、没有 Javadoc），它与 Iceberg 现有构建脚本中大量"对所有 subprojects 假定有源码"的逻辑冲突。因此本提交在多个 `.gradle` 文件中对 `iceberg-bom` 做特判短路（early return / `if (!isBom)`），让它跳过 `java-library` 插件应用、Baseline/Spotless 代码风格检查、sourceJar/javadocJar/testJar 任务、Revapi API 兼容性检查、aggregateJavadoc 聚合等不适用步骤，并在发布配置中改用 `components.javaPlatform`（而非 `components.java` 或 shadow）作为发布内容。这些适配性修改让 BOM 项目能干净地嵌入既有多模块构建而不引发插件报错。

## 如何达成设计目的

实现上分四步：(1) **注册子项目**——在 `settings.gradle` 中 `include 'bom'` 并 `project(':bom').name = 'iceberg-bom'`，与既有 `:api`→`iceberg-api`、`:core`→`iceberg-core` 的命名重写模式一致，让 Gradle 内部路径 `:bom` 与发布工件名 `iceberg-bom` 解耦。(2) **配置 BOM 内容**——在 `build.gradle` 末尾新增 `project(':iceberg-bom') { ... }` 块，应用 `java-platform` 插件，在 `dependencies { constraints { ... } }` 中遍历所有叶子子项目（排除 BOM 自身、root、`:iceberg-spark` 父项目），对非 Spark 项目直接 `add("api", project(it.path))` 加入项目依赖约束，对 Spark 项目用正则 `~"(.*)-([0-9][.][0-9]+)_([0-9][.][0-9]+)"` 解析出"项目名前缀/Spark 版本/Scala 版本"三段，再查 `sparkScalaVersions` 表为每个 Scala 版本合成字符串坐标 `${it.group}:$prjName-${sparkVer}_$scalaVer:${it.version}` 加入约束；最后调用 `javaPlatform { allowDependencies() }` 允许平台声明真正的 dependencies（而不仅是 constraints），这是把"虚构的 Scala 工件坐标"写进 BOM 的必要开关。(3) **隔离无源码项目**——在 `build.gradle`、`baseline.gradle`、`tasks.gradle` 的 `subprojects { ... }` 顶部加 `if (it.name == 'iceberg-bom') return`，让 BOM 跳过 `java-library`、Baseline、aggregateJavadoc 等需要源码的流程。(4) **定制发布**——在 `deploy.gradle` 中用 `def isBom = it.name == 'iceberg-bom'` 标记，把 `sourceJar`/`javadocJar`/`testJar`/`artifacts`/LICENSE/NOTICE 等任务包裹在 `if (!isBom)` 内，并在 `publishing` 中对 BOM 改用 `from components.javaPlatform`，对其他项目保持原 `components.java`/`shadow` 路径。这样 BOM 既被纳入统一发布流程，又只发布 `java-platform` 产生的 POM（无 JAR、无 sources、无 javadoc）。

## 修改详情

### `settings.gradle`

**修改目的**：把 `bom` 注册为新的 Gradle 子项目，并重命名工件名为 `iceberg-bom`。

**工作逻辑**：在 `rootProject.name = 'iceberg'` 之后的 `include` 列表最前面新增 `include 'bom'`（放在 `api`/`common`/`core` 等之前，按字母序保持整洁）；在 `include` 列表之后新增 `project(':bom').name = 'iceberg-bom'`，与既有 `project(':api').name = 'iceberg-api'` 等逐行对齐。这让 Gradle 内部用 `:bom` 路径引用该子项目（与 `:api`、`:core` 风格一致），而发布到 Maven 仓时工件名是 `iceberg-bom`。把 `include 'bom'` 放在列表头部是因为 BOM 在概念上是"总纲"，下游先导入 BOM 再引入具体模块，置于列表前部符合阅读直觉。

### `build.gradle`

**修改目的**：(a) 让 BOM 子项目跳过 `java-library` 等"需要源码"的通用 subprojects 配置；(b) 在文件末尾新增 `project(':iceberg-bom')` 配置块，应用 `java-platform` 插件并生成约束清单。

**工作逻辑**：
- **顶部 `subprojects { ... }` 短路**：在 `subprojects {` 之后、`apply plugin: 'java-library'` 之前插入 `if (it.name == 'iceberg-bom') { return }`——BOM 没有 `main`/`test` source set，应用 `java-library` 会触发"source set not found"类错误；后续的 Revapi、checkstyle、依赖解析等也都不适用。`return` 让该 subproject 闭包对 BOM 提前退出，相当于"对 BOM 不做任何 subprojects 通用配置"。
- **末尾 `project(':iceberg-bom')` 配置块**（38 行，本提交核心）：
  - `apply plugin: 'java-platform'`——应用 Gradle 的 `java-platform` 插件，它是产出 BOM（Maven `dependencyManagement` POM）的官方插件，对应任务 `javaPlatform` 生成 `*.pom`。
  - `dependencies { constraints { ... } }`——在约束块中遍历 `rootProject.allprojects`，对每个叶子项目（`it.name != 'iceberg-bom' && it != rootProject && it.childProjects.isEmpty()`）生成约束：
    - 非 Spark 项目：`add("api", project(it.path))`——直接用项目路径引用，Gradle 会解析为 `group:name:version`。
    - Spark 项目（`it.name.startsWith("iceberg-spark-")`）：用正则 `sparkScalaPattern = ~"(.*)-([0-9][.][0-9]+)_([0-9][.][0-9]+)"` 匹配，提取项目名前缀（如 `iceberg-spark`）、Spark 版本（如 `3.4`）、当前 Scala 版本（如 `2.12`）；若不匹配则抛 `GradleException`（防止命名约定漂移无人察觉）；然后查 `sparkScalaVersions = ["3.3": ["2.12", "2.13"], "3.4": ["2.12", "2.13"], "3.5": ["2.12", "2.13"]]` 表，对每个 Scala 版本 `add("api", "${it.group}:$prjName-${sparkVer}_$scalaVer:${it.version}")` 合成约束坐标。例如当前构建用 Scala 2.12 构建出 `iceberg-spark-3.4_2.12`，BOM 会同时写入 `iceberg-spark-3.4_2.12` 与 `iceberg-spark-3.4_2.13` 两条约束，让下游不论用哪个 Scala 版本都能从 BOM 拿到正确版本号。这是 BOM 覆盖"未在本构建中产出但已发布到 Maven Central"工件的关键手法。
  - `javaPlatform { allowDependencies() }`——默认 `java-platform` 插件只允许在 `constraints` 中声明"约束"（下游实际引入时才生效，且下游可覆盖版本），不允许在 `dependencies` 中声明"强依赖"（下游必须引入）。`allowDependencies()` 解除该限制，让 BOM 也可以含真正的 dependencies。注释 "Needed to get the 'faked' Scala artifacts into the bom" 说明：虚构的 Scala 工件坐标（当前构建未产出）需要作为 dependencies 而非仅 constraints 写入，否则下游导入 BOM 时这些虚构坐标不会被解析。实际上这里把它打开是保险措施，确保所有约束都被正确写入 POM 的 `<dependencyManagement>` 段。

### `baseline.gradle`

**修改目的**：让 BOM 子项目跳过 Baseline/Spotless 代码风格检查。

**工作逻辑**：在 `subprojects {` 之后立即新增 `if (it.name == 'iceberg-bom') { return }`，注释 "the BOM does not build anything, the below plugins are not necessary (and can fail)"。Baseline 插件（含 Spotless、Checkstyle、Baseline-Config）会对 `src/main/java` 等源码目录做风格检查，BOM 项目没有这些目录，应用 Baseline 会触发"no source"类错误或无意义地空跑。短路 return 让 BOM 完全不参与 Baseline 流程。

### `deploy.gradle`

**修改目的**：让 BOM 子项目跳过 sourceJar/javadocJar/testJar 等 JAR 任务，并在 Maven 发布时改用 `javaPlatform` 组件。

**工作逻辑**：
- 顶部 `subprojects {` 内新增 `def isBom = it.name == 'iceberg-bom'` 局部变量，作为整段发布配置的判断开关。
- 把原本无条件执行的 `sourceJar`/`javadocJar`/`testJar` 任务定义、`artifacts { archives ... }` 注册、以及 `[jar, sourceJar, javadocJar, testJar].each { ... }` 注入 LICENSE/NOTICE 的循环，整体包裹进 `if (!isBom) { ... }`——BOM 没有 `classes`、`javadoc`、`test.output`，定义这些 Jar 任务会失败，也没有意义（BOM 只发布 POM）。
- 在 `publishing { publications { apache(MavenPublication) { ... } } }` 中改写发布内容选择逻辑：原 `if (tasks.matching({task -> task.name == 'shadowJar'}).isEmpty()) { from components.java } else { project.shadow.component(it) }` 改为先判 `if (isBom) { from components.javaPlatform } else { <原逻辑> }`。`components.javaPlatform` 是 `java-platform` 插件提供的 `SoftwareComponent`，对应 POM 工件；`components.java` 是 `java-library` 插件提供的 JAR 工件；`project.shadow.component(it)` 是 shadow 插件用于 fat-jar 工件。BOM 走 `javaPlatform` 分支后，后续的 `artifact sourceJar`/`artifact javadocJar`/`artifact testJar` 与 `versionMapping` 块也被 `else` 分支隔离，不会对 BOM 执行（这些 artifact 在 BOM 上不存在）。

### `tasks.gradle`

**修改目的**：让 `aggregateJavadoc` 聚合 Javadoc 任务跳过 BOM 子项目。

**工作逻辑**：原 `task aggregateJavadoc(type: Javadoc) { dependsOn subprojects.javadoc; source subprojects.javadoc.source; classpath = ... subprojects.javadoc.classpath }` 改为先计算 `def javadocTasks = subprojects.findAll { it.name != 'iceberg-bom' }.javadoc`，再用 `dependsOn javadocTasks`、`source javadocTasks.source`、`classpath = ... javadocTasks.classpath`。`subprojects.javadoc` 是 Gradle 的"集合属性聚合"语法，会对每个子项目取 `javadoc` 任务；BOM 项目没有 `javadoc` 任务（未应用 `java-library`），聚合会触发 "task not found" 错误。先用 `findAll` 过滤掉 BOM，再取 `.javadoc`，确保聚合只对有 Javadoc 的项目执行。

## 小结

本次提交为 Iceberg 构建系统引入 `iceberg-bom` 工件，让下游用户通过 Maven BOM 导入机制一次性对齐所有 Iceberg 模块版本。实现上在 `settings.gradle` 注册 `:bom` 子项目并重命名为 `iceberg-bom`，在 `build.gradle` 末尾应用 `java-platform` 插件、用 `constraints` 块枚举所有叶子工件坐标——其中对 Spark 工件用正则解析 + 硬编码 `sparkScalaVersions` 矩阵合成"当前构建未产出但已发布"的 Scala 变体坐标，让单次构建产出的 BOM 覆盖所有 Spark/Scala 组合；`javaPlatform { allowDependencies() }` 确保"虚构"的 Scala 工件能写入 POM。同时为 BOM 这个"无源码"项目在 `build.gradle`/`baseline.gradle`/`tasks.gradle` 的 `subprojects` 块加 early-return 短路，跳过 `java-library`、Baseline、aggregateJavadoc 等不适用流程；在 `deploy.gradle` 用 `isBom` 标志把 sourceJar/javadocJar/testJar 任务与 `components.java`/shadow 发布路径隔离，改用 `components.javaPlatform` 发布 POM。这是一次典型的"在多模块 Gradle 构建中嵌入 BOM 项目"的工程实践，处理了 BOM 与既有源码导向构建流程的所有冲突点，并巧妙解决了 Spark/Scala 双维度工件矩阵的 BOM 覆盖问题。BOM 的使用是可选的，不强制下游采纳，但对需要严格版本对齐的企业用户是有价值的便利设施。
