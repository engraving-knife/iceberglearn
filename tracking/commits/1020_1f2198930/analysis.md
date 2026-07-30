# 提交 1020：Build: Bump org.apache.commons:commons-compress from 1.26.0 to 1.26.2 (#10868)

## 提交信息

- **序号**：1020 / 4088
- **哈希**：1f21989305d92f1530c785c826b221bddfb4ce0b
- **短哈希**：1f2198930
- **日期**：2024-08-05 09:08:07 +0200
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.apache.commons:commons-compress from 1.26.0 to 1.26.2 (#10868)
- **PR/Issue**：#10868

## 总体目的

这是 Dependabot 自动生成的依赖升级 PR。`org.apache.commons:commons-compress` 是 Apache Commons 提供的压缩/解压库（支持 zip、tar、gzip、brotli、zstd 等格式）。在 Iceberg 的 `kafka-connect` 模块中，由于 Kafka Connect 生态传递依赖的某些组件（如 jettison、snappy-java、commons-compress、hadoop-shaded-guava）存在已知安全或兼容性问题，`iceberg-kafka-connect-runtime` 子项目的 `build.gradle` 使用 Gradle 的 `resolutionStrategy.force` 把这些库统一强制到固定版本，以避免 CVE 或版本冲突。Dependabot 检测到强制版本 1.26.0 升级到 1.26.2（patch 升级），属于直接生产依赖。本提交的目的是跟进 commons-compress 上游 patch 修复（1.26.x 系列修复了若干 zip 解压相关 bug 与潜在安全问题）。

## 如何达成设计目的

实现方式是单行版本号替换：在 `kafka-connect/build.gradle` 的 `iceberg-kafka-connect-runtime` 子项目 `resolutionStrategy` 块中，把 `force 'org.apache.commons:commons-compress:1.26.0'` 改为 `force 'org.apache.commons:commons-compress:1.26.2'`。这样在 kafka-connect-runtime 的依赖解析图中，所有传递依赖的 commons-compress 都会被强制提升到 1.26.2。

## 修改详情

### `kafka-connect/build.gradle`

**修改目的**：把 kafka-connect-runtime 强制的 commons-compress 版本从 1.26.0 升级到 1.26.2。

**工作逻辑**：在 `project(':iceberg-kafka-connect:iceberg-kafka-connect-runtime')` 的依赖配置 `resolutionStrategy` 块中：
```
-force 'org.apache.commons:commons-compress:1.26.0'
+force 'org.apache.commons:commons-compress:1.26.2'
```
该块同时 force 了 `jettison:1.5.4`、`snappy-java:1.1.10.5`、`hadoop-shaded-guava:1.2.0`，本次只升级 commons-compress 一项。`force` 的语义是：无论传递依赖声明什么版本，最终解析都使用 force 指定的版本，用于统一钉死存在已知问题的传递依赖版本。

## 小结

- **成效**：将 kafka-connect-runtime 强制的 commons-compress 版本从 1.26.0 升级到 1.26.2，跟进上游 patch 修复。
- **影响范围**：仅 `kafka-connect/build.gradle` 一个文件，1 行改动。影响 `iceberg-kafka-connect-runtime` 的依赖解析图（仅 force 版本变更），不影响其它模块、不影响产品代码逻辑。
- **回迁到 1.4.x 的注意事项**：本提交是依赖版本升级，回迁风险极低。需注意：(1) 1.4.x 分支可能不包含 `kafka-connect` 模块（kafka-connect 是较新加入的模块），若不存在则本提交无意义、无需回迁；(2) 若 1.4.x 包含该模块且 force 版本为 1.26.0，可直接 cherry-pick 同步到 1.26.2；(3) patch 升级 API 兼容，回迁后建议跑一遍 kafka-connect 测试确认。整体可选回迁。
