# 提交 2765：Build: Don't override checkstyle version in baseline-checkstyle plugin (#14365)

## 提交信息

- **序号**：2765 / 4088
- **哈希**：9eded8add2ce62e0d1f3b5ef41c97bc3e9d19768
- **短哈希**：9eded8add
- **日期**：2025-10-18 22:21:45 -0700
- **作者**：Manu Zhang
- **提交说明**：Build: Don't override checkstyle version in baseline-checkstyle plugin (#14365)
- **PR/Issue**：#14365

## 总体目的

本提交移除了 `baseline.gradle` 中对 checkstyle 版本的手动覆盖（override），使 checkstyle 版本回归由 baseline-checkstyle 插件自身管理。

背景在于：Iceberg 使用 Palantir 的 baseline 插件（`com.palantir.baseline-checkstyle`）来执行代码风格检查。此前在 `baseline.gradle` 中有一段配置：当 `com.palantir.baseline-checkstyle` 插件应用时，通过 `CheckstyleExtension` 将 checkstyle 的 `toolVersion` 强制覆盖为 `9.3`。

原覆盖的原因（如注释所述）是：`gradle-baseline-java:4.42.0`（最后一个支持 Java 8 的版本）会拉入旧版 checkstyle 9.1，该版本存在一个 OutOfMemory bug（checkstyle/issues/10934）。因此当时将版本覆盖到 9.3（最后一个支持 Java 8 的版本）以获得修复。

移除该覆盖的原因推测是：Iceberg 已不再需要支持 Java 8（已迁移到更高版本），或者 baseline 插件已升级到不再拉入有 bug 的 checkstyle 9.1 的版本。因此手动覆盖 checkstyle 版本已无必要，反而可能导致与插件预期版本不一致的问题。移除后由插件自行管理 checkstyle 版本，减少维护负担。

## 如何达成设计目的

在 `baseline.gradle` 中删除 `pluginManager.withPlugin('com.palantir.baseline-checkstyle') { ... }` 这一整段配置块（9 行），不再手动设置 `checkstyle.toolVersion = '9.3'`。保留同文件中 `com.diffplug.spotless` 等其他插件的配置不变。

## 修改详情

### `baseline.gradle` (-9 lines)

**修改目的**：移除对 checkstyle 版本的手动覆盖。

**工作逻辑**：删除以下代码块：
```groovy
pluginManager.withPlugin('com.palantir.baseline-checkstyle') {
  checkstyle {
    // com.palantir.baseline:gradle-baseline-java:4.42.0 (the last version supporting Java 8) pulls
    // in an old version of the checkstyle(9.1), which has this OutOfMemory bug https://github.com/checkstyle/checkstyle/issues/10934.
    // So, override its checkstyle version using CheckstyleExtension to 9.3 (the latest java 8 supported version) which contains a fix.
    toolVersion '9.3'
  }
}
```
移除后，checkstyle 的版本由 baseline-checkstyle 插件默认管理，不再强制为 9.3。

## 总结

本提交移除了 `baseline.gradle` 中对 checkstyle 版本的手动覆盖（原覆盖到 9.3 以规避旧版 9.1 的 OOM bug）。由于覆盖的历史前提（Java 8 支持、旧版 baseline 插件拉入有 bug 的 checkstyle）已不再成立，该覆盖已无必要。移除后由插件自行管理版本，简化了构建配置。这是一个低风险的构建配置清理。
