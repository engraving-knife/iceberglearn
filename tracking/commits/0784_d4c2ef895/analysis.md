# 提交 0784：Spark 3.5: Support camel case session configs and options (#10310)

## 提交信息

- **序号**：0784 / 4088
- **哈希**：d4c2ef89500426eea66106b47e39841ec2383c54
- **短哈希**：d4c2ef895
- **日期**：2024-05-24 08:59:02 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.5: Support camel case session configs and options (#10310)
- **PR/Issue**：#10310

## 总体目的

本提交为 Spark 3.5 模块的 `SparkConfParser` 增加对 camelCase（驼峰）形式配置名与选项名的兼容查找能力。Iceberg 的配置项遵循 kebab-case（连字符分隔）命名约定（如 `some-int-conf`、`write-format`、`target-file-size`），但 Spark 生态中 SQL 会话配置（`spark.sql.*`）和数据源选项（DataFrameReader/Writer 的 `.option()`）长期以来有 camelCase（驼峰）命名习惯（如 `someIntConf`、`writeFormat`、`targetFileSize`）。用户在 Spark 中以 camelCase 形式设置 Iceberg 配置时，原 `SparkConfParser` 只按 kebab-case 名查找，会查不到而回退到默认值，导致用户配置"静默失效"。本提交在解析 options 与 sessionConf 两个来源时，先按原始 kebab-case 名查找，未命中再按 camelCase 转换后的名查找，从而兼容两种命名风格，提升用户体验与配置互通性。

## 如何达成设计目的

### 设计背景：命名约定的不匹配

`SparkConfParser` 是 Iceberg Spark 模块从三个来源解析配置的核心组件：
1. **options**：DataFrameReader/Writer 的 `.option(name, value)`，存储在 `CaseInsensitiveStringMap`。
2. **sessionConf**：SparkSession 的 `spark.sql.*` 配置，通过 `RuntimeConfig.get` 查询。
3. **tablePropertyName**：表属性 `table.properties`。

解析顺序为 options → sessionConf → tableProperty → defaultValue。每个来源注册时传入 kebab-case 名（如 `.option("some-int-option").sessionConf("spark.sql.iceberg.some-int-conf")`）。

问题在于查找机制：
- **options**：在前序提交 0755（#10309）后，`options` 字段已是 `CaseInsensitiveStringMap`，其 `get` 方法是**大小写不敏感**的（内部 `toLowerCase`）。但"大小写不敏感"只解决 `SomeIntOption` vs `someintoption` 的差异，**不解决连字符差异**：`"some-int-option"` 与 `"someIntOption"` 各自小写后是 `"some-int-option"` 与 `"someintoption"`，连字符有无导致仍不匹配。因此用户用 camelCase 设置选项时，`options.get("some-int-option")` 找不到 camelCase 的键。
- **sessionConf**：`RuntimeConfig.get` 是**大小写敏感且精确匹配**的（Spark SQL conf 按字面名查找），既不大小写不敏感，也不做连字符转换。用户设 `spark.sql.iceberg.someIntConf`，Iceberg 查 `spark.sql.iceberg.some-int-conf`，完全查不到。

### 设计逻辑：kebab → camel 显式转换作为回退查找

本提交的策略是：在每个来源的查找中，先按原始 kebab-case 名查（保持向后兼容），未命中再按 camelCase 转换后的名查一次。这一设计有几点考量：

1. **保持 kebab-case 为权威命名**：Iceberg 自身文档、表属性、跨引擎（Flink/Trino）一致使用 kebab-case，不能放弃。camelCase 仅作为 Spark 侧的兼容输入。

2. **camelCase 作为回退而非首选**：先查 kebab-case 避免对既有用户（已用 kebab-case）产生任何行为变化；只有 kebab-case 未命中时才尝试 camelCase，确保向后兼容。

3. **仅对 options 与 sessionConf 加 camelCase 回退，不对 tableProperty 加**：表属性是 Iceberg 自身管理的元数据，命名严格遵循 kebab-case（建表时由 Iceberg 校验/规整），不存在 camelCase 输入场景，故无需扩展。

4. **`toCamelCase` 转换规则**：将连字符 `-` 删除并将其后字母大写，其余字符原样保留。如 `some-int-option` → `someIntOption`，`spark.sql.iceberg.some-int-conf` → `spark.sql.iceberg.someIntConf`（点号 `.` 不受影响，只处理连字符）。该规则与 Spark/Java 生态常见的 kebab→camel 约定一致。

5. **对 options 而言，camelCase 查找能命中 camelCase 键的原因**：`CaseInsensitiveStringMap` 大小写不敏感。用户存 `someIntOption`，内部小写为 `someintoption`；`toCamelCase("some-int-option")` = `someIntOption`，`options.get("someIntOption")` 小写为 `someintoption`，匹配命中。即 camelCase 转换消除了连字符差异，大小写不敏感消除了大小写差异，两者配合才能完整匹配。

6. **对 sessionConf 而言**：`RuntimeConfig.get` 大小写敏感，所以用户设的 camelCase 名必须与 `toCamelCase` 输出**完全一致**才能命中（如用户设 `spark.sql.iceberg.someIntConf`，Iceberg 查 `toCamelCase("spark.sql.iceberg.some-int-conf")` = `spark.sql.iceberg.someIntConf`，精确匹配）。这里不像 options 那样有大写不敏感兜底，因此对用户的 camelCase 拼写要求更严格，但符合 Spark SQL conf 的精确查找语义。

### 实现细节

在 `ConfParser.parse` 方法中：

- **options 查找**：原逻辑遍历 `optionNames`，对每个名 `options.get(optionName)`，命中则返回。新增：未命中时再 `options.get(toCamelCase(optionName))`，命中则返回。
- **sessionConf 查找**：原逻辑 `sessionConf.get(sessionConfName, null)`，命中则返回。新增：未命中时再 `sessionConf.get(toCamelCase(sessionConfName), null)`，命中则返回。
- **tableProperty 查找**：不变。

新增私有方法 `toCamelCase(String key)`：用 `StringBuilder` 与 `capitalizeNext` 标志位实现状态机遍历——遇 `-` 置 `capitalizeNext=true`（不追加 `-`）；其后字符若 `capitalizeNext` 为 true 则大写追加并清标志，否则原样追加。

### 测试验证

新增两个测试（`TestSparkWriteConf`）：

1. **`testCamelCaseSparkSessionConf`**：用 `withSQLConf` 设置 `spark.sql.iceberg.someIntConf=1`（camelCase），然后用 `parser.intConf().sessionConf("spark.sql.iceberg.some-int-conf").parseOptional()`（kebab-case 名）解析，断言得到 `1`。验证 sessionConf 的 camelCase 回退查找。

2. **`testCamelCaseSparkOption`**：构造 `options = {someIntOption: 1}`（camelCase 键），用 `parser.intConf().option("some-int-option").parseOptional()`（kebab-case 名）解析，断言得到 `1`。验证 options 的 camelCase 回退查找。

两个测试都证明了"用 kebab-case 名注册、用 camelCase 名设置"的互通场景。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkConfParser.java`

**修改目的**：在 `ConfParser.parse` 方法的 options 与 sessionConf 查找分支增加 camelCase 回退，并新增 `toCamelCase` 工具方法。

**修改点 1**（options 查找分支，`parse` 方法内）：

```java
for (String optionName : optionNames) {
  String optionValue = options.get(optionName);
  if (optionValue != null) {
    return conversion.apply(optionValue);
  }
+ String sparkOptionValue = options.get(toCamelCase(optionName));
+ if (sparkOptionValue != null) {
+   return conversion.apply(sparkOptionValue);
+ }
}
```

每个 optionName 先按原 kebab-case 查，未命中按 camelCase 查。

**修改点 2**（sessionConf 查找分支）：

```java
if (sessionConfName != null) {
  String sessionConfValue = sessionConf.get(sessionConfName, null);
  if (sessionConfValue != null) {
    return conversion.apply(sessionConfValue);
  }
+ String sparkSessionConfValue = sessionConf.get(toCamelCase(sessionConfName), null);
+ if (sparkSessionConfValue != null) {
+   return conversion.apply(sparkSessionConfValue);
+ }
}
```

sessionConfName 先按原 kebab-case 查，未命中按 camelCase 查。

**修改点 3**（新增 `toCamelCase` 方法）：

```java
private String toCamelCase(String key) {
  StringBuilder transformedKey = new StringBuilder();
  boolean capitalizeNext = false;
  for (char character : key.toCharArray()) {
    if (character == '-') {
      capitalizeNext = true;
    } else if (capitalizeNext) {
      transformedKey.append(Character.toUpperCase(character));
      capitalizeNext = false;
    } else {
      transformedKey.append(character);
    }
  }
  return transformedKey.toString();
}
```

状态机：`-` 触发下一字符大写；连续的 `-` 会持续置 `capitalizeNext=true`（多个连字符后第一个非连字符大写）；无 `-` 时原样输出（即纯 camelCase 输入不受影响，纯 kebab-case 输出 camelCase）。注意该方法对点号 `.`、下划线 `_` 等其他分隔符不做处理，仅针对 `-`，与 Iceberg kebab-case 约定对应。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java`

**修改目的**：覆盖 sessionConf 与 options 的 camelCase 兼容查找。

**修改点**：新增 `testCamelCaseSparkSessionConf` 与 `testCamelCaseSparkOption` 两个 `@TestTemplate` 方法（见"测试验证"）。两者均用 `intConf().parseOptional()` 验证整数配置的 camelCase 回退，期望值为 `1`（Integer 类型）。

## 小结

- **成效**：Spark 3.5 模块的 `SparkConfParser` 现支持用户以 camelCase 形式设置会话配置（`spark.sql.*`）与数据源选项（`.option(...)`），与 Iceberg 既有 kebab-case 命名互通。改动局部、向后兼容（kebab-case 仍优先），显著降低了因命名风格不匹配导致的"配置静默失效"问题，对 Spark 用户（习惯 camelCase）更友好。
- **影响范围**：仅影响 Spark 3.5 模块（`spark/v3.5/`）的配置解析。Spark 3.3、3.4 模块未改动（如需对称支持需各自回迁）。`SparkConfParser` 是 Spark 读写配置解析的基础组件，所有走 Spark 读写路径的配置项都受益。tableProperty 来源不受影响（仍只 kebab-case 精确匹配）。
- **回迁注意事项**：
  1. 本提交依赖前序 0755（#10309）将 `options` 改为 `CaseInsensitiveStringMap`。若 1.4.x 分支的 0755 尚未回迁，`options` 仍是普通 `Map`，则 `options.get(toCamelCase(optionName))` 仍可工作（普通 Map 的 get 按精确键匹配，camelCase 键能命中），只是少了大小写不敏感兜底。建议 0755 与 0784 一起回迁以保持一致性。
  2. `toCamelCase` 仅处理 `-`，若 1.4.x 有用户用下划线 `_` 风格的配置（如 Flink 习惯），本提交不覆盖，需另行处理。
  3. sessionConf 的 camelCase 查找是大小写敏感的（`RuntimeConfig.get` 本身敏感），回迁后需告知用户 camelCase 拼写需精确；options 则因 `CaseInsensitiveStringMap` 而大小写不敏感。
  4. 测试依赖 `TestSparkWriteConf` 的 `withSQLConf`、`validationCatalog`、`tableIdent` 基础设施，1.4.x 中应可用。
  5. 若 1.4.x 分支计划同步支持 Spark 3.3/3.4 的 camelCase，需将相同改动分别回迁到 `spark/v3.3/` 与 `spark/v3.4/` 的 `SparkConfParser`。
