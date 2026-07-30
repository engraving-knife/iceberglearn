# 提交 2143：API: Reorder as() calls in Tests

## 提交信息

- **序号**：2143 / 4088
- **哈希**：1b30216307a73793528a56623919e5b7ec8a29bf
- **短哈希**：1b3021630
- **日期**：2025-05-19 01:17:58 -0500
- **作者**：Russell Spitzer
- **提交说明**：API: Reorder as() calls in Tests (#13083)
- **PR/Issue**：#13083

## 总体目的

这个提交调整了 TestIcebergBuild 测试中 AssertJ 断言的 `as()` 调用顺序。在 AssertJ 中，`as()` 方法用于设置断言失败时的描述信息。根据 AssertJ 的最佳实践，`as()` 应该在断言链中尽早调用（在 `isEqualTo()` 等断言方法之前），这样当断言失败时，错误信息能正确显示。原来的代码将 `as()` 放在了 `isEqualTo()` 之后，虽然不会导致功能错误，但不符合 AssertJ 的推荐用法，且可能导致断言失败时的描述信息不正确显示。这个提交修正了两个测试方法中的调用顺序。

## 如何达成设计目的

1. 将两处断言中 `.isEqualTo(...)` 和 `.as(...)` 的调用顺序调整为 `.as(...)` 在前、`.isEqualTo(...)` 在后。

## 修改详情

### `api/src/test/java/org/apache/iceberg/TestIcebergBuild.java` (修改, +4/-4 lines)

**修改目的**：修正 AssertJ 断言中 as() 的调用顺序。

**工作逻辑**：在两个测试方法中，将原来 `assertThat(IcebergBuild.version()).isEqualTo(value).as("description")` 的顺序调整为 `assertThat(IcebergBuild.version()).as("description").isEqualTo(value)`。这涉及 testVersionMatchesSystemProperty 和另一个版本文件匹配测试方法，每处修改 2 行（调换两行顺序）。

## 总结

这是一个小型的测试代码质量改进提交，修正了 AssertJ 断言中 `as()` 描述方法的调用顺序，使其符合最佳实践。虽然不影响功能正确性，但确保了断言失败时错误信息能被正确显示。
