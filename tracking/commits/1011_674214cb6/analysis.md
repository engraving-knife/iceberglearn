# 提交 1011：Build: Update baseline gradle plugin to 5.58.0 (#10788)

## 提交信息

- **序号**：1011 / 4088
- **哈希**：674214cb6bbc8f6e739245293ba31f9a3d36114e
- **短哈希**：674214cb6
- **日期**：2024-08-02 15:44:46 +0200
- **作者**：Piotr Findeisen
- **提交说明**：Build: Update baseline gradle plugin to 5.58.0 (#10788)
- **PR/Issue**：#10788

## 总体目的

Iceberg 使用 Palantir 的 `gradle-baseline-java` 插件来统一静态分析（包括 errorprone 检查、类唯一性、可复现构建等）。原版本 `4.42.0` 是 Palantir Baseline 最后一个支持 Java 8 的版本，存在两个遗留问题：

1. 它依赖一个旧版本的 errorprone，与 Gradle 8 不兼容，因此 Iceberg 不得不在 `build.gradle` 中额外引入 `net.ltpt.gradle:gradle-errorprone-plugin:3.1.0` 来覆盖（bump）errorprone 版本，让构建在 Gradle 8 下能跑通。这增加了构建配置的复杂度。
2. 它的 errorprone 检查规则集较旧，缺少一些社区新加入的有价值检查。

随着 Iceberg 在提交 1005 中正式放弃 Java 8 支持，最低 JDK 提升到 11，现在可以使用 Palantir Baseline 5.x 系列（要求 Java 11+）。本提交的目的就是把 `gradle-baseline-java` 从 4.42.0 升级到 5.58.0，并清理因升级带来的副作用：

- 移除 `build.gradle` 中为旧版本 workaround 而引入的 `gradle-errorprone-plugin:3.1.0` 单独声明，因为 5.58.0 自带兼容 Gradle 8 的 errorprone。
- 在 `baseline.gradle` 中针对 5.58.0 新引入（或行为变化）的 errorprone 检查配置策略：把三个新检查暂时降级为 WARN（带 TODO 链接，待后续评估是否调整为代码或永久 suppress），把两个会产生误报或异常的检查直接 OFF。
- 修复因新版本 `MissingSummary` 检查更严格而报错的若干 Javadoc——这些 Javadoc 原本只有 `@return` 标签而缺少摘要句，新检查要求 Javadoc 必须以摘要句开头，因此在 `core` 与 `flink/v1.17/v1.18/v1.19` 的多个类中补全摘要句。

## 如何达成设计目的

设计思路分三步：

1. **升级插件版本并清理 workaround**：在 `build.gradle` 的 `buildscript.dependencies` 中把 `gradle-baseline-java:4.42.0` 改为 `5.58.0`，并删除 `gradle-errorprone-plugin:3.1.0`（不再需要单独 bump errorprone）。
2. **配置新版本的 errorprone 检查**：在 `baseline.gradle` 的 errorprone 选项列表中新增五条配置：
   - `DangerousJavaDeserialization:WARN`、`FormatStringAnnotation:WARN`、`ImmutablesReferenceEquality:WARN`：三个新检查先降级为 WARN，每条都带 TODO issue 链接（#10853、#10854、#10855），表示后续会评估是调整代码还是永久 suppress。
   - `PreferCommonAnnotations:OFF`：因为 Iceberg 使用了 relocated 的 `@VisibleForTesting`（包名被重定位），该检查会误报，直接关闭。
   - `UnnecessarilyQualified:OFF`：Palantir 的该检查在分析时可能抛异常，直接关闭。
3. **修复被新检查报错的源代码 Javadoc**：在 `StaticDataTask.java`、`KeyAssignment.java`（v1.17/v1.18/v1.19 三份）、`MapAssignment.java`（v1.17/v1.18/v1.19 三份）共 7 个文件中，把原本形如 `/** @return ... */` 的 Javadoc 改为带摘要句的标准格式 `/** Summary. \n * @return ... */`，满足 `MissingSummary` 检查要求。

## 修改详情

### `build.gradle`

**修改目的**：升级 Palantir Baseline 插件版本并移除旧版 workaround。

**工作逻辑**：`buildscript.dependencies` 中：
- `classpath 'com.palantir.baseline:gradle-baseline-java:4.42.0'` 改为 `5.58.0`。
- 删除 `classpath "net.ltpt.gradle:gradle-errorprone-plugin:3.1.0"` 及其上方解释为何要 bump errorprone 的注释（"the last version supporting Java 8 pulls in an old version of the errorprone, which doesn't work w/ Gradle 8"）。5.58.0 自带兼容 Gradle 8 的 errorprone，无需再单独引入。

### `baseline.gradle`

**修改目的**：针对 5.58.0 引入的新 errorprone 检查配置策略。

**工作逻辑**：在 errorprone 选项数组中新增五项：
- `-Xep:DangerousJavaDeserialization:WARN`，带 TODO 链接 #10853，说明这是新加入的检查，待评估。
- `-Xep:FormatStringAnnotation:WARN`，带 TODO 链接 #10854，同上。
- `-Xep:ImmutablesReferenceEquality:WARN`，带 TODO 链接 #10855，同上。
- `-Xep:PreferCommonAnnotations:OFF`，注释说明"Triggers false-positives whenever relocated @VisibleForTesting is used"。
- `-Xep:UnnecessarilyQualified:OFF`，注释说明"Palantir's UnnecessarilyQualified may throw during analysis"。

### `core/src/main/java/org/apache/iceberg/StaticDataTask.java`

**修改目的**：修复 `MissingSummary` 检查报错。

**工作逻辑**：`tableRows()` 方法的 Javadoc 由 `/** @return the table rows before projection */` 改为
```java
/**
 * Returns the table rows before projection.
 *
 * @return the table rows before projection
 */
```
即补上摘要句"Returns the table rows before projection."。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/KeyAssignment.java`
### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/KeyAssignment.java`
### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/KeyAssignment.java`

**修改目的**：同上，修复 `MissingSummary` 检查报错（三个 Flink 版本目录的相同文件）。

**工作逻辑**：`select()` 方法的 Javadoc 由 `/** @return subtask id */` 改为
```java
/**
 * Select a subtask for the key.
 *
 * @return subtask id
 */
```

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapAssignment.java`
### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapAssignment.java`
### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapAssignment.java`

**修改目的**：同上，修复 `MissingSummary` 检查报错（三个 Flink 版本目录的相同文件）。

**工作逻辑**：在原本以 `@return` 开头的 Javadoc 前补一行摘要句"Returns assignment summary for every subtask."。

## 小结

- **成效**：将 Palantir Baseline 插件从 4.42.0（最后一个支持 Java 8 的版本）升级到 5.58.0，移除了为旧版本引入的 `gradle-errorprone-plugin` workaround；为 5.58.0 新引入的 errorprone 检查配置了合理的初始策略（三个降级为 WARN 并带 TODO issue，两个因误报/异常关闭）；并修复了 7 个源文件因 `MissingSummary` 检查变严而报错的 Javadoc。这使构建脚本与"放弃 Java 8"后的 JDK 11+ 目标对齐，简化了 errorprone 依赖管理。
- **影响范围**：构建脚本（`build.gradle`、`baseline.gradle`）+ 7 个 Java 源文件（仅 Javadoc 修改），共 9 个文件。Javadoc 修改分布在 `core` 与 `flink/v1.{17,18,19}` 四个模块目录。
- **回迁到 1.4.x 的注意事项**：**不建议整体回迁**。本提交与"Drop support for Java 8"强耦合——5.58.0 版本的 Palantir Baseline 要求 Java 11+，1.4.x 若仍支持 Java 8 则无法使用。但其中两块可以分别考虑：
  - Javadoc 摘要句修复（7 个文件的 `MissingSummary` 修复）是独立的纯文档改进，可以单独回迁到 1.4.x，无风险，但价值也很小。
  - errorprone 新检查策略与插件版本升级不应回迁到仍支持 Java 8 的 1.4.x。
  整体属于高风险回迁，建议 1.4.x 保留 4.42.0 + errorprone workaround 的现有配置。如 1.4.x 已决定放弃 Java 8，可整体回迁并配套回迁 1005、1008。
