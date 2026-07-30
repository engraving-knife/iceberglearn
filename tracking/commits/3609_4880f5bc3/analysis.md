# 提交 3609：Flink 2.1: Update LICENSE for 1.11. (#16102)

## 提交信息

- **序号**：3609 / 4088
- **哈希**：4880f5bc3ec2db8610ab671250caf7a4182c9e7d
- **短哈希**：4880f5bc3
- **日期**：2026-04-28 11:20:40 -0600
- **作者**：Ryan Blue
- **提交说明**：Flink 2.1: Update LICENSE for 1.11. (#16102)
- **PR/Issue**：#16102

## 总体目的

这个提交为 Iceberg 1.11 版本的 Flink 2.1 runtime jar 更新 LICENSE 和 NOTICE 文件，使其准确反映实际打包的第三方依赖。

这与提交 3604（Spark 4.1 的 LICENSE 更新）是平行的更新，针对的是 Flink 2.1 runtime jar。随着 1.11 版本的开发，Flink runtime jar 中的依赖发生了变化，LICENSE 和 NOTICE 文件需要同步更新以保持 Apache 项目的法律合规性。

## 如何达成设计目的

通过审查实际打包的依赖列表，与 LICENSE 文件进行比对，进行以下调整：
1. 为 Parquet 和 ORC 间接引入的依赖添加来源标注。
2. 移除不再打包的依赖条目。
3. 添加缺失的依赖条目。
4. 更正描述信息。
5. 清理 NOTICE 文件中不再需要的条目。

## 修改详情

### `flink/v2.1/flink-runtime/LICENSE` (+39/-406 lines)

**修改目的**：更新 LICENSE 文件以准确反映 1.11 版本的打包依赖。

**工作逻辑**：

1. **标注 Parquet 间接依赖**：
   - `Apache Thrift` → `Apache Thrift (bundled by Parquet)`
   - `Fastutil` → `Fastutil (bundled by Parquet)`
   - 新增 `Daniel Lemire's JavaFastPFOR (bundled by Parquet)`
   - 新增 `Zero-Allocation Hashing (bundled by Parquet)`

2. **标注 ORC 间接依赖**：
   - `Apache Hive` → `Apache Hive's Storage API (bundled by ORC)`
   - 新增 `Google protobuf (bundled by ORC)`

3. **移除不再打包的依赖**：大量条目被移除（净删除 367 行），包括 Google GAX、gRPC 相关依赖、Perfmark、org.json 等不再包含在 runtime jar 中的库。

4. **重排序**：将依赖按来源分组排列。

### `flink/v2.1/flink-runtime/NOTICE` (+0/-122 lines)

**修改目的**：清理 NOTICE 文件中不再需要的条目。

**工作逻辑**：
移除了 122 行不再需要的 NOTICE 条目，这些条目对应的依赖已不再打包或不需要在 NOTICE 中特别声明。

## 总结

这个提交是 1.11 版本发布前的 Flink 2.1 runtime jar 合规性更新，与提交 3604（Spark 4.1）平行。通过标注间接依赖来源、移除不再打包的条目、添加缺失条目，使 LICENSE 文件更加准确和清晰。这是 Apache 项目发布前的必要步骤。
