# 提交 1333：Build: Bump jackson-bom from 2.18.0 to 2.18.1 (#11448)

## 提交信息

- **序号**：1333 / 4088
- **哈希**：9dcf0d3d32355e1e254d623ffaf093783eac5663
- **短哈希**：9dcf0d3d3
- **日期**：2024-11-04（Mon Nov 4 15:15:20 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump jackson-bom from 2.18.0 to 2.18.1 (#11448)
- **PR/Issue**：#11448

## 总体目的

Iceberg 通过 Dependabot 自动管理依赖版本，本次将 Jackson BOM 版本从 2.18.0 升至 2.18.1，属于 semver patch 级别升级。Jackson BOM 用于统一管理 Jackson 全家桶（`jackson-core`、`jackson-databind`、`jackson-annotations` 等）的版本，升级 BOM 即可让所有 Jackson 子模块的版本随之统一对齐到 2.18.1，吸收上游修复并避免潜在安全漏洞。

需要注意的是，仓库中还存在针对 Spark/Flink 各版本锁定的特定 Jackson 版本（如 `jackson211`、`jackson212`、`jackson213` 等使用 `strictly` 限定符），这些是引擎生态强约束版本，本次升级不影响它们，仅升级了通用的 `jackson-bom`。

## 如何达成设计目的

直接修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `jackson-bom` 这一项的版本字符串，由 `2.18.0` 改为 `2.18.1`。所有依赖 `jackson-bom` 引入的 Jackson 子模块会自动套用新版本。无源代码改动。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Jackson BOM 版本从 2.18.0 升到 2.18.1。

**工作逻辑**：版本目录中第 55 行原本为：

```toml
jackson-bom = "2.18.0"
```

改为：

```toml
jackson-bom = "2.18.1"
```

其余 `jackson211`、`jackson212`、`jackson213` 等被 `strictly` 锁定的版本保持不变，确保 Spark/Flink 引擎对 Jackson 版本的强约束不被破坏。这是该提交唯一的一处代码改动（1 行新增、1 行删除）。

## 小结

- **成效**：仓库中通用 Jackson 依赖统一升至 2.18.1，吸收上游 patch 修复；不影响 Spark/Flink 引擎对 Jackson 特定版本的强约束。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件、一行版本字符串变更，无源代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：Jackson 是 Iceberg 序列化/反序列化的核心依赖（manifest 读写、metadata JSON 解析等），patch 级升级通常向后兼容。**可以**回迁到 1.4.x，但需注意 1.4.x 当前使用的 Jackson 版本基线（1.4.x 发布时可能仍是 2.14.x 或更早的 2.17.x），跨多个 minor 版本升级应做更充分的兼容性验证。如果 1.4.x 已锁定在某个特定 Jackson 版本且不应被改动，则不应回迁。Jackson BOM 升级属于"收益小、风险也小"的改动，回迁与否取决于 1.4.x 的依赖治理策略。
