# 提交 0611：Build: Bump arrow from 15.0.0 to 15.0.1

## 提交信息

- **序号**：0611 / 4088
- **哈希**：353e55e24fa751ec877c597b9647bcd75bccbf51
- **短哈希**：353e55e24
- **日期**：2024-03-19（Tue Mar 19 08:58:10 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump arrow from 15.0.0 to 15.0.1 (#9910)
- **PR/Issue**：#9910

## 总体目的

本提交是由 GitHub Dependabot 自动生成的依赖版本升级，将 Apache Arrow 从 15.0.0 升级到 15.0.1（一个 semver patch 版本升级）。

Arrow 在 Iceberg 项目中的角色非常关键：Iceberg 的 `:iceberg-arrow` 模块（见 `build.gradle` 中 `project(':iceberg-arrow')`）依赖 `arrow-vector` 与 `arrow-memory-netty` 两个制品，用于提供基于 Apache Arrow 列式内存格式的读取器（如 `ArrowReader`），使引擎能够以零拷贝方式读取 Iceberg 表数据。此外，`spark/v3.2`、`spark/v3.3`、`spark/v3.4`、`spark/v3.5` 各模块也都引入了 `arrow-vector`，用于 Spark 引擎与 Iceberg 之间的列式数据交换。

由于 15.0.0 → 15.0.1 是 patch 版本升级，按 semver 规范只包含 bug 修复与向后兼容的改进，不引入破坏性 API 变更。Dependabot 通过 `updated-dependencies` 元数据声明这是一次 `version-update:semver-patch` 类型的升级，目的是让 Iceberg 始终跟进上游 Arrow 的最新修复版本，避免已知 bug 影响数据读取路径的正确性与稳定性。

## 如何达成设计目的

Iceberg 使用 Gradle 版本目录（Version Catalog）集中管理依赖版本，所有依赖版本声明统一存放在 `gradle/libs.versions.toml` 中。Arrow 的版本通过一个版本别名 `arrow` 统一声明，再被 `arrow-memory-netty` 与 `arrow-vector` 两个模块引用 `version.ref = "arrow"`。

因此本次升级只需在版本目录中修改一行：

```toml
- arrow = "15.0.0"
+ arrow = "15.0.1"
```

由于两个 Arrow 制品都通过 `version.ref` 引用同一别名，这一处改动会同时将 `org.apache.arrow:arrow-memory-netty` 与 `org.apache.arrow:arrow-vector` 升级到 15.0.1。这种集中化的版本管理方式是 Dependabot 能以最小 diff 完成依赖升级的关键设计——既避免散落在多个 `build.gradle` 中的版本硬编码，也保证两个相关联的 Arrow 制品版本始终一致（Arrow 的 vector 与 memory 模块在 ABI 上必须保持同版本，否则会出现运行时不兼容）。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Arrow 版本别名从 `15.0.0` 升级到 `15.0.1`，带动 `arrow-memory-netty` 与 `arrow-vector` 两个传递依赖同步升级。

**工作逻辑**：

文件位于版本目录根，第 24 行附近声明了 `arrow` 别名。改动前后的上下文如下：

```toml
activation = "1.1.1"
aliyun-sdk-oss = "3.10.2"
antlr = "4.9.3"
aircompressor = "0.26"
arrow = "15.0.1"   # 由 15.0.0 升级
avro = "1.11.3"
assertj-core = "3.25.3"
awaitility = "4.2.1"
```

后续第 79-80 行的两条模块声明 `arrow-memory-netty = { module = "org.apache.arrow:arrow-memory-netty", version.ref = "arrow" }` 与 `arrow-vector = { module = "org.apache.arrow:arrow-vector", version.ref = "arrow" }` 通过 `version.ref` 间接引用此别名，因此无需修改即可一并升级。

值得注意的细节：在 `build.gradle` 的 `:iceberg-arrow` 项目依赖块（约第 796-805 行）中，`arrow.vector` 与 `arrow.memory.netty` 都显式 `exclude` 了 `io.netty:netty-buffer`、`io.netty:netty-common` 与 `com.google.code.findbugs:jsr305`，并通过 `runtimeOnly libs.netty.buffer` 单独管理 netty-buffer 版本；测试依赖还通过 `arrow.memory.netty` 间接引入 `netty-common` 以保证与 Arrow 同版本。这些 exclude 规则在升级到 15.0.1 后依然适用，因为 Arrow 15.0.1 仍然通过 netty 提供内存分配实现，依赖结构未变。

## 小结

本提交是一次由 Dependabot 自动完成的最小化依赖升级，仅修改 `gradle/libs.versions.toml` 一行，将 Apache Arrow 从 15.0.0 升级到 15.0.1（patch 版本）。Arrow 是 Iceberg `:iceberg-arrow` 模块以及 Spark v3.2/3.3/3.4/3.5 各模块用于列式数据读取与交换的核心依赖。

- **影响范围**：仅依赖版本声明，无源码或测试代码改动；运行时影响为引入 Arrow 15.0.1 的 bug 修复。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支当前的 `arrow` 版本（工作区显示为 `12.0.1`）远低于 15.0.x，说明 1.4.x 与 main 在 Arrow 主版本上已经分叉。直接 cherry-pick 本提交会与 1.4.x 的 `12.0.1` 发生冲突，且即便强行升级到 15.0.1 也意味着跨多个 Arrow 主版本的跳跃（12.x → 15.x），可能引入 API 不兼容。因此不建议单独回迁本提交；若 1.4.x 需要跟进 Arrow 修复，应评估是否整体升级 Arrow 主版本，并重新验证 `:iceberg-arrow` 与各 Spark 模块的兼容性。
