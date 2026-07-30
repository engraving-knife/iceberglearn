# 提交 0968：Build: Update revapi to compare against 1.6.0 (#10754)

## 提交信息

- **序号**：0968 / 4088
- **哈希**：0cce1198ca4b2f057f70f129c732e275142bda94
- **短哈希**：0cce1198c
- **日期**：2024-07-23 15:51:55 -0600
- **作者**：Ajantha Bhat
- **提交说明**：Build: Update revapi to compare against 1.6.0 (#10754)
- **PR/Issue**：#10754

## 总体目的

Iceberg 在 Gradle 构建中使用 [revapi](https://revapi.org/) 插件做 API/ABI 兼容性检查，每次构建时会拿当前代码与一个"基线版本（oldVersion）"做比较，发现破坏性变更（如方法签名变更、删除、字段可见性改变等）就会让构建失败。这一机制保证了在 minor 版本演进过程中对外公开 API 的二进制/源码兼容性，避免无意中破坏下游使用者。

之前 revapi 的基线被设置为 `1.5.0`，即在每次构建时检查"当前代码相对于 1.5.0 是否有破坏性变更"。随着 Iceberg 1.6.0 已正式发布，1.6.0 成为新的"上一个 minor 发布版本"，需要把基线前移到 1.6.0，以便 1.7.0 开发周期的破坏性变更能被正确识别——如果继续以 1.5.0 为基线，那么 1.6.0 中已经接受（或允许）的破坏性变更会在每次构建时持续触发，干扰真正的回归检测。

本提交的目标就是把 revapi 的 `oldVersion` 从 `1.5.0` 更新为 `1.6.0`，使基线对齐到最新已发布版本。

## 如何达成设计目的

实现非常直接：在 `build.gradle` 的 `subprojects { revapi { ... } }` 配置块中把 `oldVersion = "1.5.0"` 改为 `oldVersion = "1.6.0"`。`oldGroup` 与 `oldName` 保持不变（仍为当前 `project.group` 与 `project.name`）。revapi 插件会在执行兼容性检查任务时从配置的仓库拉取 `org.apache.iceberg:iceberg-*:1.6.0` 的 jar 作为对比基线。

## 修改详情

### `build.gradle`

**修改目的**：把 revapi 兼容性检查的基线版本由 1.5.0 更新为 1.6.0。

**工作逻辑**：在 `subprojects { revapi { oldGroup = project.group; oldName = project.name; oldVersion = "1.5.0" } }` 这一段中，仅把 `oldVersion` 的字符串值由 `"1.5.0"` 改为 `"1.6.0"`，其它配置（`oldGroup`、`oldName`、以及下方 `showDeprecationRulesOnRevApiFailure` 任务注册等）保持不变。后续 CI 在执行 `revapi` 任务时就会去解析 1.6.0 的 artifact 作为对照基线。

## 小结

- **成效**：revapi 兼容性检查基线已与最新发布的 1.6.0 对齐，1.7.0 开发周期中产生的破坏性 API 变更将以此为基准被检测，避免 1.6.0 已接受的变更持续误报。
- **影响范围**：仅 `build.gradle` 一个文件，1 行字符串字面量改动，无源代码或测试改动。
- **回迁到 1.4.x 的注意事项**：**不应回迁到 1.4.x 分支**。1.4.x 是维护分支，其 revapi 基线应当对齐到 1.4.x 系列的最新发布版本（如 1.4.3 或后续 patch），而非 1.6.0；如果硬把 `oldVersion` 设成 `1.6.0`，1.4.x 与 1.6.0 之间的大量 API 演进会被报为破坏性变更，构建会持续失败。1.4.x 应保持自己的基线设置。
