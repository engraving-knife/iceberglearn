# 提交 1034：Flink: remove v1.17 module

## 提交信息

- **序号**：1034 / 4088
- **哈希**：0d8f2c42ff0d1eff6a6d05f248da7085163ac93f
- **短哈希**：0d8f2c42f
- **日期**：2024-08-06 08:45:56 -0700
- **作者**：Steven Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: remove v1.17 module
- **PR/Issue**：无（提交说明中无 #编号；属于 Flink 1.20 支持系列重构的一部分，对应后续 PR #10888 中提到的 #10881）

## 总体目的

本提交是 Flink 1.20 模块引入四步序列（1032-1035）的第三步。Iceberg 对每个引擎版本有维护周期的约定：当较新版本稳定后，会淘汰（deprecate 并移除）最老的版本。Flink 1.17 已进入 Deprecated 状态（自 1.6.0 起，参见提交 1040 对 `site/docs/multi-engine-support.md` 的更新），而本次又引入了 1.20，按照"支持最近三个大版本"的策略，需要将 1.17 模块从代码库中移除，使维护的 Flink 版本收敛为 1.18、1.19、1.20 三条线。

移除 v1.17 子模块可以减少构建矩阵的规模、降低 CI 负担，并避免为已淘汰版本继续做兼容性适配。需要说明的是，本提交只删除了 `flink/v1.17/` 目录下的源码与构建文件；构建系统（`settings.gradle`、`flink/build.gradle`、`gradle/libs.versions.toml`、`gradle.properties`、`jmh.gradle`、CI 矩阵）中对 1.17 的引用清理将在后续提交 1035 中统一完成，以保证每步提交的职责单一。

## 如何达成设计目的

直接删除 `flink/v1.17/` 整个目录。git 将其识别为 311 个文件的纯删除（delete），共约 55202 行删除、0 行新增，所有文件内容被整体移除。这一步不修改任何其他文件，仅做目录级删除。

v1.17 目录的文件数（311）少于 v1.19/v1.20 的 325，是因为部分较新的测试与辅助文件（如 `source/SplitHelpers.java`、`source/SqlHelpers.java`、`source/TableSourceTestBase.java`、`source/TestBoundedTableFactory.java`、`sink/TestFlinkIcebergSinkExtended.java`、`sink/TestFlinkIcebergSinkV2Base.java` 等）只在 1.18+ 模块中添加，未回填到已进入 deprecated 状态的 v1.17。

## 修改详情

### `flink/v1.17/**`（311 个文件，纯删除）

**修改目的**：移除已淘汰的 Flink 1.17 子模块的全部源码、测试与构建文件，使仓库仅保留 1.18/1.19/1.20 三个维护中的 Flink 版本模块。

**工作逻辑**：共删除 311 个文件，覆盖 v1.17 子模块的完整内容，包括：

- `build.gradle` 构建脚本
- `flink-runtime/LICENSE`、`flink-runtime/NOTICE` 运行时 jar 许可证
- `flink/src/main/java/org/apache/iceberg/flink/**` 下全部主源码类（Catalog、Sink、Source、Shuffle、Maintenance、data、util 等子包，结构与 v1.19/v1.20 基本一致，但存在少量与 1.17 API 兼容性相关的差异，如 `FlinkAppenderFactory` 多 6 行、`SortKeySerializer` 多 9 行等）
- `flink/src/test/java/**` 下全部测试类
- `flink/src/jmh/java/**` 下的 `MapRangePartitionerBenchmark`
- `META-INF/services/org.apache.flink.table.factories.Factory` 与 `...TableFactory` SPI 注册文件

所有文件均为纯删除，无内容修改、无新增。

## 小结

- **成效**：从代码库中彻底移除 Flink 1.17 子模块（311 文件、约 5.5 万行），使维护的 Flink 版本由 1.17/1.18/1.19 调整为 1.18/1.19/1.20（1.20 由 1032 引入）。配合提交 1035 对构建配置的清理，仓库不再构建 1.17 runtime。
- **影响范围**：仅 `flink/v1.17/` 目录，311 个文件删除，无其他文件改动。1.18 模块不受影响。
- **回迁到 1.4.x 的注意事项**：**不应回迁**。1.4.x 分支仍需支持 Flink 1.15/1.16/1.17，直接删除 v1.17 会破坏 1.4.x 的构建与发布能力。是否移除 1.17 应由 1.4.x 自身的维护策略决定，而非跟随 main 分支。此外，本提交未清理构建系统中的 1.17 引用，单独 cherry-pick 会导致仓库处于不一致状态（构建脚本仍引用已删除的 v1.17 目录），必须与 1035 配套评估。
