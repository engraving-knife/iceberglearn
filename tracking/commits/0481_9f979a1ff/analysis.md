# 提交 0481：Build: Bump io.delta:delta-standalone_2.12 from 0.6.0 to 3.1.0 (#9636)

## 提交信息

- **序号**：0481 / 4088
- **哈希**：9f979a1ff187ebd69ef3b027d857ee697fd9c52c
- **短哈希**：9f979a1ff
- **日期**：2024-02-06 18:17:40 -0800
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump io.delta:delta-standalone_2.12 from 0.6.0 to 3.1.0 (#9636)
- **PR/Issue**：#9636（Dependabot 自动提交）

## 总体目的

这是一个由 GitHub Dependabot 自动生成的小版本升级提交，目的是把 Iceberg 构建依赖中的 Delta Standalone API 从 `0.6.0` 一次性升级到 `3.1.0`。Delta Standalone 是 Delta Lake 项目提供的、独立于 Spark 的 Java API，用于读取/写入 Delta Lake 表格式的元数据与事务日志。Iceberg 在 `delta-iceberg` 子模块（用于 Delta Lake 与 Iceberg 之间元数据互转的工具）里以 `compileOnly` 形式依赖它，以便在编译期使用 Delta 的 API 来实现 `DeltaLog` 的转换逻辑。

从 0.6.0 直接跳到 3.1.0 是一次跨大版本的跳跃：Delta 项目在 3.x 线路上与 Spark 3.x、Delta 3.x 对齐，API 包路径、`DeltaLog` 接口、`Snapshot`/`Metadata`/`AddFile`/`RemoveFile` 等结构都经过了重新整理。这次升级使 Iceberg 的 Delta 互转模块能够跟进 Delta 生态的最新进展，避免长期落后于上游；同时也为后续将 `delta-spark` 升级到同一基线（提交说明中可见 `delta-spark` 同样已是 `3.1.0`）保持一致，避免在同一个 build 中混用两套 Delta 主版本。

值得指出的是，`delta-standalone` 是 `compileOnly` 依赖：它只在编译期被引用、不会被打包进 Iceberg 的产物。因此升级影响仅限于编译期的 API 调用面（是否有方法签名变化、包路径迁移等），运行期由使用者自行提供 Delta Standalone 的实现 jar。Dependabot 这里只改了版本号字符串，说明此版本切换并未触发 Iceberg 侧的源码改动，编译在新版本下仍能直接通过。

## 如何达成设计目的

实现路径非常直接：Dependabot 通过 TOML 版本目录（`gradle/libs.versions.toml`）里的版本引用，把 `delta-standalone` 的版本字面量从 `"0.6.0"` 改为 `"3.1.0"`。该字符串被 `libs.versions.delta.standalone.get()` 引用到 `build.gradle` 中 `delta-iceberg` 子模块的 `compileOnly` 声明，所以这一行改动即生效；不需要任何代码层面的迁移。这表明在本次升级窗口下，Delta Standalone 0.6.0 与 3.1.0 在 `delta-iceberg` 模块用到的 API 子集上是二进制兼容的，或者至少在源码兼容范围内。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Delta Standalone 依赖的版本号从 `0.6.0` 升级到 `3.1.0`，与同文件中的 `delta-spark = "3.1.0"` 保持基线一致。

**工作逻辑**：该文件是 Gradle 版本目录（Version Catalog），统一集中管理所有第三方依赖版本。其中：

```toml
delta-standalone = "3.1.0"          # 原 "0.6.0"
delta-spark = "3.1.0"
```

并在依赖别名段定义：

```toml
delta-standalone = { module = "io.delta:delta-standalone_2.12", version.ref = "delta-standalone" }
```

`build.gradle` 第 566 行附近对 `delta-iceberg` 子项目使用：

```groovy
compileOnly "io.delta:delta-standalone_${scalaVersion}:${libs.versions.delta.standalone.get()}"
```

由于 `${scalaVersion}` 取值为 `2.12`，最终依赖坐标为 `io.delta:delta-standalone_2.12:3.1.0`。`compileOnly` 表示该依赖仅参与编译、不传递给运行时 classpath，符合"由部署环境提供 Delta 实现即可"的设计意图。一行版本字面量改动即让所有引用此 `version.ref` 的下游声明同步生效，是 Version Catalog 模式带来的最小变更面。

## 小结

这是 Dependabot 的常规依赖升级，仅修改 `gradle/libs.versions.toml` 一行版本号，将 `delta-standalone_2.12` 从 `0.6.0` 直接跨大版本提升到 `3.1.0`，与 `delta-spark` 基线对齐。由于该依赖以 `compileOnly` 形式服务于 `delta-iceberg` 互转模块，升级未触及任何 Java/Scala 源码，说明本次升级对 Iceberg 用到的 Delta API 子集源码兼容。其本质价值是让 Iceberg 的 Delta 互操作能力与 Delta 生态 3.x 主线保持同步，避免依赖陈旧。
