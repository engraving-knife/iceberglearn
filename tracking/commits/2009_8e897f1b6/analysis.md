# 提交 2009：Docs: Add the recommended style for ArrayAssertions (#12820)

## 提交信息

- **序号**：2009 / 4088
- **哈希**：8e897f1b68c38c6352f23d636c320e3754d58eef
- **短哈希**：8e897f1b6
- **日期**：2025-04-17 07:54:02 +0200
- **作者**：Tom Tanaka
- **提交说明**：Docs: Add the recommended style for ArrayAssertions (#12820)
- **PR/Issue**：#12820

## 总体目的

这个提交是纯文档变更，目的是在 Iceberg 贡献指南（contribute.md）中补充关于数组断言（ArrayAssertions）的推荐写法。在已有的代码风格指南中，已经包含了关于 Map 断言、metadata 文件位置断言等推荐写法，但缺少针对数组类型断言的最佳实践指导。

开发者在编写测试时，常常会逐元素检查数组内容（如先 `hasSize` 再逐个 `isEqualTo`），这种写法在断言失败时不会显示数组的完整内容和失败元素的索引，不利于调试。本提交在贡献文档中增加了推荐的数组断言写法，引导开发者使用 `containsExactly` 或 `contains(..., atIndex(...))` 等组合式断言，从而在断言失败时提供更丰富的诊断信息。

## 如何达成设计目的

在 `site/docs/contribute.md` 的代码风格指南章节中，紧接已有的数组相关示例之后，新增了一段关于 ArrayAssertions 推荐写法的代码示例块，展示"不推荐"和"推荐"两种写法的对比。

## 修改详情

### `site/docs/contribute.md` (修改, +12/-0 lines)

**修改目的**：在贡献指南中增加数组断言的推荐写法示例。

**工作逻辑**：
新增的代码示例展示了两种场景的对比：
1. 不推荐写法：分别调用 `assertThat(array).hasSize(2)` 和逐个元素 `assertThat(array[0]).isEqualTo("value0")`，注释说明这种方式在元素不匹配时不会显示数组内容及其索引。
2. 推荐写法一：使用 `assertThat(array).hasSize(2).containsExactly("value0", "value1")`，将所有检查组合在一起，失败时会显示数组内容。
3. 推荐写法二：使用 `assertThat(array).contains("value1", atIndex(1))`，在检查特定元素时也会显示内容和索引。

## 总结

这是一个简单的文档改进提交，在贡献指南中补充了数组断言的推荐写法，帮助开发者在测试失败时获得更好的诊断信息，提升测试代码质量。
