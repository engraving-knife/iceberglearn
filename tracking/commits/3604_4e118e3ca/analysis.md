# 提交 3604：Spark 4.1: Update LICENSE and NOTICE for 1.11. (#16104)

## 提交信息

- **序号**：3604 / 4088
- **哈希**：4e118e3ca2e5a19d42797bb2518eabefcccbd201
- **短哈希**：4e118e3ca
- **日期**：2026-04-27 18:54:17 -0600
- **作者**：Ryan Blue
- **提交说明**：Spark 4.1: Update LICENSE and NOTICE for 1.11. (#16104)
- **PR/Issue**：#16104

## 总体目的

这个提交为 Iceberg 1.11 版本的 Spark 4.1 runtime jar 更新 LICENSE 和 NOTICE 文件，使其准确反映实际打包的第三方依赖。

Iceberg 的 Spark runtime jar 是一个 fat jar，包含了 Iceberg 及其所有依赖。Apache 项目要求 LICENSE 和 NOTICE 文件必须准确列出所有打包的第三方组件及其许可证信息。随着 1.11 版本的开发，依赖发生了变化（新增、移除、版本更新），LICENSE 和 NOTICE 文件需要同步更新以保持合规性。

这个提交进行了多项清理：标注了由 Parquet 和 ORC 间接引入的依赖、移除了不再打包的依赖条目、合并了重复条目、添加了缺失的依赖条目。

## 如何达成设计目的

通过手动审查实际打包的依赖列表，与 LICENSE 文件中的条目进行比对，然后进行以下调整：
1. 为 Parquet 引入的依赖添加 "(bundled by Parquet)" 标注。
2. 为 ORC 引入的依赖添加 "(bundled by ORC)" 标注。
3. 移除不再打包的依赖条目（如 Perfmark、org.json、ThreeTen BP、JSpecify、Animal Sniffer 等）。
4. 合并重复的 HttpComponents 条目。
5. 添加缺失的依赖条目（如 Zero-Allocation Hashing、Eclipse Collections）。
6. 更正描述（如 "Apache Hive" 改为 "Apache Hive's Storage API"）。
7. 清理 NOTICE 文件中不再需要的条目。

## 修改详情

### `spark/v4.1/spark-runtime/LICENSE` (+20/-276 lines)

**修改目的**：更新 LICENSE 文件以准确反映 1.11 版本的打包依赖。

**工作逻辑**：

1. **标注间接依赖来源**：为 Parquet 引入的依赖添加标注：
   - `Apache Thrift` → `Apache Thrift (bundled by Parquet)`
   - `Daniel Lemire's JavaFastPFOR` → 添加 `(bundled by Parquet)`
   - `fastutil` → `fastutil (bundled by Parquet)`
   - 新增 `Zero-Allocation Hashing (bundled by Parquet)` 条目

2. **为 ORC 引入的依赖添加标注**：
   - `Apache Hive` → `Apache Hive's Storage API (bundled by ORC)`
   - `Google protobuf` → `Google protobuf (bundled by ORC)`

3. **移除不再打包的依赖**：Perfmark、org.json、ThreeTen BP、JSpecify、Animal Sniffer Annotations 等多个条目被移除。

4. **合并重复条目**：将 "Apache HttpComponents (client and core)" 和 "Apache HttpComponents Client" 合并为一个条目：
   ```
   This product bundles and includes code from Apache HttpComponents (core/client).
   ```

5. **新增缺失条目**：Eclipse Collections（EPL 1.0 许可证）。

6. **重排序**：将依赖按来源重新排列，使 Parquet 和 ORC 的间接依赖更清晰。

### `spark/v4.1/spark-runtime/NOTICE` (+0/-101 lines)

**修改目的**：清理 NOTICE 文件中不再需要的条目。

**工作逻辑**：
移除了 101 行不再需要的 NOTICE 条目，这些条目对应的依赖已不再打包或不需要在 NOTICE 中特别声明。

## 总结

这个提交是 1.11 版本发布前的合规性准备工作，确保 Spark 4.1 runtime jar 的 LICENSE 和 NOTICE 文件准确反映实际打包的依赖。通过标注间接依赖的来源（Parquet/ORC）、移除不再打包的条目、合并重复条目，使许可证文件更加清晰和准确。这是 Apache 项目发布前的必要步骤，对于法律合规性至关重要。
