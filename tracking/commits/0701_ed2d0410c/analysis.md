# 提交 0701：更新 Kafka-connect 中 iceberg.hadoop-conf-dir 配置项描述

## 提交信息
- **序号**：0701 / 4088
- **哈希**：ed2d0410c861c6fcec825dff738d01559f2cd590
- **短哈希**：ed2d0410c
- **日期**：2024-04-19
- **作者**：Ajantha Bhat
- **提交说明**：Kafka-connect: Update iceberg.hadoop-conf-dir config description (#10184)
- **PR/Issue**：#10184

## 总体目的

本提交修复 Iceberg Kafka Connect Sink 模块中 `iceberg.hadoop-conf-dir` 配置项的两个问题：一是常量命名拼写错误，二是该配置项的描述文本复制粘贴错误，描述了与该配置项完全无关的内容。

在 `IcebergSinkConfig.java` 中，配置项 `iceberg.hadoop-conf-dir` 对应的常量名被错误地拼写为 `HADDOP_CONF_DIR_PROP`（漏掉了一个 `O`，正确应为 `HADOOP_CONF_DIR_PROP`）。虽然常量名错误不影响运行时行为（因为常量值字符串 `"iceberg.hadoop-conf-dir"` 是正确的，配置项仍可正常读取），但作为代码可读性和规范性问题应当修正。

更严重的问题是该配置项的描述文本错误。在 `configDef.define(...)` 调用中，`iceberg.hadoop-conf-dir` 的描述被错误地写为 `"Coordinator threads to use for table commits, default is (cores * 2)"`，这显然是从上一条配置项 `iceberg.control.commit.threads` 复制过来后忘记修改的。该描述与 `hadoop-conf-dir` 配置项的实际功能完全不符。Kafka Connect 框架会使用此描述生成配置文档并向用户展示，错误描述会严重误导用户对配置项用途的理解。

## 如何达成设计目的

提交通过两处修改达成目的：

1. **常量名拼写修正**：将 `HADDOP_CONF_DIR_PROP` 重命名为 `HADOOP_CONF_DIR_PROP`，并同步更新所有引用该常量的位置（共 3 处：常量定义、`configDef.define()` 调用、`hadoopConfDir()` 方法中的 `getString()` 调用）。

2. **描述文本修正**：将 `iceberg.hadoop-conf-dir` 的描述从错误的 `"Coordinator threads to use for table commits, default is (cores * 2)"` 改为正确的 `"If specified, Hadoop config files in this directory will be loaded"`，准确描述了该配置项的作用——指定一个目录，其中的 Hadoop 配置文件将被加载。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConfig.java`
**修改目的**：修正 `iceberg.hadoop-conf-dir` 配置项的常量命名拼写和描述文本。

**工作逻辑**：

- 常量定义处（第 90 行附近）：`HADDOP_CONF_DIR_PROP` → `HADOOP_CONF_DIR_PROP`，修正拼写。
- `configDef.define()` 调用（第 219 行附近）：
  - 第一个参数从 `HADDOP_CONF_DIR_PROP` 改为 `HADOOP_CONF_DIR_PROP`。
  - 最后一个参数（描述）从 `"Coordinator threads to use for table commits, default is (cores * 2)"` 改为 `"If specified, Hadoop config files in this directory will be loaded"`。
- `hadoopConfDir()` 方法（第 407 行附近）：`getString(HADDOP_CONF_DIR_PROP)` → `getString(HADOOP_CONF_DIR_PROP)`。

由于 Java 编译期会检查常量引用，3 处引用必须同步修改，否则编译失败，因此本提交是原子的。

## 小结
- **成效**：成功达成目的。常量拼写修正提升代码可读性，描述文本修正使用户在 Kafka Connect 配置文档中看到正确的配置项用途说明。
- **影响范围**：仅影响 `kafka-connect` 模块的 `IcebergSinkConfig` 类，属于纯文档与命名层面的修改，不影响任何运行时逻辑。
- **回迁到 1.4.x 的注意事项**：可直接回迁，无依赖和冲突。若 1.4.x 分支上该文件已有其他改动，需注意合并时保持常量名一致性。
