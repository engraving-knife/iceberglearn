# 提交 0959：Build: Bump orc from 1.9.3 to 1.9.4 (#10728)

## 提交信息

- **序号**：0959 / 4088
- **哈希**：3067261c91d0e02c46a1d4ad2b1df1dc64574fba
- **短哈希**：3067261c9
- **日期**：2024-07-22（Mon Jul 22 09:18:52 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump orc from 1.9.3 to 1.9.4 (#10728)
- **PR/Issue**：#10728

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。Apache ORC 是一种高效的列式存储格式，Iceberg 支持 ORC 作为表数据文件格式之一（与 Parquet、Avro 并列）。Iceberg 的 `iceberg-orc` 模块通过 `org.apache.orc:orc-core` 实现对 ORC 文件的读写，`org.apache.orc:orc-tools` 提供相关工具支持。

本次提交将 `orc` 从 1.9.3 升级到 1.9.4（semver patch 版本升级），属于 patch 级别的小版本升级，通常包含 bug 修复、性能改进和潜在的兼容性修复。由于 ORC 是 Iceberg 直接用于读写数据文件的核心格式依赖，保持其最新修复版本对数据读写的正确性与稳定性至关重要。

Dependabot 提交说明显示本次升级同时更新了 `org.apache.orc:orc-core` 和 `org.apache.orc:orc-tools` 两个制品（均由版本目录中的 `orc` 统一版本号管理）。

## 如何达成设计目的

实现方式为修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，将其中 `orc` 的版本声明从 `1.9.3` 改为 `1.9.4`。该单一版本号被 `orc-core` 和 `orc-tools` 两个制品共享，因此单点修改即可同步升级两者。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 ORC 依赖版本（覆盖 orc-core 与 orc-tools）从 1.9.3 升级到 1.9.4。

**工作逻辑**：仅修改版本目录中的一行：

```diff
-orc = "1.9.3"
+orc = "1.9.4"
```

修改后，所有通过 `${libs.orc.core}`、`${libs.orc.tools}` 等引用该版本号的模块（如 `iceberg-orc`、ORC 读写相关测试、以及依赖 ORC 的集成模块如 `iceberg-spark` 在使用 ORC 格式时）在构建时拉取 1.9.4 版本的 ORC 制品。

## 小结

- **成效**：完成 ORC（orc-core 与 orc-tools）从 1.9.3 到 1.9.4 的 patch 版本升级，使 ORC 格式读写依赖保持最新修复版本。
- **影响范围**：仅修改 `gradle/libs.versions.toml` 一个文件，1 行改动。影响 `iceberg-orc` 模块及其下游（如使用 ORC 格式的 `iceberg-spark`、`iceberg-flink`、`iceberg-hive` 等）的构建产物依赖版本，但不改变 Iceberg 自身代码逻辑。
- **回迁到 1.4.x 的注意事项**：纯依赖升级，**适合回迁**，风险较低。ORC 1.9.x 系列内 patch 升级通常向后兼容。回迁到 1.4.x 建议运行 `iceberg-orc` 模块的完整读写测试，以及使用 ORC 格式的 Spark/Flink 集成测试以验证数据读写无回归。由于 ORC 直接参与数据文件读写，需重点验证不同 ORC 版本写入的文件能在旧版本读取（向前兼容性），避免在多版本混用环境中出现读取问题。
