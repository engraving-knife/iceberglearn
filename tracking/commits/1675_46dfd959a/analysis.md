# 提交 1675：Build: Bump me.champeau.jmh:jmh-gradle-plugin from 0.7.2 to 0.7.3 (#12152)

## 提交信息

- **序号**：1675 / 4088
- **哈希**：46dfd959aa688761aae991db15b8749d77a811fe
- **短哈希**：46dfd959a
- **日期**：2025-02-02（Sun Feb 2 09:40:22 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump me.champeau.jmh:jmh-gradle-plugin from 0.7.2 to 0.7.3 (#12152)
- **PR/Issue**：#12152

## 总体目的

Dependabot 自动升级，把 JMH Gradle 插件（`me.champeau.jmh:jmh-gradle-plugin`）从 `0.7.2` 升到 `0.7.3`。`0.7.2 → 0.7.3` 是 patch 升级，仅含 bug 修复与小幅改进。

JMH（Java Microbenchmark Harness）是 OpenJDK 的微基准测试工具，JMH Gradle 插件让 Gradle 项目能方便地集成 JMH 基准测试——自动配置 JMH 依赖、生成基准测试源码、执行基准测试与生成报告。Iceberg 使用 JMH 在 `iceberg-core`、`iceberg-spark` 等模块中进行性能基准测试（如读写吞吐、扫描性能等），插件配置在根 `build.gradle` 的 `buildscript` classpath 中。

此依赖属于构建工具链，不进入发布产物。

## 如何达成设计目的

修改根 `build.gradle` 的 `buildscript.dependencies.classpath` 中插件版本号。

## 修改详情

### `build.gradle`（修改，1 行）

```diff
-    classpath 'me.champeau.jmh:jmh-gradle-plugin:0.7.2'
+    classpath 'me.champeau.jmh:jmh-gradle-plugin:0.7.3'
```

该插件在 `buildscript` 块中声明，作为构建脚本依赖加载，各子项目通过 `apply plugin: 'me.champeau.jmh'` 或 `plugins { id 'me.champeau.jmh' }` 启用 JMH 基准测试支持。

## 小结

- **成效**：JMH Gradle 插件从 0.7.2 升到 0.7.3，获取上游 patch 修复。
- **影响范围**：仅构建工具链（JMH 基准测试执行），不影响 Java 代码、发布产物或运行时行为。
- **回迁到 1.4.x 的注意事项**：可安全 cherry-pick，无风险。仅影响基准测试构建，建议回迁后执行一次 `./gradlew jmh` 确认插件正常工作（若有 JMH 基准测试配置）。1.4.x 的根 `build.gradle` 结构应与 main 一致。
