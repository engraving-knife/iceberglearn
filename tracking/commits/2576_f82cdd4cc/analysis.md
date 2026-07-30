# 提交 2576：Build: Release with JDK17 (#13946)

## 提交信息

- **序号**：2576 / 4088
- **哈希**：f82cdd4ccf1bbd7c94ea12be4f465525d29935a7
- **短哈希**：f82cdd4cc
- **日期**：2025-08-29 07:25:49 -0700
- **作者**：Fokko Driesprong
- **提交说明**：Build: Release with JDK17 (#13946)
- **PR/Issue**：#13946

## 总体目的

此次提交将 Iceberg 发布构建要求的 JDK 版本从 11 提升到 17。背景是 Spark 4.0 要求 JDK 17 或 21 运行环境，而 Iceberg 即将发布的版本包含 Spark 4.0 模块，因此发布构建必须使用 JDK 17 以确保 Spark 4.0 模块的产物兼容性。

Iceberg 的 `build.gradle` 将编译目标（target）设为 11，因此产物本身在 JDK 11 及以上都能运行。但发布构建（`-Prelease`）有额外的 JDK 版本校验：原先要求 `jdkVersion == '11'`，否则抛出 `GradleException`。由于 Spark 4.0 需要 JDK 17+，继续用 JDK 11 构建发布版本会导致 Spark 4.0 模块无法正确构建或验证，因此需要将发布校验改为 JDK 17。

## 如何达成设计目的

- 修改 `deploy.gradle` 中的发布校验逻辑：将 `jdkVersion != '11'` 改为 `jdkVersion != '17'`，对应的错误信息从 "Releases must be built with Java 11" 改为 "Releases must be built with Java 17"。
- 提交说明中引用了 `build.gradle` 中 target=11 的配置，说明产物仍兼容 JDK 11+，只是发布构建本身必须在 JDK 17 环境下执行（因为 Spark 4.0 模块需要）。

## 修改详情

### `deploy.gradle` (+2/-2)

**修改目的**：将发布构建的 JDK 校验从 11 改为 17。

**工作逻辑**：`if (project.hasProperty('release') && jdkVersion != '17')` 时抛出 `GradleException("Releases must be built with Java 17")`，确保只有 JDK 17 环境才能执行 release 构建。

## 总结

一次构建配置变更提交，将 Iceberg 发布构建要求的 JDK 版本从 11 提升到 17，以适配 Spark 4.0 对 JDK 17+ 的要求。仅修改 `deploy.gradle` 中的校验条件和错误信息，不影响非 release 构建的 JDK 兼容性。
