# 提交 2883：Core, Flink: Use helper method to filter by prefix (#14610)

## 提交信息

- **序号**：2883 / 4088
- **哈希**：ea8eb86008c8bbc1b957e354e53c1011bfd0a50a
- **短哈希**：ea8eb8600
- **日期**：2025-11-17 19:02:10 -0800
- **作者**：Yuya Ebihara
- **提交说明**：Core, Flink: Use helper method to filter by prefix (#14610)
- **PR/Issue**：#14610

## 总体目的

Iceberg 代码库中多处存在按前缀过滤配置属性并去除前缀的重复逻辑（即提取以特定前缀开头的键，去掉前缀后组成新的 Map）。`PropertyUtil.propertiesWithPrefix(properties, prefix)` 是已经存在的通用工具方法，用于完成这一操作。然而，多个模块中仍然使用手写的循环/流式处理来实现相同功能，存在代码重复。

此提交将四处手写的前缀过滤逻辑统一替换为调用 `PropertyUtil.propertiesWithPrefix()`，减少代码重复，提高可维护性和一致性。这是一个纯重构修改，不改变任何功能行为。

## 如何达成设计目的

逐个识别代码库中使用手写前缀过滤逻辑的位置，将其替换为 `PropertyUtil.propertiesWithPrefix()` 调用。涉及四个文件，分布在 core 和 flink 模块中。

## 修改详情

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopTables.java` (+1/-12 lines)

**修改目的**：替换手写的前缀过滤循环。

**工作逻辑**：`createOrGetLockManager` 方法中，原代码使用 `Iterator` 遍历 Hadoop Configuration 的所有条目，手动检查 `key.startsWith(LOCK_PROPERTY_PREFIX)` 并截取前缀。替换为 `table.conf.getPropsWithPrefix(LOCK_PROPERTY_PREFIX)` 一行调用（这里使用的是 Hadoop Configuration 自带的 `getPropsWithPrefix` 方法，而非 PropertyUtil，因为输入是 Hadoop Configuration 类型）。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java` (+2/-7 lines)

**修改目的**：替换手写的前缀过滤 forEach 逻辑。

**工作逻辑**：`filterAndRemovePrefix` 方法中，原代码使用 `properties.forEach()` 手动检查 `key.startsWith(prefix)` 并截取前缀放入 `Properties`。替换为 `result.putAll(PropertyUtil.propertiesWithPrefix(properties, prefix))`。

### `core/src/main/java/org/apache/iceberg/rest/RESTUtil.java` (+2/-10 lines)

**修改目的**：替换手写的前缀过滤 forEach 逻辑。

**工作逻辑**：`extractPrefixMap` 方法中，原代码创建 `Maps.newHashMap()`，使用 `properties.forEach()` 手动检查前缀并截取。替换为直接 `return PropertyUtil.propertiesWithPrefix(properties, prefix)`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFilesConfig.java` (+2/-10 lines)

**修改目的**：替换手写的前缀过滤 Stream 逻辑。

**工作逻辑**：`properties()` 方法中，原代码使用 `entrySet().stream().filter().collect(Collectors.toMap(...))` 手动过滤前缀并截取。替换为 `return PropertyUtil.propertiesWithPrefix(writeProperties, PREFIX)`。

## 总结

该提交是一个代码重构，将四处手写的前缀属性过滤逻辑统一替换为已有的 `PropertyUtil.propertiesWithPrefix()` 工具方法调用（HadoopTables 中使用 Hadoop 自带的 `getPropsWithPrefix`）。这减少了约 32 行重复代码，提高了代码的一致性和可维护性，不改变任何功能行为。
