# 提交 3570：Spark: Fix RoaringBitmap version in runtime-deps.txt (#16076)

## 提交信息

- **序号**：3570 / 4088
- **哈希**：017ba2f6fc78c12f1aff81b9f8f8a28513a5703f
- **短哈希**：017ba2f6f
- **日期**：2026-04-22 06:33:48 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Spark: Fix RoaringBitmap version in runtime-deps.txt (#16076)
- **PR/Issue**：#16076

## 总体目的

该提交修复了 Spark 4.1 运行时依赖清单文件 `runtime-deps.txt` 中 RoaringBitmap 版本号不一致的问题。在提交 3561 中，`gradle/libs.versions.toml` 中的 RoaringBitmap 已从 1.6.13 升级到 1.6.14，但 Spark 4.1 的 `runtime-deps.txt` 文件中仍记录的是旧版本 1.6.13。该文件用于跟踪运行时分发包中包含的依赖版本，版本不一致可能导致依赖基线检查（runtime deps baseline）失败或分发包中包含错误版本的依赖。

## 如何达成设计目的

将 `spark/v4.1/spark-runtime/runtime-deps.txt` 中的 `org.roaringbitmap:RoaringBitmap` 版本从 1.6.13 更新为 1.6.14，与 `gradle/libs.versions.toml` 中的实际依赖版本保持一致。

## 修改详情

### `spark/v4.1/spark-runtime/runtime-deps.txt` (+1/-1 lines)

**修改目的**：同步 RoaringBitmap 版本号。

**工作逻辑**：
```
-org.roaringbitmap:RoaringBitmap:1.6.13
+org.roaringbitmap:RoaringBitmap:1.6.14
```

## 总结

这是一个版本号同步修复，确保 Spark 4.1 运行时依赖清单中的 RoaringBitmap 版本与实际构建依赖一致。这类不一致通常在依赖升级提交（如 Dependabot）未同步更新所有引用位置时出现。
