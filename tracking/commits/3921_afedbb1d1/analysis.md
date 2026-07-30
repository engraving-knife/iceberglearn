# 提交 3921：Build: Bump nessie from 0.107.9 to 0.108.0 (#16897)

## 提交信息

- **序号**：3921 / 4088
- **哈希**：afedbb1d1786e48606b54c62c100a922222ac5d8
- **短哈希**：afedbb1d1
- **日期**：2026-06-21 00:08:09 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.107.9 to 0.108.0 (#16897)
- **PR/Issue**：#16897

## 总体目的

这是由 Dependabot 发起的依赖升级，将 Project Nessie 从 0.107.9 升级到 0.108.0。Nessie 是一个为数据湖提供 Git 风格版本化目录服务的开源项目，Iceberg 通过 Nessie client 与 Nessie 服务进行交互，支持将 Nessie 作为 catalog 后端。

此次升级涉及四个 Nessie 相关依赖：`nessie-client`（生产环境客户端）、`nessie-jaxrs-testextension`（JAX-RS 测试扩展）、`nessie-versioned-storage-inmemory-tests`（内存存储测试）、`nessie-versioned-storage-testextension`（版本化存储测试扩展）。作为 semver-minor 更新，预期包含新功能且向后兼容。

## 如何达成设计目的

通过修改版本目录 `gradle/libs.versions.toml` 中 `nessie` 的版本别名条目，将版本号从 `0.107.9` 更新为 `0.108.0`。版本目录集中管理所有 Nessie 相关子模块的版本，一次升级即可同步所有 Nessie 依赖。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Nessie 相关依赖版本。

**工作逻辑**：
将 `nessie = "0.107.9"` 改为 `nessie = "0.108.0"`。该版本别名被 `iceberg-nessie` 模块及其测试代码引用，用于统一四个 Nessie 子模块的版本。

## 总结

这是一次 Nessie 依赖的次要版本升级，通过版本目录统一升级到 0.108.0，使 Nessie 客户端及测试扩展获取上游的新功能与改进。该升级在同一批次的 #16907 提交中同步更新了 Flink、Spark、GCP 等 runtime-deps.txt 中记录的传递性依赖版本（nessie-client/model 0.107 → 0.108）。
