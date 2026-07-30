# 提交 2269：Build: Remove JSpecify annotations from bundled-guava (#13379)

## 提交信息

- **序号**：2269 / 4088
- **哈希**：460736c182dcb0592d4e47ca57eee70b167fbb7b
- **短哈希**：460736c18
- **日期**：2025-06-25 12:15:19 +0200
- **作者**：David Phillips
- **提交说明**：Build: Remove JSpecify annotations from bundled-guava (#13379)
- **PR/Issue**：#13379

## 总体目的

本提交修复了 `iceberg-bundled-guava` 模块在打包时引入 JSpecify 注解类导致的重复类冲突问题。JSpecify（`org.jspecify`）是 Guava 新版本引入的依赖，用于可空性标注。然而在 `iceberg-bundled-guava` 这个将 Guava 重新打包（relocate）并分发的模块中，这些注解类没有被 shaded（即没有被重定位包名），因此会与下游项目中可能存在的 JSpecify 产生重复类冲突。

提交说明明确指出："These classes are not shaded and cause duplicate class conflicts."（这些类未被 shaded，会导致重复类冲突）。通过在 build.gradle 的依赖排除配置中新增对 `org.jspecify` 组的排除，确保打包产物中不包含这些注解类，从而消除冲突。

## 如何达成设计目的

- 在 `build.gradle` 文件中 `iceberg-bundled-guava` 项目的 `shadowJar` 配置块里，新增 `exclude group: 'org.jspecify'` 排除规则。
- 该排除规则与已有的 `com.google.errorprone`、`com.google.j2objc` 等排除规则并列，保持一致的排除策略。
- 仅一行配置变更，最小化改动，精准解决问题。

## 修改详情

### `build.gradle` (+1/-0 lines)

**修改目的**：在 `iceberg-bundled-guava` 模块的 shadowJar 配置中排除 JSpecify 注解依赖，避免重复类冲突。

**工作逻辑**：在 `project(':iceberg-bundled-guava')` 配置块的 `shadowJar` 任务中，已有若干 `exclude group:` 规则用于排除那些不应被打入最终 bundled jar 的传递依赖（如 findbugs/errorprone 注解可能是 LGPL 许可、j2objc 注解等）。新增 `exclude group: 'org.jspecify'` 后，shadow 插件在打包时不会把这些注解类包含进 bundled-guava 的产物中。由于这些注解仅用于编译期可空性检查，运行时并非必需，排除它们不会影响 Guava 的功能，但能避免下游使用 bundled-guava 的项目因同时引入 JSpecify 而出现 `DuplicateClassException`。

## 总结

本提交是一个精准的构建修复，通过一行配置排除了 JSpecify 注解在 bundled-guava 打包产物中的引入，解决了下游项目的重复类冲突问题。这类问题在依赖重新打包（shading）场景中较为常见，体现了对打包产物洁净度的维护。
