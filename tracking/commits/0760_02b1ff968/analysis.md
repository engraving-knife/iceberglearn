# 提交 0760：Spark 3.5: Add support for enums in SparkConfParser (#10311)

## 提交信息

- **序号**：0760 / 4088
- **哈希**：02b1ff968d2a4bbe299dc2668ef9cda493058db7
- **短哈希**：02b1ff968
- **日期**：2024-05-13 14:42:01 -0700
- **作者**：Huaxin Gao
- **提交说明**：Spark 3.5: Add support for enums in SparkConfParser (#10311)
- **PR/Issue**：#10311

## 总体目的

本提交为 Iceberg Spark 3.5 模块的配置解析器 `SparkConfParser` 新增对枚举类型（`Enum`）配置项的一等公民支持。在此之前，`SparkConfParser` 已提供 `stringConf()`、`intConf()`、`longConf()`、`booleanConf()`、`durationConf()` 等类型的解析器，但没有专门的枚举解析器。需要解析枚举类型配置（如 `PlanningMode`）时，调用方只能先用 `stringConf()` 解析出字符串，再在外部手动调用枚举的 `fromName` 方法转换，既冗长又容易遗漏校验。本提交新增 `EnumConfParser<T extends Enum<T>>` 解析器，把"字符串 → 枚举"的转换逻辑内聚到解析器内部，调用方只需 `confParser.enumConf(PlanningMode::fromName).sessionConf(...).tableProperty(...).defaultValue(...).parse()` 即可直接得到枚举值，代码更简洁、类型更安全，并复用 `ConfParser` 基类已有的多来源解析（sessionConf、tableProperty、option、sparkRuntimeConfig）能力。同时把 `SparkReadConf` 中 `dataPlanningMode()` 方法从"`stringConf` + 手动 `PlanningMode.fromName`"重构为直接使用 `enumConf`，作为首个使用案例。

## 如何达成设计目的

### 设计思路：解析器模式与泛型自引用

`SparkConfParser` 采用泛型自引用（self-referential generic）模式：抽象基类 `ConfParser<ThisT, T>` 中 `ThisT` 是解析器自身的具体类型（用于链式调用返回正确类型），`T` 是解析值的类型。每个具体解析器（如 `StringConfParser`、`IntConfParser`）继承 `ConfParser` 并把 `ThisT` 绑定为自身，通过 `protected abstract ThisT self()` 方法返回 `this`，从而让 `sessionConf(...)`、`tableProperty(...)`、`option(...)` 等链式方法返回的是具体解析器类型而非基类，保证链式调用不丢失类型信息。`EnumConfParser<T extends Enum<T>>` 遵循同一模式，绑定 `ThisT = EnumConfParser<T>`，`T` 为枚举类型。

### 枚举解析器的核心逻辑

`EnumConfParser` 持有一个 `Function<String, T> toEnum` 转换函数（由调用方传入，通常是枚举的 `fromName` 方法引用），以及一个 `T defaultValue` 默认值。它复用基类 `ConfParser` 的 `parse(Function<String, T>, T defaultValue)` 模板方法完成实际的多来源解析——基类的 `parse` 会按优先级依次查找 sessionConf、table property、option、spark runtime config，找到非空值后用转换函数转为目标类型，找不到则用默认值。`EnumConfParser` 只需把 `toEnum` 与 `defaultValue` 传给基类 `parse` 即可，无需重写多来源查找逻辑。这体现了模板方法模式的优势：新增类型只需提供"字符串→类型"的转换函数与默认值，解析流程由基类统一维护。

### 两个 defaultValue 重载的设计

`EnumConfParser` 提供了两个 `defaultValue` 重载：
- `defaultValue(T value)`：直接接受枚举常量，用于调用方已有枚举实例的场景。
- `defaultValue(String value)`：接受字符串，内部调用 `toEnum.apply(value)` 转换为枚举。这一重载用于默认值以字符串常量形式存在的场景（如 `TableProperties.PLANNING_MODE_DEFAULT` 是字符串 `"%"` 之类的值），避免调用方手动转换。两个重载都返回 `self()` 以支持链式调用。

`parse()` 方法要求 `defaultValue != null`（通过 `Preconditions.checkArgument` 校验），这与 `SparkReadConf.dataPlanningMode()` 的语义一致——规划模式必须有默认值。`parseOptional()` 则不强制校验，允许返回 null（用于可选的枚举配置）。

### 首个使用案例：PlanningMode

`PlanningMode` 是 Iceberg 中表示数据/删除文件规划模式（如 `local`、`distributed`）的枚举，提供静态工厂 `fromName(String)` 把字符串转为枚举常量。重构前，`SparkReadConf.dataPlanningMode()` 的实现是：

```java
String modeName = confParser.stringConf()
    .sessionConf(SparkSQLProperties.DATA_PLANNING_MODE)
    .tableProperty(TableProperties.DATA_PLANNING_MODE)
    .defaultValue(TableProperties.PLANNING_MODE_DEFAULT)
    .parse();
return PlanningMode.fromName(modeName);
```

即先用 `stringConf` 解析出字符串，再调用 `PlanningMode.fromName` 转换。重构后：

```java
return confParser.enumConf(PlanningMode::fromName)
    .sessionConf(SparkSQLProperties.DATA_PLANNING_MODE)
    .tableProperty(TableProperties.DATA_PLANNING_MODE)
    .defaultValue(TableProperties.PLANNING_MODE_DEFAULT)
    .parse();
```

转换逻辑内聚到解析器，调用方代码更简洁，且 `defaultValue(TableProperties.PLANNING_MODE_DEFAULT)` 用的是字符串重载（`PLANNING_MODE_DEFAULT` 是字符串常量），内部由 `toEnum` 转换为枚举。注意 `deletePlanningMode()` 方法本提交未改动（diff 中未出现），仅重构了 `dataPlanningMode()`。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkConfParser.java`

**修改目的**：新增 `EnumConfParser` 解析器与对应的工厂方法 `enumConf`。

**工作逻辑**：共 2 处改动：

1. **新增工厂方法 `enumConf`**（约第 73 行）：在已有的 `durationConf()` 工厂方法之后，新增：

   ```java
   public <T extends Enum<T>> EnumConfParser<T> enumConf(Function<String, T> toEnum) {
     return new EnumConfParser<>(toEnum);
   }
   ```

   方法是泛型的（`<T extends Enum<T>>`），接受 `Function<String, T>` 转换函数，返回 `EnumConfParser<T>`。`<T extends Enum<T>>` 约束确保只能用于枚举类型。

2. **新增 `EnumConfParser` 内部类**（约第 209 行，在 `DurationConfParser` 之后、抽象基类 `ConfParser` 之前）：

   ```java
   class EnumConfParser<T extends Enum<T>> extends ConfParser<EnumConfParser<T>, T> {
     private final Function<String, T> toEnum;
     private T defaultValue;

     EnumConfParser(Function<String, T> toEnum) {
       this.toEnum = toEnum;
     }

     @Override
     protected EnumConfParser<T> self() {
       return this;
     }

     public EnumConfParser<T> defaultValue(T value) {
       this.defaultValue = value;
       return self();
     }

     public EnumConfParser<T> defaultValue(String value) {
       this.defaultValue = toEnum.apply(value);
       return self();
     }

     public T parse() {
       Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
       return parse(toEnum, defaultValue);
     }

     public T parseOptional() {
       return parse(toEnum, defaultValue);
     }
   }
   ```

   - 继承 `ConfParser<EnumConfParser<T>, T>`，绑定自引用类型为 `EnumConfParser<T>`，值类型为 `T`。
   - `toEnum` 字段为 `final`，构造时赋值。
   - `defaultValue` 字段非 final（由 `defaultValue(...)` 链式方法设置）。
   - `self()` 返回 `this`，满足基类抽象方法契约。
   - 两个 `defaultValue` 重载：枚举重载直接赋值，字符串重载通过 `toEnum` 转换后赋值。
   - `parse()` 强制默认值非空，调用基类 `parse(toEnum, defaultValue)`。
   - `parseOptional()` 不强制校验，同样调用基类 `parse`（默认值可为 null，由基类处理"未找到则返回默认值"逻辑）。

   注意 `EnumConfParser` 是非静态内部类（声明为 `class EnumConfParser`，无 `static`），因此可以访问外部类 `SparkConfParser` 的实例状态，并由 `enumConf` 工厂方法创建。这与既有的 `StringConfParser`、`IntConfParser` 等内部类的声明方式一致。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java`

**修改目的**：将 `dataPlanningMode()` 方法重构为使用新增的 `enumConf` 解析器。

**工作逻辑**：修改 `dataPlanningMode()` 方法（约第 300 行），从"`stringConf` + 手动 `PlanningMode.fromName`"改为直接用 `enumConf`：

- 旧实现（约 8 行）：先用 `stringConf()` 解析字符串，再调用 `PlanningMode.fromName(modeName)` 转换。
- 新实现（约 6 行）：直接 `confParser.enumConf(PlanningMode::fromName).sessionConf(...).tableProperty(...).defaultValue(TableProperties.PLANNING_MODE_DEFAULT).parse()`，转换逻辑内聚到解析器。

`defaultValue(TableProperties.PLANNING_MODE_DEFAULT)` 使用的是 `EnumConfParser` 的字符串重载（因 `PLANNING_MODE_DEFAULT` 是字符串常量），内部由 `toEnum.apply(value)` 转换为 `PlanningMode` 枚举。对外行为完全等价：解析来源优先级（sessionConf → tableProperty）与默认值语义不变，仅是把"字符串→枚举"的转换从调用方移到了解析器内部。`deletePlanningMode()` 方法未在本提交改动。

## 小结

- **成效**：为 `SparkConfParser` 新增 `EnumConfParser` 解析器，提供枚举类型配置的一等公民支持，调用方无需再"先 `stringConf` 再手动 `fromName`"，代码更简洁、类型更安全。`EnumConfParser` 复用 `ConfParser` 基类的多来源解析模板方法，仅提供"字符串→枚举"转换函数与默认值即可，扩展性好。首个使用案例 `dataPlanningMode()` 重构后行为等价，代码行数减少。两个 `defaultValue` 重载（枚举/字符串）覆盖了常见的默认值来源场景。
- **影响范围**：仅影响 Spark 3.5 模块（`spark/v3.5/spark/`）的配置解析路径。`EnumConfParser` 是新增类，不破坏既有解析器；`dataPlanningMode()` 重构对外行为等价（解析来源、优先级、默认值语义不变）。其他 Spark 版本模块（3.3、3.4）不受影响。后续若其他枚举类型配置（如 `deletePlanningMode`、`DistributionMode` 等）需要解析，可直接复用 `enumConf`。
- **回迁注意事项**：
  1. 此提交针对 Spark 3.5 模块（`spark/v3.5/`），回迁到 1.4.x 分支时需确认 1.4.x 分支的 `SparkConfParser` 结构与此提交前状态一致（已有 `ConfParser` 抽象基类、`self()` 模式、`parse(Function, T)` 模板方法）。若 1.4.x 分支的 `SparkConfParser` 已有其他解析器调整（如新增 `intConf` 的变体），cherry-pick 时可能需手动调整插入位置。
  2. `EnumConfParser` 依赖基类 `ConfParser` 的 `parse(Function<String, T>, T defaultValue)` 模板方法签名，回迁时需确认 1.4.x 分支的 `ConfParser` 已有该方法（应已存在，因为其他解析器如 `StringConfParser` 同样依赖它）。
  3. `SparkReadConf.dataPlanningMode()` 重构依赖 `PlanningMode::fromName` 方法引用与 `TableProperties.PLANNING_MODE_DEFAULT` 字符串常量在 1.4.x 分支中存在，回迁时需确认。若 1.4.x 分支的 `dataPlanningMode()` 已有其他修改（如新增 option 来源），cherry-pick 时需手动合并。
  4. 本提交未新增测试（diff 中无测试文件改动），`EnumConfParser` 的正确性依赖 `ConfParser` 基类的既有测试覆盖与 `dataPlanningMode()` 的既有调用路径。回迁后建议确认 1.4.x 分支的 `PlanningMode` 相关测试（如 `TestSparkReadConf`）依然通过。
  5. `EnumConfParser` 是非静态内部类，依赖外部 `SparkConfParser` 实例创建，回迁时需保持其内部类声明方式与既有解析器一致。
