# 提交 3131：Core: Remove unused import in ParquetConversions (#15081)

## 提交信息

- **序号**：3131 / 4088
- **哈希**：2d6f4bbfbcb1d4a80c8de54e8f627cd559c555d6
- **短哈希**：2d6f4bbfb
- **日期**：2026-01-19
- **作者**：Resort_Annex
- **提交说明**：Core: Remove unused import in ParquetConversions (#15081)
- **PR/Issue**：#15081

## 总体目的

本提交清理 `parquet` 模块中 `ParquetConversions.java` 一个未使用的 `import java.util.UUID` 语句，属于代码卫生（code hygiene）类的小修补。

`ParquetConversions` 负责在 Parquet 的底层值类型（如 `Binary`）与 Iceberg 类型之间进行转换，其中包含对 `UUID` 类型的处理。该文件实际只使用项目自带的 `org.apache.iceberg.util.UUIDUtil`（通过 `UUIDUtil.convert(...)` 完成转换），以及 `Type.TypeID.UUID` 这一类型枚举常量，并不直接引用 `java.util.UUID` 类。因此 `import java.util.UUID` 是一条遗留的、无人使用的导入，可能是早期重构中移除了对 `java.util.UUID` 的直接引用后忘记清理的残留。未使用的导入虽不影响编译与运行，但会增加阅读噪音、在严格 lint 规则下触发告警，及时清理有助于保持代码整洁。

## 如何达成设计目的

仅删除 `ParquetConversions.java` 第 26 行的 `import java.util.UUID;` 一行，不改动任何逻辑。通过核对文件内容确认：转换逻辑全部经由 `UUIDUtil` 完成，删除该导入不会破坏编译。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetConversions.java` (+0/-1 lines)

**修改目的**：移除未使用的 `java.util.UUID` 导入。

**工作逻辑**：
删除 `import java.util.UUID;` 一行。文件中对 UUID 的处理仍保留：`case UUID` 分支与 `Type.TypeID.UUID` 判断中均调用 `UUIDUtil.convert(((Binary) value).toByteBuffer())`，由 `org.apache.iceberg.util.UUIDUtil` 完成 `ByteBuffer` 到 UUID 的转换，无需直接依赖 JDK 的 `java.util.UUID` 类型，故该导入确实冗余。

## 总结

本提交是纯代码清理，删除 `ParquetConversions` 中遗留的未使用 `java.util.UUID` 导入，无功能影响，降低了代码噪音并消除潜在的 lint 告警，体现了社区对代码卫生细节的持续维护。
