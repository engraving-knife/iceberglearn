# 提交 3657：Azure: Fix LICENSE, NOTICE, and runtime-deps for azure-bundle (#16181)

## 提交信息

- **序号**：3657 / 4088
- **哈希**：334269cd7d5114564a406a3259aa9919e4590738
- **短哈希**：334269cd7
- **日期**：2026-05-06 14:12:46 -0700
- **作者**：Kevin Liu
- **提交说明**：Azure: Fix LICENSE, NOTICE, and runtime-deps for azure-bundle (#16181)
- **PR/Issue**：#16181

## 总体目的

这个提交修复了 `azure-bundle` 的 LICENSE、NOTICE 和 runtime-deps 的合规性问题，并调整了 slf4j 依赖的排除方式。

此前 azure-bundle 存在两个问题：
1. LICENSE/NOTICE 遗漏了多个传递依赖的许可证声明：FastDoubleParser、fast_float、bigint、MSAL4J Persistence Extension、Apache Tomcat Native、Reactor Pool、Aalto XML 等。这不符合 Apache 发布的许可证合规要求。
2. slf4j-api 的排除方式不合理：原在 shadowJar 任务的 dependencies 块中排除 `org.slf4j:slf4j-api`，但这种排除方式可能不够彻底。改为在 `configurations.implementation` 中排除整个 `org.slf4j` group，更彻底地避免 slf4j 被打包进 bundle（因为 slf4j 应由运行环境提供）。同时从 runtime-deps.txt 移除 slf4j-api 条目。

## 如何达成设计目的

1. 在 LICENSE 中补全所有缺失依赖的完整许可证文本。
2. 在 NOTICE 中补充相关 NOTICE 声明。
3. 将 slf4j 排除从 shadowJar dependencies 移到 configurations.implementation，并从 runtime-deps.txt 移除 slf4j-api。

## 修改详情

### `azure-bundle/LICENSE` (+165/-14 lines)

**修改目的**：补全缺失的依赖许可证声明。

**工作逻辑**：新增以下依赖的许可证文本：
- FastDoubleParser（via Jackson JSON Processor）— MIT
- fast_float（bundled by FastDoubleParser）— Apache 2.0 / BSL / MIT
- bigint（bundled by FastDoubleParser）— MIT
- MSAL4J Persistence Extension — MIT
- Apache Tomcat Native（netty-tcnative-classes 和 netty-tcnative-boringssl-static，bundled by Reactor Netty）— Apache 2.0
- Reactor Pool（bundled by Reactor Netty）— Apache 2.0
- Aalto XML（bundled by Azure SDK for Java）— Apache 2.0

### `azure-bundle/NOTICE` (+13/-4 lines)

**修改目的**：补充相关依赖的 NOTICE 声明。

### `azure-bundle/build.gradle` (+6/-4 lines)

**修改目的**：改进 slf4j 排除方式。

**工作逻辑**：
```gradle
// 新增：在 implementation 配置中排除整个 org.slf4j group
configurations {
  implementation {
    exclude group: 'org.slf4j'
  }
}
// 移除：shadowJar 中的 dependencies { exclude(dependency('org.slf4j:slf4j-api')) }
```

### `azure-bundle/runtime-deps.txt` (+0/-1 line)

**修改目的**：移除 slf4j-api 条目。

**工作逻辑**：移除 `org.slf4j:slf4j-api:2.0.17`。

## 总结

这个提交修复了 azure-bundle 的 LICENSE/NOTICE 合规性问题，补全了 7 个传递依赖的许可证声明。同时改进了 slf4j 的排除方式，从 shadowJar 级别移到 configuration 级别，更彻底地避免 slf4j 被打包进 bundle。这是 Apache 发布前的必要合规修复。
