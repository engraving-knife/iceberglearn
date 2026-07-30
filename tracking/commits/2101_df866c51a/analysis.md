# 提交 2101：ORC: Upgrade ORC to 1.9.6 (#13003)

## 提交信息

- **序号**：2101 / 4088
- **哈希**：df866c51af8f99d213037f0bd49b1d7dd061f7b5
- **短哈希**：df866c51
- **日期**：2025-05-08 07:32:47 +0200
- **作者**：Dongjoon Hyun <dongjoon@apache.org>
- **提交说明**：ORC: Upgrade ORC to 1.9.6 (#13003)
- **PR/Issue**：#13003

## 总体目的

将 Iceberg 依赖的 Apache ORC 库从 `1.9.5` 升级到 `1.9.6`。ORC 1.9.6 是 1.9.x 维护分支的 bugfix 版本，包含缺陷修复与稳定性改进。Iceberg 的 `iceberg-orc` 模块使用 ORC 读写 ORC 格式数据文件，升级后可获得上游修复而无需改动集成代码。

## 如何达成设计目的

由于 ORC 版本号已集中在 Gradle 版本目录 `gradle/libs.versions.toml` 的 `orc` 别名中，所有引用 ORC 的依赖都通过 `version.ref` 取值，因此只需把 `orc = "1.9.5"` 改为 `orc = "1.9.6"` 一行即可完成全项目升级。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 ORC 版本。

**工作逻辑**：把 `orc = "1.9.5"` 改为 `orc = "1.9.6"`。所有通过 `version.ref = "orc"` 引用的依赖（如 `org.apache.orc:orc-core`、`orc-mapreduce`、`orc-tools`）将自动指向 1.9.6。

## 总结

本次提交是一次单行依赖升级，把 Apache ORC 从 1.9.5 升到 1.9.6（维护版本 bugfix）。得益于版本目录集中管理，改动仅 1 行，无代码变更。
