# 提交 1018：Build: Bump nessie from 0.94.2 to 0.94.4 (#10869)

## 提交信息

- **序号**：1018 / 4088
- **哈希**：98ecc9a9ef7e7bd136d308828355c107de86f4b2
- **短哈希**：98ecc9a9e
- **日期**：2024-08-05 08:57:45 +0200
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.94.2 to 0.94.4 (#10869)
- **PR/Issue**：#10869

## 总体目的

这是 Dependabot 自动生成的依赖升级 PR。Nessie 是 Iceberg 支持的一种"事务型目录"（catalog with Git-like branching），Iceberg 通过 `nessie-client` 与 Nessie 服务交互，测试侧通过 `nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 提供内嵌测试环境。这四个 Nessie 组件共享同一版本号 `nessie`，定义在 `gradle/libs.versions.toml` 中。Dependabot 检测到 0.94.2 升级到 0.94.4（两个 patch 版本），属于直接生产依赖。本提交的目的是跟进 Nessie 上游 patch 修复，保持客户端与测试扩展的最新稳定版。

## 如何达成设计目的

实现方式是单行版本号替换：在 `gradle/libs.versions.toml` 的版本目录中，把 `nessie = "0.94.2"` 改为 `nessie = "0.94.4"`。由于 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 四个库都通过 `${libs.versions.nessie}` 引用同一版本变量，一次替换即可同步升级全部四个组件。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Nessie 版本变量从 0.94.2 升级到 0.94.4，同步覆盖 client 与三个测试扩展。

**工作逻辑**：在 `[versions]` 段中：
```
-nessie = "0.94.2"
+nessie = "0.94.4"
```
该变量被 `[libraries]` 段中 `nessie-client = { module = "org.projectnessie.nessie:nessie-client", version.ref = "nessie" }` 等四条引用，Gradle 解析时这四个库会统一使用 0.94.4。其它依赖版本（microprofile-openapi-api、mockito、mockserver、netty-buffer 等）保持不变。

## 小结

- **成效**：将 Nessie 全家桶（client + 三个测试扩展）从 0.94.2 升级到 0.94.4，跟进上游两个 patch 版本的 bug 修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动。影响 Nessie catalog 客户端运行时与 Nessie 相关测试，不涉及其它 catalog 实现或核心功能。
- **回迁到 1.4.x 的注意事项**：本提交是依赖版本升级，回迁风险低。需注意：(1) 1.4.x 分支的 `libs.versions.toml` 中 `nessie` 版本可能与 main 不同，cherry-pick 时直接同步版本号即可；(2) 0.94.4 是 patch 升级，API 兼容，但建议回迁后跑一遍 Nessie 相关测试（如 `TestNessieCatalog`）确认无回归；(3) 若 1.4.x 已有等价或更高版本，可跳过。整体可选回迁。
