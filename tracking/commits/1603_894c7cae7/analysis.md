# 提交 1603 894c7cae7 分析

## 提交信息
- 哈希：894c7cae75d1fe16074ae56222ae846491d02752
- 日期：2025-01-19 22:13:33 +0100
- 作者：dependabot[bot]
- 消息：Build: Bump org.apache.datasketches:datasketches-java (#12000)

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 Apache DataSketches 的 Java 实现 `datasketches-java` 从 `6.1.1` 升级到 `6.2.0`。DataSketches 是一个用于快速、近似计算的随机草图算法库，Iceberg 利用它实现大规模数据集的近似统计聚合（如 NDV 即不同值数量估计、分位数估计、KLL 草图等），主要应用于表统计信息（statistics）的采集与存储，支撑查询优化器的代价估算。

本次升级为次版本（Minor）升级（6.1.1 → 6.2.0），按照 DataSketches 的版本约定，次版本升级可能新增 API 或功能特性，但通常保持向后兼容。Dependabot 通过 Pull Request #12000 提交该升级建议。

升级草图库有助于获取上游新增的算法改进、精度提升或性能优化，对 Iceberg 的统计信息采集模块（如 `oap` / statistics 相关模块）的准确性和效率有正向价值。

## 如何达成设计目的

设计思路与前述依赖升级一致：通过修改 Gradle 版本目录 `gradle/libs.versions.toml` 集中升级依赖版本，所有引用 `datasketches` 键的子模块在构建时自动同步到新版本，无需修改各模块的构建脚本，保证版本一致性。

### 修改详情

#### gradle/libs.versions.toml

修改了第 37 行附近的版本声明：

- `datasketches = "6.1.1"` → `datasketches = "6.2.0"`

`datasketches` 是版本目录中用于引用 `org.apache.datasketches:datasketches-java` 的键名。在 Iceberg 中，统计信息相关模块（如 core 模块的 statistics 包、Spark/Flink 集成模块中的统计信息采集逻辑）通过 `libs.datasketches` 这样的方式引用，统一升级后所有引用点都会使用 6.2.0 版本。

由于 DataSketches 的序列化格式（如 KLL 草图、Theta 草图等）通常在不同版本间保持向后读取兼容，已写入的统计信息文件在新版本下仍可被正确解析。

## 小结

本次提交将 DataSketches 升级至 6.2.0，获取上游次版本带来的算法与功能改进。修改范围极小，仅涉及版本目录的一行变更，不触碰业务代码。

回迁到 1.4.x 分支的注意事项：
- 次版本升级需关注 6.2.0 是否引入了新的序列化格式或 API 签名变更。建议查阅 DataSketches 6.2.0 的 Release Notes 确认兼容性。
- 重点回归测试统计信息采集与读取路径（如 `TestStatisticsFile`、KLL/Theta 草图相关测试），确认旧版本写入的统计文件在新版本下仍可读取。
- 若 1.4.x 已包含表统计信息相关功能，回迁后需确保序列化格式向前兼容，避免影响已存在的统计文件。
