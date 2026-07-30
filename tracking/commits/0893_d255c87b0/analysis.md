# 提交 0893：Build: Enable the Gradle build cache (#10602)

## 提交信息

- **序号**：0893 / 4088
- **哈希**：d255c87b00c8ca422a1d32a33b3a9cfe2f04cea2
- **短哈希**：d255c87b0
- **日期**：2024-07-03（Wed Jul 3 14:39:08 2024 +0200）
- **作者**：Robert Stupp <snazy@snazy.de>
- **提交说明**：Build: Enable the Gradle build cache (#10602)
- **PR/Issue**：#10602

## 总体目的

Apache Iceberg 是一个多模块、多 Spark/Flink 版本并行构建的大型 Gradle 工程，单次构建（尤其是 CI）耗时较长。Gradle 自 4.x 起内置 build cache 功能：它把每个 task 的输出按"task 输入哈希"作为 key 缓存起来，下次相同输入可复用缓存输出，不必重新执行 task；这一机制对本地多项目构建、CI 跨 build 复用尤其有效。

本提交的目的是在仓库根 `gradle.properties` 中显式开启并调优若干 Gradle 性能选项，让所有开发者与 CI 在不加额外参数的情况下默认享受 build cache、并行构建、按需配置等加速能力，从而显著缩短构建时间，提升迭代效率。

## 如何达成设计目的

实现方式极其简单：直接在 `gradle.properties` 中追加四项 Gradle 全局配置，并通过注释说明意图。这些属性会被 Gradle 自动加载，对所有 build 与所有子模块生效，无需修改任何 `build.gradle` 脚本或 CI 配置。

设计取舍上，作者选择把"显式关闭 configuration cache"也写入文件——`org.gradle.configuration-cache=false`。configuration cache 是 Gradle 6.x 引入、7.x 强化的实验特性，能缓存整个 build 的配置阶段，但需要所有插件与脚本严格符合隔离要求。Iceberg 当前依赖的部分插件与脚本对该特性兼容性不足，因此显式设为 false 以避免 build 失败或不可预期的副作用。

## 修改详情

### `gradle.properties`

**修改目的**：开启 Gradle build cache 并调优并行/配置相关选项。

**工作逻辑**：在原有 `org.gradle.parallel=true` 与 `org.gradle.jvmargs=-Xmx1024m` 之外新增四行配置（其中一行原已存在）。改动后的文件相关段落如下：

```diff
 systemProp.defaultSparkVersions=3.5
 systemProp.knownSparkVersions=3.3,3.4,3.5
 systemProp.defaultScalaVersion=2.12
 systemProp.knownScalaVersions=2.12,2.13
+# enable the Gradle build cache - speeds up builds!
+org.gradle.caching=true
+# enable Gradle parallel builds
 org.gradle.parallel=true
+# configure only necessary Gradle tasks
+org.gradle.configureondemand=true
+# explicitly disable the configuration cache
+org.gradle.configuration-cache=false
 org.gradle.jvmargs=-Xmx1024m
```

各属性作用：
- `org.gradle.caching=true`：开启 Gradle build cache。Gradle 会把每个 task 的输出按其输入哈希缓存到本地 `~/.gradle/caches/build-cache-1/`，相同输入的 task 直接复用结果。在 CI 上若再配合远程 build cache（如内部 CI 共享 cache），跨 PR/分支也可复用。
- `org.gradle.parallel=true`：开启并行构建（原本已存在，本次仅加注释）。Gradle 会按依赖图并行调度无依赖关系的 project/task，缩短 wall-clock 时间。
- `org.gradle.configureondemand=true`：开启"按需配置"（configuration on demand）。Gradle 仅对参与本次 build 的 project 执行 configuration 阶段，跳过无关 project 的 `build.gradle`，对超大型多 project 工程尤其有用。
- `org.gradle.configuration-cache=false`：显式关闭 configuration cache。该实验特性要求插件/脚本完全隔离 state，Iceberg 当前还不完全兼容，关闭可避免 build 不稳定。

## 小结

- **成效**：默认开启 build cache 与 configure-on-demand，配合已开启的 parallel，能在本地增量构建与 CI 上加速构建。开发者在本地反复跑相同 task、或 CI 跑相同输入时可直接命中缓存跳过执行。
- **影响范围**：仅 `gradle.properties` 一个文件，7 行新增（含注释）。无任何源代码、测试代码或构建脚本逻辑变更。
- **回迁到 1.4.x 的注意事项**：该提交属于构建性能优化，**回迁到 1.4.x 完全安全且推荐**。理由：
  1. 改动只是开启 Gradle 内置特性，对所有版本都通用，没有版本依赖；
  2. `org.gradle.configuration-cache=false` 的显式关闭在 1.4.x 同样需要（1.4.x 用 Gradle 8.x，默认情况下 configuration cache 仍为实验性，显式关闭能避免兼容性问题）；
  3. 该改动可立即让 1.4.x 维护分支的 CI 与本地构建同样提速，且无副作用；
  4. 唯一注意点：1.4.x 分支若已有自定义 CI 缓存策略（如显式注入 `--build-cache` 参数），需检查是否与新默认值冲突；通常 `gradle.properties` 与命令行参数不会冲突，但值得确认一次。
