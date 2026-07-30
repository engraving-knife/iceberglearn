# 提交 0582：Build: Let revapi compare against 1.5.0

## 提交信息

- **序号**：0582 / 4088
- **哈希**：5c27843d77e19fa3eeb3472ee9dbc319566429dd
- **短哈希**：5c27843d7
- **日期**：2024-03-11（Mon Mar 11 20:14:07 2024 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Build: Let revapi compare against 1.5.0 (#9777)
- **PR/Issue**：#9777

## 总体目的

Iceberg 项目使用 [Revapi](https://revapi.org/)（通过 `com.palantir.gradle.revapi` Gradle 插件）对每个子模块做 API/ABI 兼容性检查，以确保版本演进遵循语义化版本（Semantic Versioning）和贡献指南中规定的 deprecation 周期——破坏性 API 变更必须先经过一个 deprecation 周期，不允许直接引入 API/ABI breaks。

`build.gradle` 中的 `revapi { oldVersion = "..." }` 配置用于指定"上一次发布的稳定版本"作为基线，新版本（即当前 main 分支正在开发的下一个版本）会和这个基线做 API/ABI 比较。在 1.5.0 发布之前，基线是 1.4.0；1.5.0 正式发布（2024-03-11）后，main 分支上正在开发的下一个版本应当以 1.5.0 作为比较基线，因为 1.5.0 已经成为新的"上一次发布版本"。

本提交的目的是在 1.5.0 发布当天，将 Revapi 的比较基线从 `1.4.0` 更新为 `1.5.0`，让 main 分支后续的 API 变更都相对于 1.5.0 进行检测，确保 1.5.0 之后引入的任何不兼容变更都能被 Revapi 准确捕获，从而维护 1.5.0 → 1.6.0（或下一个版本）的 API 兼容性契约。

## 如何达成设计目的

实现方式非常简单：在 `build.gradle` 的 `subprojects { ... }` 块内，将 `revapi` 配置中的 `oldVersion` 字段值由字符串 `"1.4.0"` 修改为 `"1.5.0"`，其余配置（`oldGroup`、`oldName`）保持不变。

设计上的考量如下：

1. **基线版本随发布节奏滚动**：Revapi 的 `oldVersion` 应当始终指向"上一个已发布的稳定版本"。1.5.0 发布后，main 分支的下一个开发版本（预期是 1.6.0）需要和 1.5.0 做比较，所以基线必须从 1.4.0 滚到 1.5.0。如果保持 1.4.0 不变，1.5.0 引入的新 API 会被错误地当作"已存在 API"，无法发现 1.5.0 之后对其的破坏性变更。

2. **配合 deprecation 周期**：`build.gradle` 紧随其后定义了 `showDeprecationRulesOnRevApiFailure` 任务，当 Revapi 检测到 break 时会失败并提示开发者遵循 CONTRIBUTING.md 中的语义化版本和 deprecation 规则。基线版本的及时更新是这一机制发挥作用的前提。

3. **仅修改基线版本字符串，不引入额外校验逻辑**：保持改动最小化，避免触碰其他构建配置，方便回滚或后续再次滚动到 1.6.0。

## 修改详情

### `build.gradle`

**修改目的**：将 Revapi 的 API/ABI 兼容性比较基线从 1.4.0 升级到 1.5.0，使 main 分支后续的 API 变更检测基于 1.5.0 进行。

**工作逻辑**：

在 `subprojects` 块内，Revapi 插件配置如下（修改前后对比）：

```diff
     revapi {
       oldGroup = project.group
       oldName = project.name
-      oldVersion = "1.4.0"
+      oldVersion = "1.5.0"
     }
```

- `oldGroup = project.group`：基线构件的 groupId，与当前子模块一致（`org.apache.iceberg`）；
- `oldName = project.name`：基线构件的 artifactId，与当前子模块一致；
- `oldVersion = "1.5.0"`：基线构件的版本号，从 `"1.4.0"` 改为 `"1.5.0"`。

Revapi 插件在执行 `revapi` 任务时，会从 Maven 仓库（本地缓存或远程）解析 `org.apache.iceberg:<artifactId>:1.5.0` 这一已发布构件，将其作为"旧 API"，与当前子模块构建产物（"新 API"）进行二进制和源码级 API/ABI 比较，输出差异报告。若有 break（如删除 public 方法、改变方法签名、新增抽象方法等），任务失败并触发 `showDeprecationRulesOnRevApiFailure` 任务抛出异常，提示开发者必须走 deprecation 流程。

紧随其后的相关任务配置（未改动，提供上下文）：

```gradle
tasks.register('showDeprecationRulesOnRevApiFailure') {
  doLast {
    throw new RuntimeException("..." +
            "\nAPI/ABI breaks detected.\n" +
            "Adding RevAPI breaks should only be done after going through a deprecation cycle." +
            "\nPlease make sure to follow the deprecation rules defined in\n" +
            "https://github.com/apache/iceberg/blob/master/CONTRIBUTING.md#semantic-versioning.\n" +
            "...")
  }
  onlyIf {
    tasks.revapi.state.failure != null
  }
}

tasks.configureEach { rootTask ->
  if (rootTask.name == 'revapi') {
    rootTask.finalizedBy showDeprecationRulesOnRevApiFailure
  }
}
```

这一配套机制保证了 Revapi 检测到 break 时会强制失败并指引开发者遵守 deprecation 周期。

## 小结

- **成效**：完成 Revapi 基线版本滚动，main 分支自此以 1.5.0 作为 API/ABI 兼容性检查基准，确保后续开发能正确检测 1.5.0 之后的破坏性 API 变更。
- **影响范围**：仅修改 `build.gradle` 一行配置，不影响任何生产代码、文档或运行时行为；仅影响 CI 中的 `revapi` 任务和本地构建时的 API 检查结果。
- **回迁到 1.4.x 的注意事项**：**不应回迁到 1.4.x 分支**。1.4.x 维护分支上的 Revapi 基线应当保持指向 1.4.0 之前的稳定版本（在 1.4.x 分支当前为 `1.3.0`），用于校验 1.4.x patch 发布之间的 API 兼容性。如果把 main 上的这条改动（基线改为 1.5.0）cherry-pick 到 1.4.x，会让 1.4.x 分支的 Revapi 试图从 Maven 仓库解析 `1.5.0` 构件作为基线，但 1.4.x 分支的 API 与 1.5.0 可能存在预期外的差异（1.5.0 是新 minor 版本，包含新 API），导致 Revapi 在 1.4.x 分支上频繁误报 break，干扰 1.4.x patch 的正常发布流程。
