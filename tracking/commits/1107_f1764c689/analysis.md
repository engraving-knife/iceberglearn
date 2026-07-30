# 提交 1107：Build: Bump org.xerial:sqlite-jdbc from 3.46.0.1 to 3.46.1.0 (#11007)

## 提交信息

- **序号**：1107 / 4088
- **哈希**：f1764c6894409e802e4b8b8643a2dd7d400b696c
- **短哈希**：f1764c689
- **日期**：2024-08-26（Mon Aug 26 20:38:25 2024 -0600）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.46.0.1 to 3.46.1.0 (#11007)
- **PR/Issue**：#11007（Dependabot 自动 PR）
- **影响模块**：gradle 依赖版本管理（`gradle/libs.versions.toml`）

## 总体目的

Iceberg 仓库使用 Dependabot 自动监控直接生产依赖的版本更新，定期为过期依赖提交 patch/minor 升级 PR。本提交把 `org.xerial:sqlite-jdbc` 从 `3.46.0.1` 升到 `3.46.1.0`，属于 SQLite JDBC 驱动的 patch 版本升级，通常包含 bug 修复与小幅改进（无 breaking change）。

Iceberg 在测试场景下使用 SQLite 作为轻量级 JDBC catalog 后端（如 `SqlCatalog` 测试），sqlite-jdbc 是测试与部分集成场景的基础依赖。

## 如何达成设计目的

Iceberg 使用 Gradle 的 version catalog（`gradle/libs.versions.toml`）集中管理依赖版本。Dependabot 识别到上游 `org.xerial:sqlite-jdbc` 发布了 `3.46.1.0`，自动修改 catalog 中的版本字符串，提交 PR。合入后所有引用 `libs.sqlite.jdbc`（或类似别名）的子项目在下次构建时自动使用新版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 sqlite-jdbc 版本字符串。

**工作逻辑**：在 `[versions]` 区段把

```toml
sqlite-jdbc = "3.46.0.1"
```

改为

```toml
sqlite-jdbc = "3.46.1.0"
```

其他行不变。这是单行、单字符段（patch 段 `0.1` → `1.0`）的版本字符串替换。

## 小结

- **成效**：sqlite-jdbc 升到 3.46.1.0，享受上游 patch 修复；Dependabot 元数据完整（dependency-name、dependency-type=direct:production、update-type=version-update:semver-patch）。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行，无代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：
  - 依赖版本升级通常 **无需回迁**到维护分支——1.4.x 有自己的依赖策略与 Dependabot 配置，会自行跟进上游版本。
  - 即使 1.4.x 仍用 3.46.0.1 也不会有功能问题，patch 升级主要修复上游 bug；如果 1.4.x 已知存在 SQLite 相关测试 bug，可考虑手工回迁这一行。
  - 如确实要回迁，确认 1.4.x 的 `libs.versions.toml` 结构与 main 一致，然后改对应行即可。
