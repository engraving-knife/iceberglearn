# 提交 1777：Build: Bump nessie from 0.102.5 to 0.103.0 (#12383)

## 提交信息

- **序号**：1777 / 4088
- **哈希**：0a4ac6ecd80821939757aae7a8f5171feb06e6e1
- **短哈希**：0a4ac6ecd
- **日期**：2025-02-24 12:18:09 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.102.5 to 0.103.0 (#12383)
- **PR/Issue**：#12383

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Nessie 相关依赖从 0.102.5 版本升级到 0.103.0 版本。Nessie 是一个提供 Git-like 版本控制的数据目录服务，Iceberg 项目通过 Nessie 客户端支持 Nessie 目录后端。此次升级为次版本升级（semver-minor），涉及四个 Nessie 组件：nessie-client、nessie-jaxrs-testextension、nessie-versioned-storage-inmemory-tests 和 nessie-versioned-storage-testextension。

## 如何达成设计目的

提交通过更新 `gradle/libs.versions.toml` 文件中 nessie 的版本号来完成升级。由于 Nessie 的四个组件共享同一个版本号变量，只需修改一处即可同时升级所有组件。

## 修改详情

### `gradle/libs.versions.toml`（修改, +1/-1 lines）

**修改目的**：升级 Nessie 依赖版本。

**工作逻辑**：将 `nessie = "0.102.5"` 修改为 `nessie = "0.103.0"`，同时影响 nessie-client、nessie-jaxrs-testextension、nessie-versioned-storage-inmemory-tests 和 nessie-versioned-storage-testextension 四个组件。

## 小结

- **成效**：将 Nessie 相关的四个组件统一升级到 0.103.0 次版本。
- **影响范围**：影响 Nessie 目录后端的客户端代码和相关测试。次版本升级可能包含 API 变更，需验证 Nessie 目录测试是否通过。
- **回迁到 1.4.x 的注意事项**：中等优先级回迁。Nessie 次版本升级可能包含不兼容的 API 变更，回迁时需仔细验证 Nessie 目录相关测试是否通过。无前置依赖，但需确认 1.4.x 分支中 Nessie 集成代码与 0.103.0 版本兼容。
