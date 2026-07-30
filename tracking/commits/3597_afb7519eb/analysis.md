# 提交 3597：AWS Bundle: Exclude logging dependencies (#16105)

## 提交信息

- **序号**：3597 / 4088
- **哈希**：afb7519eb5d99bef089f535f876d8bae538369e7
- **短哈希**：afb7519eb
- **日期**：2026-04-27 00:30:12 -0600
- **作者**：Ryan Blue
- **提交说明**：AWS Bundle: Exclude logging dependencies (#16105)
- **PR/Issue**：#16105

## 总体目的

这个提交从 AWS Bundle 中排除了日志相关依赖（log4j 和 slf4j），避免将日志框架打包到 bundle jar 中。

AWS Bundle 是 Iceberg 提供的一个独立的 AWS 集成 jar 包，用于简化用户在使用 Iceberg 与 AWS 服务（如 S3、Glue 等）集成时的依赖管理。之前 bundle 中包含了 log4j 和 slf4j 的实现，这会导致与使用 bundle 的应用程序自身的日志框架产生冲突。

日志框架（如 slf4j-api、log4j-core 等）是应用程序级别的基础设施，应该由应用程序自身统一管理，而不是由一个库 bundle 强制带入。如果 bundle 中包含了 slf4j 的绑定（如 log4j-slf4j-impl），可能会导致应用程序出现多个 slf4j 绑定的警告，甚至引发类加载冲突。移除这些日志依赖可以让 bundle 更加轻量，并遵循日志库不应被打包到库 jar 中的最佳实践。

## 如何达成设计目的

实现方式是将日志依赖的排除从 shadow jar 插件的配置阶段移到了 `implementation` 配置级别，这样可以在依赖解析的更早期就排除这些库，确保它们不会出现在任何依赖解析结果中（包括 runtime-deps.txt 的生成）。

之前的方式是在 shadow jar 插件的 `dependencies` 块中排除，这只在打包阶段生效，依赖仍然会出现在依赖树中。新方式在 `configurations` 块中针对 `implementation` 配置进行排除，是更彻底的做法。

## 修改详情

### `aws-bundle/build.gradle` (+8/-6 lines)

**修改目的**：将日志依赖排除从 shadow jar 阶段提升到 implementation 配置阶段，更彻底地排除日志库。

**工作逻辑**：
新增了 `configurations` 块，在 `implementation` 配置上排除三个日志相关的 group：
```groovy
  configurations {
    implementation {
      exclude group: 'org.slf4j'
      exclude group: 'org.apache.logging.slf4j'
      exclude group: 'org.apache.logging.log4j'
    }
  }
```
同时移除了 shadow jar 插件中原来的排除配置：
```groovy
    dependencies {
      exclude(dependency('org.slf4j:.*'))
      exclude(dependency('org.apache.logging.log4j:.*'))
      exclude(dependency('org.apache.logging.slf4j:.*'))
    }
```
新方式使用 group 级别的排除，覆盖更全面；并且在配置解析阶段就排除，使得依赖树中不再包含这些日志库，runtime-deps.txt 也会自动同步更新。

### `aws-bundle/runtime-deps.txt` (+0/-4 lines)

**修改目的**：同步移除依赖清单中的日志库条目。

**工作逻辑**：
移除了 4 个日志相关的依赖条目：
- `org.apache.logging.log4j:log4j-api:2.20.0`
- `org.apache.logging.log4j:log4j-core:2.20.0`
- `org.apache.logging.log4j:log4j-slf4j-impl:2.20.0`
- `org.slf4j:slf4j-api:2.0.17`

这些条目的移除是 build.gradle 配置变更的直接结果。

## 总结

这个提交改进了 AWS Bundle 的依赖管理，通过在配置层面排除日志框架依赖，避免了 bundle jar 与应用程序日志系统的冲突。这是遵循 Java 生态中"日志库不应打包到库 jar 中"的最佳实践的重要改进，同时也使 bundle 更加轻量。
