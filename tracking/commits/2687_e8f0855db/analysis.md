# 提交 2687：Spark 3.5: Upgrade to Spark 3.5.7 (#14114)

## 提交信息

- **序号**：2687 / 4088
- **哈希**：e8f0855db92a1f61812c4bc126c4b31591630c92
- **短哈希**：e8f0855db
- **日期**：2025-09-25 23:55:26 -0700
- **作者**：Manu Zhang
- **提交说明**：Spark 3.5: Upgrade to Spark 3.5.7 (#14114)
- **PR/Issue**：#14114

## 总体目的

本提交将 Iceberg 项目中 Spark 3.5 模块使用的 Spark 版本从 3.5.6 升级到 3.5.7。Spark 3.5.7 是 Spark 3.5.x 维护分支的 patch 版本更新，通常包含 bug 修复、安全补丁和小的性能改进，不引入破坏性变更。

Iceberg 为 Spark 3.4、3.5、4.0 分别维护独立的适配模块，每个模块绑定特定 Spark 版本。保持 Spark 版本最新有助于及时获取上游修复，确保 Iceberg 在最新 patch 版本上经过测试和验证。从版本号看，3.5.6 到 3.5.7 是 patch 级别升级，风险较低。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `spark35` 的版本声明，从 `3.5.6` 改为 `3.5.7`。所有引用 `spark35` 版本的 Spark 3.5 模块依赖会自动应用新版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Spark 3.5 版本声明。

**工作逻辑**：将 `spark35 = "3.5.6"` 一行修改为 `spark35 = "3.5.7"`。相邻的 `spark34 = "3.4.4"` 和 `spark40 = "4.0.1"` 保持不变。通过 Gradle 版本目录的集中管理，单点修改即可同步 Spark 3.5 模块的所有 Spark 依赖。

## 总结

这是一次常规的 Spark 版本 patch 升级，将 Spark 3.5 从 3.5.6 升级到 3.5.7。修改仅涉及一行版本目录配置，风险极低。保持 Spark patch 版本最新有助于获取上游 bug 修复和安全补丁，确保 Iceberg 与最新 Spark 3.5 版本的兼容性。
