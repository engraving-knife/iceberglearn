# 提交 3331：Build: Bump nessie from 0.107.2 to 0.107.3 (#15481)

## 提交信息

- **序号**：3331 / 4088
- **哈希**：9a53e2eda36a29da48ac694653d6f72282c06dc9
- **短哈希**：9a53e2eda
- **日期**：2026-02-28 22:09:34 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.107.2 to 0.107.3 (#15481)
- **PR/Issue**：#15481

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 Nessie 相关依赖从 `0.107.2` 升级到 `0.107.3`。

Nessie（Project Nessie）是一个提供"事务化版本化数据目录"（transactional catalog with Git-like branching）的开源服务，可作为 Iceberg 的 catalog 后端。与传统的 Hive/Glue catalog 不同，Nessie 允许对表/命名空间进行分支、提交、回滚等版本控制操作，使数据工程团队可以在隔离的"数据分支"上做实验、审计与回滚。Iceberg 提供了 `nessie` catalog 实现，通过 Nessie 的 REST API（基于 JAX-RS）与 Nessie 服务交互。

本次升级同时覆盖四个 Nessie 模块：
- `nessie-client`：与 Nessie 服务通信的客户端库（生产代码使用）；
- `nessie-jaxrs-testextension`：用于在测试中嵌入 Nessie REST 服务的 JAX-RS 测试扩展；
- `nessie-versioned-storage-inmemory-tests`：基于内存存储的 Nessie 版本化存储测试支持；
- `nessie-versioned-storage-testextension`：Nessie 版本化存储的通用测试扩展。

其中 `nessie-client` 是运行时依赖，其余三个主要用于 Iceberg 的 Nessie catalog 测试套件（在测试中启动一个嵌入式 Nessie 服务来验证 catalog 行为）。

本次升级属于语义化版本的 **patch（补丁）** 升级（`0.107.2` → `0.107.3`，`update-type: version-update:semver-patch`，四个模块均为 patch 级）。patch 升级仅含缺陷修复与内部改进，不引入 API 破坏。预期影响是获得 Nessie 0.107.3 在客户端协议、存储层与测试扩展方面的缺陷修复，对 Iceberg 的 Nessie catalog 功能行为无可见变化。

## 如何达成设计目的

改动仅修改版本目录文件 `gradle/libs.versions.toml` 中 `nessie` 这一项的版本字符串。Iceberg 在版本目录里定义了单一 `nessie` 版本变量，被上述四个模块坐标共同引用，因此一行版本号变更即可联动升级全部 Nessie 依赖，无需改动各模块的构建脚本或代码。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Nessie 全套依赖版本从 0.107.2 提升到 0.107.3。

**工作逻辑**：
在版本目录 `[versions]` 段中，将 `nessie = "0.107.2"` 修改为 `nessie = "0.107.3"`。该变量被 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 四个库坐标共同引用，构建时会全部解析到 `0.107.3`。这是一次纯版本号变更，不涉及代码或配置逻辑调整，保证生产客户端与测试扩展版本一致。

## 总结

本次提交通过 Dependabot 将 Nessie 全套依赖（客户端 + 三个测试扩展）从 0.107.2 升级到 0.107.3（patch 级），以获取上游缺陷修复。改动局限于版本目录单行，借助单一版本变量统一约束四个模块，风险低，对 Iceberg 的 Nessie catalog 功能与测试行为无破坏性影响。
