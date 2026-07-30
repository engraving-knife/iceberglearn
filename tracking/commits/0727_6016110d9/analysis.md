# 提交分析：Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#10239)

## 提交信息

| 项目 | 内容 |
| --- | --- |
| 哈希 | `6016110d941ec001f593a7c20bdaff81a131208a` |
| 短哈希 | `6016110d9` |
| 作者 | dependabot[bot] |
| 提交时间 | 2024-04-29 08:45:52 +0200 |
| 提交标题 | Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#10239) |
| 提交正文 | Bumps com.gorylenko.gradle-git-properties:gradle-git-properties from 2.4.1 to 2.4.2.（dependabot 自动生成，含 updated-dependencies 元数据） |
| 变更范围 | 1 个文件，1 行新增，1 行删除 |

## 总体目的

由 dependabot 自动发起的构建插件依赖升级，将 Gradle 插件 `com.gorylenko.gradle-git-properties:gradle-git-properties` 从 `2.4.1` 升级到 `2.4.2`，以获取该插件在 2.4.2 中提供的修复与改进。这是一次 patch 级别的版本升级。

## 如何达成设计目的

`gradle-git-properties` 插件在 Iceberg 的根 `build.gradle` 的 `buildscript` classpath 中声明，用于在构建时生成 `git.properties` 资源文件（记录当前 git 描述信息，便于运行时排查版本）。本次直接在 `buildscript` 依赖声明的 GAV 坐标上把版本号从 `2.4.1` 改为 `2.4.2`，Gradle 会在下一次构建时解析并拉取新版本插件，无需修改任何插件配置或应用代码。

## 修改详情

### `build.gradle`

`buildscript` 依赖块中的插件坐标版本号更新：

```groovy
-    classpath 'com.gorylenko.gradle-git-properties:gradle-git-properties:2.4.1'
+    classpath 'com.gorylenko.gradle-git-properties:gradle-git-properties:2.4.2'
```

- 仅此一行变更，作用域为构建脚本自身的 classpath（`buildscript { dependencies { classpath ... } }`），不影响产物的运行时依赖。
- `2.4.1 → 2.4.2` 为 patch 升级，按该插件的版本约定向后兼容。

## 小结

### 成效
- 对齐 `gradle-git-properties` 插件的最新 patch 版本，纳入上游修复。
- 改动仅限构建脚本 classpath，对 Iceberg 产物与 API 无任何影响。

### 影响范围
- 仅影响构建期：`git.properties` 的生成行为可能因插件版本变化而出现细微差异（属于构建产物元数据，非功能行为）。
- 不影响 Iceberg 的源码、测试逻辑或运行时依赖图。

### 回迁注意事项（1.4.x ← main）
- 纯构建插件版本号升级，回迁无冲突风险，直接同步 `build.gradle` 中该行即可。
- 若 1.4.x 分支对 `build.gradle` 的 `buildscript` 块有自定义改动，注意合并时保留本行新版本号。
- 由于是构建期依赖，回迁后建议本地执行一次完整构建，确认插件 2.4.2 在 1.4.x 的 Gradle 版本上正常工作。
