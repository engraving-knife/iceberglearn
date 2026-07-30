# 提交 3634：Build: Bump nessie from 0.107.4 to 0.107.5 (#16202)

## 提交信息

- **序号**：3634 / 4088
- **哈希**：d69d92ce0b5d7cbbe7efb841da9c34bc5b913155
- **短哈希**：d69d92ce0
- **日期**：2026-05-02 23:04:54 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.107.4 to 0.107.5 (#16202)
- **PR/Issue**：#16202

## 总体目的

这个提交将 Nessie 从版本 0.107.4 升级到 0.107.5，这是一个 patch 级别的版本更新。

Nessie 是一个提供 Git 式版本控制的数据目录服务，Iceberg 支持将 Nessie 作为 catalog 后端。本次升级影响以下四个 Nessie 模块：
- `org.projectnessie.nessie:nessie-client`：Nessie 客户端库（生产代码使用）。
- `org.projectnessie.nessie:nessie-jaxrs-testextension`：JAX-RS 测试扩展。
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`：内存存储测试。
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`：版本存储测试扩展。

升级到 0.107.5 可以获得最新的 bug 修复和改进。

## 如何达成设计目的

通过 Dependabot 自动生成的 PR，更新 `gradle/libs.versions.toml` 中的 `nessie` 版本号。由于所有 Nessie 模块共享同一个版本变量，一个修改即可更新所有模块。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 nessie 版本。

**工作逻辑**：
```toml
nessie = "0.107.5"  # 从 0.107.4 升级
```

## 总结

这是一个 Dependabot 自动依赖升级提交，将 Nessie 从 0.107.4 升级到 0.107.5（patch 版本）。作为 patch 级别升级，通常包含 bug 修复，风险较低。Nessie 客户端库用于生产代码，其余模块用于测试。
