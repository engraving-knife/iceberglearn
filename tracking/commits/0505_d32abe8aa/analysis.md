# 提交 0505：Build: Bump arrow from 14.0.2 to 15.0.0 (#9574)

## 提交信息

| 字段 | 内容 |
| --- | --- |
| 序号 | 0505 |
| 完整哈希 | d32abe8aa589e6c93cdc96dc7ae77ca5369af106 |
| 短哈希 | d32abe8aa |
| 日期 | 2024-02-14（Wed Feb 14 21:18:01 2024 +0100） |
| 作者 | dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com> |
| 说明 | Build: Bump arrow from 14.0.2 to 15.0.0 (#9574) |
| PR | #9574 |
| 依赖类型 | direct:production（arrow-memory-netty、arrow-vector 均为 direct:production） |
| 更新类型 | version-update:semver-major（主版本升级） |

提交统计：1 个文件修改，1 行新增，1 行删除。

涉及文件：`gradle/libs.versions.toml`

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 的 Arrow 集成依赖从 `14.0.2` 升级到 `15.0.0`，跨 1 个主版本。Apache Arrow 是跨语言内存列式数据格式，Iceberg 的 `:iceberg-arrow` 模块以 Arrow 向量（`arrow-vector`）与基于 Netty 的内存分配器（`arrow-memory-netty`）为底座，提供 Iceberg 数据与 Arrow 向量之间的零拷贝读写桥接，使下游引擎（Spark、Flink、Trino 等）能以 Arrow 格式高效消费 Iceberg 表数据。该依赖同时进入生产类路径（`implementation`）与测试类路径（`testImplementation`），`iceberg-arrow` 模块将其作为核心运行时依赖。

`14.0.2 → 15.0.0` 属主版本级别（`version-update:semver-major`），是本批次五次依赖升级中幅度最大的一类。Arrow 主版本升级可能伴随向量类型布局调整、分配器 API 演进或对齐/字节序语义变化，理论上存在破坏性 API 变更的风险。但本提交仅改动版本目录一行版本号，未同步修改任何 `:iceberg-arrow` 源码或构建脚本中的 exclude 规则，说明在合并时 `:iceberg-arrow` 既有代码与 Arrow 15.0.0 仍兼容（CI 验证通过），Iceberg 对 Arrow 向量 API 的使用点落在 14.x→15.x 保持稳定的子集内。与同批次的 0502（assertj，patch）、0503（tez，patch）、0504（awssdk，minor）相比，本提交虽为主版本升级，但实际改动面同样是一行，反映 Dependabot 以最小改动推进升级、由 CI 守护兼容性的工作模式。

需要关注的是，`:iceberg-arrow` 在 `build.gradle`（约第 796-805 行）对 `arrow-vector` 与 `arrow-memory-netty` 显式排除了 `io.netty:netty-buffer`、`io.netty:netty-common` 与 `com.google.code.findbugs:jsr305`，并在运行期以 `runtimeOnly libs.netty.buffer` 显式补入 netty-buffer、测试期通过 `testImplementation libs.arrow.memory.netty` 拉入 netty-common（见同块第 817-820 行注释说明）。这些 exclude/对齐机制是为控制 Netty 版本冲突而设，Arrow 15.0.0 对 Netty 的依赖范围若未变则无需调整；本提交未触碰这些规则，表明升级后既有 Netty 协调策略仍然有效。

## 如何达成设计目的

实现路径是单点修改：在 `gradle/libs.versions.toml` 的版本声明区把 `arrow = "14.0.2"` 改为 `arrow = "15.0.0"`。库定义区 `arrow-memory-netty` 与 `arrow-vector` 通过 `version.ref = "arrow"` 引用该变量，无需改动；`build.gradle` 中 `:iceberg-arrow` 的 `implementation(libs.arrow.vector) {...}` 与 `implementation(libs.arrow.memory.netty) {...}` 引用亦无需改动。一处版本号变更即把 `:iceberg-arrow` 生产与测试类路径上的两个 Arrow 构件统一升到 15.0.0。

## 修改详情

### `gradle/libs.versions.toml`

修改目的：把 Arrow（向量与内存分配器）的锁定版本从 `14.0.2` 提升到 `15.0.0`。

工作逻辑：

- 版本声明区第 24 行附近：`arrow = "14.0.2"` → `arrow = "15.0.0"`。
- 该版本变量被库定义区第 79-80 行附近的两个库别名引用：
  - `arrow-memory-netty = { module = "org.apache.arrow:arrow-memory-netty", version.ref = "arrow" }`
  - `arrow-vector = { module = "org.apache.arrow:arrow-vector", version.ref = "arrow" }`
- 二者又被根 `build.gradle` 第 796-805 行（`:iceberg-arrow` 项目，约第 786 行起）消费：
  - `implementation(libs.arrow.vector) { exclude group: 'io.netty', module: 'netty-buffer'; exclude group: 'io.netty', module: 'netty-common'; exclude group: 'com.google.code.findbugs', module: 'jsr305' }`——生产期引入 Arrow 向量 API，排除 netty-buffer/netty-common 以由 Iceberg 统一管控 Netty 版本，排除 jsr305 以避免注解依赖冲突。
  - `implementation(libs.arrow.memory.netty) { exclude group: 'com.google.code.findbugs', module: 'jsr305'; exclude group: 'io.netty', module: 'netty-common'; exclude group: 'io.netty', module: 'netty-buffer' }`——生产期引入基于 Netty 的内存分配器，同样排除 netty 缓冲/公共构件。
  - 第 807 行 `runtimeOnly libs.netty.buffer` 显式补入 netty-buffer 运行期依赖；第 820 行 `testImplementation libs.arrow.memory.netty` 在测试期通过 Arrow 的 netty 分配器传递引入 netty-common（注释说明为确保 `ArrowReaderTest` 用例获得与 `arrow-memory-netty` 一致的 netty-common 版本）。
- 升级为主版本级别，但本提交未改动任何源码与 exclude 规则，依赖 CI 验证 `:iceberg-arrow`（含 `ArrowReader`、`ArrowSchema`、`ArrowArray` 等桥接代码与 `ArrowReaderTest` 测试）在 15.0.0 下编译并通过。Arrow 15.x 相对 14.x 在向量读写与内存分配的核心 API 上保持了 Iceberg 使用点的兼容性，因此最小改动即可完成升级。

## 小结

本提交是 Dependabot 触发的 Arrow 主版本升级：将 `gradle/libs.versions.toml` 中 `arrow` 由 `14.0.2` 升至 `15.0.0`，连带把 `arrow-memory-netty` 与 `arrow-vector` 两个构件升版。该依赖经 `:iceberg-arrow` 模块以 `implementation`（生产）与 `testImplementation`（测试）形式消费，是 Iceberg 与 Arrow 列式内存格式桥接的底座，并进入该模块发布产物。升级属主版本级别（本批次幅度最大），但实际仅改一行版本号，未同步改动源码或 Netty exclude 规则，说明 `:iceberg-arrow` 既有代码在 Arrow 15.0.0 下兼容并通过 CI。对 Iceberg 公共 API 无影响；回迁 1.4.x 时建议在升级后重点回归 `:iceberg-arrow` 测试（尤其 `ArrowReaderTest`）以确认 Netty 协调策略仍有效。
