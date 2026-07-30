# 提交 4078：API: Shorten dropped source type comment in PartitionSpec

## 提交信息

- **序号**：4078 / 4088
- **哈希**：2f17d9bafdb643819530ec21371fd1380438303e
- **短哈希**：2f17d9baf
- **日期**：2026-07-20 09:46:40 -0700
- **作者**：Anoop Johnson
- **提交说明**：API: Shorten dropped source type comment in PartitionSpec (follow-up to #17262) (#17303)
- **PR/Issue**：#17303（follow-up to #17262）

## 总体目的

这是对先前 PR #17262 的一处注释文案清理。#17262 修改了 `PartitionSpec.resultType` 方法对"源字段已被 drop"场景的处理：当 `schema.findType(field.sourceId())` 返回 null 时，使用 `Types.UnknownType.get()` 作为替代的源类型，并让 transform 自行从 unknown 推导结果类型。

原 PR 留下了一段较长的注释，解释了不同 transform（identity、truncate、void 等会返回源类型，而具有固定结果类型的 transform 仍能正常解析）的行为差异。本提交将这段注释缩短精简，仅保留核心语义：源字段被 drop 后源类型已丢失，固定结果类型的 transform 仍可工作，因此用 unknown 作为源类型。

这是一次纯文档/注释维护，不涉及任何代码行为变化。

## 如何达成设计目的

直接将原 3 行注释压缩为 2 行，去除冗长的 transform 举例，保留"why"（源类型已丢失）和"how"（固定结果类型 transform 仍工作，使用 unknown 替代）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionSpec.java` (+2/-3 lines)

**修改目的**：精简 `resultType` 方法中关于 dropped source type 的注释。

**工作逻辑**：

修改前：
```java
// when the source field has been dropped, substitute unknown and let the transform derive
// its result type; transforms that return the source type (identity, truncate, void) yield
// unknown, while transforms with a fixed result type still resolve
sourceType = Types.UnknownType.get();
```

修改后：
```java
// When the source field has been dropped, the source type has been lost
// Transforms with a fixed result type still work, so use unknown for source
sourceType = Types.UnknownType.get();
```

代码逻辑完全不变，仅注释更简洁。首字母大写以匹配常见 Java 注释风格，去掉了具体 transform 类型的枚举（identity/truncate/void），让注释聚焦于规则而非示例。

## 总结

一次纯粹的注释清理提交，作为 #17262 的后续。它没有改变任何运行时行为，只是让 `PartitionSpec.resultType` 中关于"源字段被 drop 后用 UnknownType 替代"的说明更加简洁清晰，便于后续维护者快速理解意图。
