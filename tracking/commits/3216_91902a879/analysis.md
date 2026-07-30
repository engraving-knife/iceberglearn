# 提交 3216：Build: Remove JDK17 target configuration for Spark 4.1 (#15256)

## 提交信息

- **序号**：3216 / 4088
- **哈希**：91902a87970ff8841e99e6fb3f27f583f7645a6b
- **短哈希**：91902a879
- **日期**：2026-02-07
- **作者**：Manu Zhang
- **提交说明**：Build: Remove JDK17 target configuration for Spark 4.1 (#15256)
- **PR/Issue**：#15256

## 总体目的

Iceberg 的 Spark 4.1 模块（`spark/v4.1`）此前在 `build.gradle` 中显式为 `ScalaCompile` 任务设置了一组 JDK17 目标配置：`sourceCompatibility = "17"`、`targetCompatibility = "17"`，并通过 `scalaCompileOptions.additionalParameters.add("-release:17")` 传入 Scala 编译器的 `-release:17` 标志。这段配置的存在是为了规避一个具体的编译错误——注释中记录为 `ThetaSketchAgg.scala:52:12: Class java.lang.Record not found`，即 Scala 编译器在编译引用了 `java.lang.Record`（Java 16+ 引入的记录类）的源文件时找不到该类。

这种"打补丁式"的目标版本覆盖在以下情形下会变得多余：当项目整体工具链（toolchain）或 Spark 4.1 模块的默认目标 JDK 已经提升到 17 及以上，或 Scala 编译器版本升级到能正确识别 `java.lang.Record` 后，该错误不再出现，此时再在子模块里手动覆盖 `sourceCompatibility`/`targetCompatibility`/`-release` 不仅冗余，还可能与全局工具链配置产生冲突或带来维护负担。本提交即在此前提下移除该段临时性配置，让 Spark 4.1 模块的 Scala 编译回归到由项目统一管理的 JDK 目标版本，消除特殊 case。

## 如何达成设计目的

整体思路就是直接删除 `spark/v4.1/build.gradle` 中针对 `ScalaCompile` 的 JDK17 覆盖块，不引入任何替代配置，依赖项目级/工具链级的默认 JDK17+ 目标来保证 `java.lang.Record` 可被 Scala 编译器解析。改动只涉及这一个构建文件，且是纯删除，不影响任何源代码或运行时行为。

## 修改详情

### `spark/v4.1/build.gradle` (+0/-8 lines)

**修改目的**：移除 Spark 4.1 模块中冗余的 JDK17 Scala 编译目标覆盖。

**工作逻辑**：
被删除的是一个 `tasks.withType(ScalaCompile.class) { ... }` 配置块，原内容为：

```groovy
// Set target to JDK17 for Spark 4.1 to fix following error
// "spark/v4.1/spark/src/main/scala/org/apache/spark/sql/stats/ThetaSketchAgg.scala:52:12: Class java.lang.Record not found"
tasks.withType(ScalaCompile.class) {
  sourceCompatibility = "17"
  targetCompatibility = "17"
  scalaCompileOptions.additionalParameters.add("-release:17")
}
```

该块把所有 `ScalaCompile` 任务的源/目标兼容性强制设为 17 并追加 `-release:17`，原本用于让 Scala 编译器以 JDK17 字节码目标编译、从而能解析 `java.lang.Record`。删除后，Spark 4.1 模块不再单独覆盖这些值，改由项目级工具链（Spark 4 本身要求 JDK17+）统一决定编译目标。注释连同代码一并删除，说明触发该 workaround 的错误已不再复现，配置已无必要。其余 `sourceSets`、插件应用等配置均未改动。

## 总结

本提交移除了 Spark 4.1 模块 `build.gradle` 中为规避 `java.lang.Record not found` 编译错误而临时添加的 JDK17 Scala 编译目标覆盖（`sourceCompatibility`/`targetCompatibility`/`-release:17`），在错误根因已由工具链/Scala 版本演进解决后回归统一构建配置，消除了冗余的子模块特例，降低维护成本，且不改变编译产物或运行时行为。
