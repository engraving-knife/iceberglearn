# 提交 2456：Build: Bump org.apache.commons:commons-compress from 1.27.1 to 1.28.0 (#13723)

## 提交信息

- **序号**：2456 / 4088
- **哈希**：34b377e0d08043e7a1c23b43aab361e5fb901e71
- **短哈希**：34b377e0d
- **日期**：2025-08-05 11:42:20 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.apache.commons:commons-compress from 1.27.1 to 1.28.0 (#13723)
- **PR/Issue**：#13723

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，将 Apache Commons Compress 库从 1.27.1 升级到 1.28.0。Commons Compress 是一个用于处理压缩和归档文件（如 ZIP、TAR、GZIP 等）的 Java 库，在 Iceberg 项目中主要用于 Kafka Connect 模块的依赖管理。

此次升级属于 semver-minor（次版本号）更新，通常包含新功能添加和错误修复，同时保持向后兼容性。

## 如何达成设计目的

通过修改 Kafka Connect 模块的 `build.gradle` 文件中的依赖强制版本声明，将 `commons-compress` 的版本从 `1.27.1` 更新到 `1.28.0`。该声明使用了 Gradle 的 `resolutionStrategy.force` 机制，用于在依赖解析时强制使用指定版本，解决版本冲突。

## 修改详情

### `kafka-connect/build.gradle` (+1/-1 lines)

**修改目的**：更新 Kafka Connect 模块中 commons-compress 的强制版本声明。

**工作逻辑**：
将 `resolutionStrategy` 块中的版本强制声明从：
```gradle
force 'org.apache.commons:commons-compress:1.27.1'
```
修改为：
```gradle
force 'org.apache.commons:commons-compress:1.28.0'
```

`resolutionStrategy.force` 用于在 Gradle 依赖解析过程中，当存在多个版本的同一依赖时，强制使用指定版本。这在 Kafka Connect 模块中尤为重要，因为 Kafka Connect 生态系统可能引入多个传递依赖的不同版本的 commons-compress。

## 总结

这是一个常规的依赖升级提交，由 Dependabot 自动生成。升级 Apache Commons Compress 从 1.27.1 到 1.28.0，属于次版本号升级。该修改仅涉及 Kafka Connect 模块的 build.gradle 文件中的依赖版本声明，不涉及任何代码逻辑变更。
