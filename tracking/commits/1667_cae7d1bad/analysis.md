# 提交 1667：Spark 3.5: Remove use of File.Separator in RewriteTablePath (#12066)

## 提交信息

- **序号**：1667 / 4088
- **哈希**：cae7d1bad863082bd5808f93424290706f4c6cc8
- **短哈希**：cae7d1bad
- **日期**：2025-02-01（Sat Feb 1 11:15:55 2025 +0100）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 3.5: Remove use of File.Separator in RewriteTablePath (#12066)
- **PR/Issue**：#12066

## 总体目的

`RewriteTablePath` 是 Iceberg 提供的"表路径重写"动作，用于把表的元数据与数据文件从一个存储位置（sourcePrefix）搬迁到另一个存储位置（targetPrefix），常用于表迁移、存储搬迁场景。该动作在拼接路径、提取文件名、做路径相对化时需要用到路径分隔符。

此前代码使用 `java.io.File.separator` 作为路径分隔符。问题在于：`File.separator` 取决于**运行 RewriteTablePath 动作的客户端所在操作系统**——在 Windows 上是 `\`，在 Linux/macOS 上是 `/`。但 Iceberg 表的目标文件系统通常是远程对象存储（S3、GCS、Azure Blob、HDFS 等），它们统一使用 POSIX 风格的 `/` 分隔符。当用户在 Windows 客户端上对一个 S3 表执行 RewriteTablePath 时，代码会用 `\` 去拼接/切分 S3 路径，导致生成的路径形如 `s3://bucket\warehouse\table`，既无法被对象存储识别，也会破坏 manifest 中记录的文件 location，造成表损坏。

本提交移除对 `File.separator` 的依赖，统一改用硬编码的 POSIX 分隔符 `/`，并抽取常量 `FILE_SEPARATOR` 与工具方法 `maybeAppendFileSeparator`，使路径操作与目标文件系统语义一致，而与客户端 OS 无关。

## 如何达成设计目的

通过两处改动实现：

1. 在 `RewriteTablePathUtil`（core 模块，Spark 各版本共享的工具类）中定义常量 `FILE_SEPARATOR = "/"`，替换所有 `File.separator` 引用；并把"在路径末尾补分隔符"的重复逻辑抽取为公共方法 `maybeAppendFileSeparator`，供本类与 Spark Action 复用，消除重复代码。
2. 在 `RewriteTablePathSparkAction`（Spark 3.5 模块）中移除 `java.io.File` 导入，把所有 `File.separator` 引用改为 `RewriteTablePathUtil.FILE_SEPARATOR`；把 stagingDir 的拼接逻辑改为复用 `maybeAppendFileSeparator`；并把原本私有静态方法 `newPath` 移除，改为直接调用 `RewriteTablePathUtil` 上已有的同名公共方法，避免逻辑重复。

## 修改详情

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java`（修改，22 行变化）

**修改目的**：把路径分隔符从 OS 相关的 `File.separator` 改为 POSIX `/`，并抽取公共工具方法。

**工作逻辑**：
- 移除 `import java.io.File`；
- 新增常量 `public static final String FILE_SEPARATOR = "/"`，并加注释说明"使用 POSIX 分隔符而非 File.separator，因为 File.separator 依赖客户端环境而非目标文件系统，POSIX 与 S3/GCS 等兼容"；
- `combinePaths(absolutePath, relativePath)`：原本手写"若不以 `/` 结尾则补 `/` 再拼接 relativePath"，改为直接 `return maybeAppendFileSeparator(absolutePath) + relativePath`；
- `fileName(path)`：`path.lastIndexOf(File.separator)` → `path.lastIndexOf(FILE_SEPARATOR)`；
- `relativize(path, prefix)`：原本手写"若 prefix 不以 `/` 结尾则补 `/`"，改为 `String toRemove = maybeAppendFileSeparator(prefix)`；
- 新增 `public static String maybeAppendFileSeparator(String path)`：`return path.endsWith(FILE_SEPARATOR) ? path : path + FILE_SEPARATOR`，统一"确保路径以分隔符结尾"的逻辑。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java`（修改，25 行变化）

**修改目的**：让 Spark 3.5 的 Action 与 core 工具类保持一致，移除对 `File.separator` 的使用与重复的辅助方法。

**工作逻辑**：
- 移除 `import java.io.File`；
- stagingDir 初始化逻辑：原 `stagingDir = getMetadataLocation(table) + "copy-table-staging-" + UUID.randomUUID() + "/"` 改为用 `RewriteTablePathUtil.FILE_SEPARATOR` 拼接末尾分隔符；原 `else if (!stagingDir.endsWith("/"))` 分支改为 `else { stagingDir = RewriteTablePathUtil.maybeAppendFileSeparator(stagingDir); }`，统一调用工具方法；
- 两处 `newPath(...)` 调用改为 `RewriteTablePathUtil.newPath(...)`（即调用 core 工具类上的公共方法）；
- 移除本类中私有的 `newPath` 方法（其实现就是调用 `RewriteTablePathUtil.combinePaths` + `relativize`，core 已有公共 `newPath` 封装）；
- `getMetadataLocation` 中 `currentMetadataPath.lastIndexOf(File.separator)` → `lastIndexOf(RewriteTablePathUtil.FILE_SEPARATOR)`。

## 小结

- **成效**：修复 RewriteTablePath 在 Windows 客户端上对远程对象存储表操作时路径分隔符错误导致的路径损坏问题，使路径操作与目标文件系统语义一致；同时抽取 `maybeAppendFileSeparator` 公共方法，消除 core 与 Spark Action 间的重复逻辑。
- **影响范围**：`core` 模块的 `RewriteTablePathUtil`（Spark/Flink 各版本共享）与 `spark/v3.5` 的 `RewriteTablePathSparkAction`。行为上对 Linux/macOS 客户端无变化（`File.separator` 本就是 `/`），仅修正 Windows 客户端的潜在 bug。
- **回迁到 1.4.x 的注意事项**：回迁安全且建议回迁，这是一个 bug 修复。需注意 1.4.x 分支可能同时存在 `spark/v3.4`、`spark/v3.5` 等多个版本的 `RewriteTablePathSparkAction`，本提交只改了 3.5；若 1.4.x 的 3.4 版本 Action 也有同样的 `File.separator` 问题，应一并修复以保持一致。同时确认 1.4.x 的 `RewriteTablePathUtil` 是否已有 `newPath` 公共方法，若无则需一并补齐。
