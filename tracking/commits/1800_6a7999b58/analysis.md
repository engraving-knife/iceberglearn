# 提交 1800：API: Fix IndexOutOfBounds exception in FileFormat#fromFileName (#12301)

## 提交信息

- **序号**：1800 / 4088
- **哈希**：6a7999b58e9f8eae0f86569f3dcd4f57ec557996
- **短哈希**：6a7999b58
- **日期**：2025-02-28 11:29:53 +0100
- **作者**：Willi Raschkowski
- **提交说明**：API: Fix IndexOutOfBounds exception in FileFormat#fromFileName (#12301)
- **PR/Issue**：#12301

## 总体目的

`FileFormat.fromFileName(CharSequence filename)` 方法用于根据文件名后缀判断文件格式（PARQUET、AVRO、ORC、PUFFIN、METADATA）。原实现在遍历各格式时，直接用 `filename.length() - format.ext.length()` 计算后缀起始位置，然后调用 `filename.subSequence(extStart, filename.length())` 取后缀进行比较。

这存在两个缺陷：
1. 当 `filename` 为 `null` 时会抛出 `NullPointerException`。
2. 当文件名长度小于扩展名长度（如文件名为空串 `""`、单字符 `"a"`、或无扩展名的短文件名）时，`extStart` 为负数，`subSequence` 会抛出 `IndexOutOfBoundsException`。

这些异常会在上游调用方（如读取分区统计文件时根据文件名判断格式）意外暴露，导致非预期的失败。本提交修复这两个缺陷，使方法在 `null` 或过短文件名时安全返回 `null`（表示无法识别格式），同时补充完整的单元测试覆盖各种边界场景。

## 如何达成设计目的

1. 在 `fromFileName` 方法开头增加 `null` 检查，`filename` 为 `null` 时直接返回 `null`。
2. 在比较后缀前增加 `extStart > 0` 的判断，确保只有当文件名长度严格大于扩展名长度时才取后缀比较，避免负数索引。
3. 新增 `TestFileFormat` 测试类，使用参数化测试覆盖正常文件名、短文件名、不支持的格式、无格式、空串、null 等场景。

## 修改详情

### `api/src/main/java/org/apache/iceberg/FileFormat.java`（修改, +8 -3 lines）

**修改目的**：修复 `fromFileName` 方法的空指针和越界异常。

**工作逻辑**：
- 方法开头新增 `if (filename == null) { return null; }`，处理 null 输入。
- 在循环中，将原来的条件从直接比较改为先判断 `extStart > 0`，再进行比较：
  ```java
  int extStart = filename.length() - format.ext.length();
  if (extStart > 0
      && Comparators.charSequences()
              .compare(format.ext, filename.subSequence(extStart, filename.length()))
          == 0) {
    return format;
  }
  ```
  这样当文件名长度不大于扩展名长度时（`extStart <= 0`），跳过比较，避免 `IndexOutOfBoundsException`。注意此处用 `> 0` 而非 `>= 0`，意味着文件名长度必须严格大于扩展名长度（即至少有一个字符在扩展名之前），这与“文件名.扩展名”的语义一致。

### `api/src/test/java/org/apache/iceberg/TestFileFormat.java`（新增, +67 lines）

**修改目的**：为 `FileFormat.fromFileName` 提供完整的单元测试覆盖。

**工作逻辑**：
- 定义二维数组 `FILE_NAMES` 作为参数源，每行为 `{文件名, 期望格式}`，覆盖：
  - 带格式的文件名（如 `file.puffin`、`dir/file.orc`、`file.parquet`、`file.avro`、`v1.metadata.json` 及带目录前缀的变体）。
  - 短文件名（如 `x.puffin`、`x.orc` 等，文件名主体仅一个字符）。
  - 不支持的格式（如 `file.csv`、`dir/file.csv`，期望 `null`）。
  - 无格式的文件名（如 `file`、`dir`，期望 `null`）。
  - 空白字符串（如 `""`、`" "`，期望 `null`）。
  - `null` 输入（期望 `null`）。
- 使用 JUnit 5 的 `@ParameterizedTest` + `@FieldSource` 对每组参数执行 `fromFileName` 并断言结果等于期望值。

## 小结

- **成效**：修复了 `FileFormat.fromFileName` 在 null 和过短文件名输入下的异常，使方法安全返回 null；并补充了全面的参数化单元测试。
- **影响范围**：涉及 `api` 模块的核心工具类 `FileFormat`，影响所有通过文件名判断格式的调用路径。改动向后兼容（原本会抛异常的输入现在返回 null）。
- **回迁到 1.4.x 的注意事项**：建议回迁。这是一个 bug 修复，1.4.x 分支同样存在该缺陷。回迁无前置依赖，改动小且安全。需确认 1.4.x 上 `FileFormat` 类的代码与 main 分支一致（扩展名列表等），若一致则可直接应用相同的修改和测试。
