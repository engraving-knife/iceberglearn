# 提交 3693：Build: Bump to Parquet 1.17.1 (#16257)

## 提交信息

- **序号**：3693 / 4088
- **哈希**：4cebcdd6ae8b19607efd880870c61d553d318730
- **短哈希**：4cebcdd6a
- **日期**：2026-05-12 13:00:00 -0700
- **作者**：Fokko Driesprong
- **提交说明**：Build: Bump to Parquet 1.17.1 (#16257)
- **PR/Issue**：#16257

## 总体目的

这个提交将 Apache Parquet 库从 1.17.0 升级到 1.17.1。Parquet 是 Iceberg 支持的核心列式存储格式之一，Iceberg 使用 Parquet 库进行 Parquet 文件的读写操作。

此次升级为补丁版本（patch version）升级，主要包含 bug 修复和小幅改进。保持 Parquet 库的最新补丁版本对于修复已知问题、确保数据读写的正确性和稳定性具有重要意义。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 文件中的 `parquet` 版本号定义，将依赖版本从 1.17.0 升级到 1.17.1。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 parquet 版本号。

**工作逻辑**：

```toml
-parquet = "1.17.0"
+parquet = "1.17.1"
```

仅修改 `parquet` 版本字符串定义。该变量在 Gradle 构建配置中被引用，会自动应用到所有使用 Parquet 库的模块中。

## 总结

这是一个常规的依赖维护提交，将 Apache Parquet 库升级到最新的补丁版本。作为 Iceberg 核心存储格式之一，保持 Parquet 库的最新版本对于确保数据读写的正确性和性能具有重要意义。
