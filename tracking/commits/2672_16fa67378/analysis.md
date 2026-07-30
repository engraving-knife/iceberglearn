# 提交 2672：Flink: Add support for Flink 2.1.0 follow-up (#14156)

## 提交信息

- **序号**：2672 / 4088
- **哈希**：16fa673782233eb2b692d463deb8be3e52c5f378
- **短哈希**：16fa67378
- **日期**：2025-09-22 10:58:12 -0700
- **作者**：Manu Zhang
- **提交说明**：Flink: Add support for Flink 2.1.0 follow-up (#14156)
- **PR/Issue**：#14156

## 总体目的

本提交是此前"为 Iceberg 添加 Flink 2.1.0 支持"主 PR 的后续补丁。主 PR 完成了 Flink 2.1.0 模块代码层面的适配，但构建与发布脚本中仍残留旧版本 Flink 1.19 的配置，且未把新的 Flink 2.1 模块纳入 JMH 基准测试和二进制打包流程。本提交补齐这些遗漏，确保 Flink 2.1.0 在 Iceberg 的 CI/CD 和发布流水线中被正确对待。

具体而言有两点动机：一是 Iceberg 已经决定移除对 Flink 1.19 的支持（在 stage-binaries.sh 中不再发布 1.19 的二进制包），转而支持 Flink 2.1；二是 JMH 基准测试配置需要同步更新，移除 1.19 模块、新增 2.1 模块，否则运行基准测试时会缺少 2.1 的覆盖或因 1.19 模块不存在而失败。

## 如何达成设计目的

通过修改两个构建相关文件来完成：`dev/stage-binaries.sh` 负责控制发布时打包哪些 Flink 版本的二进制分发包；`jmh.gradle` 负责定义哪些子项目参与 JMH 基准测试。两者都将 Flink 版本列表从 `1.19,1.20,2.0` 更新为 `1.20,2.0,2.1`，保持一致性。

## 修改详情

### `dev/stage-binaries.sh` (+1/-1 lines)

**修改目的**：更新发布脚本中打包的 Flink 版本列表。

**工作逻辑**：将 `FLINK_VERSIONS=1.19,1.20,2.0` 改为 `FLINK_VERSIONS=1.20,2.0,2.1`。移除了 Flink 1.19，新增了 Flink 2.1。这决定了 `dev/stage-binaries.sh` 脚本在准备发布二进制包时会为 1.20、2.0、2.1 三个 Flink 版本各构建一个分发包。

### `jmh.gradle` (+8/-4 lines)

**修改目的**：更新 JMH 基准测试项目列表。

**工作逻辑**：移除了对 `iceberg-flink-1.19` 项目的条件添加逻辑（`if (flinkVersions.contains("1.19"))`），新增了对 `iceberg-flink-2.1` 项目的条件添加逻辑（`if (flinkVersions.contains("2.1"))`）。这样在运行 JMH 基准测试时，Flink 2.1 模块会被纳入测试范围，而已废弃的 1.19 模块不再参与。注意新增代码使用了 4 空格缩进而非项目惯用的 2 空格，可能是小幅风格不一致。

## 总结

这是一次构建脚本维护性提交，作为 Flink 2.1.0 支持主 PR 的后续，将发布打包脚本和 JMH 基准测试配置从包含 Flink 1.19 更新为包含 Flink 2.1，使新版本的 Flink 支持在发布和性能测试流程中得到完整覆盖。改动小而聚焦，风险低。
