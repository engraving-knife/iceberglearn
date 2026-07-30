# 提交 3494：BigQuery: Fix dependency leak into runtime Jars (#15655)

## 提交信息

- **序号**：3494 / 4088
- **哈希**：ff298a676d287f6bb065c030aca2ef66bbab4fed
- **短哈希**：ff298a676d
- **日期**：2026-04-01 14:38:54 -0700
- **作者**：Alex Stephen
- **提交说明**：BigQuery: Fix dependency leak into runtime Jars (#15655)
- **PR/Issue**：#15655

## 总体目的

修复 BigQuery 模块中依赖泄漏到运行时 Jar 的问题。原配置中 `google-cloud-bigquery`、`google-cloud-core` 和 `google.libraries.bom` 使用 `implementation` 配置，这意味着这些依赖会被包含在运行时 classpath 中。BigQuery 模块作为一个集成库，这些 Google Cloud 依赖应该由用户的运行时环境提供，而非打包进 Iceberg 的 Jar 中。改为 `compileOnly` 确保它们仅在编译时可用，不会泄漏到运行时 Jar。

## 如何达成设计目的

将 `iceberg-bigquery` 项目中的三个 Google Cloud 依赖从 `implementation` 改为 `compileOnly`。

## 修改详情

### `build.gradle` (+3/-3 lines)

**修改目的**：将 Google Cloud 依赖改为 compileOnly。

**工作逻辑**：
```diff
-    implementation platform(libs.google.libraries.bom)
+    compileOnly platform(libs.google.libraries.bom)
     compileOnly "com.google.cloud:google-cloud-storage"
-    implementation "com.google.cloud:google-cloud-bigquery"
-    implementation "com.google.cloud:google-cloud-core"
+    compileOnly "com.google.cloud:google-cloud-bigquery"
+    compileOnly "com.google.cloud:google-cloud-core"
```

## 总结

依赖配置修复提交。将 BigQuery 模块的 Google Cloud 依赖（google-cloud-bigquery、google-cloud-core 和 google.libraries.bom）从 `implementation` 改为 `compileOnly`，防止这些依赖泄漏到运行时 Jar 中。这些依赖应由用户的运行时环境提供。
