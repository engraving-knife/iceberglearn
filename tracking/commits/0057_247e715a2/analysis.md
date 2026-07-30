# 提交 0057：Build: Bump arrow from 12.0.1 to 13.0.0 (#8785)

## 提交信息

- **序号**：0057 / 4088
- **哈希**：247e715a26677c563b8a41cb6ec898204fc1b1db
- **短哈希**：247e715a2
- **日期**：2023-10-16 07:56:44 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump arrow from 12.0.1 to 13.0.0 (#8785)
- **PR/Issue**：#8785

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，将 Iceberg 所依赖的 Apache Arrow 从 12.0.1 升级到 13.0.0。受影响的制品有两个：`org.apache.arrow:arrow-memory-netty` 与 `org.apache.arrow:arrow-vector`。这两个制品在 Iceberg 的 `arrow/` 模块中被使用，用于支持基于 Arrow 列式内存格式的向量化读取，是 Iceberg 性能敏感路径上的关键依赖（例如将 Parquet/ORC 数据转换为 Arrow 向量供 Spark、Flink 等引擎消费）。

本次升级属于 semver-major（主版本号）升级（12.x → 13.x），是这批 5 个提交中风险最高的一项。Apache Arrow 的 Java 制品在主版本升级时历史上多次引入过 API 不兼容变更，例如向量类型构造方式、Allocator 生命周期、字段元数据（Field）处理等方面的调整。Arrow 13.0.0 于 2023 年 8 月发布，相对 12.0.1 带来了若干新特性与 bug 修复，但也可能存在影响 Iceberg `arrow/` 模块现有代码的 API 变更。

Dependabot 在此提交中只动了版本号、未改动源代码，这意味着升级的兼容性验证完全依赖 Iceberg 的 CI（特别是 `arrow/` 模块的单元测试以及涉及向量化读取的 Spark/Flink 集成测试）。从 Iceberg 演进角度看，跟进 Arrow 主版本是必要的：Iceberg 1.4.x 时期已经在持续优化 Arrow 向量化读取路径（如 PR #8568 "Arrow: Propagate correct field info while reading metadata columns"、PR #8466 "Arrow, Spark 3.4: Support vectorized reads with struct constants"），保持 Arrow 依赖为较新主版本可以让这些优化基于上游最新的向量 API 与性能改进。

## 如何达成设计目的

与其它依赖升级一致，Dependabot 利用 Gradle 集中式版本目录来完成升级。在 [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml) 中，`arrow` 版本变量被 `arrow-memory-netty` 与 `arrow-vector` 两个库声明通过 `version.ref = "arrow"` 引用，因此只需修改一行版本号即可让两个制品同步升级，无需触碰任何 `build.gradle` 或 Java 源码。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Arrow 版本变量从 12.0.1 提升到 13.0.0，使 `arrow-memory-netty` 与 `arrow-vector` 两个制品同步升级。

**工作逻辑**：在 `[versions]` 段中，将 `arrow = "12.0.1"` 改为 `arrow = "13.0.0"`。由于 `arrow-memory-netty`（提供基于 Netty 的内存分配器，用于 off-heap 向量内存管理）与 `arrow-vector`（提供 Arrow 向量类型，如 `IntVector`、`VarCharVector`、`StructVector` 等）都通过 `version.ref` 引用同一变量，二者会同时升到 13.0.0。考虑到这是 major 版本升级，潜在风险包括：Arrow 13 可能修改了向量的构造/分配 API、Allocator 的 close 语义、或 `VectorSchemaRoot`/`Field` 的行为；这些都需要通过 `arrow/` 模块的测试以及依赖 Arrow 的向量化读取路径（Spark/Flink 集成测试）来回归验证。若 CI 全绿，则 Iceberg 获得了 Arrow 13 的改进与修复；若存在不兼容，后续提交会跟进源码适配（本提交本身不含适配代码）。

## 小结

通过一行版本目录改动，将 Iceberg 向量化读取所依赖的 Apache Arrow 从 12.0.1 跨主版本升级到 13.0.0，是本批提交中风险最高的一项，兼容性依赖 CI 中 `arrow/` 模块及相关向量化读取测试来保障。
