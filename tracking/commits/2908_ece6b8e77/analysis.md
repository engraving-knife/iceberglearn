# 提交 2908：Core: Deprecate PositionDeleteReaderWriter.writer with rowSchema (#14651)

## 提交信息

- **序号**：2908 / 4088
- **哈希**：ece6b8e7734cdcb7aeca6c59085fe2cce5216c79
- **短哈希**：ece6b8e77
- **日期**：2025-11-21 17:20:55 +0100
- **作者**：pvary
- **提交说明**：Core: Deprecate PositionDeleteReaderWriter.writer with rowSchema (#14651)
- **PR/Issue**：#14651

## 总体目的

本提交是一处 API 弃用（deprecation）改动，针对 `RewriteTablePathUtil` 内部定义的 `PositionDeleteReaderWriter` 接口。`PositionDeleteReaderWriter` 是一个面向引擎实现的 SPI 接口，用于在重写表路径（rewrite table path）时读写位置删除文件（position delete file）。该接口原本只提供一个 `writer` 方法，签名为 `writer(OutputFile, FileFormat, PartitionSpec, StructLike partition, Schema rowSchema)`，其中 `rowSchema` 参数用于在写入位置删除文件时同时携带行数据（row data）。

Iceberg 的格式演进方向是：位置删除文件只携带位置信息，不再携带行数据。也就是说，"带行数据的位置删除（position deletes that include row data）"这种用法将被淘汰。因此，带 `rowSchema` 参数的 `writer` 方法应当被弃用，并由一个不带 `rowSchema` 的新方法取代——新方法在内部以 `null` 作为 `rowSchema` 调用旧实现，表明新写入的位置删除文件不再包含行数据。本提交就是这步"新增替代方法 + 标记旧方法弃用"的过渡动作：既为外部引擎实现提供了新的、不带 `rowSchema` 的写入入口，又通过 `@Deprecated` 与 Javadoc 明确告知旧方法自 1.11.0 起弃用、将在 1.12.0 移除，并指明替代方案。

需要说明的是，本提交只做接口层面的弃用声明，并未迁移 `RewriteTablePathUtil` 内部 `rewritePositionDeleteFile` 方法里对旧 5 参 `writer` 的调用——该调用根据被重写文件是否含行数据动态计算 `rowSchema`（`record.get(2) != null ? spec.schema() : null`），用于在路径重写时保留既有位置删除文件中的行数据。这是合理的过渡状态：弃用声明面向新写入与外部实现，而既有文件的重写仍需兼容可能已存在的行数据，待 1.12.0 真正移除旧方法时再一并改造内部调用。

## 如何达成设计目的

整体思路是经典的"新增重载 + 弃用旧方法"过渡模式。在 `PositionDeleteReaderWriter` 接口中新增一个 `default` 方法 `writer(OutputFile, FileFormat, PartitionSpec, StructLike)`（4 参，无 `rowSchema`），其默认实现直接委托给旧的 5 参方法并传入 `null` 作为 `rowSchema`；同时给旧的 5 参方法加上 `@Deprecated` 注解和 Javadoc，说明自 1.11.0 弃用、1.12.0 移除、原因是位置删除不再支持携带行数据、替代方法为新 4 参方法。`default` 方法保证了二进制兼容——现有实现该接口的类只需继续实现 5 参方法即可，新 4 参方法自动可用；调用方则被引导逐步迁移到 4 参方法。

## 修改详情

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java` (+12/-0 lines)

**修改目的**：为 `PositionDeleteReaderWriter` 接口新增不带 `rowSchema` 的 4 参 `writer` 默认方法作为替代 API，并将原 5 参 `writer`（带 `rowSchema`）标记为 `@Deprecated`。

**工作逻辑**：
改动前接口只有一个 `writer` 方法：
```java
PositionDeleteWriter<Record> writer(
    OutputFile outputFile,
    FileFormat format,
    PartitionSpec spec,
    StructLike partition,
    Schema rowSchema)
    throws IOException;
```
改动后变为两个方法。新增的 `default` 4 参方法：
```java
default PositionDeleteWriter<Record> writer(
    OutputFile outputFile, FileFormat format, PartitionSpec spec, StructLike partition)
    throws IOException {
  return writer(outputFile, format, spec, partition, null);
}
```
该方法以 `null` 作为 `rowSchema` 委托给旧 5 参方法，语义上即"新写入的位置删除文件不携带行数据"。由于是 `default` 方法，已有接口实现无需改动即可获得该入口，保持二进制兼容。

原 5 参方法加上弃用声明：
```java
/**
 * @deprecated This method is deprecated as of version 1.11.0 and will be removed in 1.12.0.
 *     Position deletes that include row data are no longer supported. Use {@link
 *     #writer(OutputFile, FileFormat, PartitionSpec, StructLike)} instead.
 */
@Deprecated
PositionDeleteWriter<Record> writer(
    OutputFile outputFile,
    FileFormat format,
    PartitionSpec spec,
    StructLike partition,
    Schema rowSchema)
    throws IOException;
```
Javadoc 明确三点：弃用起始版本 1.11.0、移除版本 1.12.0、弃用原因"Position deletes that include row data are no longer supported"，并指向新 4 参方法作为替代。`@link` 指向新方法，方便 IDE 跳转迁移。

接口内未改动 `reader(...)` 方法，也未改动文件中 `rewritePositionDeleteFile` 对 5 参 `writer` 的既有调用（该调用仍按需传入动态计算的 `rowSchema` 以保留既有文件中的行数据）。

## 总结

本提交对 `RewriteTablePathUtil.PositionDeleteReaderWriter` 接口做了一次标准的 API 弃用过渡：新增不带 `rowSchema` 的 4 参 `default writer` 方法作为替代（默认以 `null` 委托旧实现），并将原带 `rowSchema` 的 5 参 `writer` 标记为 `@Deprecated`（自 1.11.0 起、1.12.0 移除），原因是 Iceberg 不再支持携带行数据的位置删除。改动通过 `default` 方法保证二进制兼容，引导外部引擎实现与调用方逐步迁移到不带 `rowSchema` 的新 API，为 1.12.0 彻底移除"带行数据的位置删除"铺路。
