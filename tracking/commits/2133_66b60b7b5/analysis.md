# 提交 2133：Build: Remove JUnit4 dependency

## 提交信息

- **序号**：2133 / 4088
- **哈希**：66b60b7b5c555fc744d53e7567771624f90ee9db
- **短哈希**：66b60b7b5
- **日期**：2025-05-15 08:41:34 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Build: Remove JUnit4 dependency (#12938)
- **PR/Issue**：#12938

## 总体目的

这个提交的目的是彻底移除 Iceberg 项目对 JUnit4 的依赖，完成从 JUnit4 到 JUnit5 的迁移。在之前的迁移过程中，项目通过 JUnit Vintage Engine 来兼容运行 JUnit4 风格的测试，但这只是一个过渡方案。现在所有的测试都已经迁移到 JUnit5，所以需要移除剩余的 JUnit4 依赖，包括 junit-vintage-engine、JUnit4 的 @org.junit.Test、org.junit.Assert、org.junit.Assume 等引用，以及 JUnit4 的 TemporaryFolder 等 API 使用。同时，项目中残留的 JUnit4 兼容代码（如被 @Deprecated 标记的构造函数和已不再使用的 RESTServerRule）也需要一并清理，以保证代码库的整洁和依赖关系的简化。

## 如何达成设计目的

1. 从 Gradle 版本目录文件（libs.versions.toml）中移除 junit-vintage-engine 的依赖声明，这是 JUnit4 兼容运行时的核心组件。
2. 从所有模块的 build.gradle 文件中移除对 libs.junit.vintage.engine 的依赖引用，涉及 core、data、flink（v1.19/v1.20/v2.0）、spark（v3.4/v3.5/v4.0）等多个模块。
3. 更新 checkstyle 规则，将原来仅禁止 junit.framework 的规则替换为禁止所有 JUnit4 用法（org.junit.Assert、org.junit.Assume、org.junit.Test）的新规则，确保后续不会再引入 JUnit4 代码。
4. 在 build.gradle 中对 guava-testlib 依赖排除 junit 传递依赖，防止 JUnit4 通过传递依赖被重新引入。
5. 将测试代码中剩余的 JUnit4 API 调用替换为 JUnit5/AssertJ 等价物，例如将 org.junit.Assume.assumeFalse 替换为 AssertJ 的 assumeThat。
6. 删除不再需要的 JUnit4 兼容代码，包括 GenericAppenderHelper 中基于 TemporaryFolder 的已废弃构造函数，以及 spark v3.4 中已不再使用的 RESTServerRule 类。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml` (修改, +10/-7 lines)

**修改目的**：更新 checkstyle 规则以禁止 JUnit4 的使用，并移除旧的 JUnit3 禁止规则。

**工作逻辑**：移除了原来禁止导入 junit.framework 包的 IllegalImport 模块（针对 JUnit3），同时移除了 checkstyle 中对 org.junit.Assert.* 的额外导入许可。新增了一个 id 为 BanJUnit4Usage 的 IllegalImport 模块，明确禁止 org.junit.Assert、org.junit.Assume、org.junit.Test 三个类的导入，提示使用 JUnit5/AssertJ 替代。

### `build.gradle` (修改, +4/-2 lines)

**修改目的**：移除 JUnit Vintage Engine 依赖并排除 guava-testlib 的 JUnit 传递依赖。

**工作逻辑**：在 iceberg-core 模块中，对 guava-testlib 依赖添加了 exclude group: 'junit' 配置，防止 JUnit4 通过传递依赖被引入。在 iceberg-data 模块中移除了 testImplementation libs.junit.vintage.engine 依赖行。

### `data/src/test/java/org/apache/iceberg/data/GenericAppenderHelper.java` (修改, +0/-15 lines)

**修改目的**：移除基于 JUnit4 TemporaryFolder 的已废弃构造函数。

**工作逻辑**：删除了两个标记为 @Deprecated 的构造函数，它们接收 org.junit.rules.TemporaryFolder 参数。这些构造函数是 JUnit4 时代的遗留物，现在使用基于 java.nio.file.Path 的构造函数替代。同时移除了对 TemporaryFolder 的导入。

### `flink/v1.19/build.gradle`、`flink/v1.20/build.gradle`、`flink/v2.0/build.gradle` (修改, 各 -1 line)

**修改目的**：从 Flink 各版本的集成测试依赖中移除 JUnit Vintage Engine。

**工作逻辑**：在每个 Flink 版本的 build.gradle 文件中，移除了 integrationImplementation libs.junit.vintage.engine 依赖行。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSink.java`（及 v1.20、v2.0 对应文件）(修改, +1/-2 lines each)

**修改目的**：将测试中的 JUnit4 Assume API 替换为 AssertJ 的等价物。

**工作逻辑**：将 org.junit.Assume.assumeFace(...) 调用替换为 AssertJ 的 assumeThat(format).as(...).isNotEqualTo(...) 链式调用，并更新对应的导入语句。

### `gradle/libs.versions.toml` (修改, +0/-1 line)

**修改目的**：从版本目录中移除 junit-vintage-engine 的依赖定义。

**工作逻辑**：删除了 junit-vintage-engine = { module = "org.junit.vintage:junit-vintage-engine", version.ref = "junit" } 这一行声明。

### `spark/v3.4/build.gradle`、`spark/v3.5/build.gradle`、`spark/v4.0/build.gradle` (修改, 各 -1 line)

**修改目的**：从 Spark 各版本的测试和集成测试依赖中移除 JUnit Vintage Engine。

**工作逻辑**：移除了 testImplementation/integrationImplementation libs.junit.vintage.engine 依赖行。其中 spark/v3.4 移除了3处引用。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/rest/RESTServerRule.java` (删除, -107 lines)

**修改目的**：删除不再使用的基于 JUnit4 规则的 RESTServerRule 测试辅助类。

**工作逻辑**：这是一个基于 JUnit4 TestRule 接口的测试辅助类，用于在测试中管理 REST 服务器的生命周期。由于 JUnit4 已被移除，且该类已不再被使用，因此整个文件被删除。

## 总结

这个提交完成了 Iceberg 项目从 JUnit4 到 JUnit5 迁移的最后一步，彻底清除了 JUnit4 的所有依赖和残留代码。通过移除 junit-vintage-engine、更新 checkstyle 规则禁止 JUnit4 用法、替换测试中的 JUnit4 API 调用，以及删除不再需要的兼容代码，确保了项目测试框架的一致性和依赖关系的简化。这对减少技术债务、统一测试规范有重要意义。
