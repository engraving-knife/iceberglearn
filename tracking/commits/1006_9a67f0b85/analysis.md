# 提交 1006：Drop support for Java 8 (#10518)

## 提交信息

- **序号**：1006 / 4088
- **哈希**：9a67f0b85c82ae09089e150f2c663a65f145670e
- **短哈希**：9a67f0b85
- **日期**：2024-08-02 09:23:23 +0200
- **作者**：Piotr Findeisen
- **提交说明**：Drop support for Java 8 (#10518)
- **PR/Issue**：#10518

## 总体目的

Iceberg 长期以来同时支持 Java 8/11/17/21 四个 LTS 版本作为构建与运行时 JDK。Java 8 已于 2019 年 1 月停止公开更新（Oracle），社区维护也日趋萎缩；同时 Iceberg 周边的依赖（如 Nessie 的 in-JVM 测试组件）早已要求 Java 11+，导致 Iceberg 在 Java 8 下不得不通过条件分支禁用部分模块的测试，增加了构建脚本的复杂度与维护成本。维护 Java 8 还意味着代码中无法使用 Java 9+ 引入的语言与 API 改进（如 `List.of`、`Map.of`、`var`、Records 等）。

本提交的目的是正式停止对 Java 8 的支持，将最低支持 JDK 提升到 Java 11。这涵盖了：

1. 移除所有 GitHub Actions CI 工作流中的 JDK 8 矩阵项。
2. 移除 `build.gradle` 中针对 Java 8 的分支与 Nessie 模块"Java 8 下禁用测试"的条件逻辑。
3. 把发布（release）构建所要求的 JDK 由 8 改为 11。
4. 同步更新 README、contribute 文档、JMH 与 hive-runtime 等构建脚本中的 JDK 描述。
5. 简化 `tasks.gradle` 中 Javadoc 搜索修复的 Java 11+ 条件判断（现在必然为真）。

## 如何达成设计目的

实现方式是逐文件移除 Java 8 相关的分支与条件，并把"Java 8 / 11 / 17 / 21"的表述统一改为"Java 11 / 17 / 21"。具体策略：

1. CI 工作流：把 `matrix.jvm` 由 `[8, 11, 17, 21]` 改为 `[11, 17, 21]`，并相应调整 `publish-snapshot.yml` 中发布用的 `java-version` 由 8 改为 11。
2. `build.gradle`：删除 `JavaVersion.VERSION_1_8` 分支，把"必须使用 JDK 8/11/17/21"的报错信息改为"11/17/21"；删除 `iceberg-nessie` 子项目中"Java 8 下禁用 test/compileTestJava"的 else 分支，仅保留 Java 11+ 的逻辑。
3. `deploy.gradle`：把 release 构建的 JDK 校验由 `jdkVersion != '8'` 改为 `jdkVersion != '11'`，即发布必须用 JDK 11。
4. `hive-runtime/build.gradle`：删除 `jdkVersion == '8'` 的前置条件，直接按 hive 版本包含 iceberg-hive3。
5. `jmh.gradle`：把允许的 JDK 列表去掉 8。
6. `tasks.gradle`：`aggregateJavadoc` 中删除 `JavaVersion.current() >= JavaVersion.VERSION_11` 判断，直接执行 Javadoc 搜索修复。
7. 文档：README 与 `site/docs/contribute.md` 中"Java 8, 11, 17, or 21"改为"Java 11, 17, or 21"。

## 修改详情

### `.github/workflows/*.yml`（delta-conversion-ci、flink-ci、hive-ci、java-ci、publish-snapshot、spark-ci）

**修改目的**：从 CI 矩阵中移除 JDK 8，发布快照改用 JDK 11。

**工作逻辑**：各工作流的 `matrix.jvm` 由 `[8, 11, 17, 21]` 改为 `[11, 17, 21]`；`publish-snapshot.yml` 中 `java-version: 8` 改为 `java-version: 11`。

### `build.gradle`

**修改目的**：移除 Java 8 的构建分支与 Nessie 模块的条件禁用逻辑。

**工作逻辑**：
- 顶层 JDK 检测中删除 `if (JavaVersion.current() == JavaVersion.VERSION_1_8) { jdkVersion = '8'; extraJvmArgs = [] }` 分支，直接从 VERSION_11 开始判断；异常信息由 "JDK 8 or 11 or 17 or 21" 改为 "JDK 11 or 17 or 21"。
- `iceberg-nessie` 子项目删除 `if (JavaVersion.current().isJava11Compatible()) { ... } else { // Java 8 禁用 test 与 compileTestJava }` 的条件，直接保留 Java 11+ 的 test/compileTestJava 配置与依赖声明。

### `deploy.gradle`

**修改目的**：将 release 构建所要求的 JDK 从 8 改为 11。

**工作逻辑**：`if (project.hasProperty('release') && jdkVersion != '8')` 改为 `jdkVersion != '11'`，错误信息由 "Releases must be built with Java 8" 改为 "with Java 11"。

### `hive-runtime/build.gradle`

**修改目的**：不再为 Java 8 跳过 hive3 模块依赖。

**工作逻辑**：`if (jdkVersion == '8' && hiveVersions.contains("3"))` 改为 `if (hiveVersions.contains("3"))`，使 hive3 在所有支持的 JDK 下都被包含进 hive-runtime。

### `jmh.gradle`

**修改目的**：JMH 基准测试不再支持 JDK 8。

**工作逻辑**：允许的 JDK 列表去掉 `'8'`，异常信息由 "JDK 8 or JDK 11 or JDK 17 or JDK 21" 改为 "JDK 11 or JDK 17 or JDK 21"。

### `tasks.gradle`

**修改目的**：简化 `aggregateJavadoc` 中 Javadoc 搜索修复的 JDK 版本判断。

**工作逻辑**：删除 `if (JavaVersion.current() >= JavaVersion.VERSION_11)` 外层判断，直接执行 `searchScript.append JAVADOC_FIX_SEARCH_STR`（因为最低 JDK 已是 11，条件恒真）。

### `README.md` 与 `site/docs/contribute.md`

**修改目的**：同步文档中关于构建 JDK 的描述。

**工作逻辑**：将 "Iceberg is built using Gradle with Java 8, 11, 17, or 21." 改为 "Iceberg is built using Gradle with Java 11, 17, or 21."

## 小结

- **成效**：正式移除 Java 8 支持，最低构建/运行 JDK 提升到 Java 11。CI 矩阵、构建脚本、发布校验、JMH、Javadoc、文档全部同步更新，并简化了 Nessie 模块原本为 Java 8 准备的条件禁用逻辑。
- **影响范围**：仅构建与 CI 配置层面，13 个文件、+38/-56 行，无生产 Java 代码变更。
- **回迁到 1.4.x 的注意事项**：**不建议回迁**。1.4.x 作为已发布的维护分支，其发布产物（如 1.4.x jar）应保持与发布时声明的 JDK 兼容性（即仍支持 Java 8）。把 main 分支"移除 Java 8 支持"回迁到 1.4.x 会改变 1.4.x 的最低 JDK 要求，破坏下游用户在 Java 8 上的使用。1.4.x 应保留 Java 8 支持直至分支生命周期结束。如果 1.4.x 上确有 Java 8 相关的构建脚本 bug 需要修复，应单独处理，不要整体回迁此提交。
