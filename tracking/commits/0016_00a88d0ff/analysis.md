# 提交 0016：Build: Let revapi compare against 1.4.0 (#8727)

## 提交信息

- **序号**：0016 / 4088
- **哈希**：00a88d0fffb714d8e1dc2bd0bcc02b2d0cffd891
- **短哈希**：00a88d0ff
- **日期**：2023-10-06 08:11:22 +0200
- **作者**：Anton Okolnychyi
- **提交说明**：Build: Let revapi compare against 1.4.0 (#8727)
- **PR/Issue**：#8727

## 总体目的

这个提交把 Iceberg 构建脚本中 revapi（API 兼容性检查工具）比对的"旧版本基线"从 `1.3.0` 切换到 `1.4.0`，是 1.4.0 版本发布后的配套构建维护变更。

Iceberg 在 `build.gradle` 中集成了 revapi 插件，用于在构建阶段自动检查公共 API 是否发生了破坏性变更（binary/source compatibility）。revapi 需要一个"参照版本"作为对比基线——即当前代码不得相对于该基线版本出现不兼容的 API 变更。基线通常设为"上一个已发布版本"或"最近的稳定版本"。

1.4.0 是 Iceberg 在 2023 年 10 月初发布的新版本（本提交时间与 1.4.0 发布几乎同期）。一旦 1.4.0 发布，开发主线（main/1.4.x）后续的任何改动都应当以 1.4.0 为兼容性参照点，而不是继续停留在 1.3.0。否则后续若引入了相对 1.3.0 不兼容但相对 1.4.0 兼容的变更，revapi 会误报；反之，若引入了相对 1.4.0 不兼容的变更而基线仍是 1.3.0，则可能被 1.3.0→当前 的兼容路径掩盖而漏报。把基线推进到 1.4.0，意味着从这一刻起，所有后续 PR 都必须保证相对 1.4.0 的 API 兼容性，符合 Iceberg 语义化版本的演进规则。

## 如何达成设计目的

改动非常直接：在 `build.gradle` 的 `subprojects { ... revapi { ... } }` 配置块中，将 `oldVersion = "1.3.0"` 修改为 `oldVersion = "1.4.0"`，`oldGroup` 和 `oldName` 保持不变（仍为当前项目的 group 和 name）。这样 revapi 在执行 API 检查任务时会从 Maven 仓库解析对应项目 1.4.0 版本的 jar 作为旧版本，与当前构建产物进行差异比对。

## 修改详情

### `build.gradle`

**修改目的**：将 revapi API 兼容性检查的基线版本从 1.3.0 推进到 1.4.0。

**工作逻辑**：改动位于 `subprojects` 块内的 `revapi` 配置中（约第 133-136 行）。原配置 `oldVersion = "1.3.0"` 被改为 `oldVersion = "1.4.0"`。`oldGroup = project.group` 与 `oldName = project.name` 不变，意味着 revapi 仍以当前子项目的坐标去解析旧版本 artifact。这是 1.4.0 发版后的常规基线推进，确保后续开发以 1.4.0 为 API 兼容性参照点。

## 小结

该提交将 revapi API 兼容性检查基线从 1.3.0 推进到 1.4.0，使 1.4.0 成为后续开发的 API 兼容性参照版本，是 1.4.0 发版后的标准构建维护动作。
