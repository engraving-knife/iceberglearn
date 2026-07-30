# 提交 2405：Flink: Adjust the configuration precedence for the dynamic sink (#13609)

## 提交信息

- **序号**：2405 / 4088
- **哈希**：bc154539a96b819bcba970cc208b8ec089af9cd6
- **短哈希**：bc154539a
- **日期**：2025-07-24 12:58:25 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Adjust the configuration precedence for the dynamic sink (#13609)
- **PR/Issue**：#13609

## 总体目的

此提交修复了 Flink 动态 Sink（DynamicWriter）中写入属性配置优先级的问题。原先代码中存在一个 TODO 注释："Handle precedence correctly for the write properties coming from the sink conf and from the table defaults"，表明配置优先级处理不正确。

问题在于：原代码先以 `commonWriteProperties`（来自 Sink 配置）为基础，然后用 `table.properties()`（来自表默认配置）覆盖，导致表属性优先级高于 Sink 配置。这是错误的——Sink 配置（用户通过 Flink 作业显式设置的属性）应该具有更高优先级，应覆盖表的默认属性。

修复后，调整为先以表属性为基础，再用 Sink 配置覆盖，确保 Sink 配置的优先级正确高于表默认属性。同时移除了 TODO 注释。此修复应用到 Flink 1.19、1.20 和 2.0 三个版本。

## 如何达成设计目的

通过调换 `Map.putAll` 的调用顺序来修正配置优先级：

- **原逻辑**：`Maps.newHashMap(commonWriteProperties)` → `putAll(table.properties())`（表属性覆盖 Sink 配置）
- **新逻辑**：`Maps.newHashMap(table.properties())` → `putAll(commonWriteProperties)`（Sink 配置覆盖表属性）

`Map.putAll` 的语义是后调用的覆盖先调用的，因此调换顺序即可改变优先级。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (+2/-4 lines)

**修改目的**：修正 Flink 1.19 动态 Sink 的配置优先级。

**工作逻辑**：移除 TODO 注释。将 `tableWriteProperties` 的构建逻辑从"先 commonWriteProperties 后 table.properties()"改为"先 table.properties() 后 commonWriteProperties"，使 Sink 配置（commonWriteProperties）能覆盖表默认属性。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (+2/-4 lines)

**修改目的**：修正 Flink 1.20 动态 Sink 的配置优先级。

**工作逻辑**：与 1.19 相同的修改。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (+2/-4 lines)

**修改目的**：修正 Flink 2.0 动态 Sink 的配置优先级。

**工作逻辑**：与 1.19 相同的修改。

## 总结

此提交修复了 Flink 动态 Sink 中写入属性的配置优先级 bug，确保用户通过 Sink 配置显式设置的属性能正确覆盖表的默认属性。修复方式简洁——调换两个 `Map.putAll` 的调用顺序，同时移除了遗留的 TODO 注释。修复同步应用到 Flink 1.19、1.20 和 2.0 三个版本。
