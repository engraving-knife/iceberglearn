# 提交 3487：Docs: Update Flink version in contribute.md build example (#15812)

## 提交信息

- **序号**：3487 / 4088
- **哈希**：149cc464f9b7df800cc5718af725983473819504
- **短哈希**：149cc464f9
- **日期**：2026-03-30 23:03:52 -0700
- **作者**：Eunbin Son
- **提交说明**：Docs: Update Flink version in contribute.md build example (#15812)
- **PR/Issue**：#15812

## 总体目的

更新贡献指南文档 `contribute.md` 中的构建示例命令。原示例使用 `flinkVersions=1.14`，但该版本已 End of Life 并从 Iceberg 构建中移除。新贡献者复制该命令会直接遇到 Gradle 异常。需要更新为当前支持的版本。

## 如何达成设计目的

将构建示例中的 Spark 和 Flink 版本更新为当前支持的版本：
- Spark：`3.4,3.5` → `3.5,4.0`
- Flink：`1.14` → `1.20,2.0`

## 修改详情

### `site/docs/contribute.md` (+1/-1 line)

**修改目的**：更新构建示例命令中的版本号。

**工作逻辑**：
```diff
-* To build particular Spark/Flink Versions: `./gradlew build -DsparkVersions=3.4,3.5 -DflinkVersions=1.14`
+* To build particular Spark/Flink Versions: `./gradlew build -DsparkVersions=3.5,4.0 -DflinkVersions=1.20,2.0`
```

## 总结

简单的文档修复提交。将贡献指南中的构建示例命令从已过时的 Flink 1.14 和旧 Spark 版本更新为当前支持的版本（Flink 1.20/2.0，Spark 3.5/4.0），避免新贡献者遇到构建失败。
