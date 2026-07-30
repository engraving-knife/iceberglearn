# 提交 0562：构建——不发布 iceberg-open-api 模块

## 提交信息

- **序号**：0562 / 4088
- **哈希**：2519ab43d654927802cc02e19c917ce90e8e0265
- **短哈希**：2519ab43d
- **日期**：2024-03-05（AuthorDate 2024-03-05 19:30:48 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Build: Don't publish iceberg-open-api module (#9871)
- **PR/Issue**：#9871

## 总体目的

本提交要阻止 `iceberg-open-api` 模块被发布到 Maven 仓库（Apache Snapshots / Maven Central），避免这个并非 Java 构件的模块在 `./gradlew publish` 或 `./gradlew -Prelease publish` 时被当作普通的 Maven artifact 上传。

**背景动机**：

- `iceberg-open-api` 模块（目录 `open-api/`）是 Iceberg 用来维护 REST Catalog OpenAPI 规范的特殊模块，内容为 `rest-catalog-open-api.yaml`（OpenAPI 规范文件）、`rest-catalog-open-api.py`（由规范生成的 Python 数据模型）、`Makefile`、`requirements.txt`、`header.txt`、`README.md` 等。它**不是**一个 Java/Groovy 工程，没有 `build.gradle`，也没有 `src/main/java` 等源码集。
- 然而该模块在 `settings.gradle` 中被 `include 'open-api'` 并 `project(':open-api').name = 'iceberg-open-api'` 注册为一个 Gradle subproject。
- `deploy.gradle` 的 `subprojects { ... }` 闭包会对**所有**子项目统一套用发布配置：`apply plugin: 'maven-publish'`、`apply plugin: 'signing'`，并在 `afterEvaluate` 中创建 `sourceJar`/`javadocJar`/`testJar` 任务、配置 `publishing.publications.apache` publication（`from components.java`）、配置 `publishing.repositories` 指向 Apache 仓库、在 release 模式下还 `sign`。
- 对一个没有 Java 源码、没有 `components.java`、没有 `javadoc` 任务的 `iceberg-open-api` 模块套用这套配置，会导致发布流程出错（找不到 `sourceSets.main`、`javadoc` 等任务）或产出空/无意义的 artifact。因此在发布前必须把这个模块排除掉。

## 如何达成设计目的

设计思路非常直接：在 `deploy.gradle` 的 `subprojects` 闭包最开头加一道**早退守卫**，遇到 `iceberg-open-api` 就 `return`，使其跳过后续所有发布相关配置。

具体地，在 `subprojects {` 之后、`def isBom = ...` 之前插入：

```groovy
if (it.name == 'iceberg-open-api') {
  // don't publish iceberg-open-api
  return
}
```

由于 Groovy 闭包中的 `return` 等价于"跳过该子项目后续语句"，这道守卫使得 `iceberg-open-api` 不会：
- 被 `apply plugin: 'maven-publish'` / `'signing'`；
- 创建 `sourceJar`/`javadocJar`/`testJar` 任务；
- 注册 `apache` publication；
- 在 release 模式下被 `sign`；
- 出现在 `publish` 任务的发布列表中。

注意这里采用的是"按模块名排除"而非"按是否是 BOM/是否有 java plugin 排除"的策略，与紧随其后的 `def isBom = it.name == 'iceberg-bom'` 风格一致，都是用模块名做特殊判断。`iceberg-bom` 仍需发布（它是 BOM artifact），只是发布方式不同；而 `iceberg-open-api` 则是彻底不发布。

## 修改详情

### `deploy.gradle`

**修改目的**：在发布配置闭包入口处把 `iceberg-open-api` 模块短路掉，使其不参与 Maven 发布。

**工作逻辑**：

原 `subprojects` 闭包开头直接 `apply plugin: 'maven-publish'`：

```groovy
subprojects {
  apply plugin: 'maven-publish'
  apply plugin: 'signing'
  afterEvaluate {
    task sourceJar(type: Jar, dependsOn: classes) { ... }
    task javadocJar(type: Jar, dependsOn: javadoc) { ... }
    ...
    publishing { publications { apache(MavenPublication) { ... } } }
    ...
  }
}
```

改后在最前面加守卫：

```groovy
subprojects {
  if (it.name == 'iceberg-open-api') {
    // don't publish iceberg-open-api
    return
  }

  def isBom = it.name == 'iceberg-bom'

  apply plugin: 'maven-publish'
  apply plugin: 'signing'
  afterEvaluate {
    ...
  }
}
```

`return` 在 Groovy 闭包语义下只是结束当前迭代（当前子项目）的闭包执行，不会终止整个 `subprojects` 遍历，因此其他子项目（api、core、aws 等）仍会正常套用发布配置。守卫放在 `def isBom` 之前，确保 `iceberg-open-api` 在任何发布相关 plugin/任务被创建之前就退出。

## 小结

本提交用 5 行代码（含注释）解决了 `iceberg-open-api` 模块被误发布的问题。改动极小但必要：`iceberg-open-api` 是纯规范/工具模块，既无 Java 构件也不应出现在 Maven Central；若不排除，`./gradlew publish` 会在该模块上因缺少 `sourceSets`/`javadoc` 等而失败，或在 `afterEvaluate` 中产生空 artifact。

**影响范围**：仅影响发布流程（`deploy.gradle` 作用于 `publish` / `sign` 链路），不改变任何运行时行为，也不影响日常构建与测试（`build` 任务不依赖 `maven-publish`）。

**回迁到 1.4.x 的注意事项**：

- 该提交本身就是在 1.4.x 维护期内的构建修复，回迁无障碍，只需保证 `settings.gradle` 中确实 `include 'open-api'` 且该模块没有 `build.gradle`（即仍是纯规范模块）即可。
- 排除逻辑按模块名 `iceberg-open-api` 硬编码，若后续重命名模块需同步更新此守卫。
- 守卫位置必须在 `apply plugin: 'maven-publish'` 之前，否则 plugin 已应用、任务已创建，再 `return` 也无法撤回已注册的 publication；当前实现满足这一要求。
