# 提交 1392：Build: Bump orc from 1.9.4 to 1.9.5 (#11571)

## 提交信息

- **序号**：1392 / 4088
- **哈希**：f9256c698107b0303eb3d33c3f7fa8c3ed87b020
- **短哈希**：f9256c698
- **日期**：2024-11-18（Mon Nov 18 11:37:05 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump orc from 1.9.4 to 1.9.5 (#11571)
- **PR/Issue**：#11571

## 总体目的

这是由 Dependabot 自动生成的依赖版本升级 PR，将 Iceberg 构建所依赖的 Apache ORC 库从 1.9.4 升级到 1.9.5。ORC 是 Iceberg 支持的底层文件格式之一，`orc-core` 与 `orc-tools` 两个制品同步升级。1.9.4 → 1.9.5 属于 semver 的 patch 级别升级，按版本号约定只包含 bug 修复与小幅改进，理论上不引入破坏性变更。

社区接受该升级是为了持续跟进上游 ORC 的修复，避免在 1.9.4 中残留的缺陷（例如潜在的读取/写入或安全相关问题）累积到 Iceberg 仓库中。

## 如何达成设计目的

直接修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `orc` 这一项的版本字符串，将 `"1.9.4"` 改为 `"1.9.5"`。所有引用 `libs.versions.toml` 中 `orc` 版本变量（如 `orc-core`、`orc-tools`）的构建脚本会自动继承新版本，无需逐个修改 build.gradle。这是 Gradle 版本目录（Version Catalog）的标准用法。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 ORC 依赖版本变量提升至 1.9.5。

**工作逻辑**：仅修改一行：

```toml
-orc = "1.9.4"
+orc = "1.9.5"
```

`parquet` 等其它依赖版本保持不变。由于 Iceberg 的 ORC 集成模块（`orc/src/main/java/org/apache/iceberg/orc/`）在 `build.gradle` 中通过 `libs.orc.core`、`libs.orc.tools` 等方式引用此变量，本次升级会自动作用于所有相关模块。

## 小结

- **成效**：Iceberg 构建产物的 ORC 依赖版本从 1.9.4 升级到 1.9.5，跟随上游 patch 修复；不涉及任何代码逻辑改动。
- **影响范围**：仅 1 个文件、1 行变更；运行时影响范围限于 ORC 文件读写路径（`ORC.read`、`ORC.write` 等）。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 作为维护分支，是否需要回迁取决于 1.9.5 是否修复了影响 1.4.x 现网部署的 bug。如果是常规 patch 升级、且 1.4.x 当前 ORC 版本与 1.9.4/1.9.5 兼容，可直接回迁。
  - 回迁前需确认 1.9.5 不引入对更高 JDK 或其它依赖的强制要求；由于是 patch 级别，通常风险极低。
  - 若 1.4.x 的 `libs.versions.toml` 中 `orc` 仍为 1.9.4 或更早版本，建议同步升级以保持与 main 一致并受益于上游修复。
