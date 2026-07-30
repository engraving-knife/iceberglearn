# 提交 0901：Build: Use official revapi Gradle plugin (#10631)

## 提交信息

- **序号**：0901 / 4088
- **哈希**：83dd59a6f89bf9a93e430ba0cc0995e14897db63
- **短哈希**：83dd59a6f
- **日期**：2024-07-05（Fri Jul 5 12:23:38 2024 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Build: Use official revapi Gradle plugin (#10631)
- **PR/Issue**：#10631

## 总体目的

Revapi 是 Iceberg 项目用于检测 Java API/ABI 破坏性变更的工具——在构建时把当前代码的 API 与上一个发布版本（`oldVersion = "1.5.0"`）做比较，发现破坏性变更（如删除 public 方法、改变方法签名等）时报错，强制开发者走 deprecation 流程。Iceberg 的 `build.gradle` 中对 `iceberg-api`、`iceberg-core`、`iceberg-parquet`、`iceberg-orc`、`iceberg-common`、`iceberg-data` 这 6 个核心模块应用 revapi 插件。

此前 Iceberg 使用的是社区第三方维护的 revapi Gradle 插件：`io.github.nastra.gradle.revapi:gradle-revapi:1.8.1`（plugin id `io.github.nastra.revapi`），由开发者 nastra 个人维护并发布到 Gradle Plugin Portal。

本提交的目的是从社区第三方 revapi 插件切换到 revapi 官方维护的 Gradle 插件：`org.revapi:gradle-revapi:1.8.0`（plugin id `org.revapi.revapi-gradle-plugin`）。官方插件由 revapi 项目组直接维护，发布到 Maven Central，长期维护与安全更新更有保障，也避免了对个人维护者发布的依赖。

## 如何达成设计目的

实现方式非常简洁：在 `build.gradle` 的 `buildscript.ext` classpath 中把依赖坐标从 `io.github.nastra.gradle.revapi:gradle-revapi:1.8.1` 改为 `org.revapi:gradle-revapi:1.8.0`，并在 `subprojects` 块中把 `apply plugin:` 从 `io.github.nastra.revapi` 改为 `org.revapi.revapi-gradle-plugin`。`revapi { ... }` 配置块、`REVAPI_PROJECTS` 列表、`showDeprecationRulesOnRevApiFailure` 任务注册、`revapiAnalyze` 依赖配置等均保持不变，因为官方插件与社区插件在配置 DSL 上兼容。

## 修改详情

### `build.gradle`

**修改目的**：将 revapi Gradle 插件从社区版切换到官方版。

**工作逻辑**：两处改动：

1. `buildscript.ext` classpath 依赖：

```diff
-    classpath 'io.github.nastra.gradle.revapi:gradle-revapi:1.8.1'
+    classpath 'org.revapi:gradle-revapi:1.8.0'
```

把 Maven 坐标从 `io.github.nastra.gradle.revapi:gradle-revapi:1.8.1`（社区版，发布在 Gradle Plugin Portal）改为 `org.revapi:gradle-revapi:1.8.0`（官方版，发布在 Maven Central）。注意版本号从 1.8.1 降到 1.8.0——官方插件的首个发布版本号略低于社区版的最新版本号，但功能等价。

2. `subprojects` 块中的 plugin 应用：

```diff
-    apply plugin: 'io.github.nastra.revapi'
+    apply plugin: 'org.revapi.revapi-gradle-plugin'
```

把 Gradle plugin id 从 `io.github.nastra.revapi`（社区版 plugin id）改为 `org.revapi.revapi-gradle-plugin`（官方版 plugin id）。

其余配置不变，包括：
- `REVAPI_PROJECTS = ["iceberg-api", "iceberg-core", "iceberg-parquet", "iceberg-orc", "iceberg-common", "iceberg-data"]`——指定 6 个核心模块应用 revapi；
- `revapi { oldGroup = project.group; oldName = project.name; oldVersion = "1.5.0" }`——与 1.5.0 版本做 API 比较；
- `showDeprecationRulesOnRevApiFailure` 任务——在 revapi 失败时提示开发者遵循 deprecation 流程；
- `revapiAnalyze.dependsOn(":iceberg-common:jar")`——确保分析时 iceberg-common 的 jar 已构建。

## 小结

- **成效**：将 revapi Gradle 插件从社区第三方版（`io.github.nastra.gradle.revapi:gradle-revapi:1.8.1`）切换到官方版（`org.revapi:gradle-revapi:1.8.0`），提升长期维护可靠性与供应链安全性。配置 DSL 兼容，所有 revapi 配置块与任务无需改动。
- **影响范围**：仅 `build.gradle` 1 个文件，2 行改动（classpath 坐标 + plugin id）。无源代码、测试代码变更，不影响任何模块的编译或运行时行为，仅影响构建时的 API 兼容性检查工具链。
- **回迁到 1.4.x 的注意事项**：该提交是构建工具链切换，**可以回迁到 1.4.x**，但需注意：
  1. 1.4.x 的 `build.gradle` 中 revapi 插件配置应与 main 类似，cherry-pick 冲突概率低；
  2. 回迁前需确认 1.4.x 构建环境能访问 `org.revapi:gradle-revapi:1.8.0`（Maven Central），若 1.4.x CI 在受限网络环境下，需确认 Maven 镜像中有该 artifact；
  3. 注意 1.4.x 的 `oldVersion` 配置——main 中是 `oldVersion = "1.5.0"`，1.4.x 中可能应设为 `1.4.0` 或 `1.4.x` 系列的最新发布版本，以正确检查 1.4.x 内部的 API 兼容性（但这是已有配置，本提交不涉及 `oldVersion` 的修改）；
  4. 官方插件版本 1.8.0 与社区版 1.8.1 功能等价，回迁后 revapi 检查行为不变；
  5. 若 1.4.x 团队对构建工具链稳定性要求高，可在 1.4.x CI 验证通过后再合并；若 1.4.x 已有社区版插件且工作正常，回迁的紧迫性不高，可作为构建优化的一部分择机进行。
