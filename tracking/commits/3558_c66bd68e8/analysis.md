# 提交 3558：Build: Bump at.yawk.lz4:lz4-java from 1.10.4 to 1.11.0 (#16038)

## 提交信息

- **序号**：3558 / 4088
- **哈希**：c66bd68e86d52ae6df219e7c6b9162890fe64990
- **短哈希**：c66bd68e8
- **日期**：2026-04-19 07:15:58 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump at.yawk.lz4:lz4-java from 1.10.4 to 1.11.0 (#16038)
- **PR/Issue**：#16038

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 LZ4 压缩库的 Java 实现 `at.yawk.lz4:lz4-java` 从版本 1.10.4 升级到 1.11.0。LZ4 是一种无损压缩算法，Iceberg 在数据读写时使用它进行压缩以减少存储空间和 I/O 开销。这是一个 semver-minor（次版本）升级，通常包含新功能和小幅改进，按照语义化版本约定应保持向后兼容。

## 如何达成设计目的

Dependabot 自动检测到 `gradle/libs.versions.toml` 中定义的 lz4-java 版本有新的次版本可用，自动创建 PR 升级版本声明。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 lz4-java 版本声明。

**工作逻辑**：
将 lz4-java 版本从 `1.10.4` 升级到 `1.11.0`（具体变量名在 toml 中定义）。该库被 Iceberg 用于数据压缩，升级后可获取 1.11.0 版本中的新功能和改进。

## 总结

这是一个常规的依赖维护提交，通过次版本升级获取 lz4-java 1.11.0 的改进和新功能，保持压缩依赖的最新状态。
