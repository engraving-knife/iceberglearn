# 提交 3834：Build: Bump jackson-bom from 2.21.3 to 2.21.4 (#16702)

## 提交信息

- **序号**：3834 / 4088
- **哈希**：44ecffa67d45cc9ac5c340e1014d199c60595b99
- **短哈希**：44ecffa67
- **日期**：2026-06-07 09:40:44 -0700
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump jackson-bom from 2.21.3 to 2.21.4 (#16702)
- **PR/Issue**：#16702

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 项目依赖的 Jackson BOM（`com.fasterxml.jackson:jackson-bom`）从 `2.21.3` 升级到 `2.21.4`，同步覆盖 `jackson-core` 与 `jackson-databind` 三个组件。Jackson 是 Java 生态中最广泛使用的 JSON 处理库，Iceberg 在 REST catalog、表元数据序列化、OpenAPI 校验等多处依赖它进行 JSON 读写。Jackson BOM 统一管理 Jackson 各模块版本，确保彼此兼容。这是一个 patch 级升级（2.21.3 → 2.21.4），通常包含 bug 修复与小改进，不引入破坏性变更，属于常规依赖维护，有助于及时获取上游修复与安全补丁。

## 如何达成设计目的

Dependabot 检测到 `gradle/libs.versions.toml` 中 `jackson-bom` 版本有新发布，自动创建 PR 升级版本字符串。由于使用 BOM 管理版本，仅需修改一处即可让 `jackson-core`、`jackson-databind`、`jackson-annotations` 等所有 Jackson 模块版本同步更新。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Jackson BOM 版本。

**工作逻辑**：
将版本目录中的 BOM 版本声明从 `2.21.3` 改为 `2.21.4`：
```toml
-jackson-bom = "2.21.3"
+jackson-bom = "2.21.4"
```
所有通过 BOM 引入的 Jackson 模块会自动获得新版本。

## 总结

这是一次常规的 patch 级依赖升级，由 Dependabot 自动完成，改动仅一行。Jackson 是 Iceberg JSON 处理的核心依赖，及时升级有助于获取 bug 修复与安全补丁。由于是 BOM 管理，影响范围可控，版本间兼容性由 BOM 保证。
