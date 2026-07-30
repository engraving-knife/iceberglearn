# 提交 2353：Enforce that test classes start with "Test" (#13466)

## 提交信息

- **序号**：2353 / 4088
- **哈希**：6a6c5b8061ca67750159a674bd9e361fc8d8a3ca
- **短哈希**：6a6c5b806
- **日期**：2025-07-15 08:54:06 +0200
- **作者**：Yu-Chuan Hung
- **提交说明**：Enforce that test classes start with "Test" (#13466)
- **PR/Issue**：#13466

## 总体目的

这个提交通过 Checkstyle 规则强制要求测试类以 "Test" 前缀开头（而非 "Test" 后缀结尾），并将所有不符合该约定的测试类进行重命名。涉及约 59 个文件的改动。

背景：Iceberg 项目的测试类此前使用的是 `XxxTest` 后缀命名约定（如 `DataTest`、`RecordWrapperTest`），这是 Java 社区中常见的做法。然而，项目决定统一采用 `TestXxx` 前缀命名约定（如 `TestData`、`TestRecordWrapper`），这种约定在 JUnit 5 生态和一些大型项目中更流行，且在按字母排序时测试类会集中排列在一起，便于查找。

本提交包含两部分：一是新增 Checkstyle 规则检测以 "Test" 后缀结尾的类名并报错；二是对所有现有的违规测试类进行重命名。对于基类（非测试但被测试类继承的抽象类），如 `DataTest` 改为 `DataTestBase`，`RecordWrapperTest` 改为 `RecordWrapperTestBase`，以 `TestBase` 后缀标识基类。

## 如何达成设计目的

1. **新增 Checkstyle 规则**：在 `checkstyle.xml` 中新增 `RegexpSinglelineJava` 模块（id 为 `TestClassNamingConvention`），使用正则匹配以 `Test` 结尾的类声明，并给出提示信息。
2. **抑制主代码误报**：在 `checkstyle-suppressions.xml` 中对 `src/main/` 路径下的文件抑制该规则，避免主代码中合法的 `XxxTest` 类被误报。
3. **批量重命名测试类**：将所有以 `Test` 后缀结尾的测试类重命名为 `Test` 前缀开头，或对于基类使用 `TestBase` 后缀。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml` (+5/-0 lines)

**修改目的**：新增测试类命名约定检查规则。

**工作逻辑**：新增 `RegexpSinglelineJava` 模块，id 为 `TestClassNamingConvention`，正则为 `^\s*(public\s+)?(abstract\s+)?class\s+[A-Za-z0-9]*Test(\s|<)`，匹配类名以 "Test" 结尾的声明。报错消息提示应使用 `Test` 前缀而非后缀，如 `TestNewFeature` 而非 `NewFeatureTest`。

### `.baseline/checkstyle/checkstyle-suppressions.xml` (+3/-0 lines)

**修改目的**：对主代码抑制测试类命名规则。

**工作逻辑**：新增抑制规则 `<suppress files=".*[/\\]src[/\\]main[/\\].*" id="TestClassNamingConvention" />`，使该规则仅作用于测试代码（`src/test/`）。

### 测试类重命名（约 57 个文件，+96/-93 lines）

**修改目的**：将所有以 `Test` 后缀结尾的测试类重命名以符合新约定。

**工作逻辑**：重命名涵盖 core、data、flink、connect、parquet、spark 等多个模块。主要重命名模式包括：
- `XxxTest` → `TestXxx`：如 `TestSchemaParser`、`TestAvroEncoderUtil`、`TestGenericAvro`、`TestInternalAvro`、`TestGenericData`、`TestInternalParquet` 等。
- 基类 `XxxTest` → `XxxTestBase`：如 `DataTest` → `DataTestBase`、`RecordWrapperTest` → `RecordWrapperTestBase`、`AvroDataTest` → `AvroDataTestBase`、`ScanTest` → `ScanTestBase`。这些是抽象基类，被具体测试类继承，使用 `TestBase` 后缀避免被当作测试类执行。
- 集成测试重命名：如 `IntegrationTest` → `TestIntegration`、`RoundTrip` → `TestRoundTrip`、`IcebergConnectorSmoke` → `TestIcebergConnectorSmoke`。
- 由于跨 Spark 版本（3.3/3.4/3.5）的测试类存在重复，重命名在多个版本目录下同步进行。

## 总结

该提交通过 Checkstyle 规则强制推行测试类以 `Test` 前缀开头的命名约定，并批量重命名了约 57 个违规测试类（含基类改用 `TestBase` 后缀）。这是一次代码规范统一的重构，提升了测试代码的可发现性和一致性，并通过自动化规则确保后续新增测试类遵守该约定。
