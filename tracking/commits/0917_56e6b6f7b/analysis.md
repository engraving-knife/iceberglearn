# 提交 0917：Build: Define JUnit4 dependency only where necessary (#10672)

## 提交信息

- **序号**：0917 / 4088
- **哈希**：56e6b6f7b1ae3892534908a9ad276657c5d180f9
- **短哈希**：56e6b6f7b
- **日期**：2024-07-10（Wed Jul 10 14:40:22 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Build: Define JUnit4 dependency only where necessary (#10672)
- **PR/Issue**：#10672

## 总体目的

Iceberg 项目在 1.5.x/1.6.x 周期持续推进 JUnit 4 → JUnit 5 迁移（见 #10657、#10663、#10670 等）。迁移完成后，大部分模块的测试已不再使用 JUnit 4 API。然而根 `build.gradle` 的 `subprojects { dependencies { ... } }` 块仍全局声明了 `testImplementation libs.junit.vintage.engine` 依赖（JUnit Vintage Engine 用于在 JUnit 5 平台上运行 JUnit 3/4 风格的测试）。这意味着即使某模块已完全迁移到 JUnit 5，开发者仍可能在其中新增 JUnit 4 风格的测试（`import org.junit.Test`、`@RunWith` 等）并能编译通过，因为 JUnit 4 类仍在 classpath 上。这违背了迁移的目标——让 JUnit 4 风格在已迁移模块中"不可用"以防止回退。

本提交的目的是把 JUnit Vintage Engine（即 JUnit 4 兼容层）依赖从全局 `subprojects` 块移除，仅在仍然需要 JUnit 4 的模块（`iceberg-data`、`flink/v1.17|1.18|1.19`、`spark/v3.3|v3.4`）中显式声明。这样已迁移模块的 classpath 上不再有 JUnit 4，新增 JUnit 4 风格测试会直接编译失败，从机制上防止迁移回退。同时为了配合 `api` 模块去除对 JUnit 4 的编译期依赖，需要把 `Parameter.java` 中对 `org.junit.runners.Parameterized` 的 `import` 改为 javadoc 中的全限定名引用。

## 如何达成设计目的

通过两层调整达成：
1. **构建脚本层**：在根 `build.gradle` 的 `subprojects` 块中删除 `testImplementation libs.junit.vintage.engine`，使其不再对所有子模块自动可用。然后在仍需要 JUnit 4 的 6 个模块（`iceberg-data`、`flink/v1.17`、`flink/v1.18`、`flink/v1.19`、`spark/v3.3`、`spark/v3.4`）的 `build.gradle` 中显式添加该依赖。注意 `spark/v3.5` 不在列表中，因为该模块此前已完全迁移到 JUnit 5（见 #10657 等），不再需要 vintage engine；`spark/v3.3` 与 `spark/v3.4` 各自的 spark 主子项目与 spark-extensions 子项目都需要添加（每文件 2 处）。
2. **源码层**：`api/src/test/java/org/apache/iceberg/Parameter.java` 是 Iceberg 自定义的 JUnit 5 参数化注解，原本通过 `import org.junit.runners.Parameterized;` 引入 JUnit 4 类用于 javadoc `{@link Parameterized.Parameter}` 引用。这会让 `api` 模块测试源码在编译期依赖 JUnit 4。本提交把 import 删除，javadoc 中改为 `{@link org.junit.runners.Parameterized.Parameter}` 全限定名（javadoc 链接不需要 import 也不要求类在编译期 classpath 上），从而消除 `api` 模块对 JUnit 4 的编译期依赖。

## 修改详情

### `api/src/test/java/org/apache/iceberg/Parameter.java`

**修改目的**：移除对 `org.junit.runners.Parameterized` 的 `import`，消除 `api` 模块测试源码对 JUnit 4 的编译期依赖，使其在 `subprojects` 块移除 vintage engine 后仍能编译。

**工作逻辑**：

```diff
-import org.junit.runners.Parameterized;
-
 /**
- * The annotation is used to replace {@link Parameterized.Parameter} for Junit 5 parameterized
- * tests.
+ * The annotation is used to replace {@link org.junit.runners.Parameterized.Parameter} for Junit 5
+ * parameterized tests.
```

`Parameter.java` 是 Iceberg 自定义的注解，用于在 JUnit 5 中替代 JUnit 4 的 `@Parameterized.Parameter`。原本 javadoc 中 `{@link Parameterized.Parameter}` 依赖 import 才能解析；改为全限定名 `{@link org.junit.runners.Parameterized.Parameter}` 后，javadoc 工具仍能解析（在生成 javadoc 时需要 JUnit 4 在 classpath，但编译期 javadoc 注释不影响字节码），而源码本身不再有 `import` 语句，因此编译期不再需要 JUnit 4 类在 classpath 上。

### `build.gradle`

**修改目的**：从全局 `subprojects` 依赖块中移除 `junit.vintage.engine`，使其不再默认对所有子模块可用。

**工作逻辑**：

```diff
   dependencies {
     implementation libs.slf4j.api

-    testImplementation libs.junit.vintage.engine
     testImplementation libs.junit.jupiter
     testImplementation libs.junit.jupiter.engine
     testImplementation libs.slf4j.simple
```

同时在 `project(':iceberg-data')` 块的 `dependencies` 中显式添加：

```diff
     testImplementation project(path: ':iceberg-api', configuration: 'testArtifacts')
     testImplementation project(path: ':iceberg-core', configuration: 'testArtifacts')
+    testImplementation libs.junit.vintage.engine
```

注意 `iceberg-data` 模块虽然主体已迁移到 JUnit 5（见 #10657），但仍有少量遗留 JUnit 4 测试或第三方依赖需要 vintage engine，因此显式保留。`iceberg-data` 也是 `flink`/`spark` 各版本测试（通过 `testArtifacts` 配置）的依赖来源，需要 vintage 时通过各自 build.gradle 声明。

### `flink/v1.17/build.gradle`、`flink/v1.18/build.gradle`、`flink/v1.19/build.gradle`

**修改目的**：为三个 Flink 版本模块显式添加 `junit.vintage.engine` 依赖。

**工作逻辑**：每个文件在 spark-flink 子项目的 `dependencies` 块中（与 `libs.awaitility`、`libs.assertj.core` 同级）新增一行：

```diff
     testImplementation libs.awaitility
     testImplementation libs.assertj.core
+    testImplementation libs.junit.vintage.engine
```

Flink 模块仍有大量 JUnit 4 风格测试（部分由 #10663 迁移，但尚未完全完成），需要 vintage engine 兼容运行。

### `spark/v3.3/build.gradle`、`spark/v3.4/build.gradle`

**修改目的**：为 Spark 3.3 与 3.4 模块显式添加 `junit.vintage.engine` 依赖。

**工作逻辑**：每个 build.gradle 中有两处添加——spark 主子项目与 spark-extensions 子项目各一处：

```diff
     testImplementation libs.awaitility
+    testImplementation libs.junit.vintage.engine
   ...
     testImplementation libs.parquet.hadoop
+    testImplementation libs.junit.vintage.engine
```

Spark 3.3/3.4 测试仍有 JUnit 4 风格代码（extensions 子项目的 Spark SQL 扩展测试尤其多），需要 vintage engine。`spark/v3.5` 不在修改列表中，因为该模块此前已完全迁移到 JUnit 5。

## 小结

- **成效**：把 JUnit Vintage Engine（JUnit 4 兼容层）依赖从全局 `subprojects` 收窄到 6 个仍需要 JUnit 4 的模块（`iceberg-data`、`flink/v1.17|1.18|1.19`、`spark/v3.3|v3.4`），使已迁移模块的 classpath 上不再有 JUnit 4，从机制上防止 JUnit 4 风格测试在已迁移模块中被新增（新增会导致编译失败）。同时清理 `api` 模块 `Parameter.java` 对 JUnit 4 的编译期依赖。
- **影响范围**：1 个源码文件（`api` 模块测试辅助类）+ 6 个 build.gradle 文件（根 build.gradle + 3 个 flink + 2 个 spark），共 7 个文件，10 行新增 / 4 行删除。属于构建基础设施层，不影响生产代码逻辑，但会影响各模块测试的依赖解析。
- **回迁到 1.4.x 的注意事项**：构建依赖管理类提交，回迁需谨慎：
  - 1.4.x 分支的 JUnit 5 迁移进度可能不同。如果 1.4.x 仍有大量模块使用 JUnit 4，那么从 `subprojects` 移除 vintage engine 会导致这些模块测试编译失败，不能直接回迁。
  - 回迁前需先盘点 1.4.x 各模块的 JUnit 4 使用情况：只有确认 `subprojects` 块移除后所有未显式声明 vintage 的模块都不再需要 JUnit 4，才能安全回迁。否则需要同时回迁一组 JUnit 5 迁移提交（#10657、#10663、#10670 等）使各模块先完成迁移。
  - 如果 1.4.x 已完成或基本完成 JUnit 5 迁移，则本提交可以回迁，且建议回迁以获得"防止回退"的机制保障。回迁时需同步检查 `api` 模块 `Parameter.java` 是否仍有 JUnit 4 import，以及 `iceberg-data`、`flink/v1.17|1.18|1.19`、`spark/v3.3|v3.4` 各模块是否确实仍需要 vintage（取决于 1.4.x 的迁移进度）。
  - 特别注意：本提交假设 `spark/v3.5` 已完全迁移；若 1.4.x 的 `spark/v3.5` 仍有 JUnit 4 测试，回迁时需要给 v3.5 也加上 vintage engine 依赖。
