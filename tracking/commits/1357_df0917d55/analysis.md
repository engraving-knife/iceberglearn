# 提交 1357：Build: Let revapi compare against 1.7.0 (#11490)

## 提交信息

- **序号**：1357 / 4088
- **哈希**：df0917d5536eae151d667a62231c0023dcd5f1e9
- **短哈希**：df0917d55
- **日期**：2024-11-08（Fri Nov 8 13:35:12 2024 -0600）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：Build: Let revapi compare against 1.7.0 (#11490)
- **PR/Issue**：#11490

## 总体目的

Iceberg 在 `build.gradle` 中集成了 [revapi](https://revapi.org/)（Java API 兼容性检查工具），用于在每个子项目的构建中比对当前代码与某个基准版本的公开 API 差异（新增、移除、不兼容变更等），以在 CI 中及时捕捉破坏性 API 变更。基准版本通过 `revapi.oldVersion` 指定。

1.7.0 已发布，main 分支后续开发的 API 基线应从 1.6.0 提升到 1.7.0，这样 CI 中的 revapi 检查只关注"自 1.7.0 以来的 API 变化"，避免 1.6.0→1.7.0 之间已经评审过的变更持续产生噪音，让维护者聚焦于最新 main 与 1.7.0 之间的差异。本提交把 `oldVersion` 从 `1.6.0` 改为 `1.7.0`。

## 如何达成设计目的

直接编辑 `build.gradle`，将 `subprojects` 块中 `revapi` 配置的 `oldVersion` 由 `"1.6.0"` 改为 `"1.7.0"`。`oldGroup` 与 `oldName` 保持不变（仍为当前 project 的 group/name），revapi 会据此从仓库/Maven 本地缓存中解析 1.7.0 的 jar 进行对比。这是单行构建配置调整，无代码逻辑。

## 修改详情

### `build.gradle`

**修改目的**：把 revapi API 兼容性检查的基准版本从 1.6.0 提升到 1.7.0。

**工作逻辑**：在 `subprojects` 块内（约第 138 行）：

```groovy
    revapi {
      oldGroup = project.group
      oldName = project.name
      oldVersion = "1.7.0"
    }
```

此后 CI 运行 revapi 任务时，会对每个子项目比对当前代码与已发布的 1.7.0 artifact 的 API，输出二者之间的差异报告，从而在 PR 阶段暴露破坏性 API 变更。

## 小结

- **成效**：revapi 现以 1.7.0 为 API 基线，后续 main 上的 API 变更将基于 1.7.0 进行兼容性比较，避免历史噪音。
- **影响范围**：仅 `build.gradle` 一个文件，修改 1 行，无代码、运行时或文档变更。
- **回迁到 1.4.x 的注意事项**：1.4.x 是已发布的维护分支，其 `build.gradle` 中的 `oldVersion` 应保持与该分支版本对应的基线（如 1.4.x），不应回迁 main 上指向 1.7.0 的改动——否则 1.4.x 分支的 revapi 会去比对一个尚未存在于该分支上下文的 1.7.0 artifact，产生错误结果。**无需回迁**。
