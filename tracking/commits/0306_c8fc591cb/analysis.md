# 提交 0306：Build: Bump arrow from 14.0.1 to 14.0.2 (#9372)

## 提交信息

- **序号**：0306 / 4088
- **哈希**：c8fc591cba1dbdc8ba24f8f04071e93fe7802390
- **短哈希**：c8fc591cb
- **日期**：2023-12-24 11:08:58 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump arrow from 14.0.1 to 14.0.2 (#9372)
- **PR/Issue**：#9372

## 总体目的

本提交由 Dependabot 自动生成，把 Iceberg 依赖的 Apache Arrow 版本从 14.0.1 升级到 14.0.2，属于 version-update:semver-patch（补丁版本升级），按 Dependabot 分类标准意味着只有兼容性变更，不包含破坏性 major 升级，风险较低。Arrow 14.0.2 于 2023-12-18 由 Apache Arrow PMC 发布，是一个以 bugfix 为主的维护版本，共 33 个已修复 issue、来自 11 位贡献者，主要修复了 14.0.0/14.0.1 引入的若干回归，包括 Parquet 读取 binary/string 列的性能回归、并行读取 Parquet 文件的回归、读取旧版本 Parquet 文件的兼容性 bug，以及 S3FileSystem 删除显式创建子目录的回归等。

Iceberg 中 Arrow 通过 gradle/libs.versions.toml 中的 arrow 版本常量集中驱动两个 artifact：org.apache.arrow:arrow-memory-netty（基于 Netty 的内存分配器，提供 ArrowBuf 的内存管理）和 org.apache.arrow:arrow-vector（Arrow 列式内存模型的核心 ValueVector 实现）。这两个 artifact 是 Spark 在内存中操作列式数据（例如 ArrowReader、VectorizedArrowBatch、UDF 序列化等场景）的底层依赖。本次升级让 Iceberg 跟进 Arrow Java 的最新补丁版本，获取稳定性修复并降低与最新 Arrow 生态的兼容性风险。

虽然 14.0.2 修复的内容主要面向 Arrow 的 C++/Python/R 实现（Iceberg 只消费 Arrow Java artifact），但保持 Arrow Java artifact 在最新 patch level 仍能拿到 Java 端的同步 bugfix，并避免后续若有依赖链触发 transitive 升级时出现版本漂移。

## 如何达成设计目的

改动只涉及一处版本常量：在 Gradle 版本目录文件 gradle/libs.versions.toml 中，将 arrow = "14.0.1" 改为 arrow = "14.0.2"。所有通过 version.ref = "arrow" 引用该常量的 artifact（arrow-memory-netty、arrow-vector）会在构建时自动解析到新版本，无需逐个修改各模块 build.gradle。

## 修改详情

### gradle/libs.versions.toml

**修改目的**：将 Apache Arrow 版本从 14.0.1 升级到 14.0.2，作为 Dependabot 周期依赖扫描产生的 patch 升级。

**工作逻辑**：gradle/libs.versions.toml 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区（约第 26 行附近），原行 arrow = "14.0.1" 被改为 arrow = "14.0.2"。该版本常量通过 version.ref = "arrow" 被两个 artifact 引用：

- arrow-memory-netty = { module = "org.apache.arrow:arrow-memory-netty", version.ref = "arrow" }
- arrow-vector = { module = "org.apache.arrow:arrow-vector", version.ref = "arrow" }

这两个 artifact 在 Iceberg 中的实际影响范围（基于 build.gradle 分析）：

- iceberg-arrow 模块（根 build.gradle 中 project(':iceberg-arrow')）：implementation(libs.arrow.vector) 和 implementation(libs.arrow.memory.netty)，并在引入时排除 io.netty:netty-buffer、io.netty:netty-common、com.google.code.findbugs:jsr305，由 Iceberg 自身声明 runtimeOnly libs.netty.buffer 来对齐 Netty 版本，避免与 Spark 自带的 Netty 产生冲突；测试侧还额外 testImplementation libs.arrow.memory.netty 以运行 ArrowReaderTest 等用例。该模块还依赖 iceberg-parquet（间接关联，但 parquet 模块本身不直接依赖 Arrow）。
- Spark v3.2/v3.3/v3.4/v3.5 集成模块（spark/v3.x/build.gradle）：通过 implementation project(':iceberg-arrow') 间接消费 Arrow，并对 org.apache.arrow 做 relocate（relocate 'org.apache.arrow', 'org.apache.iceberg.shaded.org.apache.arrow'），将 Arrow 字节码 shade 进 Spark 集成 jar，避免与 Spark 运行时自带的 Arrow 产生版本冲突。Flink 模块不直接依赖 Arrow。

升级后，相关 artifact 及其传递依赖（如 arrow-format、arrow-memory-core、arrow-memory-unsafe 等）会按 14.0.2 BOM 解析到对应的较新版本，获取该版本中 Java 端同步的 bugfix。

## 小结

该提交由 Dependabot 自动将 Apache Arrow 从 14.0.1 升级到 14.0.2（一个 semver-patch 级别的 bugfix 维护版本），使 Iceberg 的 iceberg-arrow 模块及 Spark v3.2~v3.5 集成跟进 Arrow Java 的最新补丁版本，获取稳定性修复与兼容性改进。改动局限在 gradle/libs.versions.toml 一行版本常量，影响范围通过 version.ref 集中传播到两个 Arrow artifact，符合 Iceberg 一贯的"版本目录统一管理依赖"的工程实践。
