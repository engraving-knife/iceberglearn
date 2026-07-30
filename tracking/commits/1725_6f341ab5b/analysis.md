# 提交 1725：Build: Clean up dependencies (#12252)

## 提交信息

- **序号**：1725 / 4088
- **哈希**：6f341ab5ba78add5722a19ed4fb02cf05b50efcc
- **短哈希**：6f341ab5b
- **日期**：2025-02-13 14:38:06 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Build: Clean up dependencies (#12252)
- **PR/Issue**：#12252

## 总体目的

清理 Gradle 版本目录中未使用的依赖声明。随着时间的推移，项目中会积累一些不再被任何模块引用的依赖声明，这些声明增加了版本目录的维护负担，也可能导致混淆。此提交移除了以下未使用的依赖：

1. `netty-buffer-compat`（Netty 兼容版本缓冲区）- 与 `netty-buffer` 重复，不再需要兼容版本。
2. `tez010`（Tez 0.10.4）- Tez 0.10 系列的版本声明和对应的 `tez010-dag`、`tez010-mapreduce` 库声明，项目中仅使用 Tez 0.8。
3. `hive2-serde`（Hive 2 SerDe）- Hive 2 序列化/反序列化库，不再被任何模块引用。

## 如何达成设计目的

修改 `gradle/libs.versions.toml`，移除未使用的版本声明和库声明。具体来说，从 `[versions]` 部分移除版本变量，从 `[libraries]` 部分移除对应的库引用。

## 修改详情

### `gradle/libs.versions.toml`（修改, -6 lines）

**修改目的**：移除未使用的依赖声明。

**工作逻辑**：

1. **移除版本声明**：
   - `netty-buffer-compat = "4.1.117.Final"` - Netty 兼容版本，与 `netty-buffer` 重复。
   - `tez010 = "0.10.4"` - Tez 0.10 版本，项目仅使用 Tez 0.8。

2. **移除库声明**：
   - `netty-buffer-compat = { module = "io.netty:netty-buffer", version.ref = "netty-buffer-compat" }` - 对应的 Netty 兼容库引用。
   - `hive2-serde = { module = "org.apache.hive:hive-serde", version.ref = "hive2" }` - Hive 2 SerDe 库引用。
   - `tez010-dag = { module = "org.apache.tez:tez-dag", version.ref = "tez010" }` - Tez 0.10 DAG 库引用。
   - `tez010-mapreduce = { module = "org.apache.tez:tez-mapreduce", version.ref = "tez010" }` - Tez 0.10 MapReduce 库引用。

## 小结

- **成效**：版本目录更简洁，移除了 6 行未使用的依赖声明，减少了维护负担和潜在混淆。
- **影响范围**：仅影响构建配置，不影响项目代码和运行时行为。这些依赖本就未被使用，移除后不会产生任何功能影响。
- **回迁到 1.4.x 的注意事项**：可以安全回迁，但需确认 1.4.x 分支中这些依赖是否确实未被使用。1.4.x 分支可能仍有模块引用 `tez010` 或 `hive2-serde`，如果确实未使用则可回迁，否则不应回迁。建议在回迁前用 `grep` 检查 1.4.x 分支中是否有对这些依赖的引用。
