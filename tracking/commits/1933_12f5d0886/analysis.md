# 提交 1933：AWS: Use assertThat instead of JUnit4 assertions (#12668)

## 提交信息

- **序号**：1933 / 4088
- **哈希**：12f5d0886f3aa1a1ffad25e96507485326974975
- **短哈希**：12f5d0886
- **日期**：2025-03-28 09:38:44 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：AWS: Use assertThat instead of JUnit4 assertions (#12668)
- **PR/Issue**：#12668

## 总体目的

这是一个测试代码风格统一的小型重构。Iceberg 项目整体倾向于使用 AssertJ（`assertThat`）的流式断言风格，而不是 JUnit4 的 `assertEquals`/`assertTrue` 等静态断言方法。AssertJ 提供了更可读的链式断言语法、更丰富的失败信息，且与 JUnit5 生态兼容性更好。

本提交针对 `TestS3FileIO` 中一处仍使用 JUnit4 断言的测试方法进行清理，将其改为 AssertJ 风格，使整个测试类的断言风格保持一致，并移除对 JUnit4 `org.junit.Assert.*` 静态导入的依赖。

## 如何达成设计目的

设计思路直接：将 JUnit4 的 `assertEquals(expected, actual)` 与 `assertTrue(condition)` 替换为等价的 AssertJ `assertThat(actual).hasSize(expected)` 与 `assertThat(collection).anyMatch(predicate)`，并删除对应的 JUnit4 静态导入。

## 修改详情

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIO.java` (修改, +13/-17 lines)

**修改目的**：将某测试方法中的 JUnit4 断言替换为 AssertJ 断言。

**工作逻辑**：
- 移除静态导入 `import static org.junit.Assert.assertEquals;` 与 `import static org.junit.Assert.assertTrue;`（保留已有的 `org.assertj.core.api.Assertions.assertThat` 与 `assertThatThrownBy`）。
- 将 `assertEquals(2, fileInfoList.size());` 改为 `assertThat(fileInfoList).hasSize(2);`。
- 将两处 `assertTrue(fileInfoList.stream().anyMatch(fi -> ...));` 改为 `assertThat(fileInfoList).anyMatch(fi -> ...);`，断言语义不变（验证列表中存在满足条件的元素），但使用了 AssertJ 的流式 API，失败信息更友好。

涉及的断言验证 `FileInfo` 列表包含两个文件（file1.txt 大小 1024 且创建时间在 120 秒内、file2.txt 大小 2048 且创建时间早于 30 秒前），用于测试 S3FileIO 的文件信息读取功能。

## 总结

本次提交为测试风格统一的小型重构，将 `TestS3FileIO` 中残留的 JUnit4 `assertEquals`/`assertTrue` 断言替换为 AssertJ 的 `assertThat().hasSize()`/`.anyMatch()` 风格，并移除 JUnit4 静态导入，使整个测试类的断言风格与项目约定一致。不涉及功能变更。
