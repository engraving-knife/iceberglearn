# 提交 3776：Core: Add V4 location relativization utilities (#16174)

## 提交信息

- **序号**：3776 / 4088
- **哈希**：29ba0a14dc6667db0683fdfcd520639b3da77774
- **短哈希**：29ba0a14d
- **日期**：2026-05-22 21:16:44 -0700
- **作者**：Anoop Johnson
- **提交说明**：Core: Add V4 location relativization utilities (#16174)
- **PR/Issue**：#16174

## 总体目的

这个提交为 Iceberg V4 规范的相对路径功能（见第 3775 号提交的规范定义）提供 Java 实现工具方法。在 `LocationUtil` 中新增了路径解析（resolve）和相对化（relativize）方法，用于在绝对路径和相对路径之间进行转换。

V4 规范允许元数据中的路径使用相对路径（相对于表位置），这需要：
1. **resolveLocation**：读取时将相对路径解析为绝对路径（表位置 + `/` + 相对路径）。
2. **relativizeLocation**：写入时将绝对路径转换为相对路径（去除表位置前缀）。
3. **hasScheme**：判断路径是否包含 URI scheme 以区分绝对/相对路径。

这些工具方法是 V4 相对路径功能的基础设施，后续的元数据读写代码将使用它们。

## 如何达成设计目的

在 `LocationUtil` 类中新增三个方法：
1. `hasScheme(String location)`：按 RFC 3986 Section 3.1 规范检查路径是否包含 URI scheme。
2. `resolveLocation(String tableLocation, String location)`：如果路径有 scheme 则直接返回，否则拼接表位置和相对路径。
3. `relativizeLocation(String tableLocation, String location)`：如果路径以表位置 + `/` 开头则去除前缀返回相对路径，否则原样返回。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/LocationUtil.java` (+74/-2 lines)

**修改目的**：添加 V4 相对路径的解析和相对化工具方法。

**工作逻辑**：

1. **`PATH_SEPARATOR` 常量**：定义为 `"/"`，替换原硬编码的 `/`。

2. **`hasScheme(String location)`**（私有方法）：
   按 RFC 3986 Section 3.1 的 `scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )` 规范，遍历路径字符：
   - 遇到 `:` 且位置 > 0，返回 true（有 scheme）。
   - 遇到不符合 scheme 字符规则的字符，返回 false。
   - `isSchemeChar` 辅助方法检查：首字符必须是字母（ALPHA），后续字符可以是字母、数字、`+`、`-`、`.`。

3. **`resolveLocation(String tableLocation, String location)`**：
   ```java
   if (hasScheme(location)) {
     return location;  // 绝对路径直接返回
   }
   return tableLocation + PATH_SEPARATOR + location;  // 相对路径拼接
   ```
   无条件添加 `/` 分隔符，调用者需确保 tableLocation 不以 `/` 结尾且 location 不以 `/` 开头。

4. **`relativizeLocation(String tableLocation, String location)`**：
   ```java
   int prefixLength = tableLocation.length();
   if (location.length() > prefixLength
       && location.startsWith(PATH_SEPARATOR, prefixLength)
       && location.startsWith(tableLocation)) {
     return location.substring(prefixLength + PATH_SEPARATOR.length());
   }
   return location;
   ```
   检查路径是否以 `tableLocation + "/"` 开头，是则去除前缀返回相对路径。关键检查 `location.length() > prefixLength` 确保路径不等于表位置本身，`startsWith(PATH_SEPARATOR, prefixLength)` 确保前缀后紧跟分隔符（避免 `table` 和 `table_v2` 的误匹配）。

### `core/src/test/java/org/apache/iceberg/util/TestLocationUtil.java` (+208/-0 lines)

**修改目的**：全面测试新增的路径解析和相对化方法。

**工作逻辑**：包含 14 个测试方法覆盖各种场景：
- `testResolveRelativeLocations`：相对路径解析。
- `testResolveLocationsWithColonsInSegments`：路径段中包含冒号的解析（如 `partition=key:value`）。
- `testResolveAbsoluteLocationsUnchanged`：绝对路径直接返回。
- `testRelativize`：基本相对化。
- `testRelativizeLocationNotUnderTableLocation`：不在表位置下的路径不相对化。
- `testRelativizeLocationWithSharedPrefix`：共享前缀但不是子路径的路径不相对化（如 `table` vs `table_v2`）。
- `testRelativizeLocationEqualToTableLocation`：等于表位置的路径不相对化。
- `testRelativizeMismatchedFileSchemeNotRelativized`：`file:` 和 `file:///` 混用不相对化。
- `testResolveAbsoluteLocationWithNonAlphanumericScheme`：含 `+` 的 scheme（如 `git+ssh://`）。
- `testResolveTreatsNonAsciiSchemeAsRelative`：非 ASCII 字符（如希腊字母）被视为相对路径。
- `testResolveTreatsNonAlphaLeadingCharAsRelative`：以数字或 `+`/`-`/`.` 开头的路径被视为相对。
- `testRelativizeResolveRoundTrip`：相对化和解析的往返测试（S3、file、HDFS）。
- `testResolveWithTrailingOrLeadingSlashProducesDuplicateSeparator`：尾部/首部 `/` 导致重复分隔符。
- `testRelativizeWithTrailingSlashTableLocationNotRelativized`：表位置尾部 `/` 阻止相对化。

## 总结

这个提交实现了 V4 规范中相对路径功能的核心工具方法，为后续 V4 元数据读写中使用相对路径提供了基础。`resolveLocation` 和 `relativizeLocation` 方法严格按照规范定义的规则实现，并通过全面的测试覆盖了各种边界情况，包括 URI scheme 识别、共享前缀、往返一致性等。这是 V4 相对路径功能从规范到实现的关键一步。
