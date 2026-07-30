# 提交 0154：Build: Bump arrow from 14.0.0 to 14.0.1 (#9043)

## 提交信息

- **序号**：0154 / 4088
- **哈希**：28a95652540515252728b90e85d00e09f5d67f31
- **短哈希**：28a956525
- **日期**：2023-11-13 10:00:35 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump arrow from 14.0.0 to 14.0.1 (#9043)
- **PR/Issue**：#9043

## 总体目的

这个提交由 Dependabot 自动生成，把 Iceberg 依赖的 Apache Arrow 库从 `14.0.0` 升级到 `14.0.1`，属于 `version-update:semver-patch` 级别的依赖更新。Dependabot 在 PR 描述中说明本次一并更新两个共享 `arrow` 版本引用的 artifact：`org.apache.arrow:arrow-memory-netty` 从 14.0.0 到 14.0.1，`org.apache.arrow:arrow-vector` 从 14.0.0 到 14.0.1。

Apache Arrow 是跨语言的内存列式数据格式，Iceberg 的 `iceberg-arrow` 模块依赖它来提供内存中的列式向量读写能力，主要场景包括 Spark/Flink 等引擎与 Iceberg 之间的内存数据交换、以及 Arrow 格式的读取与转换。`arrow-vector` 提供 Arrow 的列式向量类型（`FieldVector`、`VarCharVector` 等），`arrow-memory-netty` 则提供基于 Netty 的内存分配器（`BufferAllocator`），用于高效管理 Arrow 向量背后的堆外内存。两者版本必须对齐以避免 ABI 不一致。

本次升级是 14.0.0 首个 patch（14.0.1），按 Dependabot 分类属于 `version-update:semver-patch`，意味着只有兼容性补丁，不包含破坏性变更，风险较低。14.0.1 通常包含自 14.0.0 发布以来 Arrow 社区积累的 bug 修复与稳定性改进。Iceberg 维护者合并此 PR 即表示认可升级在 Iceberg 使用范围内是兼容的。

## 如何达成设计目的

改动只涉及一处版本常量：在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 `arrow = "14.0.0"` 改为 `arrow = "14.0.1"`。由于 `arrow-memory-netty` 和 `arrow-vector` 两个 artifact 都通过 `version.ref = "arrow"` 共享该版本常量，一次修改即可同步升级两个 artifact，无需逐个修改各模块的 build.gradle。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `org.apache.arrow:arrow-memory-netty` 与 `org.apache.arrow:arrow-vector` 的共享版本从 14.0.0 升级到 14.0.1。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区（第 26 行附近），原行 `arrow = "14.0.0"` 被改为 `arrow = "14.0.1"`。该版本常量在版本目录中分别被 `arrow-memory-netty = { module = "org.apache.arrow:arrow-memory-netty", version.ref = "arrow" }`（第 79 行附近）和 `arrow-vector = { module = "org.apache.arrow:arrow-vector", version.ref = "arrow" }`（第 80 行附近）引用。在根 `build.gradle` 的 `project(':iceberg-arrow')` 配置中，`arrow-vector` 作为 `implementation` 引入并提供 `netty-common` 排除规则以避免版本冲突，`arrow-memory-netty` 作为 `implementation` 与测试依赖引入，保证 Arrow 向量所用的堆外内存分配器与向量类型版本一致。升级后，`iceberg-arrow` 模块会解析到 Arrow 14.0.1，获取该版本的修复与改进。

## 小结

该提交由 Dependabot 自动将 Apache Arrow（`arrow-memory-netty` 与 `arrow-vector`）从 14.0.0 升级到 14.0.1，使 Iceberg 的 Arrow 内存列式集成跟进 Arrow 最新 patch 版本，获取 bug 修复与稳定性改进。
