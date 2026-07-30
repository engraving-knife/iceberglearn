# 提交 4042：Build: Bump orc from 1.9.8 to 1.9.9 (#17225)

## 提交信息

- **序号**：4042 / 4088
- **哈希**：91afff3cfb433f7c1d74c038500304068687813c
- **短哈希**：91afff3cf
- **日期**：2026-07-15 18:51:46 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump orc from 1.9.8 to 1.9.9 (#17225)
- **PR/Issue**：#17225

## 总体目的

Dependabot 自动升级提交，将 Apache ORC 库从 1.9.8 升级到 1.9.9（semver patch 补丁版本升级）。ORC 是一种高性能的列式存储格式，Iceberg 支持 ORC 作为数据文件格式之一。本次升级是 1.9.x 系列内的 patch 升级，通常包含 bug 修复、性能改进和潜在的 CVE 安全补丁，不引入 API 破坏性变更。

## 如何达成设计目的

通过 Gradle 版本目录 `gradle/libs.versions.toml` 中的 `orc` 版本变量统一管理。Dependabot 将该变量从 `1.9.8` 改为 `1.9.9`，所有引用该变量的 ORC 依赖随之同步升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 ORC 版本。

**工作逻辑**：
```toml
orc = "1.9.9"
```
将 `orc` 变量从 `1.9.8` 改为 `1.9.9`。该变量被 ORC 相关依赖引用，更新后所有 ORC 组件升级到 1.9.9。

## 总结

常规的 ORC 列式存储库补丁版本升级，保持 Iceberg 的 ORC 格式支持基于最新的稳定补丁版本，获取上游 bug 修复与可能的 CVE 补丁。patch 级别升级风险很低。
