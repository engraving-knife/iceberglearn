# 提交 2849：Build: Bump nessie from 0.105.6 to 0.105.7 (#14535)

## 提交信息

- **序号**：2849 / 4088
- **哈希**：a53811b9138f31759f07609e5b9f649989ef7aee
- **短哈希**：a53811b91
- **日期**：2025-11-08 22:15:05 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.105.6 to 0.105.7 (#14535)
- **PR/Issue**：#14535

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 项目依赖的 Nessie 版本从 0.105.6 升级到 0.105.7。

Nessie（Project Nessie）是一个提供事务型目录（catalog）与版本化数据湖能力的服务。Iceberg 通过 `nessie-client` 与 Nessie 服务通信，把 Nessie 作为一类 catalog 后端来管理表/分支/提交等元数据。本次升级涉及四个 Nessie 组件：`org.projectnessie.nessie:nessie-client`（生产依赖，用于客户端调用 Nessie REST API）、`nessie-jaxrs-testextension`（JAX-RS 测试扩展）、`nessie-versioned-storage-inmemory-tests`（内存存储测试扩展）以及 `nessie-versioned-storage-testextension`（版本化存储测试扩展），后三者主要用于 Iceberg 对 Nessie catalog 集成模块的测试场景。

从语义化版本看，0.105.6 到 0.105.7 属于补丁版本（semver-patch）升级，按约定只包含向后兼容的缺陷修复与小幅改进，不引入破坏性 API 变更。因此预期影响是低风险的：保持 Nessie 集成功能不变的同时获得上游的最新修复。Dependabot 定期推进此类补丁升级，是项目维护依赖新鲜度、及时获取安全与稳定性修复的常规实践。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中统一定义的 `nessie` 版本常量，将值由 `0.105.6` 改为 `0.105.7`。由于所有 Nessie 组件坐标都引用该同一版本变量，单点修改即可同步更新全部四个组件，无需改动各模块的构建脚本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Nessie 依赖版本统一升级到 0.105.7。

**工作逻辑**：该文件是 Gradle 的版本目录（version catalog），集中管理项目所有第三方依赖版本。其中 `nessie = "0.105.6"` 这一行定义了 Nessie 的版本常量，被 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 等库坐标引用。本次将 `-nessie = "0.105.6"` 改为 `+nessie = "0.105.7"`，所有引用该常量的依赖坐标随之升级，实现一次改动覆盖全部组件。

## 总结

这是一次例行的依赖补丁升级，将 Nessie 从 0.105.6 提升到 0.105.7，覆盖客户端与三个测试扩展组件。借助版本目录的单点版本管理，改动极小（一行），同时为 Nessie catalog 集成带来上游最新的向后兼容修复，风险低、收益稳定。
