# 提交 1261：Build: Bump com.google.errorprone:error_prone_annotations (#11360)

## 提交信息

- **序号**：1261 / 4088
- **哈希**：181476471a75afcee1768ddf99afd88b6e348006
- **短哈希**：181476471
- **日期**：2024-10-21（Mon Oct 21 09:07:18 2024 +0200）
- **作者**：dependabot[bot]；共同作者：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#11360)
- **PR/Issue**：#11360

## 总体目的

Google 的 error_prone_annotations 是 Error Prone 静态分析工具的注解库（提供 @FormatMethod、@CompatibleWith、@CanIgnoreReturnValue 等注解），Iceberg 在源码中多处使用这些注解辅助编译期检查。dependabot 检测到 gradle/libs.versions.toml 中声明的版本 2.33.0 已落后，发起本次升级至 2.34.0（semver minor 升级）。

升级目的是获取 Error Prone 2.34.0 的注解库改进与缺陷修复，保持与最新 Error Prone 工具链的兼容性。error_prone_annotations 是纯注解库（仅含注解定义，无运行时逻辑），升级风险极低。

## 如何达成设计目的

单点修改版本目录：在 gradle/libs.versions.toml 中将 errorprone-annotations = "2.33.0" 改为 "2.34.0"。该版本目录条目被各模块引用以获取注解依赖坐标，改一处全局生效。本次升级未伴随任何源码或测试调整，说明 2.33.0→2.34.0 的注解库变化未影响 Iceberg 既有注解用法（新版本通常只是新增注解或修复注解元数据，不破坏既有 API）。

提交说明保留 dependabot 标准 元信息：依赖类型 direct:production、更新类型 version-update:semver-minor，并附 release notes 与 commits 对比链接，便于评估升级内容。注意本提交与 #11362（gradle-baseline-java 升级，本批序号 1260）几乎同一时间由 dependabot 分别发起，二者协同维护 Error Prone 工具链的一致性（baseline 插件内部也依赖 Error Prone）。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 error_prone_annotations 依赖版本。

**工作逻辑**：在版本目录中，将

```toml
errorprone-annotations = "2.33.0"
```

改为

```toml
errorprone-annotations = "2.34.0"
```

该条目被 Iceberg 各子模块通过 libs.errorprone.annotations（或类似别名）引用，作为编译期注解依赖。升级后，源码中使用的 Error Prone 注解将解析到 2.34.0 版本的注解定义。

## 小结

- **成效**：error_prone_annotations 从 2.33.0 升级到 2.34.0，获取注解库的改进与修复，保持与最新 Error Prone 工具链兼容；本次升级未引入任何源码改动，说明既有注解用法在新版本下仍合规。
- **影响范围**：仅 gradle/libs.versions.toml 1 个文件、1 行版本号改动。属编译期注解依赖升级，error_prone_annotations 是纯注解库（无运行时逻辑），不改变任何产品代码、API 或运行时行为。
- **回迁到 1.4.x 的注意事项**：注解库升级属极低风险变更。1.4.x **可安全回迁**，注解库向后兼容（新版本通常只新增注解、不删除或改名既有注解），不会破坏 1.4.x 既有源码的编译。回迁时需确认：1.4.x 的源码中使用的注解在 2.34.0 仍存在（Error Prone 注解库高度重视向后兼容，几乎不会移除注解）；若 1.4.x 同时使用 gradle-baseline-java，建议与 #11362（baseline 升级）协同回迁以保持工具链版本一致。本质上这是开发期依赖维护，对 1.4.x 的运行时功能无影响。
