# 提交 1655 e89798ea7 分析

## 提交信息
- 哈希：e89798ea7c64a419b41a2be178f3751960e7f1bd
- 日期：2025-01-29 00:20:12 -0800
- 作者：Manu Zhang
- 消息：Build: Bump scala-collection-compat from 2.12.0 to 2.13.0 (#12121)

## 总体目的

本提交将 Scala 集合兼容库 `scala-collection-compat` 从 2.12.0 升级到 2.13.0。该库为 Scala 2.12 提供了 Scala 2.13 集合 API 的向后兼容层，使代码能在两个 Scala 版本间共享同一套集合 API 用法。Iceberg 的 Spark 集成模块（v3.3、v3.4、v3.5）使用该库以兼容 Spark 不同版本所依赖的 Scala 版本。

升级的核心动机在于：旧版本 `scala-collection-compat_2.12` 会传递依赖 Scala 2.12.17，而 Iceberg 为支持 JDK 21 需要 Scala 2.12.18。此前为了解决这一冲突，三个 Spark 模块的 `build.gradle` 中都通过显式 `implementation 'org.scala-lang:scala-library:2.12.18'` 来强制提升 Scala 版本。新版本 2.13.0 已经直接依赖 Scala 2.12.18，因此这些 workaround 可以移除，让构建配置更简洁。

## 如何达成设计目的

设计思路是两步走：
1. 在统一的版本目录 `gradle/libs.versions.toml` 中将 `scala-collection-compat` 版本号从 `2.12.0` 改为 `2.13.0`，所有引用该版本号的模块自动升级。
2. 移除三个 Spark 模块（v3.3、v3.4、v3.5）的 `build.gradle` 中针对 Scala 2.12 强制提升 scala-library 到 2.12.18 的 workaround 代码块，因为新版本已不再需要。

### 修改详情

#### gradle/libs.versions.toml
将 `scala-collection-compat` 版本常量从 `2.12.0` 修改为 `2.13.0`。该常量通过 `libs.versions.scala.collection.compat.get()` 被各 Spark 模块引用。

#### spark/v3.3/build.gradle
移除两处（iceberg-spark 子项目和 iceberg-spark-extensions 子项目）针对 `scalaVersion == '2.12'` 的条件依赖块：
```
if (scalaVersion == '2.12') {
  // scala-collection-compat_2.12 pulls scala 2.12.17 and we need 2.12.18 for JDK 21 support
  implementation 'org.scala-lang:scala-library:2.12.18'
}
```
这两处 workaround 不再需要，因为 scala-collection-compat 2.13.0 已直接依赖 scala-library 2.12.18。

#### spark/v3.4/build.gradle
与 v3.3 相同，移除 iceberg-spark 和 iceberg-spark-extensions 两个子项目中的同类 workaround 代码块。

#### spark/v3.5/build.gradle
与 v3.3、v3.4 相同，移除两个子项目中的同类 workaround 代码块。

## 小结

本次升级成效：
- 简化了 Spark 模块的构建配置，移除了 6 处重复的 Scala 版本强制提升 workaround。
- 与 JDK 21 支持保持一致（Scala 2.12.18 是支持 JDK 21 的最低版本）。
- 跟随上游 scala-collection-compat 的版本演进，获取 2.13.0 的 bug 修复和改进。

影响范围：Spark v3.3、v3.4、v3.5 三个集成模块的构建配置，不改变运行时行为（依赖版本已通过 workaround 维持在 2.12.18，现在只是改由 scala-collection-compat 直接传递）。

回迁到 1.4.x 注意事项：
- 1.4.x 是维护分支，通常不升级依赖版本。但若 1.4.x 也存在 JDK 21 支持需求且当前使用了相同的 workaround，回迁此提交可以简化构建配置。
- 回迁前需确认 scala-collection-compat 2.13.0 与 1.4.x 其他 Spark/Scala 依赖的兼容性。
- 注意本提交只涉及 spark v3.3/v3.4/v3.5，若 1.4.x 还维护其他 Spark 版本（如 v3.2），需检查是否也有类似 workaround 需要清理（本提交未涉及）。
- 若 1.4.x 不升级，则保留原有 workaround 即可，无需回迁。
