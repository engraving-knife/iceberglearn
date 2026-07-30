# 提交 3226：Core: FormatModelRegistry javadoc tweaks (#15257)

## 提交信息

- **序号**：3226 / 4088
- **哈希**：ee97581bde4c0d860a94eaea3be526cf2ee58a7b
- **短哈希**：ee97581bd
- **日期**：2026-02-09
- **作者**：pvary
- **提交说明**：Core: FormatModelRegistry javadoc tweaks (#15257)
- **PR/Issue**：#15257

## 总体目的

本次提交是一个文档类的微调提交，针对 `FormatModelRegistry` 类的 javadoc 注释和一处编译器抑制注解进行小幅修正。`FormatModelRegistry` 是 Iceberg 核心模块中负责管理基于文件格式的读写器（reader/writer）的注册表类，通过统一的对象模型工厂接口来选择对应的 `ReadBuilder` 和 `FileWriterBuilder`。

随着代码的演进，类注释中描述的构建器选择依据已经从"对象模型名称（object model name）"变更为"对象模型类（object model class）"，原有的 javadoc 文本未同步更新，导致注释与实际实现不一致。同时，类注释中保留了一段描述"写构建器可能会根据请求的构建器类型被包装在专门的内容文件写实现中"的说明，该描述在当前实现中已不再准确，属于过时的遗留说明。本次提交将注释修正为与当前实现相符的表述，使 `FormatModel` 注册用于创建 reader 和 writer 的描述更加简洁准确。

此外，`positionDeleteWriteBuilder` 方法上的 `@SuppressWarnings({"unchecked", "rawtypes"})` 注解中，`rawtypes` 已非必要（该方法已使用泛型参数化），本次提交将其简化为仅抑制 `unchecked` 警告，保持代码整洁并遵循最佳实践。

## 如何达成设计目的

通过直接编辑 `FormatModelRegistry.java` 文件，修正类级别 javadoc 中的措辞，使其与实际实现逻辑一致；同时简化方法级别的 `@SuppressWarnings` 注解，移除不再需要的 `rawtypes` 抑制项。改动范围极小，仅涉及注释文本和注解参数，不影响任何运行时行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/formats/FormatModelRegistry.java` (+3/-5 lines)

**修改目的**：修正 javadoc 注释使其与当前实现一致，并简化抑制注解。

**工作逻辑**：
类注释中将"is selected based on `FileFormat` and object model name"修改为"is selected based on `FileFormat` and object model class"，反映了实际代码使用 `Pair<FileFormat, Class<?>>` 作为索引键（即 `MODELS` 映射），而非基于名称字符串查找。同时删除了关于写构建器包装行为的多余描述，因为当前实现已将 reader 和 writer 的创建统一收口于 `FormatModel` 注册机制，不再需要说明额外的包装逻辑。在 `positionDeleteWriteBuilder` 方法上，将 `@SuppressWarnings({"unchecked", "rawtypes"})` 简化为 `@SuppressWarnings("unchecked")`，因为该方法签名已显式声明泛型类型 `PositionDelete<D>`，`Class<PositionDelete<D>>` 是参数化类型，不再产生 raw type 警告，仅保留对不可避免的 unchecked 转换的抑制。

## 总结

本次提交是纯粹的文档和注解清理，确保 `FormatModelRegistry` 的 javadoc 与实际实现保持一致，避免误导开发者。虽然改动微小，但体现了项目对文档准确性和代码整洁度的维护标准，属于代码卫生（code hygiene）类的常规维护。
