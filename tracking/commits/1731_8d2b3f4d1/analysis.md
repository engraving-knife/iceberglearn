# 提交 1731：Spark: Fix assertion checks (#12255)

## 提交信息

- **序号**：1731 / 4088
- **哈希**：8d2b3f4d122004a234fa87fab40fed6be5fbf9c0
- **短哈希**：8d2b3f4d1
- **日期**：2025-02-14 09:02:24 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Fix assertion checks (#12255)
- **PR/Issue**：#12255

## 总体目的

在 `TestRewritePositionDeleteFilesAction` 测试类的断言验证方法中，存在 AssertJ 断言方向反写的问题。原来的写法形如 `assertThat(expectedValue).isEqualTo(actualValue)`，即将期望值作为断言主体、实际值作为比较目标。这种写法虽然在功能上不影响测试是否通过（因为 `isEqualTo` 是对称的），但在测试失败时，AssertJ 生成的错误信息会产生误导——它会将期望值标记为 "actual"，而将真正的实际值标记为 "expected"，使得开发者难以快速定位问题。

本提交的目标是修正所有这些断言的方向，使其符合 AssertJ 的最佳实践：`assertThat(actualValue).isEqualTo(expectedValue)`，即把被测对象（来自 `result` 的实际结果）作为断言主体，把期望值作为比较目标。同时，将部分断言替换为更地道的 AssertJ API（如 `hasSize`）。

## 如何达成设计目的

提交修改了 `TestRewritePositionDeleteFilesAction` 中一个验证方法内的 8 组断言，共涉及 24 行代码的调整。修改策略统一且机械：

1. 将 `assertThat(expectedValue).isEqualTo(result.xxx())` 反转为 `assertThat(result.xxx()).isEqualTo(expectedValue)`。
2. 将 `assertThat(expectedGroups).isEqualTo(result.rewriteResults().size())` 替换为更地道的 `assertThat(result.rewriteResults()).hasSize(expectedGroups)`。
3. 对于涉及 Stream 操作的复杂表达式，调整 `assertThat` 的参数位置，使流式计算结果作为断言主体。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java`（修改, +24/-24 lines）

**修改目的**：修正断言方向，使测试失败时的错误信息更加清晰准确。

**工作逻辑**：修改了以下 8 组断言（每组 3 行，涉及 `assertThat` 主体和 `isEqualTo` 参数的互换）：

1. **重写的删除文件数**：`assertThat(rewrittenDeletes.size()).isEqualTo(result.rewrittenDeleteFilesCount())` 改为 `assertThat(result.rewrittenDeleteFilesCount()).isEqualTo(rewrittenDeletes.size())`。
2. **新增的删除文件数**：`assertThat(newDeletes.size()).isEqualTo(result.addedDeleteFilesCount())` 改为 `assertThat(result.addedDeleteFilesCount()).isEqualTo(newDeletes.size())`。
3. **重写的删除字节数**：`assertThat(size(rewrittenDeletes)).isEqualTo(result.rewrittenBytesCount())` 改为 `assertThat(result.rewrittenBytesCount()).isEqualTo(size(rewrittenDeletes))`。
4. **新增的删除字节数**：`assertThat(size(newDeletes)).isEqualTo(result.addedBytesCount())` 改为 `assertThat(result.addedBytesCount()).isEqualTo(size(newDeletes))`。
5. **重写组数**：`assertThat(expectedGroups).isEqualTo(result.rewriteResults().size())` 改为 `assertThat(result.rewriteResults()).hasSize(expectedGroups)`，使用更地道的 `hasSize` API。
6. **所有组中重写的删除文件数**：将 Stream 求和表达式从 `isEqualTo` 参数移至 `assertThat` 主体。
7. **所有组中新增的删除文件数**：同上调整。
8. **所有组中重写/新增的删除字节数**：将 `mapToLong` 求和表达式从 `isEqualTo` 参数移至 `assertThat` 主体。

## 小结

- **成效**：修正了 8 组断言的方向，使测试失败时 AssertJ 生成的错误信息能正确区分 "actual"（实际值）和 "expected"（期望值），提升了调试效率。同时引入了更地道的 `hasSize` API。
- **影响范围**：仅涉及 Spark 3.5 模块的一个测试文件，不影响任何生产代码或功能逻辑。
- **回迁到 1.4.x 的注意事项**：此提交为测试代码的改进，无功能影响，回迁风险极低。需确认 1.4.x 分支中该测试文件存在且断言结构一致。建议回迁以保持测试代码质量。
