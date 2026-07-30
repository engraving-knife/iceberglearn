# 提交 1053：Build: Bump org.apache.commons:commons-compress from 1.26.2 to 1.27.0 (#10914)

## 提交信息

- **序号**：1053 / 4088
- **哈希**：994c0fb790c217501c2fba432a1fda8ec1117c3f
- **短哈希**：994c0fb79
- **日期**：2024-08-12（Mon Aug 12 23:54:25 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.apache.commons:commons-compress from 1.26.2 to 1.27.0 (#10914)
- **PR/Issue**：#10914

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交。`org.apache.commons:commons-compress` 是 Apache Commons 提供的通用压缩/解压缩库，支持 zip、tar、gzip、bzip2、zstd 等多种格式。在 Iceberg 项目中，`commons-compress` 被 `kafka-connect` 模块强制固定版本，用于处理 Kafka Connect 运行时引入的传递依赖中的版本统一，并避免已知的 CVE 漏洞。

本次提交将 `kafka-connect` 模块 `build.gradle` 中 `resolutionStrategy.force` 段下的 `commons-compress` 版本从 `1.26.2` 升级到 `1.27.0`，属于 semver-minor 升级。升级目的是跟进上游迭代、获得 1.27.0 中带来的改进和潜在的 CVE 修复。

## 如何达成设计目的

实现方式是修改 `kafka-connect/build.gradle` 文件中 `resolutionStrategy.force` 段中 `commons-compress` 的版本字符串，从 `1.26.2` 改为 `1.27.0`。`force` 策略会让 Gradle 在解析传递依赖时强制使用该版本，覆盖所有其他来源的版本声明，从而保证 Kafka Connect 运行时使用的 commons-compress 版本一致且无已知漏洞。

## 修改详情

### `kafka-connect/build.gradle`

**修改目的**：将 Kafka Connect 运行时强制使用的 commons-compress 版本从 1.26.2 升级到 1.27.0。

**工作逻辑**：仅修改 `resolutionStrategy.force` 中的一行：

```diff
-        force 'org.apache.commons:commons-compress:1.26.2'
+        force 'org.apache.commons:commons-compress:1.27.0'
```

该 `force` 语句位于 `iceberg-kafka-connect-runtime` 子项目的运行时配置中，与同段中强制的 `jettison`、`snappy-java`、`hadoop-shaded-guava` 一起构成对已知漏洞依赖的统一版本治理。

## 小结

- **成效**：完成 Kafka Connect 模块中 commons-compress 的小版本升级（1.26.2 → 1.27.0），使强制版本与上游最新发布对齐。
- **影响范围**：仅 `kafka-connect/build.gradle` 一个文件、一行改动；运行时影响 Kafka Connect runtime 的传递依赖解析。
- **回迁到 1.4.x 的注意事项**：可选择性回迁。这是纯依赖升级，无 API 改动。需注意 1.4.x 分支的 Kafka Connect 模块是否已经存在 `force` 配置；若 1.4.x 使用更老的 commons-compress 版本且存在 CVE，则建议回迁；否则可不回迁。回迁后需运行 Kafka Connect 相关测试确认无回归。
