# 提交 1453：Build: Bump jackson-bom from 2.18.1 to 2.18.2 (#11681)

## 提交信息

- **序号**：1453 / 4088
- **哈希**：a993b799816c25cfeb126d9f99ca7d0648bbdcaf
- **短哈希**：a993b7998
- **日期**：2024-12-02（Mon Dec 2 06:27:25 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump jackson-bom from 2.18.1 to 2.18.2 (#11681)
- **PR/Issue**：#11681

## 总体目的

由 Dependabot 自动发起的依赖版本升级，把 Jackson BOM（Bill of Materials）从 `2.18.1` 升到 `2.18.2`。Jackson 是 Iceberg 全栈核心的 JSON 序列化库——REST Catalog 的请求/响应序列化、表元数据 JSON、配置序列化等都依赖它。`jackson-bom` 是一个 POM 型依赖，通过 Gradle 的版本目录（version catalog）引入后，会统一管理 `jackson-core`、`jackson-databind`、`jackson-annotations` 以及 `jackson-datatype-*`、`jackson-module-*` 等一整套 Jackson 模块的版本，确保彼此兼容。

`2.18.1 → 2.18.2` 是同一 minor（2.18.x）内的 patch 升级，按 SemVer 约定仅含 bug 修复与小幅改进，无破坏性 API 变更。Dependabot 的动机是跟进上游修复、保持依赖最新以减少已知缺陷与安全风险。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `jackson-bom` 这一行版本号即可。由于项目用 BOM 统一管理 Jackson 各模块版本，且各模块在 `build.gradle` 中以 `jackson-bom` 为依赖来源（不写死版本），因此单点修改版本号即可让所有 Jackson 模块（core/databind/annotations 及其子模块）整体平移到 2.18.2。

## 修改详情

### `gradle/libs.versions.toml`（修改，1 行）

**修改目的**：将 `jackson-bom` 版本从 `2.18.1` 升至 `2.18.2`。

**工作逻辑**：

```toml
-jackson-bom = "2.18.1"
+jackson-bom = "2.18.2"
```

该文件是 Gradle 7 引入的版本目录（version catalog），集中声明所有依赖版本。`jackson-bom` 条目位于依赖版本区，其他 build 脚本通过 `libs.jackson.bom` 引用，进而让 Gradle 解析时把 Jackson 全家桶锁定到 2.18.2。

注意：同文件中还有 `jackson211`、`jackson212`、`jackson213`、`jackson214`、`jackson215` 等带 `strictly` 的版本——这些是 Spark 各版本强制的特定 Jackson 版本（Spark 自身对 Jackson 版本有严格约束），与本次升级的 `jackson-bom`（用于 iceberg-core 等非 Spark 模块）互不影响。

## 小结

- **成效**：把 Iceberg 自身使用的 Jackson 全家桶从 2.18.1 升到 2.18.2，获得上游 patch 修复；改动仅 1 行，无代码逻辑变化。
- **影响范围**：所有依赖 `jackson-bom` 的模块（core、rest、aws、azure、gcp 等非 Spark 模块）；Spark 模块因使用各自 `jackson21x` 严格版本不受影响。
- **回迁到 1.4.x 的注意事项**：可安全 cherry-pick。需确认 1.4.x 分支的 `gradle/libs.versions.toml` 仍采用同样的版本目录结构与 `jackson-bom` 命名；若 1.4.x 上有针对 Jackson 的自定义补丁或对 2.18.x 已知问题的 workaround，升级后应回归 REST Catalog 的 JSON 序列化路径（建表、加载表、提交、OAuth token 解析等）。2.18.x 内 patch 升级通常无破坏，但仍建议跑一遍 REST 相关测试。
