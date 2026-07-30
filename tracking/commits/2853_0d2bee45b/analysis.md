# 提交 2853：Build: Bump org.apache.hadoop.thirdparty:hadoop-shaded-guava (#14542)

## 提交信息

- **序号**：2853 / 4088
- **哈希**：0d2bee45b81a20b71a426318b95ecf1ad9d816bc
- **短哈希**：0d2bee45b
- **日期**：2025-11-09 08:05:18 -0800
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump org.apache.hadoop.thirdparty:hadoop-shaded-guava (#14542)
- **PR/Issue**：#14542

## 总体目的

这个提交将 Hadoop Shaded Guava 依赖从 1.4.0 升级到 1.5.0，由 dependabot 发起、Yuya Ebihara 审核合并。Hadoop Shaded Guava 是 Apache Hadoop 第三方库的一部分，提供了 Guava 的重打包（shaded）版本，用于避免类加载冲突。

值得注意的是，此次升级不仅修改了 Kafka Connect 模块的 `build.gradle` 中的 `resolutionStrategy.force` 版本，还额外添加了对 `org.jspecify:jspecify` 模块的排除（exclude）。这表明新版本 1.5.0 可能引入了 jspecify 依赖，而该依赖在 Kafka Connect 运行时环境中可能引发冲突或不需要。

## 如何达成设计目的

修改集中在 `kafka-connect/build.gradle` 文件中。主要做了两件事：

1. 在依赖排除规则中新增排除 `org.jspecify:jspecify` 模块，防止新版本 hadoop-shaded-guava 引入的 jspecify 传递依赖造成冲突。
2. 将 `resolutionStrategy.force` 中强制的 `hadoop-shaded-guava` 版本从 1.4.0 更新为 1.5.0，确保所有传递依赖也被统一强制到新版本。

此外，提交还修复了文件末尾缺少换行符的问题。

## 修改详情

### `kafka-connect/build.gradle` (+3/-2 lines)

**修改目的**：升级 Kafka Connect 模块中强制的 hadoop-shaded-guava 版本，并排除新引入的 jspecify 传递依赖。

**工作逻辑**：

1. **新增 jspecify 排除**：在 `all { ... }` 依赖配置块中添加 `exclude group: 'org.jspecify', module: 'jspecify'`。JSpecify 是一个用于 Java 空值注解规范的库，hadoop-shaded-guava 1.5.0 可能新增了对它的依赖。在 Kafka Connect 运行时环境中，这个传递依赖可能与其他模块的版本冲突或不必要，因此通过 exclude 排除。

2. **更新强制版本**：将 `resolutionStrategy.force` 中的 `org.apache.hadoop.thirdparty:hadoop-shaded-guava:1.4.0` 改为 `1.5.0`。`resolutionStrategy.force` 是 Gradle 的依赖解析策略，用于强制所有传递依赖使用指定版本，避免版本冲突。Kafka Connect 依赖树复杂，通过 force 策略统一版本是常见的做法。

3. **文件末尾换行符**：原文件末尾缺少换行符（`\ No newline at end of file`），此次修改顺便补上了，符合 POSIX 文件标准。

## 总结

这个提交将 Kafka Connect 模块的 hadoop-shaded-guava 从 1.4.0 升级到 1.5.0，同时排除新版本引入的 jspecify 传递依赖以避免潜在冲突。修改针对 Kafka Connect 模块的依赖解析策略，体现了对依赖升级时传递依赖变化的细致处理。
