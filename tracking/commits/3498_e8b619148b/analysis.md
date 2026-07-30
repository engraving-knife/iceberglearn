# 提交 3498：Aliyun: Remove leaked transitive dependencies. (#15858)

## 提交信息

- **序号**：3498 / 4088
- **哈希**：e8b619148b557c5d72522e7f24bdadc9170eabfe
- **短哈希**：e8b619148b
- **日期**：2026-04-01 21:44:39 -0500
- **作者**：Ryan Blue
- **提交说明**：Aliyun: Remove leaked transitive dependencies. (#15858)
- **PR/Issue**：#15858

## 总体目的

修复 Aliyun 模块中传递依赖泄漏到运行时 Jar 的问题。`aliyun.credentials.java` 和 `aliyun.tea` 原本使用 `implementation` 配置，会泄漏到运行时 classpath。改为 `compileOnly` 确保它们仅在编译时可用，由用户的运行时环境提供。

这与 #15655（BigQuery 依赖泄漏修复）和 #15855（运行时依赖守卫）属于同一系列的依赖清理工作。

## 如何达成设计目的

将两个 Aliyun 依赖从 `implementation` 改为 `compileOnly`。

## 修改详情

### `build.gradle` (+2/-2 lines)

**修改目的**：将 Aliyun 依赖改为 compileOnly。

**工作逻辑**：
```diff
-    implementation libs.aliyun.credentials.java
-    implementation libs.aliyun.tea
+    compileOnly libs.aliyun.credentials.java
+    compileOnly libs.aliyun.tea
```

## 总结

依赖配置修复提交，将 Aliyun 模块的 `aliyun.credentials.java` 和 `aliyun.tea` 依赖从 `implementation` 改为 `compileOnly`，防止传递依赖泄漏到运行时 Jar。这与 BigQuery 依赖泄漏修复（#15655）和运行时依赖守卫（#15855）属于同一系列工作。
