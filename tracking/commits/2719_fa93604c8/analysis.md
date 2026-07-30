# 提交 2719：Build: Bump jackson-bom from 2.19.2 to 2.20.0 and jackson-annotations to 2.20

## 提交信息

- **序号**：2719 / 4088
- **哈希**：fa93604c8503ec5e02c48b403cbd7ec905c0af4c
- **短哈希**：fa93604c8
- **日期**：2025-10-06 14:27:40 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump jackson-bom from 2.19.2 to 2.20.0 and jackson-annotations to 2.20
- **PR/Issue**：#13961

## 总体目的

此提交将 Jackson JSON 处理库从 2.19.2 升级到 2.20.0。Jackson 是 Java 生态中最广泛使用的 JSON 序列化/反序列化库，在 Iceberg 项目中被大量用于 REST API 的 JSON 处理、配置序列化等场景。

此次升级除了将 `jackson-bom`（BOM，统一管理 jackson-core、jackson-databind、jackson-annotations 等模块的版本）从 2.19.2 升级到 2.20.0 外，还有一个重要的结构变更：`jackson-annotations` 不再跟随 `jackson-bom` 的版本号，而是单独引用一个新的版本变量 `jackson-annotations = "2.20"`。

Jackson 2.20.0 是一个 minor 版本升级（从 2.19.x 到 2.20.x），包含新功能和改进。Jackson 团队有时会将 `jackson-annotations` 的版本号与 `jackson-core`/`jackson-databind` 区分管理，`jackson-annotations` 使用 `2.20` 而非 `2.20.0` 格式的版本号。

## 如何达成设计目的

主要设计思路：
1. 在版本目录中新增 `jackson-annotations = "2.20"` 版本变量
2. 将 `jackson-bom` 从 `2.19.2` 升级到 `2.20.0`
3. 将 `jackson-annotations` 库的 `version.ref` 从 `jackson-bom` 改为 `jackson-annotations`

这样 `jackson-core` 和 `jackson-databind` 仍跟随 `jackson-bom` 版本（2.20.0），而 `jackson-annotations` 使用独立的版本变量 `2.20`。

## 修改详情

### `gradle/libs.versions.toml` (+3/-2 lines)

**修改目的**：升级 Jackson 版本并分离 jackson-annotations 的版本引用。

**工作逻辑**：
1. **版本定义部分**：新增 `jackson-annotations = "2.20"` 版本变量；将 `jackson-bom` 从 `"2.19.2"` 更改为 `"2.20.0"`。
2. **库定义部分**：将 `jackson-annotations` 的 `version.ref` 从 `"jackson-bom"` 更改为 `"jackson-annotations"`，使其引用新定义的独立版本变量，而非跟随 BOM 版本。

这一改动意味着 `jackson-annotations` 将使用 `2.20` 版本，而 `jackson-core` 和 `jackson-databind` 使用 `2.20.0` 版本。这反映了 Jackson 项目中 annotations 模块的版本号命名约定（使用两位版本号而非三位）。

## 总结

此提交将 Jackson JSON 库从 2.19.2 升级到 2.20.0，并将 `jackson-annotations` 的版本引用从跟随 BOM 改为使用独立的版本变量。这确保了 Iceberg 使用最新版本的 Jackson，获得新功能和改进。分离 annotations 版本变量的改动反映了 Jackson 项目的版本号约定差异。Jackson 是 Iceberg 的核心依赖之一，minor 版本升级通常保持向后兼容。
