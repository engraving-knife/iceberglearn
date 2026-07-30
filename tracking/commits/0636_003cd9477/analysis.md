# 提交 0636：Build: Bump arrow from 15.0.1 to 15.0.2

## 提交信息

- **序号**：0636 / 4088
- **哈希**：003cd9477ac2d6226a776110eaf80af8ef422e38
- **短哈希**：003cd9477
- **日期**：2024-03-27（Wed Mar 27 17:20:00 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump arrow from 15.0.1 to 15.0.2 (#10034)
- **PR/Issue**：#10034

## 总体目的

本提交由 GitHub Dependabot 自动生成，将 Apache Arrow 从 15.0.1 升级到 15.0.2，是一次 `version-update:semver-patch` 级别的补丁升级。Dependabot 在元数据中声明了两个受影响的制品：`org.apache.arrow:arrow-memory-netty` 与 `org.apache.arrow:arrow-vector`（均为 `direct:production`）。按 semver 规范，patch 升级只包含 bug 修复与向后兼容的改进，不引入破坏性 API 变更。

Arrow 在 Iceberg 中的角色是列式内存格式的底座。Iceberg 的 `:iceberg-arrow` 模块依赖 `arrow-vector`（提供 `ValueVector` 等列式向量类型）与 `arrow-memory-netty`（提供基于 Netty 的堆外内存分配器 `ArrowBuf`），用于把 Parquet 文件中的列式数据向量化地读入 Arrow 内存并向计算引擎（Spark）输出批次。`:iceberg-arrow` 是 Iceberg 扫描执行层与 Apache Arrow 之间的桥接层，`ArrowReader` 即位于此模块。各 Spark 集成模块（spark/v3.2~v3.5）通过 `implementation project(':iceberg-arrow')` 间接消费 Arrow，并对 `org.apache.arrow` 做 relocate shade 以避免与 Spark 自带 Arrow 版本冲突。

本次升级的目的就是让 `:iceberg-arrow` 及下游 Spark 集成跟进 Arrow Java 15.0.x 的最新补丁，获取 15.0.1 之后累积的稳定性修复，降低与上游 Arrow 生态的兼容性风险。

## 如何达成设计目的

Iceberg 采用 Gradle 版本目录（Version Catalog）集中管理依赖版本，所有版本声明统一存放在 `gradle/libs.versions.toml` 中。Arrow 的版本通过单个版本别名 `arrow` 统一声明，再被 `arrow-memory-netty` 与 `arrow-vector` 两个库别名以 `version.ref = "arrow"` 引用。

因此本次升级只需在版本目录中修改一行：

```toml
- arrow = "15.0.1"
+ arrow = "15.0.2"
```

由于两个 Arrow 制品都通过 `version.ref` 引用同一别名，这一处改动会同时把 `org.apache.arrow:arrow-memory-netty` 与 `org.apache.arrow:arrow-vector` 升级到 15.0.2。这种集中化版本管理是 Dependabot 能以最小 diff 完成升级的关键设计——既避免散落在多个 `build.gradle` 中的版本硬编码，也保证 vector 与 memory 两个在 ABI 上必须同版本的 Arrow 构件版本始终一致。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Arrow（向量与内存分配器）的锁定版本从 `15.0.1` 提升到 `15.0.2`。

**工作逻辑**：

改动位于版本声明区（第 27 行附近），原行 `arrow = "15.0.1"` 被改为 `arrow = "15.0.2"`，上下文如下：

```toml
activation = "1.1.1"
aliyun-sdk-oss = "3.10.2"
antlr = "4.9.3"
aircompressor = "0.26"
arrow = "15.0.2"   # 由 15.0.1 升级
avro = "1.11.3"
assertj-core = "3.25.3"
awaitility = "4.2.1"
```

该版本别名被库定义区两个库别名引用（约第 79-80 行）：

- `arrow-memory-netty = { module = "org.apache.arrow:arrow-memory-netty", version.ref = "arrow" }`
- `arrow-vector = { module = "org.apache.arrow:arrow-vector", version.ref = "arrow" }`

二者又被根 `build.gradle` 的 `project(':iceberg-arrow')` 依赖块消费（约第 796-820 行）：

- `implementation(libs.arrow.vector) { exclude group: 'io.netty', module: 'netty-buffer'; exclude group: 'io.netty', module: 'netty-common'; exclude group: 'com.google.code.findbugs', module: 'jsr305' }`——生产期引入 Arrow 向量 API，排除 netty-buffer/netty-common 以由 Iceberg 统一管控 Netty 版本，排除 jsr305 以避免注解依赖冲突。
- `implementation(libs.arrow.memory.netty) { exclude group: 'com.google.code.findbugs', module: 'jsr305'; exclude group: 'io.netty', module: 'netty-common'; exclude group: 'io.netty', module: 'netty-buffer' }`——生产期引入基于 Netty 的内存分配器，同样排除 netty 缓冲/公共构件。
- `runtimeOnly libs.netty.buffer`——显式补入 netty-buffer 运行期依赖，由 Iceberg 锁定 Netty 版本。
- `testImplementation libs.arrow.memory.netty`——测试期通过 Arrow 的 netty 分配器传递引入 netty-common（注释说明为确保 `ArrowReaderTest` 用例获得与 `arrow-memory-netty` 一致的 netty-common 版本）。

本提交未触碰这些 exclude/对齐规则，说明升级到 15.0.2 后 Arrow 依然通过 netty 提供内存分配实现、依赖结构未变，既有 Netty 协调策略仍然有效。

## 小结

本提交是 Dependabot 触发的 Arrow patch 升级：仅修改 `gradle/libs.versions.toml` 一行，把 `arrow` 由 `15.0.1` 升至 `15.0.2`，连带把 `arrow-memory-netty` 与 `arrow-vector` 两个构件升版。Arrow 是 `:iceberg-arrow` 模块（生产 + 测试）及 Spark v3.2~v3.5 集成用于列式数据读取与交换的核心依赖。

- **影响范围**：仅依赖版本声明一处，无源码或测试代码改动；运行时影响为引入 Arrow 15.0.2 的 bug 修复。对 Iceberg 公共 API 无影响。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支当前的 `arrow` 版本为 `12.0.1`（见 1.4.x 的 `gradle/libs.versions.toml`），与 main 的 15.0.x 之间存在 13.x、14.x 两个主版本的差距。直接 cherry-pick 本提交会产生上下文冲突（1.4.x 该行为 `arrow = "12.0.1"`），且即便强行升到 15.0.2 也意味着跨多个 Arrow 主版本的跳跃，可能引入向量 API、Allocator 语义等不兼容。因此不建议单独回迁本提交；若 1.4.x 需要跟进 Arrow 修复，应整体评估 Arrow 主版本升级并重新验证 `:iceberg-arrow` 与各 Spark 模块的兼容性。
