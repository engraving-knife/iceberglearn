# 提交 2470：Core: Remove redundant V2TableTestBase (#13757)

## 提交信息

- **序号**：2470 / 4088
- **哈希**：6eeb92484f619a2e389b656accd7dc619fcadcd3
- **短哈希**：6eeb92484
- **日期**：2025-08-07 18:30:24 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Core: Remove redundant V2TableTestBase (#13757)
- **PR/Issue**：#13757

## 总体目的

该提交删除了冗余的测试基类 `V2TableTestBase`，清理测试代码中不再被使用的代码。

`V2TableTestBase` 是一个继承自 `TestBase` 的测试基类，其作用是通过 `@Parameters` 注解将测试的 `formatVersion` 固定为 2（V2 表格式）。然而该类已经没有任何地方引用使用，属于遗留的冗余代码。保留无用的测试基类会增加维护负担并造成代码库的混乱，因此将其删除以保持测试代码的整洁。

## 如何达成设计目的

直接删除 `V2TableTestBase.java` 文件。该文件仅包含一个继承 `TestBase` 的类，通过 `parameters()` 方法返回 `Arrays.asList(2)` 来限定 formatVersion 为 2。由于没有任何测试类继承或引用它，删除操作不会影响任何现有测试。

## 修改详情

### `core/src/test/java/org/apache/iceberg/V2TableTestBase.java` (+0/-29 lines)

**修改目的**：删除冗余的测试基类文件。

**工作逻辑**：该文件定义了 `V2TableTestBase` 类，继承 `TestBase`，并通过 `@Parameters(name = "formatVersion = {0}")` 注解的 `parameters()` 方法返回 `Arrays.asList(2)`，将表格式版本固定为 V2。由于该类无任何引用，整文件删除。

## 总结

这是一个简单的测试代码清理提交，删除了不再被使用的 `V2TableTestBase` 测试基类。该提交不涉及任何功能逻辑修改，仅减少了冗余代码，有助于保持测试代码库的整洁与可维护性。
