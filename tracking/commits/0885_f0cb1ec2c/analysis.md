# 提交 0885：Build: Bump nessie from 0.91.2 to 0.91.3 (#10608)

## 提交信息

- **序号**：0885 / 4088
- **哈希**：f0cb1ec2c6c97ed07b1f3acbdb28d985cae3e7e8
- **短哈希**：f0cb1ec2c
- **日期**：2024-06-30（Sun Jun 30 15:15:21 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.91.2 to 0.91.3 (#10608)
- **PR/Issue**：#10608

## 总体目的

本提交由 Dependabot 自动生成，目的是将 Iceberg 项目依赖的 Nessie 版本从 0.91.2 升级到 0.91.3。Nessie 是一个提供 Git 风格版本化数据目录的组件，Iceberg 通过 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 等多个模块与之集成，用于 Nessie catalog 集成与测试。

0.91.2 → 0.91.3 是一个 semver-patch（补丁版本）升级，按照语义版本约定通常包含 bug 修复与小改进，不引入破坏性 API 变更，属于低风险的例行依赖维护。Dependabot 一次性升级所有以 `nessie` 版本变量驱动的相关组件，保持它们版本一致。

## 如何达成设计目的

Nessie 的版本在 Iceberg Gradle 版本目录 `gradle/libs.versions.toml` 中以单一变量 `nessie` 统一管理，所有相关 Nessie 组件坐标（`nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension`）均引用该变量。因此只需把该变量的值从 `0.91.2` 改为 `0.91.3`，即可让全部 Nessie 依赖同步升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Nessie 依赖版本从 0.91.2 升级到 0.91.3。

**工作逻辑**：将版本目录中 `nessie = "0.91.2"` 一行改为 `nessie = "0.91.3"`。该变量被 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 四个依赖坐标引用，改一处即全部生效。

## 小结

- **成效**：完成 Nessie 依赖的补丁版本升级（0.91.2 → 0.91.3），获取上游 bug 修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行改动，影响 Nessie 相关生产与测试依赖；无代码改动。
- **回迁到 1.4.x 的注意事项**：可按需回迁。属于例行依赖升级，风险低。需确认 1.4.x 分支的 Nessie 版本基线与 API 兼容性（patch 版本通常兼容）。若 1.4.x 已冻结依赖版本策略，可不必回迁。
