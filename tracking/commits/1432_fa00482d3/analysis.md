# 提交 1432：Build: Bump nessie from 0.100.0 to 0.100.2 (#11637)

## 提交信息

- **序号**：1432 / 4088
- **哈希**：fa00482d3fb45a053d9569f82983295a5c6a7499
- **短哈希**：fa00482d3
- **日期**：2024-11-25 22:25:31 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.100.0 to 0.100.2 (#11637)
- **PR/Issue**：#11637

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将项目中的 Nessie 版本从 0.100.0 升级到 0.100.2。Nessie 是 Iceberg 支持的 catalog 实现之一（Projectnessie），提供类似 Git 的分支/标签版本化数据湖管理能力。Iceberg 在构建和测试中引用 Nessie 的客户端和测试扩展组件，用于 Nessie catalog 集成测试。

0.100.2 是 0.100.x 系列的补丁版本，按语义化版本约定包含 bug 修复和改进，不引入破坏性 API 变更。通过版本目录统一管理，一次声明变更即可同步影响所有引用 Nessie 版本的依赖。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中把 `nessie` 的版本从 `0.100.0` 改为 `0.100.2`。版本目录中 `nessie = "0.100.2"` 会被所有引用该别名的依赖自动继承，无需逐个修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 line)

**修改目的**：升级 Nessie 版本声明。

**工作逻辑**：
```toml
- nessie = "0.100.0"
+ nessie = "0.100.2"
```
该版本别名被以下依赖引用（根据 Dependabot 提交说明）：
- `org.projectnessie.nessie:nessie-client`（0.100.0 → 0.100.2）
- `org.projectnessie.nessie:nessie-jaxrs-testextension`（0.100.0 → 0.100.2）
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`（0.100.0 → 0.100.2）
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`（0.100.0 → 0.100.2）

这四者均为 `direct:production` 依赖，更新类型为 `version-update:semver-patch`（语义化版本的补丁升级）。其中 `nessie-client` 是运行时依赖，其余三个主要用于 Nessie catalog 的集成测试。

## 总结

这是一次 Dependabot 自动依赖升级提交，将 Nessie 从 0.100.0 升级到 0.100.2（补丁版本）。改动仅一行版本声明，影响 `nessie-client` 及三个 Nessie 测试扩展依赖。补丁版本升级通常包含 bug 修复和稳定性改进，破坏性风险很低，属于常规的依赖维护工作，有助于保持 Nessie catalog 集成测试与最新 Nessie 版本同步。
