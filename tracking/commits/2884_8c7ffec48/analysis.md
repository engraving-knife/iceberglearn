# 提交 2884：Refactor: populate writeOptions in extraSnapshotMetadata using common util (#14604)

## 提交信息

- **序号**：2884 / 4088
- **哈希**：8c7ffec48ffd8d51e0ec7c8eb12d9aaeee214212
- **短哈希**：8c7ffec48
- **日期**：2025-11-17 21:10:46 -0800
- **作者**：yingjianwu98
- **提交说明**：Refactor: populate writeOptions in extraSnapshotMetadata using common util (#14604)
- **PR/Issue**：#14604

## 总体目的

提交 2878 在 `SparkWriteConf.extraSnapshotMetadata()` 中新增了从会话配置提取快照属性的功能，其中 write options 的处理仍然使用手写的 `forEach` 循环：遍历 write options，检查键是否以 `SnapshotSummary.EXTRA_METADATA_PREFIX` 开头，截取前缀后放入 `extraSnapshotMetadata`。

这与刚引入的会话配置提取逻辑（使用 `PropertyUtil.propertiesWithPrefix()`）风格不一致。此提交将 write options 的处理也改为使用 `PropertyUtil.propertiesWithPrefix()`，统一代码风格，减少重复逻辑。

## 如何达成设计目的

将 `extraSnapshotMetadata()` 方法中处理 write options 的 `forEach` 循环替换为 `PropertyUtil.propertiesWithPrefix()` 调用。由于 `putAll` 会覆盖已有键的值，write options 的处理在会话配置之后执行，因此 write options 仍然能覆盖会话配置中的同名属性，行为与原来完全一致。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java` (+2/-7 lines)

**修改目的**：用通用工具方法替换手写的前缀过滤循环。

**工作逻辑**：原代码：
```java
writeOptions.forEach(
    (key, value) -> {
      if (key.startsWith(SnapshotSummary.EXTRA_METADATA_PREFIX)) {
        extraSnapshotMetadata.put(
            key.substring(SnapshotSummary.EXTRA_METADATA_PREFIX.length()), value);
      }
    });
```
替换为：
```java
extraSnapshotMetadata.putAll(
    PropertyUtil.propertiesWithPrefix(writeOptions, SnapshotSummary.EXTRA_METADATA_PREFIX));
```

`PropertyUtil.propertiesWithPrefix()` 返回去掉前缀后的键值对 Map，`putAll` 将其合并到 `extraSnapshotMetadata` 中，覆盖会话配置中的同名键，保持原有的优先级语义。

## 总结

该提交是提交 2878 的后续重构，将 `SparkWriteConf.extraSnapshotMetadata()` 中处理 write options 的手写循环替换为 `PropertyUtil.propertiesWithPrefix()` 调用，与会话配置处理的风格保持一致。功能行为完全不变，仅减少代码重复、提升可读性。注意此修改仅应用于 Spark v4.0 分支。
