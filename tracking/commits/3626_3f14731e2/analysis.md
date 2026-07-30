# 提交 3626：Open API: Remove runtime Jar from build and deploy (#16163)

## 提交信息

- **序号**：3626 / 4088
- **哈希**：3f14731e23a7fbe6e3357bf40cd1a4ccc2f35a81
- **短哈希**：3f14731e2
- **日期**：2026-05-01 09:45:57 -0500
- **作者**：Ryan Blue
- **提交说明**：Open API: Remove runtime Jar from build and deploy (#16163)
- **PR/Issue**：#16163

## 总体目的

这个提交从 Open API 模块的构建和部署配置中移除了 runtime jar（shadow jar）的发布。

Iceberg 的 `deploy.gradle` 负责配置各模块的 Maven 发布。对于 Open API 模块（`isOpenApi` 为 true 的项目），之前会发布 `shadowJar`（即 fat jar / runtime jar）作为额外的 artifact。然而，Open API 模块的 runtime jar 不需要发布到 Maven 仓库，因为该模块主要用于定义 REST API 规范，用户不需要使用其 runtime jar。

移除这个 artifact 可以减少发布产物的大小，避免不必要的 jar 被发布到 Maven 中央仓库。

## 如何达成设计目的

在 `deploy.gradle` 中，移除 Open API 模块分支中的 `artifact shadowJar` 行。

## 修改详情

### `deploy.gradle` (+0/-1 lines)

**修改目的**：移除 Open API 模块的 shadow jar 发布。

**工作逻辑**：
```groovy
// 之前
} else if (isOpenApi) {
  artifact testJar
  artifact testFixturesJar
  artifact shadowJar    // <-- 移除这行

// 之后
} else if (isOpenApi) {
  artifact testJar
  artifact testFixturesJar
```
Open API 模块仍然发布 testJar 和 testFixturesJar，但不再发布 shadowJar。

## 总结

这个提交从 Open API 模块的部署配置中移除了 runtime jar（shadow jar）的发布。这是一个简单的构建配置清理，减少了不必要的发布产物，使 Maven 发布更加精简和聚焦。
