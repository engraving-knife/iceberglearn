# 提交 0764：Spark 3.4: Add support for enums in SparkConfParser (#10330)

## 提交信息

- **序号**：0764 / 4088
- **哈希**：a6fb9cd2d3f6bd1d6a678795c86b00077846ee53
- **短哈希**：a6fb9cd2d
- **日期**：2024-05-14 11:00:17 -0700
- **作者**：Huaxin Gao
- **提交说明**：Spark 3.4: Add support for enums in SparkConfParser (#10330)
- **PR/Issue**：#10330

## 总体目的

本提交为 Spark 3.4 模块的 `SparkConfParser` 新增**枚举类型配置解析器（EnumConfParser）**，使配置解析器能直接将字符串配置值转换为枚举类型，而无需调用方先用字符串解析再手动调用 `Enum.fromName()` 转换。

此前的模式存在"两步式解析"问题：对于 `PlanningMode` 这类枚举配置，调用方需先用 `stringConf()...parse()` 取得字符串，再单独调用 `PlanningMode.fromName(modeName)` 转为枚举。这种模式将类型转换逻辑分散在调用方，且字符串解析阶段无法校验值是否合法（只有 `fromName` 调用时才抛 `IllegalArgumentException`）。新增 `EnumConfParser` 后，转换逻辑内聚到解析器中，调用方一行链式调用即可得到类型安全的枚举值，且校验在解析阶段即生效。

## 如何达成设计目的

### 设计逻辑：复用父类 parse(Function, T) 的转换机制

`SparkConfParser` 内部有一个抽象基类 `ConfParser<ThisT, T>`，它定义了核心的 `protected T parse(Function<String, T> conversion, T defaultValue)` 方法。该方法实现多源查找：依次尝试 options（数据源选项）→ sessionConf（SparkSession 配置）→ tableProperty（表属性）→ defaultValue（默认值），对找到的字符串值调用 `conversion.apply(value)` 转换为目标类型 `T`。

`StringConfParser`、`IntConfParser`、`LongConfParser`、`BooleanConfParser` 等既有解析器都基于此机制：它们在各自的 `parse()` 方法中传入对应的转换函数（如 `Integer::parseInt`、`Long::parseLong`）和默认值。

本提交新增的 `EnumConfParser<T extends Enum<T>>` 遵循完全相同的模式：它持有一个 `Function<String, T> toEnum` 转换器（如 `PlanningMode::fromName`），在 `parse()` 时将其传给父类的 `parse(toEnum, defaultValue)`。这样新增类型几乎没有重复代码，仅是声明一个泛型子类并桥接到父类转换机制。

### EnumConfParser 的 API 设计

`EnumConfParser` 提供两个 `defaultValue` 重载，体现灵活性：

1. **`defaultValue(T value)`**：直接传入枚举常量（如 `PlanningMode.AUTO`），用于调用方已有枚举实例的场景。
2. **`defaultValue(String value)`**：传入字符串（如 `TableProperties.PLANNING_MODE_DEFAULT`，值为 `"auto"`），内部通过 `toEnum.apply(value)` 转换为枚举。这对接 Iceberg 既有的字符串常量默认值（`TableProperties.PLANNING_MODE_DEFAULT` 是 String 常量），避免调用方手动转换。

两个 `parse` 方法体现"必需 vs 可选"语义：

1. **`parse()`**：要求 `defaultValue != null`，否则抛 `IllegalArgumentException("Default value cannot be null")`。用于必须有默认值的配置（如 `dataPlanningMode` 必须返回一个 `PlanningMode`）。
2. **`parseOptional()`**：不校验默认值，允许返回 null。用于可选配置（未配置且无默认时返回 null）。

### 应用示例：dataPlanningMode 重构

重构前后对比：

重构前（两步式）：
```java
String modeName = confParser.stringConf()
    .sessionConf(SparkSQLProperties.DATA_PLANNING_MODE)
    .tableProperty(TableProperties.DATA_PLANNING_MODE)
    .defaultValue(TableProperties.PLANNING_MODE_DEFAULT)
    .parse();
return PlanningMode.fromName(modeName);
```

重构后（一步式，类型安全）：
```java
return confParser.enumConf(PlanningMode::fromName)
    .sessionConf(SparkSQLProperties.DATA_PLANNING_MODE)
    .tableProperty(TableProperties.DATA_PLANNING_MODE)
    .defaultValue(TableProperties.PLANNING_MODE_DEFAULT)
    .parse();
```

改进点：
- `PlanningMode.fromName` 作为 `toEnum` 函数传入解析器，转换在解析阶段内完成。
- `defaultValue(TableProperties.PLANNING_MODE_DEFAULT)` 使用字符串重载，自动转换。
- 链式调用直接返回 `PlanningMode`，无需中间变量 `modeName`。
- 若配置值非法（如 `"foobar"`），`PlanningMode.fromName` 在解析阶段即抛异常，错误更早暴露。

### 注意：deletePlanningMode 未同步重构

本提交仅重构了 `dataPlanningMode()`，同文件中结构几乎相同的 `deletePlanningMode()` 仍保留旧的 stringConf + fromName 两步式写法。这是一个小范围重构，未覆盖所有可受益的场景，后续提交可能补全。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkConfParser.java`

**修改目的**：新增 `EnumConfParser` 内部类和 `enumConf` 工厂方法。

**工作逻辑**：
- 新增 public 工厂方法 `enumConf(Function<String, T> toEnum)`，返回 `new EnumConfParser<>(toEnum)`，泛型 `<T extends Enum<T>>`。新增 4 行。
- 新增内部类 `EnumConfParser<T extends Enum<T>> extends ConfParser<EnumConfParser<T>, T>`，共 33 行：
  - 字段 `private final Function<String, T> toEnum` 和 `private T defaultValue`。
  - 构造器 `EnumConfParser(Function<String, T> toEnum)` 赋值 toEnum。
  - `self()` 返回 this（满足父类抽象方法）。
  - `defaultValue(T value)` 设置枚举默认值。
  - `defaultValue(String value)` 设置字符串默认值，经 `toEnum.apply(value)` 转换。
  - `parse()` 校验默认值非 null 后调用 `parse(toEnum, defaultValue)`。
  - `parseOptional()` 直接调用 `parse(toEnum, defaultValue)`，允许 null 默认。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java`

**修改目的**：用 `EnumConfParser` 重构 `dataPlanningMode()` 方法。

**工作逻辑**：
- 将原"stringConf + PlanningMode.fromName"两步式写法替换为"enumConf(PlanningMode::fromName)"一步式写法。
- 链式调用保持 sessionConf/tableProperty/defaultValue 不变，但 defaultValue 现在走字符串重载（自动转换）。
- 返回类型仍为 `PlanningMode`，但无需中间字符串变量。
- 净减 5 行（14 行替换为 8 行，体现链式调用更简洁）。

## 小结

- **成效**：Spark 3.4 模块的 `SparkConfParser` 获得了枚举类型配置解析能力，新增的 `EnumConfParser` 复用父类多源查找与转换机制，代码内聚且类型安全。`dataPlanningMode()` 重构为一步式解析，更简洁，且非法值校验前移到解析阶段。新解析器可供后续其他枚举配置（如 `deletePlanningMode` 及未来枚举配置项）复用。
- **影响范围**：仅影响 Spark 3.4 模块。`SparkConfParser` 是 Spark 3.4 读写配置解析的基础组件，新增 `EnumConfParser` 是纯增量（不改动既有解析器行为），`dataPlanningMode()` 重构对外行为等价（解析逻辑不变，仅写法从两步变一步）。其他 Spark 版本模块（3.3、3.5）不受影响。
- **回迁注意事项**：
  1. 此提交针对 Spark 3.4 模块（`spark/v3.4/`），cherry-pick 到 1.4.x 分支需确认 1.4.x 的 Spark 3.4 模块结构与此提交前状态一致。
  2. `EnumConfParser` 依赖父类 `ConfParser.parse(Function<String, T>, T)` 方法，该方法在 1.4.x 分支的 Spark 3.4 模块中已存在（是 `SparkConfParser` 的既有抽象方法），无需额外改动。
  3. `PlanningMode` 枚举及其 `fromName` 方法位于 core 模块（`org.apache.iceberg.PlanningMode`），1.4.x 分支已有，回迁无依赖问题。
  4. 重构仅涉及 `dataPlanningMode()`，若 1.4.x 分支中该方法已被其他改动修改，cherry-pick 时可能在该方法处产生冲突，需手动合并。
  5. 注意 `deletePlanningMode()` 未被重构，回迁后该处仍为旧写法，这是预期行为（本提交范围限定）。若后续需要可单独重构。
  6. Spark 3.5 模块可能已有等价的 enumConf 支持（由其他提交添加），回迁本提交到 1.4.x 的 Spark 3.4 模块不会与 3.5 模块冲突（两者独立）。
