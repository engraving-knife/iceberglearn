# 提交 0771：Build: Bump io.delta:delta-spark_2.12 from 3.1.0 to 3.2.0 (#10320)

## 提交信息

- **序号**：0771 / 4088
- **哈希**：f4aaa375e9243c9c8c6619a0f8e0a922b6dd9313
- **短哈希**：f4aaa375e
- **日期**：2024-05-16 17:18:00 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.delta:delta-spark_2.12 from 3.1.0 to 3.2.0 (#10320)
- **PR/Issue**：#10320

## 总体目的

这是一次由 Dependabot 自动发起的依赖版本升级。目的是将 Iceberg 构建中使用的 `io.delta:delta-spark_2.12`（Delta Lake 的 Spark 集成库）从 3.1.0 升级到 3.2.0，获取 Delta Lake 3.2.0 版本的 bug 修复、功能改进与安全补丁，保持 Iceberg 与上游 Delta Lake 生态的兼容性。

Delta Lake 的 `delta-spark` 库被 Iceberg 用于跨格式互操作场景的集成测试与兼容性验证（例如 Iceberg 与 Delta Lake 之间的表迁移、格式转换等功能）。将该依赖保持在较新版本有助于确保这些集成场景在最新的 Delta Lake 行为下仍然正确。

## 如何达成设计目的

通过修改 Gradle 版本目录（version catalog）文件 `gradle/libs.versions.toml`，将 `delta-spark` 版本别名（alias）的值从 `3.1.0` 改为 `3.2.0`。由于 Iceberg 的各模块构建脚本通过该别名引用 delta-spark 版本，因此只需在这一处改动即可让所有依赖该别名的模块统一升级到新版本，无需修改各模块的 `build.gradle`。

Dependabot 的工作方式是：扫描仓库中的依赖声明文件，发现可升级的依赖后自动提交 Pull Request。本次提交对应的 PR #10320 即为 Dependabot 生成的自动化升级 PR，提交信息中包含 `updated-dependencies` 元数据块，标注了依赖名称、类型（direct:production）与升级类型（version-update:semver-minor，即次版本号升级）。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `delta-spark` 依赖版本从 3.1.0 升级到 3.2.0。

**工作逻辑**：

Gradle 版本目录文件 `libs.versions.toml` 集中管理 Iceberg 项目的所有依赖版本。在该文件的 `[versions]` 段中，`delta-spark` 别名的值由 `"3.1.0"` 改为 `"3.2.0"`。

```toml
-delta-spark = "3.1.0"
+delta-spark = "3.2.0"
```

同文件中 `delta-standalone = "3.1.0"` 保持不变（本提交只升级 delta-spark，不涉及 delta-standalone）。

**diff 摘要**：1 file changed, 1 insertion(+), 1 deletion(-)。

## 小结

### 成效

本次升级将 delta-spark 依赖从 3.1.0 推进到 3.2.0，属于次版本号（minor）升级。Delta Lake 3.2.0 相对 3.1.0 包含若干功能增强与 bug 修复。对 Iceberg 而言，升级后可确保 Iceberg 与 Delta Lake 3.2.x 的互操作测试基于最新行为运行，避免因依赖旧版本而掩盖潜在的兼容性问题。

### 影响范围

改动仅涉及构建配置文件，不影响 Iceberg 核心运行时代码逻辑。影响范围限于：使用 delta-spark 的模块（主要是与 Delta Lake 集成/互操作相关的测试与工具模块）在构建时拉取 3.2.0 版本的 delta-spark artifact。

### 回迁注意事项

- 回迁到 1.4.x 分支时，确认 1.4.x 分支的 `gradle/libs.versions.toml` 中 `delta-spark` 当前版本。若仍为 3.1.0，则该提交可直接 cherry-pick，无冲突风险。
- 注意 `delta-standalone` 版本未随本次提交升级（仍为 3.1.0），如 1.4.x 分支已有其他 delta 依赖升级需确认是否需要同步。
- delta-spark 3.2.0 可能引入新的传递依赖或对 Scala/Spark 版本有不同要求，回迁后应执行一次完整构建验证。
