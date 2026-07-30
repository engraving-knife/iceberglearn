# 提交 2032：Flink: Move v1.20 to v2.0 directory

## 提交信息

- **序号**：2032 / 4088
- **哈希**：e996688c56ed980276d1c045d26ecdc18faf2e26
- **短哈希**：e996688c5
- **日期**：2025-04-23 13:21:24 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Move v1.20 to v2.0 directory
- **PR/Issue**：无（该提交说明中未包含 PR 号，属于 Flink 2.0 支持系列的一部分）

## 总体目的

本提交是 Flink 2.0 支持重构系列（提交 2032-2035）的第一步。Iceberg 的 Flink 集成按 Flink 大版本分别维护独立的源码目录（如 `flink/v1.18/`、`flink/v1.19/`、`flink/v1.20/`）。为了添加 Flink 2.0 支持，作者选择以最接近的 Flink 1.20 版本目录为基础进行升级。

本提交将整个 `flink/v1.20/` 目录重命名为 `flink/v2.0/`，作为后续适配 Flink 2.0 API 变更的工作起点。由于这是纯文件移动（git rename），不涉及任何内容修改，所有的源码内容和目录结构保持不变，只是顶层目录名从 `v1.20` 变为 `v2.0`。

此系列提交采用分步重构策略，便于审查和理解每一步的变更：
1. 提交 2032（本提交）：将 v1.20 目录移动到 v2.0
2. 提交 2033：将 v1.20 目录内容复制回来（保留对 1.20 的支持）
3. 提交 2034：在 v2.0 目录中适配 Flink 2.0 的 API 变更
4. 提交 2035：移除 Flink 1.18 支持

## 如何达成设计目的

通过 `git mv` 将 `flink/v1.20/` 下所有文件（包括 `build.gradle`、`flink-runtime/` 下的 LICENSE/NOTICE、`flink/` 下的全部 main 和 test 源码、SPI 服务注册文件等）整体移动到 `flink/v2.0/`。移动后 v1.20 目录暂时不存在（将在下一个提交中复制回来）。

关键点：本提交不修改任何文件内容，只做目录重命名。涉及的文件涵盖 Flink 集成的全部模块，包括 sink（写入器）、source（读取器）、catalog、maintenance（维护任务）、data（数据格式转换）等。

## 修改详情

### `flink/v1.20/` => `flink/v2.0/` (重命名, 0 lines 变更)

**修改目的**：将 Flink 1.20 的源码目录重命名为 v2.0，作为 Flink 2.0 支持的基础。

**工作逻辑**：
纯文件移动操作，涉及数百个文件（build.gradle、LICENSE、NOTICE、所有 Java 源码和测试文件、SPI 服务注册文件等），所有文件内容不变。移动的文件覆盖了 Flink 集成的完整代码库，包括：
- `build.gradle`：模块构建配置
- `flink-runtime/`：运行时依赖的 LICENSE 和 NOTICE
- `flink/src/main/java/`：主源码（FlinkCatalog、FlinkSink、IcebergSource 等）
- `flink/src/test/java/`：测试源码
- `flink/src/main/resources/`：SPI 服务注册文件（Factory、TableFactory）

## 总结

本提交是 Flink 2.0 支持系列的第一步，通过纯目录重命名将 `flink/v1.20/` 移动到 `flink/v2.0/`，为后续的 Flink 2.0 API 适配和保留 Flink 1.20 支持做准备。不涉及任何代码内容变更。
