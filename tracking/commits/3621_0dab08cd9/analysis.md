# 提交 3621：Flink: Apply LICENSE changes to older Flink versions. (#16159)

## 提交信息

- **序号**：3621 / 4088
- **哈希**：0dab08cd9339a323f051b31ff7925825df3c70d3
- **短哈希**：0dab08cd9
- **日期**：2026-04-30 08:18:14 -0600
- **作者**：Ryan Blue
- **提交说明**：Flink: Apply LICENSE changes to older Flink versions. (#16159)
- **PR/Issue**：#16159

## 总体目的

这个提交将提交 3609（Flink 2.1 的 LICENSE/NOTICE 更新）的变更同步到 Flink 1.20 和 Flink 2.0 版本，确保所有 Flink 版本的 runtime jar 许可证文件保持一致。

与提交 3610（将 Spark LICENSE 变更同步到旧版本）类似，这个提交是 1.11 版本发布前多版本许可证合规性工作的一部分。

## 如何达成设计目的

将 Flink 2.1 中的 LICENSE 和 NOTICE 变更原样应用到 Flink 1.20 和 2.0 的对应文件中。

## 修改详情

### `flink/v1.20/flink-runtime/LICENSE` (+39/-406 lines)

**修改目的**：更新 Flink 1.20 的 LICENSE 文件。

**工作逻辑**：
与提交 3609 中 Flink 2.1 的修改一致：
1. 为 Parquet 和 ORC 间接引入的依赖添加来源标注。
2. 移除不再打包的依赖条目（大量条目被移除）。
3. 更正描述信息。
4. 添加缺失条目。

### `flink/v1.20/flink-runtime/NOTICE` (+0/-201 lines)

**修改目的**：清理 Flink 1.20 的 NOTICE 文件，移除不再需要的条目。

### `flink/v2.0/flink-runtime/LICENSE` (+39/-406 lines)

**修改目的**：更新 Flink 2.0 的 LICENSE 文件，与 Flink 1.20 相同的修改。

### `flink/v2.0/flink-runtime/NOTICE` (+0/-122 lines)

**修改目的**：清理 Flink 2.0 的 NOTICE 文件。

## 总结

这个提交是提交 3609 的扩展，将 Flink 2.1 的 LICENSE/NOTICE 更新同步到 Flink 1.20 和 2.0 版本，确保所有 Flink 版本的 runtime jar 在 1.11 版本中保持许可证文件的一致性和合规性。
