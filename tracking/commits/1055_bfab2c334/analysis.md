# 提交 1055：Build: Bump org.xerial.snappy:snappy-java from 1.1.10.5 to 1.1.10.6 (#10911)

## 提交信息

- **序号**：1055 / 4088
- **哈希**：bfab2c334e9b4c11de65f1f9bd1de5dab18aae5b
- **短哈希**：bfab2c334
- **日期**：2024-08-13（Tue Aug 13 09:25:34 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.xerial.snappy:snappy-java from 1.1.10.5 to 1.1.10.6 (#10911)
- **PR/Issue**：#10911

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交。`org.xerial.snappy:snappy-java` 是 Snappy 压缩算法的 Java 绑定，提供高性能的压缩/解压能力。在 Iceberg 项目中，`snappy-java` 被 `kafka-connect` 模块通过 `resolutionStrategy.force` 强制固定版本，用于统一 Kafka Connect 运行时引入的传递依赖中的 snappy-java 版本，避免版本冲突和已知 CVE 漏洞。

本次提交将 `kafka-connect` 模块中强制的 `snappy-java` 版本从 `1.1.10.5` 升级到 `1.1.10.6`，属于 semver-patch 升级（1.1.10.5 → 1.1.10.6）。patch 级别升级通常只包含 bug 修复和小的安全补丁，不引入 API 变更，风险极低。

## 如何达成设计目的

实现方式是修改 `kafka-connect/build.gradle` 文件中 `resolutionStrategy.force` 段中 `snappy-java` 的版本字符串，从 `1.1.10.5` 改为 `1.1.10.6`。`force` 策略会在 Gradle 解析依赖图时强制使用该版本，覆盖其他传递依赖来源的版本声明。

## 修改详情

### `kafka-connect/build.gradle`

**修改目的**：将 Kafka Connect 运行时强制使用的 snappy-java 版本从 1.1.10.5 升级到 1.1.10.6。

**工作逻辑**：仅修改 `resolutionStrategy.force` 中的一行：

```diff
-        force 'org.xerial.snappy:snappy-java:1.1.10.5'
+        force 'org.xerial.snappy:snappy-java:1.1.10.6'
```

注意：本提交的 diff 中还可以看到 `force 'org.apache.commons:commons-compress:1.27.0'` 已经存在（来自前一个提交 1053），说明这两个 dependabot 提交是顺序合入的，依赖配置在 `force` 段中累积。

## 小结

- **成效**：完成 Kafka Connect 模块中 snappy-java 的 patch 版本升级（1.1.10.5 → 1.1.10.6），与上游最新发布对齐。
- **影响范围**：仅 `kafka-connect/build.gradle` 一个文件、一行改动；运行时影响 Kafka Connect runtime 传递依赖中的 snappy-java 版本。
- **回迁到 1.4.x 的注意事项**：可安全回迁。这是 patch 级别升级，无 API 变更，风险极低。如果 1.4.x 的 Kafka Connect 模块中已有 `snappy-java` 的 `force` 声明，直接 cherry-pick 即可；若无，需添加 `force` 配置。回迁有助于获得上游的安全补丁。
