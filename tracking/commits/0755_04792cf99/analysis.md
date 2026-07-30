# 提交 0755：Spark 3.5: Remove obsolete conf parsing logic (#10309)

## 提交信息

- **序号**：0755 / 4088
- **哈希**：04792cf991608d836a1ce60651bf65d1b7f67120
- **短哈希**：04792cf99
- **日期**：2024-05-11 09:55:09 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.5: Remove obsolete conf parsing logic (#10309)
- **PR/Issue**：#10309

## 总体目的

本提交移除 Spark 3.5 模块中 `SparkConfParser` 已过时（obsolete）的配置解析逻辑。被移除的逻辑是一段针对 Spark 2 时代 `DataSourceOptions.asMap()` 返回小写键 Map 的兼容性处理：在解析配置选项（options）时，将选项名转换为小写后再到 options Map 中查找。这段逻辑在 Spark 3.5 环境下已无必要，因为 Spark 3.5 使用 `CaseInsensitiveStringMap` 来传递数据源选项，其 `get` 方法本身就是大小写不敏感的，无需调用方手动做小写转换。保留这段过时逻辑不仅多余，还会引入不必要的 `Locale` 依赖，并使解析路径与 Spark 3.5 的实际数据结构不一致。本提交将 options 的类型从普通 `Map<String, String>` 改为 Spark 3.5 的 `CaseInsensitiveStringMap`，移除小写转换，从而简化解析逻辑并使其与 Spark 3.5 的约定对齐。

## 如何达成设计目的

### 移除原因详述

`SparkConfParser` 是 Iceberg Spark 模块中负责从多个来源（表属性 table.properties、SparkSession 配置 sessionConf、数据源选项 options）解析配置项的核心类。在解析 options 来源时，原代码有一段特殊处理：

```java
if (!optionNames.isEmpty()) {
  for (String optionName : optionNames) {
    // use lower case comparison as DataSourceOptions.asMap() in Spark 2 returns a lower case map
    String optionValue = options.get(optionName.toLowerCase(Locale.ROOT));
    if (optionValue != null) {
      return conversion.apply(optionValue);
    }
  }
}
```

这段注释明确说明：之所以做 `toLowerCase(Locale.ROOT)`，是因为 Spark 2 的 `DataSourceOptions.asMap()` 返回的是一个键全小写的 Map，因此查找时也需用小写键。然而，Spark 3.5 早已不再使用 `DataSourceOptions`，而是使用 `CaseInsensitiveStringMap`（来自 `org.apache.spark.sql.util`）。`CaseInsensitiveStringMap` 的 `get` 方法内部已实现大小写不敏感查找（基于 `toLowerCase`），因此调用方无需再做小写转换。

此外，原代码将 options 字段声明为 `Map<String, String>`，而实际传入的往往是 `CaseInsensitiveStringMap`。这种类型不精确导致：一方面无法享受 `CaseInsensitiveStringMap` 的大小写不敏感特性（所以手动做了小写转换作为补偿），另一方面解析逻辑与 Spark 3.5 的实际数据结构脱节。

本提交的修复方式是：将 options 字段类型直接改为 `CaseInsensitiveStringMap`，并在构造时通过 `asCaseInsensitiveStringMap` 辅助方法将传入的 `Map` 转换为 `CaseInsensitiveStringMap`（若传入的已是该类型则直接强转，避免包装开销）。这样，解析时直接 `options.get(optionName)` 即可，`CaseInsensitiveStringMap` 内部保证大小写不敏感，无需手动小写转换。

### 行为等价性

由于 `CaseInsensitiveStringMap.get` 本身就是大小写不敏感的，而原代码的 `toLowerCase(Locale.ROOT)` 也是为了实现大小写不敏感查找，因此修改前后对外行为等价——都能用任意大小写的选项名查到对应的值。区别仅在于实现方式从"调用方手动小写"变为"数据结构内置不敏感"，更简洁且与 Spark 3.5 约定一致。

### 测试验证

新增测试 `testOptionCaseInsensitive`，构造选项 `{"option": "value"}`，用大写混合的 `"oPtIoN"` 作为选项名解析，验证能取到 `"value"`，从而确认大小写不敏感行为在移除手动小写转换后依然成立。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkConfParser.java`

**修改目的**：将 options 类型改为 `CaseInsensitiveStringMap`，移除过时的小写转换逻辑。

**工作逻辑**：

1. **import 调整**：移除 `java.util.Locale`（不再需要小写转换），新增 `org.apache.spark.sql.util.CaseInsensitiveStringMap`。

2. **字段类型变更**：`options` 字段从 `Map<String, String>` 改为 `CaseInsensitiveStringMap`。

3. **无参构造函数**：`this.options = ImmutableMap.of()` 改为 `this.options = CaseInsensitiveStringMap.empty()`。

4. **三参构造函数**：`this.options = options` 改为 `this.options = asCaseInsensitiveStringMap(options)`。

5. **新增私有静态方法 `asCaseInsensitiveStringMap(Map<String, String> map)`**：
   - 若传入的 map 已是 `CaseInsensitiveStringMap` 实例，直接强转返回（避免重复包装）。
   - 否则用 `new CaseInsensitiveStringMap(map)` 包装。这一设计使得当上游已传入 `CaseInsensitiveStringMap` 时不会产生额外开销。

6. **`parse` 方法中的 options 查找逻辑简化**：
   - 移除 `if (!optionNames.isEmpty())` 外层判断（直接遍历，空列表自然不进入循环）。
   - 移除 `optionName.toLowerCase(Locale.ROOT)` 转换，改为 `options.get(optionName)`，依赖 `CaseInsensitiveStringMap` 的大小写不敏感特性。
   - 移除相关注释。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java`

**修改目的**：移除不再使用的 `readOptions` 字段。

**工作逻辑**：`SparkReadConf` 原本持有 `private final Map<String, String> readOptions` 字段，在构造函数中赋值。但该字段在类中未被任何方法使用（readOptions 仅传给 `SparkConfParser` 构造函数后即不再需要）。本提交移除该字段声明与构造函数中的赋值行，消除无用字段。注意 `readOptions` 参数本身仍保留在构造函数签名中并传给 `SparkConfParser`，只是不再单独存储。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java`

**修改目的**：验证移除小写转换后大小写不敏感行为依然成立。

**工作逻辑**：新增测试 `testOptionCaseInsensitive`：
- 构造 `options = ImmutableMap.of("option", "value")`。
- 用 `new SparkConfParser(spark, table, options)` 创建解析器。
- 调用 `parser.stringConf().option("oPtIoN").parseOptional()`，用大小写混合的选项名查找。
- 断言结果为 `"value"`，确认 `CaseInsensitiveStringMap` 的大小写不敏感查找生效。

## 小结

- **成效**：移除了 Spark 3.5 模块中针对 Spark 2 时代的过时小写转换兼容逻辑，使 `SparkConfParser` 的 options 解析直接使用 Spark 3.5 的 `CaseInsensitiveStringMap`，代码更简洁、与 Spark 3.5 约定一致，并减少了不必要的 `Locale` 依赖。同时清理了 `SparkReadConf` 中未使用的 `readOptions` 字段。行为上保持大小写不敏感的等价性，由测试覆盖验证。
- **影响范围**：仅影响 Spark 3.5 模块的配置解析路径。`SparkConfParser` 是 Spark 3.5 读写配置解析的基础组件，但改动前后对外行为等价（大小写不敏感查找语义不变），对用户无感知。其他 Spark 版本模块（3.3、3.4）不受影响。
- **回迁注意事项**：
  1. 此提交针对 Spark 3.5 模块（`spark/v3.5/`），回迁到 1.4.x 分支时需确认 1.4.x 分支的 Spark 3.5 模块结构与此提交前状态一致。
  2. `CaseInsensitiveStringMap` 是 Spark 3.x 标准 API，1.4.x 分支的 Spark 3.5 依赖中必然存在，无需额外引入依赖。
  3. 若 1.4.x 分支的 `SparkConfParser` 已有其他修改（例如对 optionNames 处理逻辑的调整），cherry-pick 时可能在 `parse` 方法处产生冲突，需手动合并。
  4. `SparkReadConf` 中 `readOptions` 字段的移除是独立的小清理，若 1.4.x 分支中该字段被其他代码引用，需一并处理引用点。
  5. 新增测试 `testOptionCaseInsensitive` 依赖 `TestSparkWriteConf` 的测试基础设施（`validationCatalog`、`tableIdent`、`spark`），回迁时需确认这些测试基类成员在 1.4.x 中可用。
