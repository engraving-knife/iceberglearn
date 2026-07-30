# 提交 2735：Build: Bump nessie from 0.105.3 to 0.105.4

## 提交信息

- **序号**：2735 / 4088
- **哈希**：0e04429c4b36c116bd5bd124d96677665a426121
- **短哈希**：0e04429c4
- **日期**：2025-10-12 08:08:50 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.105.3 to 0.105.4
- **PR/Issue**：#14299

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。Nessie 是一个提供 Git 风格版本控制的数据目录服务（Data Catalog），Iceberg 提供 Nessie 集成模块，允许通过 Nessie 管理 Iceberg 表的版本化目录。此次升级涉及多个 Nessie 子模块：nessie-client（生产依赖）、nessie-jaxrs-testextension、nessie-versioned-storage-inmemory-tests、nessie-versioned-storage-testextension（测试依赖）。

本次升级将 Nessie 从 0.105.3 升级到 0.105.4，属于 semver-patch（补丁版本）升级，主要包含 bug 修复和小改进。保持 Nessie 客户端与服务器版本同步对 Iceberg-Nessie 集成的稳定性很重要。

## 如何达成设计目的

Dependabot 通过修改 Gradle 版本目录中的 Nessie 版本声明完成升级。由于 Nessie 的多个子模块共享同一个版本变量 `nessie`，修改一处即可使所有 Nessie 相关依赖统一升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Nessie 依赖版本。

**工作逻辑**：将 `nessie = "0.105.3"` 修改为 `nessie = "0.105.4"`。项目中引用 `nessie` 版本变量的所有模块（nessie-client、nessie-jaxrs-testextension 等）会自动使用新版本。

## 总结

这是常规的依赖维护升级，将 Nessie 从 0.105.3 升级到 0.105.4。作为 semver-patch 升级，风险很低，主要获取 bug 修复。该升级影响 Iceberg 的 Nessie 集成模块，包括客户端和测试扩展。
