# 提交 0793：Url encode field names for partition paths (#10329)

## 提交信息
- **序号**：0793 / 4088
- **哈希**：795fea9446723cf9c2dbc3cac07883cace8997c9
- **短哈希**：795fea944
- **日期**：2024-05-27 14:52:29 -0700
- **作者**：Daniel Weeks <dweeks@apache.org>
- **提交说明**：Url encode field names for partition paths (#10329)
- **PR/Issue**：#10329

## 总体目的

本提交修复了分区路径中字段名未做 URL 编码的缺陷。Iceberg 在将分区数据转换为物理路径时，会生成形如 `fieldName=fieldValue/fieldName2=fieldValue2` 的路径片段。此前仅对分区值（`fieldValue`）调用 `escape()` 进行 URL 编码，而字段名（`fieldName`）被原样拼入路径。

当字段名包含 `#`、`"`、`/`、空格等特殊字符时（这在实际业务中并不罕见，例如字段名 `data#1`），原样拼接会导致分区路径中出现非法或歧义字符：轻则路径解析异常，重则在对象存储上产生不可预期的目录结构。本提交将字段名也纳入 `escape()` 处理，确保整个 `name=value` 路径片段中的两部分都经过合法的 URL 编码。

## 如何达成设计目的

修改集中在 `PartitionSpec.partitionToPath()` 方法中的一行：将 `sb.append(field.name())` 改为 `sb.append(escape(field.name()))`。`escape()` 是该类已有的私有方法，内部调用 `URLEncoder.encode(string, "UTF-8")`，能将 `#` 编码为 `%23`、`"` 编码为 `%22`、`/` 编码为 `%2F` 等。

这一改动复用了既有的转义机制，保持了对称性——`name` 和 `value` 采用相同的编码规则，使路径片段的构造逻辑更加一致和可预测。由于 `escape()` 对纯字母数字字段名是幂等无副作用的（ASCII 字母数字不会被 URL 编码改变），因此对常规字段名不会产生任何行为变化，属于向后兼容的修复。

测试方面，提交者在 `TestPartitionPaths` 中新增 `testEscapedFieldNames` 用例，直接验证含 `"` 和 `#` 的字段名编码后为 `%22esc%22%231`；在 `TestLocationProvider` 中新增 `testEncodedFieldNameInPartitionPath`，端到端验证对象存储模式下含特殊字符字段名生成的数据文件路径中分区片段为 `data%231=val%231`。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionSpec.java`
**修改目的**：对分区路径中的字段名进行 URL 编码。
**工作逻辑**：
- `partitionToPath()` 方法遍历分区字段构建路径字符串。原代码 `sb.append(field.name()).append("=").append(escape(valueString))` 仅对值编码。
- 改为 `sb.append(escape(field.name())).append("=").append(escape(valueString))`，使字段名也经过 `URLEncoder.encode(..., "UTF-8")` 处理。
- 对于常规字段名（如 `data`、`ts`）编码后无变化；对于含特殊字符的字段名（如 `data#1`）则编码为 `data%231`。

### `api/src/test/java/org/apache/iceberg/TestPartitionPaths.java`
**修改目的**：补充对含特殊字符字段名的分区路径编码测试。
**工作逻辑**：
- 在测试 SCHEMA 中新增字段 `Types.NestedField.optional(4, "\"esc\"#1", Types.StringType.get())`，字段名含双引号和井号。
- 新增 `testEscapedFieldNames` 测试：以该字段做 identity 分区，验证 `partitionToPath` 输出为 `%22esc%22%231=a%2Fb%2Fc%2Fd`，即字段名中 `"` → `%22`、`#` → `%23`，值中 `/` → `%2F`。

### `core/src/test/java/org/apache/iceberg/TestLocationProvider.java`
**修改目的**：端到端验证含特殊字符字段名在对象存储模式下的分区路径。
**工作逻辑**：
- 新增 `testEncodedFieldNameInPartitionPath` 测试：开启 `OBJECT_STORE_ENABLED`，向表添加字符串字段 `data#1` 并以其做分区。
- 构造分区数据 `CustomRow.of(0, "val#1")`，调用 `locationProvider().newDataLocation(...)` 生成数据文件位置。
- 断言路径中倒数第二段（分区片段）为 `data%231=val%231`，验证字段名与值中的 `#` 均被编码为 `%23`。

## 小结
- **成效**：修复了字段名含特殊字符时分区路径非法的问题，使 `name=value` 路径片段的编码逻辑对称一致。
- **影响范围**：影响所有通过 `PartitionSpec.partitionToPath()` 生成分区路径的场景（含 LocationProvider、数据文件写入路径计算等）。对常规字段名无行为变化，仅对含特殊字符的字段名产生编码效果。
- **回迁注意事项**：1.4.x 回迁为单行核心修改，风险低。回迁时需同步带上两个测试用例以验证。需注意 1.4.x 中若已有依赖原始未编码字段名路径的下游逻辑（如自定义 LocationProvider 或路径解析工具），编码后可能改变既有路径结构，需评估兼容性。
