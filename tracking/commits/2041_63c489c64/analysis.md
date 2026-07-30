# 提交 2041：Build: Bump nessie from 0.103.3 to 0.103.5

## 提交信息

- **序号**：2041 / 4088
- **哈希**：63c489c64db7c9d79c72fd347b4038f937f50fab
- **短哈希**：63c489c64
- **日期**：2025-04-28 07:54:08 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.103.3 to 0.103.5 (#12902)
- **PR/Issue**：#12902

## 总体目的

本提交由 Dependabot 自动生成，将 Nessie 依赖从 0.103.3 升级到 0.103.5。Nessie 是 Iceberg 支持的 Catalog 实现之一（NessieCatalog），提供基于 Git 风格版本控制的数据目录管理。此次升级为补丁版本升级（semver-patch），包含 bug 修复和小改进。

## 如何达成设计目的

通过修改 Gradle 版本目录文件中的 Nessie 版本号，自动更新所有引用该版本变量的 Nessie 依赖。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 Nessie 版本号。

**工作逻辑**：
将 `nessie = "0.103.3"` 改为 `nessie = "0.103.5"`。此版本变量被以下 4 个 Nessie 依赖引用，均自动从 0.103.3 升级到 0.103.5：
- `org.projectnessie.nessie:nessie-client`
- `org.projectnessie.nessie:nessie-jaxrs-testextension`
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`

## 总结

Dependabot 自动依赖升级提交，将 Nessie 从 0.103.3 升级到 0.103.5（补丁版本），涉及 4 个 Nessie 相关依赖。改动仅 1 行版本号变更。
