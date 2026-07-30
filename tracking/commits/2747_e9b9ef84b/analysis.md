# 提交 2747：Spark: Refactor to use ArrayUtil

## 提交信息

- **序号**：2747 / 4088
- **哈希**：e9b9ef84b5f907f39d1b0bc94b74011612565c54
- **短哈希**：e9b9ef84b
- **日期**：2025-10-14 19:01:19 +0200
- **作者**：Raghav Mahajan
- **提交说明**：Spark: Refactor to use ArrayUtil
- **PR/Issue**：#14291

## 总体目的

这是一个代码重构提交，改进 Spark v4.0 中 `CreateChangelogViewProcedure` 的数组操作代码。此前的实现手动构建数组——先创建固定大小的数组，再通过 for 循环逐个赋值，然后使用 `Arrays.copyOf` 扩展数组并手动添加最后一个元素。这种手动数组操作代码冗长、易错，且不符合 Java 集合操作的现代风格。

本提交使用 Iceberg 工具类 `ArrayUtil.add()` 和 Java Stream API 重构这段代码，使逻辑更简洁、更易读、更易维护。重构不改变任何功能行为，仅改善代码质量。

## 如何达成设计目的

重构思路：
1. 使用 `ArrayUtil.add(identifierColumns, MetadataColumns.CHANGE_ORDINAL.name())` 替代手动创建数组 + `Arrays.copyOf` + 手动赋值的模式，一行代码即可在数组末尾添加元素
2. 使用 Java Stream API（`Arrays.stream().map().map().toArray()`）替代手动 for 循环构建 `Column[]` 数组，使代码声明式而非命令式

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/procedures/CreateChangelogViewProcedure.java` (+8/-10 lines)

**修改目的**：重构 `computeUpdateImages` 方法中的数组构建逻辑。

**工作逻辑**：

原始代码（10 行）：
- 创建 `Column[] repartitionSpec` 数组（大小为 `identifierColumns.length + 1`）
- 通过 for 循环逐个填充 `repartitionSpec[i]`
- 手动设置最后一个元素为 `CHANGE_ORDINAL`
- 使用 `Arrays.copyOf` 扩展 `identifierColumns` 数组
- 手动设置最后一个元素为 `CHANGE_ORDINAL.name()`

重构后代码（8 行）：
- 使用 `ArrayUtil.add(identifierColumns, MetadataColumns.CHANGE_ORDINAL.name())` 一步构建 `identifierFields`
- 使用 `Arrays.stream(identifierFields).map(::delimitedName).map(df::col).toArray(Column[]::new)` 构建 `repartitionSpec`

重构后的代码更简洁，避免了手动数组大小管理和索引操作，利用 `ArrayUtil.add` 工具方法和 Stream API 使意图更清晰。同时新增了 `ArrayUtil` 的 import。

## 总结

本提交是纯重构，将 `CreateChangelogViewProcedure` 中冗长的手动数组操作替换为 `ArrayUtil.add` 和 Stream API。代码从 10 行减少到 8 行，可读性和可维护性显著提升。重构不改变功能行为，仅适用于 Spark v4.0 模块。
