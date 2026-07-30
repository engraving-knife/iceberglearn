# 提交 3389：Build: Bump nessie from 0.107.3 to 0.107.4 (#15636)

## 提交信息

- **序号**：3389 / 4088
- **哈希**：8bca96e65e072848d8c21f2b348a4f6908bc399d
- **短哈希**：8bca96e65
- **日期**：2026-03-14 23:42:27 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.107.3 to 0.107.4 (#15636)
- **PR/Issue**：#15636

## 总体目的

由 dependabot 自动发起的依赖版本升级，将 Nessie 相关依赖从 0.107.3 升级到 0.107.4（semver-patch 级别更新）。Nessie 是 ProjectNessie 提供的事务型数据目录（catalog）实现，Iceberg 通过 `nessie-client` 与之集成，并在测试中使用 `nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 等模块。patch 版本升级通常包含 bug 修复和小改进，风险较低。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `nessie` 版本变量的值，所有引用该变量的 Nessie 子模块依赖会自动同步升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Nessie 版本变量。

**工作逻辑**：
- 将 `nessie = "0.107.3"` 改为 `nessie = "0.107.4"`。该变量被 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 等依赖引用，一次修改即可全部升级。

## 总结

这是一次由 dependabot 自动生成的 patch 级依赖升级，将 Nessie 从 0.107.3 提升到 0.107.4。改动仅涉及版本目录中的一行版本号，影响范围受限于 Nessie 集成和测试模块。patch 版本升级通常用于获取最新的 bug 修复，不引入破坏性变更。
