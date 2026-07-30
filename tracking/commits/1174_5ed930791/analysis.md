# 提交 1174：Build: Bump tez010 from 0.10.3 to 0.10.4 (#11183)

## 提交信息

- **序号**：1174 / 4088
- **哈希**：5ed930791862bd5c471428f0843a8c55ecc78249
- **短哈希**：5ed930791
- **日期**：2024-09-23（Mon Sep 23 14:43:16 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump tez010 from 0.10.3 to 0.10.4 (#11183)
- **PR/Issue**：#11183

## 总体目的

本提交由 Dependabot 自动生成，将 Apache Tez 0.10 系列（`tez010`）从 0.10.3 升级到 0.10.4，属于 `version-update:semver-patch` 补丁版本升级。一次性升级两个模块：
- `org.apache.tez:tez-dag`
- `org.apache.tez:tez-mapreduce`

Apache Tez 是基于 YARN 的分布式执行框架，Hive on Tez 用它作为执行引擎。Iceberg 的 `iceberg-mr`（MapReduce/Hive 集成）模块在测试中用 Tez 跑 Hive 集成测试，所以依赖 tez-dag 与 tez-mapreduce。补丁版本升级主要包含 bug 修复与稳定性改进，API 兼容。

## 如何达成设计目的

仅修改 `gradle/libs.versions.toml`，把 `tez010` 版本号从 `0.10.3` 改为 `0.10.4`。所有通过 `version.ref = "tez010"` 引用的库坐标（`tez-dag`、`tez-mapreduce`）会自动同步到新版本。无代码改动。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 tez010 版本号。

**工作逻辑**：在 `[versions]` 段把：

```toml
tez010 = "0.10.3"
```

改为：

```toml
tez010 = "0.10.4"
```

该变量在 `[libraries]` 段被 `tez-dag`、`tez-mapreduce` 两条目通过 `version.ref = "tez010"` 引用，本提交未改这两条目本身，但它们解析出的版本号随之变为 0.10.4。这些依赖主要用于 `iceberg-mr` 模块的 Hive/Tez 集成测试场景。

**升级背景**：Tez 0.10.4 是 0.10.x 维护系列的补丁版本，包含对 0.10.3 的 bug 修复。补丁版本升级风险最低。

## 小结

- **成效**：Tez 0.10 系列升级到 0.10.4，Hive on Tez 集成测试使用最新的补丁版本。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更，无代码改动。仅在 `iceberg-mr` 模块的 Tez 相关测试场景生效，不影响生产运行时（Iceberg 不在运行时直接依赖 Tez）。
- **回迁到 1.4.x 的注意事项**：Tez 0.10.3 → 0.10.4 是补丁版本升级，风险极低。1.4.x 若维护 Tez 集成测试，可安全回迁以保证测试稳定性。但若 1.4.x 测试无 Tez 相关问题，**也可不回迁**。回迁时确认 1.4.x 的 `libs.versions.toml` 中 `tez010` 变量仍存在且未做其他调整。
