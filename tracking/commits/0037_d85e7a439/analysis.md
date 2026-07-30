# 提交 0037：Build: Bump guava from 32.1.1-jre to 32.1.3-jre (#8777)

## 提交信息

- **序号**：0037 / 4088
- **哈希**：d85e7a439f4498406db2677b9530f55675a0eaad
- **短哈希**：d85e7a439
- **日期**：2023-10-11 10:09:42 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump guava from 32.1.1-jre to 32.1.3-jre (#8777)
- **PR/Issue**：#8777

## 总体目的

这个提交由 Dependabot 自动生成，把 Iceberg 依赖的 Google Guava 从 `32.1.1-jre` 升级到 `32.1.3-jre`，属于 `version-update:semver-patch`（修订号升级），同时覆盖 `com.google.guava:guava` 与 `com.google.guava:guava-testlib` 两个 artifact，风险等级低。

Guava 是 Google 维护的 Java 核心工具库，在 Iceberg 中地位极其特殊：它是**全仓库最底层的基础设施**。Iceberg 通过 `iceberg-bundled-guava` 模块把 Guava 重打包（shade + relocate）到私有命名空间 `org.apache.iceberg.relocated.com.google.common.*`，与宿主引擎（Spark/Flink/Hive）自带的 Guava 完全隔离，从而避免 Java 生态最常见的依赖冲突。几乎所有 Iceberg 模块（api/common/core/parquet/orc/spark/flink/aws/azure 等）都通过 `project(path: ':iceberg-bundled-guava', configuration: 'shadow')` 方式间接使用重打包后的 Guava，代码中大量使用 `Preconditions`、`ImmutableMap`、`ImmutableList`、`Lists`、`Joiner`、`Hashing`、`Maps`、`Sets` 等工具类。

Guava 32.1.x 是 32 系列的 patch 版本线。32.0.0 之前曾修复过安全相关问题，32.1.1 到 32.1.3 之间的差异主要是 bug 修复与小改进，无破坏性 API 变更。由于 Iceberg 通过 relocate 隔离使用 Guava，升级只影响 Iceberg 自身 shade 后的产物，不会泄漏到宿主引擎的 classpath，因此兼容性风险被进一步降低。

## 如何达成设计目的

改动极简：仅在 Gradle 版本目录 [`gradle/libs.versions.toml`](../../../../gradle/libs.versions.toml) 中把 `guava = "32.1.1-jre"` 改为 `guava = "32.1.3-jre"`。`guava-testlib` 在版本目录中以 `version.ref = "guava"` 引用同一版本常量，因此 guava 与 guava-testlib 会同步升级，保持版本对齐。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Google Guava 的版本从 32.1.1-jre 升级到 32.1.3-jre，同时同步升级 guava-testlib。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区（约第 26 行），原行 `guava = "32.1.1-jre"` 被改为 `guava = "32.1.3-jre"`。该版本常量在 Iceberg 构建中被以下关键位置引用：

- `iceberg-bundled-guava` 模块（[`build.gradle:256`](../../../../build.gradle) `compileOnly(libs.guava.guava)`）：作为重打包的源头，Guava 字节码被打入 shadow jar 并 relocate 到 `org.apache.iceberg.relocated.com.google.common.*`，供所有上层模块使用。
- `iceberg-core` 测试（[`build.gradle:364`](../../../../build.gradle) `testImplementation libs.guava.testlib`）：guava-testlib 提供测试用的工具类（如 `EquivalenceTester`、`Tester` 系列基类），仅在测试范围使用，不打入 shadow jar。

升级后，重新构建 `:iceberg-bundled-guava` 的 shadow jar 会内嵌 32.1.3-jre 的字节码，所有通过 relocated 包名引用 Guava 的模块自动获得新版本的 bug 修复。需要注意的是，若新增使用了 `GuavaClasses` 清单外的 Guava 类，需同步增补该清单（见 [`bundled-guava/src/main/java/org/apache/iceberg/GuavaClasses.java`](../../../../bundled-guava/src/main/java/org/apache/iceberg/GuavaClasses.java)），否则会被 `minimize()` 裁剪掉。

## 小结

该提交由 Dependabot 将 Google Guava 从 32.1.1-jre patch 升级到 32.1.3-jre，使 Iceberg 的核心工具库（经 bundled-guava 重打包后供全仓库使用）跟进上游 bug 修复版本，因属 patch 升级且经 relocate 隔离，风险极低。
