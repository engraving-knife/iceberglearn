# 提交 3330：Build: Bump jackson-bom from 2.21.0 to 2.21.1 (#15482)

## 提交信息

- **序号**：3330 / 4088
- **哈希**：e7a021c9c7608842acc78489ead290cdddc5ec51
- **短哈希**：e7a021c9c
- **日期**：2026-02-28 22:09:24 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jackson-bom from 2.21.0 to 2.21.1 (#15482)
- **PR/Issue**：#15482

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 Jackson 的 BOM `com.fasterxml.jackson:jackson-bom` 从 `2.21.0` 升级到 `2.21.1`。

Jackson 是 Java 生态中最主流的 JSON 序列化/反序列化库，在 Iceberg 中承担多重职责：表元数据（`TableMetadata`、`ViewMetadata`）、manifest 与快照信息、catalog 元数据、REST catalog 与 S3 signer 等模块的 JSON 编解码都依赖 Jackson。Iceberg 通过引入 `jackson-bom` 这个 BOM 来统一管理 Jackson 全套子模块（`jackson-core`、`jackson-databind`、`jackson-annotations` 以及各数据格式模块）的版本。BOM 本身是一个版本清单，被 import 后所有 `com.fasterxml.jackson.*` 子模块都会按 BOM 中声明的版本解析，避免子模块版本不一致导致的兼容性问题。

值得注意的背景是，Iceberg 为了与不同版本的 Spark/Flink 宿主环境兼容，对 Jackson 采用了"多版本共存"的 rich version 策略：除了主版本 `jackson-bom = 2.21.x` 外，版本目录里还存在 `jackson214 = { strictly = "2.14.2"}` 与 `jackson215 = { strictly = "2.15.2"}` 等被严格锁定的旧版本，用于在特定引擎模块中强制对齐宿主的 Jackson 版本。本次升级只动主版本 `jackson-bom`，不触及这些严格锁定版本。

本次升级属于语义化版本的 **patch（补丁）** 升级（`2.21.0` → `2.21.1`，`update-type: version-update:semver-patch`）。Dependabot 同时识别出 `jackson-bom`、`jackson-core`、`jackson-databind` 三个依赖跟随升级，均属 patch 级。patch 升级仅含缺陷修复与内部改进，不引入 API 破坏。预期影响是获得 2.21.1 对 `JsonParser`、`ObjectMapper` 等核心组件的缺陷修复（如序列化边界、流式解析、安全相关修复），对 Iceberg 现有 JSON 编解码行为无可见变化。

## 如何达成设计目的

改动仅修改版本目录文件 `gradle/libs.versions.toml` 中 `jackson-bom` 这一项的版本字符串。`jackson-annotations` 单独保持 `2.21`（不带 patch，由 BOM 统一约束），而 `jackson-core`、`jackson-databind` 通过 BOM 间接解析到 `2.21.1`，因此只需改动 BOM 版本一行即可联动升级全套 Jackson 模块。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 jackson-bom 版本从 2.21.0 提升到 2.21.1。

**工作逻辑**：
在版本目录 `[versions]` 段中，将 `jackson-bom = "2.21.0"` 修改为 `jackson-bom = "2.21.1"`。该变量被用于 import `com.fasterxml.jackson:jackson-bom` 平台，从而统一约束 `jackson-core`、`jackson-databind` 及其他 Jackson 子模块的版本。注意 `jackson-annotations = "2.21"` 不带 patch 号，其精确版本同样由 BOM 约束为 `2.21.1`。这是一次纯版本号变更，不涉及代码逻辑；与 Spark/Flink 兼容用的严格锁定旧版本（`jackson214`、`jackson215`）不受影响。

## 总结

本次提交通过 Dependabot 将 jackson-bom 从 2.21.0 升级到 2.21.1（patch 级），借助 BOM 机制联动升级 jackson-core、jackson-databind 等全套 Jackson 模块，以获取上游缺陷修复。改动局限于版本目录单行，不触及严格锁定的旧 Jackson 版本，风险低，对 Iceberg 的 JSON 编解码行为无破坏性影响。
