# 提交 0319：Build: Bump Nessie to 0.76.0 (#9398)

## 提交信息

- **序号**：0319 / 4088
- **哈希**：38eb1b18b069d855269ecfe26fe351b79116139b
- **短哈希**：38eb1b18b
- **日期**：2024-01-03 09:11:46 +0100
- **作者**：Robert Stupp
- **提交说明**：Build: Bump Nessie to 0.76.0 (#9398)
- **PR/Issue**：#9398

## 总体目的

这是依赖升级提交，把 Iceberg 中 `iceberg-nessie` 模块依赖的 Nessie 客户端从 `0.75.0` 升级到 `0.76.0`。Nessie 是 Iceberg 支持的多种 catalog 实现之一（NessieCatalog），它本身是一个独立的版本化数据目录服务，会持续发布新版本修复 bug、提升稳定性、增强功能。Iceberg 作为下游需要定期跟进 Nessie 的版本以保持兼容性与获得上游修复。

本次升级除了单纯的版本号变更，还顺带处理了一个依赖管理问题：Nessie 客户端会把 `jackson-bom` 作为传递依赖带进来，这与 Iceberg 自己通过 `implementation platform(libs.jackson.bom)` 管理的 Jackson 版本可能冲突或重复。提交通过 `exclude group: 'com.fasterxml.jackson'` 在引入 `libs.nessie.client` 时把 Nessie 带来的 Jackson 相关依赖排除掉，让 Iceberg 统一使用自己声明的 Jackson BOM 版本，避免版本漂移与潜在的类路径冲突。

## 如何达成设计目的

设计思路分两步：(1) 在 `gradle/libs.versions.toml` 把 `nessie = "0.75.0"` 改为 `0.76.0`，这是版本号真正落地的地方，所有引用 `libs.nessie.client` 的模块（即 `iceberg-nessie`）都会随之升级；(2) 在 `build.gradle` 的 `iceberg-nessie` 模块里，把原来的 `implementation libs.nessie.client` 改为带 `exclude group: 'com.fasterxml.jackson'` 的写法，从而切掉 Nessie 传递进来的 Jackson BOM/依赖。两个改动配合，既升级了 Nessie，又保持了 Iceberg 自身 Jackson 版本管理的清晰。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Nessie 依赖版本从 `0.75.0` 升级到 `0.76.0`。

**工作逻辑**：版本目录（version catalog）中 `nessie = "0.75.0"` 改为 `nessie = "0.76.0"`。这一处是 Nessie 版本的单点真相（single source of truth），`libs.nessie.client` 会自动解析到新版本。

### `build.gradle`

**修改目的**：在 `iceberg-nessie` 模块引入 Nessie 客户端时排除其传递的 Jackson 依赖。

**工作逻辑**：
原来：
```groovy
implementation libs.nessie.client
```
改为：
```groovy
implementation(libs.nessie.client) {
  exclude group: 'com.fasterxml.jackson'
}
```
随后的几行继续通过 `implementation platform(libs.jackson.bom)` 以及显式声明 `jackson-databind`、`jackson-core` 来确定 Iceberg 自身使用的 Jackson 版本。这样 Nessie 不会把另一份 Jackson BOM 或具体 Jackson artifact 引入类路径，避免重复/冲突。从 commit message 第二条 bullet `* Exclude jackson-bom as a dependency for Nessie` 可以印证作者的有意为之。

## 小结

一个常规的依赖升级提交：把 Nessie 从 `0.75.0` 升到 `0.76.0`，并通过在 `iceberg-nessie` 模块排除 `com.fasterxml.jackson` 依赖来收紧 Jackson 版本管理、避免传递依赖造成的版本漂移。改动小但有助于保持依赖图健康和与 Nessie 上游的兼容性。
