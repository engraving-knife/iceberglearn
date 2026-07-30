# 提交 0971：Flink: Remove JUnit4 dependency (#10770)

## 提交信息

- **序号**：0971 / 4088
- **哈希**：c00635a38a59c916ebc298e9b9a68ede92fbc699
- **短哈希**：c00635a38
- **日期**：2024-07-24 17:07:42 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Flink: Remove JUnit4 dependency (#10770)
- **PR/Issue**：#10770

## 总体目的

在前序提交 0965（`Flink 1.17, 1.18: Migrate remaining tests to JUnit5`）以及更早的 1.19 JUnit5 迁移完成后，Iceberg 的 Flink 模块（1.17/1.18/1.19 三套目录）所有测试已经全部使用 JUnit5 编写，不再需要 JUnit4 的运行时支持。但 `flink/v1.*/build.gradle` 中仍然在 `testImplementation` 依赖里保留着 `libs.junit.vintage.engine`——JUnit Vintage Engine 是 JUnit5 平台用来在 JUnit5 runner 下兼容运行 JUnit3/JUnit4 测试的桥接引擎。一旦代码中再无任何 JUnit4 测试，这个依赖就是冗余的：

- 它会无谓地拉入 JUnit4 的 jar，增加依赖图复杂度；
- 在某些 classpath 检查严格的环境下会引入潜在的版本冲突；
- 让人误以为该项目仍"在用 JUnit4"，造成迁移状态歧义。

本提交的目标就是把三套 Flink 模块的 build.gradle 中冗余的 `libs.junit.vintage.engine` 依赖移除，正式宣告 Flink 模块彻底告别 JUnit4。

## 如何达成设计目的

实现非常直接：在 `flink/v1.17/build.gradle`、`flink/v1.18/build.gradle`、`flink/v1.19/build.gradle` 三份构建脚本的 `testImplementation` 块中，各删除一行 `testImplementation libs.junit.vintage.engine`。其它依赖（`libs.awaitility`、`libs.assertj.core` 以及上方其它 test 依赖）保持不变。删除后这些模块的测试运行只依赖 JUnit5（`jupiter`）平台。

## 修改详情

### `flink/v1.17/build.gradle`、`flink/v1.18/build.gradle`、`flink/v1.19/build.gradle`

**修改目的**：移除冗余的 JUnit Vintage Engine 测试依赖，正式去除 Flink 模块对 JUnit4 的依赖。

**工作逻辑**：三份 build.gradle 中都做了相同的删除：在 `project(":iceberg-flink:iceberg-flink-${flinkMajorVersion}")` 的 `dependencies { ... testImplementation ... }` 块里，删除 `testImplementation libs.junit.vintage.engine` 这一行。`libs.junit.vintage.engine` 来自 Iceberg 的版本目录（`libs.versions.toml`），是 JUnit5 平台的 vintage 引擎 artifact（`org.junit.vintage:junit-vintage-engine`），其作用是把 JUnit3/4 风格的测试在 JUnit5 platform 上跑起来；既然 Flink 模块已无此类测试，该依赖可以安全移除。其它测试依赖（`awaitility`、`assertj.core`）保留不变，仍由 JUnit5 平台使用。

## 小结

- **成效**：Flink 1.17/1.18/1.19 三套模块的 build.gradle 不再依赖 `junit-vintage-engine`，JUnit4 相关 jar 不会再被引入测试 classpath，Flink 模块彻底完成 JUnit5 迁移。
- **影响范围**：仅构建脚本，3 个文件各删 1 行，无源代码或测试改动。
- **回迁到 1.4.x 的注意事项**：本提交**只有在 1.4.x 上也完成了对应 Flink 模块的 JUnit5 迁移后才能回迁**——也就是必须先把 1.4.x 上 Flink 1.17/1.18/1.19 三套目录的所有 JUnit4 测试迁到 JUnit5（即把 0965 与更早的 1.19 迁移工作整体回迁过去），否则一旦移除 vintage engine，残留的 JUnit4 测试将无法运行（被 JUnit5 platform 跳过），CI 会"看起来全绿但实际啥都没跑"。如果 1.4.x 上 Flink 模块仍是 JUnit4 测试为主，则**不应回迁本提交**。如已决定在 1.4.x 上做整体 JUnit5 迁移，建议把本提交作为收尾的最后一步，紧跟在测试迁移提交之后。
