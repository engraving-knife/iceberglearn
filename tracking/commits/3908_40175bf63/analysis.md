# 提交 3908：Build: Remove duplicate jackson.databind declaration (#16879)

## 提交信息

- **序号**：3908 / 4088
- **哈希**：40175bf63abe132387367b08122d1a79cc13916c
- **短哈希**：40175bf63
- **日期**：2026-06-19 05:53:18 -0700
- **作者**：Fezinvirtual
- **提交说明**：Build: Remove duplicate jackson.databind declaration (#16879)
- **PR/Issue**：#16879

## 总体目的

移除 Kafka Connect 模块 `build.gradle` 中重复的 `jackson.databind` 依赖声明。该依赖在 dependencies 闭包中出现了两次，虽然 Gradle 能正确处理重复声明（使用最后一次），但重复声明会造成混乱并可能在未来修改时引入不一致。

## 如何达成设计目的

直接删除 `kafka-connect/build.gradle` 中重复的依赖行。

## 修改详情

### `kafka-connect/build.gradle` (+0/-1 lines)

**修改目的**：移除重复的 jackson.databind 依赖声明。

**工作逻辑**：
删除一行重复的 `jackson.databind` 依赖声明，保留另一处相同声明。不影响实际依赖解析结果，因为 Gradle 会去重处理。

## 总结

移除了 Kafka Connect 构建文件中重复的 jackson.databind 依赖声明，属于代码整洁性修复，不引入任何功能或行为变化。
