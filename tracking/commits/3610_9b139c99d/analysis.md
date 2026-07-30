# 提交 3610：Spark: Carry over changes to LICENSE and NOTICE in older Spark versions. (#16142)

## 提交信息

- **序号**：3610 / 4088
- **哈希**：9b139c99d8ee1bb4991025ab95539eb2274952d0
- **短哈希**：9b139c99d
- **日期**：2026-04-28 11:44:07 -0600
- **作者**：Ryan Blue
- **提交说明**：Spark: Carry over changes to LICENSE and NOTICE in older Spark versions. (#16142)
- **PR/Issue**：#16142

## 总体目的

这个提交将提交 3604 中对 Spark 4.1 runtime LICENSE 和 NOTICE 文件的更新同步到 Spark 3.4、3.5 和 4.0 版本。

Iceberg 同时维护多个 Spark 版本的 runtime jar，每个版本都有独立的 LICENSE 和 NOTICE 文件。提交 3604 首先更新了 Spark 4.1 的 LICENSE/NOTICE，本提交将相同的变更应用到 Spark 3.4、3.5 和 4.0，确保所有 Spark 版本的许可证文件保持一致和准确。

## 如何达成设计目的

将 Spark 4.1 中的 LICENSE 和 NOTICE 变更原样应用到 Spark 3.4、3.5 和 4.0 的对应文件中。三个版本的修改内容完全相同。

## 修改详情

### `spark/v3.4/spark-runtime/LICENSE` (+20/-276 lines)

**修改目的**：更新 Spark 3.4 的 LICENSE 文件。

**工作逻辑**：
与提交 3604 中 Spark 4.1 的修改完全一致：
1. 为 Parquet 引入的依赖添加 "(bundled by Parquet)" 标注（Apache Thrift、JavaFastPFOR、fastutil、Zero-Allocation Hashing）。
2. 为 ORC 引入的依赖添加 "(bundled by ORC)" 标注（Apache Hive's Storage API、Google protobuf）。
3. 移除不再打包的依赖条目（Perfmark、org.json、ThreeTen BP、JSpecify、Animal Sniffer 等）。
4. 合并 HttpComponents 重复条目。
5. 新增 Eclipse Collections 条目。

### `spark/v3.4/spark-runtime/NOTICE` (+0/-101 lines)

**修改目的**：清理 Spark 3.4 的 NOTICE 文件。

### `spark/v3.5/spark-runtime/LICENSE` (+20/-276 lines)

**修改目的**：更新 Spark 3.5 的 LICENSE 文件，与 Spark 3.4 相同的修改。

### `spark/v3.5/spark-runtime/NOTICE` (+0/-101 lines)

**修改目的**：清理 Spark 3.5 的 NOTICE 文件。

### `spark/v4.0/spark-runtime/LICENSE` (+20/-276 lines)

**修改目的**：更新 Spark 4.0 的 LICENSE 文件，与 Spark 3.4 相同的修改。

### `spark/v4.0/spark-runtime/NOTICE` (+0/-101 lines)

**修改目的**：清理 Spark 4.0 的 NOTICE 文件。

## 总结

这个提交是提交 3604 的扩展，将 Spark 4.1 的 LICENSE/NOTICE 更新同步到 Spark 3.4、3.5 和 4.0 版本，确保所有 Spark 版本的许可证文件保持一致。这是 Iceberg 多版本维护的标准做法，确保 1.11 版本在所有支持的 Spark 版本上都符合法律合规要求。
