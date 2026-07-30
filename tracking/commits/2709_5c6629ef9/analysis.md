# 提交 2709：AWS: exclude logging classes from bundle (#14225)

## 提交信息

- **序号**：2709 / 4088
- **哈希**：5c6629ef915061967fa67e4b981b0151cf8d42d4
- **短哈希**：5c6629ef9
- **日期**：2025-09-30 17:03:14 -0600
- **作者**：Daniel Weeks
- **提交说明**：AWS: exclude logging classes from bundle (#14225)
- **PR/Issue**：#14225

## 总体目的

本提交扩展 `iceberg-aws-bundle`（AWS 依赖打包 jar，shadow/fat jar）在构建 shadow jar 时排除的日志相关依赖范围，避免将日志框架类打入 bundle，从而防止与使用方（如 Spark、Flink 等引擎）已有的日志框架产生冲突。

**背景**：`iceberg-aws-bundle` 是一个把 AWS SDK for Java v2 及其传递依赖打包（shadow）成单个 jar 的产物，方便用户只需引入一个 jar 即可获得 Iceberg 的 AWS 集成能力。在 shadow jar 构建中，需要排除一些不应被打入的依赖（通过 shadow 插件的 `dependencies { exclude(...) }` 机制），以避免：

1. **日志框架冲突**：日志框架（SLF4J、Log4j2 等）通常是应用顶层提供的，由应用统一选择绑定。如果 bundle 把日志实现类打入，会与应用自身的日志绑定冲突，导致 `ClassNotFound`、`IllegalAccessError`、重复绑定警告、日志输出异常等问题。
2. **体积冗余**：日志类不属于 AWS 集成的核心功能，打入 bundle 只会增加体积。

此前 bundle 仅排除了 `org.slf4j:slf4j-api` 这一个具体的 SLF4J API 依赖。但 AWS SDK 的传递依赖中还包含其它日志相关组件：
- `org.slf4j:.*`（SLF4J 的其它模块，如 `jul-to-slf4j`、`jcl-over-slf4j` 等）；
- `org.apache.logging.log4j:.*`（Log4j2 的 API 与实现，如 `log4j-api`、`log4j-core`、`log4j-slf4j-impl` 等）；
- `org.apache.logging.slf4j:.*`（Log4j2 与 SLF4J 的桥接，如 `log4j-slf4j2-impl`、`slf4j-impl` 等）。

这些未排除的日志类会被打入 bundle，带来冲突风险。本提交将排除规则从单一 `org.slf4j:slf4j-api` 扩展为按 group 通配排除上述三组日志依赖。

## 如何达成设计目的

修改 `aws-bundle/build.gradle` 中 `shadowJar` 任务内的 `dependencies { exclude(...) }` 块，将原来的单条 `exclude(dependency('org.slf4j:slf4j-api'))` 替换为三条按 group 通配的排除规则：

- `exclude(dependency('org.slf4j:.*'))`：排除 SLF4J group 下所有模块；
- `exclude(dependency('org.apache.logging.log4j:.*'))`：排除 Log4j2 group 下所有模块；
- `exclude(dependency('org.apache.logging.slf4j:.*'))`：排除 Log4j2-SLF4J 桥接 group 下所有模块。

shadow 插件的 `dependency('group:.*')` 通配语法会匹配指定 group 下的所有 artifact，从而把它们从 shadow jar 中排除。

## 修改详情

### `aws-bundle/build.gradle` (+3/-1 lines)

**修改目的**：扩大 shadow jar 排除的日志依赖范围。

**工作逻辑**：在 `shadowJar` 任务的 `dependencies { ... }` 块中，将 `exclude(dependency('org.slf4j:slf4j-api'))` 替换为三条通配排除规则：
```
exclude(dependency('org.slf4j:.*'))
exclude(dependency('org.apache.logging.log4j:.*'))
exclude(dependency('org.apache.logging.slf4j:.*'))
```
这样 SLF4J 全部模块、Log4j2 全部模块以及 Log4j2-SLF4J 桥接模块都不会被打入 `iceberg-aws-bundle` jar，由使用方应用提供日志框架，避免冲突。

## 总结

本提交将 `iceberg-aws-bundle` 的 shadow jar 排除规则从仅排除 `org.slf4j:slf4j-api` 扩展为按 group 通配排除 SLF4J（`org.slf4j:.*`）、Log4j2（`org.apache.logging.log4j:.*`）与 Log4j2-SLF4J 桥接（`org.apache.logging.slf4j:.*`）三组日志依赖，防止 AWS bundle 把日志框架类打入 jar 与使用方应用的日志绑定冲突。属于构建配置改进，降低冲突风险与 jar 体积，不影响功能逻辑。
