# 提交 0868：Build: Bump nessie from 0.90.4 to 0.91.1 (#10551)

## 提交信息

- **序号**：0868 / 4088
- **哈希**：a75fb1c961128f2fc49dd3f7835edd005410585e
- **短哈希**：a75fb1c96
- **日期**：2024-06-24（Mon Jun 24 10:28:52 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.90.4 to 0.91.1 (#10551)
- **PR/Issue**：#10551

## 总体目的

Iceberg 通过 `nessie` 模块对接 Project Nessie（一种提供 Git-like 版本控制的 catalog）。Nessie 相关的客户端与测试扩展由 `org.projectnessie.nessie` 提供的多个工件组成（`nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension`），这些工件的版本在 `gradle/libs.versions.toml` 中由统一的 `nessie` 变量管理。本提交由 dependabot 自动生成，目的是把该统一版本从 0.90.4 升级到 0.91.1，跟进 Nessie 0.91.x 系列的修复与改进。

Nessie 0.91.x 是一个 minor 版本升级（0.90 → 0.91），通常包含新功能、bug 修复以及可能的 API 调整。本提交只升级依赖版本，不修改任何使用 Nessie API 的代码，依赖 Nessie 自身向后兼容性。

## 如何达成设计目的

实现方式非常直接：修改 `gradle/libs.versions.toml` 中 `nessie` 变量的版本钉，从 `0.90.4` 改为 `0.91.1`。该变量被 version catalog 中所有 nessie 相关工件坐标引用，统一对齐。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Nessie 相关 4 个工件的统一版本从 0.90.4 升级到 0.91.1。

**工作逻辑**：仅修改一行，diff 如下：

```diff
 microprofile-openapi-api = "3.1.1"
 mockito = "4.11.0"
 mockserver = "5.15.0"
-nessie = "0.90.4"
+nessie = "0.91.1"
 netty-buffer = "4.1.111.Final"
 netty-buffer-compat = "4.1.111.Final"
 object-client-bundle = "3.3.2"
```

被该变量影响的工件包括 `nessie-client`（运行时 NessieCatalog 调用 Nessie 服务用的客户端）、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension`（后三者主要用于 Nessie 相关测试场景）。

## 小结

- **成效**：把 Project Nessie 相关 4 个工件版本从 0.90.4 升级到 0.91.1，跟进 Nessie 0.91.x 系列修复与改进。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动。运行时影响：使用 `NessieCatalog` 对接 Nessie 服务的用户会切换到 Nessie 0.91.1 客户端；测试影响：所有 Nessie 相关测试扩展会切换版本。
- **回迁到 1.4.x 的注意事项**：本提交是 Nessie minor 版本升级（0.90 → 0.91），**回迁需谨慎评估**。注意事项：(1) minor 版本升级可能伴随 API 变化，需确认 Iceberg `nessie` 模块代码（如 `NessieCatalog`、`NessieTableOperations` 等）在 Nessie 0.91.1 客户端 API 下仍能编译通过；(2) 测试扩展 API 也可能变化，需运行 Nessie 相关测试；(3) 1.4.x 分支若同时回迁了 awssdk-bom 升级（提交 0866）需注意 Nessie 传递依赖的 awssdk 版本会被 BOM 覆盖；(4) 本提交发布后不久就有后续 #10563（提交 0870）把 Nessie 再升到 0.91.2，1.4.x 回迁时可直接采用 0870 的 0.91.2 版本，跳过 0867；(5) 该升级不依赖其他提交，可独立 cherry-pick。
