# 提交 1619 5091e5752 分析

## 提交信息
- 哈希：5091e5752f8000afe3e116e5114d15eb61a4f9ef
- 日期：2025-01-22 08:14:29 -0700
- 作者：Ryan Blue
- 消息：ORC: Fail when initial default support is required. (#12026)

## 总体目的

本提交调整 ORC 读取路径在 schema 演进场景下对"必需字段缺失"的处理逻辑，使其与 Iceberg v3 表格式引入的"字段默认值"语义对齐，并以更清晰的错误信息暴露 ORC 读取器尚未支持默认值的限制。

具体来说，Iceberg v3 schema 允许为字段声明 `initial-default`（读取旧文件时用于填充的默认值）。Parquet 和 Avro 读取器已经实现了默认值填充（参见提交 1615 把该能力回迁到 Spark 3.3），但 ORC 读取器尚未实现该能力。当用户在 v3 表上新增一个带 `initial-default` 的 required 字段、然后读取一个不含该字段的旧 ORC 文件时，ORC 读取器目前无法用默认值填充该字段。本提交的目标是：

1. 当 required 字段缺失、且字段没有声明 `initial-default` 时，抛出 `IllegalArgumentException`，错误信息明确指出是哪个字段（用字段名而非字段 ID），便于用户定位 schema 演进问题；
2. 当 required 字段缺失、但字段声明了 `initial-default` 时，抛出 `UnsupportedOperationException`，明确说明"ORC 暂不支持读取默认值"，把"功能未实现"和"用户配置错误"区分开，避免用户误以为是自己 schema 设计问题。

这样错误信息更具可操作性：用户看到 `UnsupportedOperationException` 就知道是 ORC 读取器的限制（可以切换文件格式或等待实现），看到 `IllegalArgumentException` 就知道是 schema 演进时漏了默认值。

## 如何达成设计目的

设计思路是修改 `ORCSchemaUtil.buildOrcProjection` 中"在文件 schema 中找不到对应字段"的分支，按 `isRequired` 与 `field.initialDefault()` 两个维度分流：

- `isRequired == false`：字段是 optional，缺失时填充 null，沿用原逻辑（`convert(fieldId, type, false)` 生成 nullable ORC 类型）。
- `isRequired == true` 且 `field.initialDefault() == null`：required 字段既不在文件中也没有默认值，无法安全读取，抛 `IllegalArgumentException`，消息使用字段名（`root.findColumnName(fieldId)`）而非字段 ID。
- `isRequired == true` 且 `field.initialDefault() != null`：required 字段有默认值，但 ORC 读取器尚未实现默认值填充，抛 `UnsupportedOperationException`，消息包含字段名、类型与默认值，明确说明"ORC cannot read default value"。

为了在递归遍历类型树时能够查到字段的 `initialDefault` 与字段名，`buildOrcProjection` 方法签名新增 `Schema root` 参数，把整个 schema 传递到每层递归，通过 `root.findField(fieldId)` 拿到 `Types.NestedField` 进而访问 `initialDefault()` 和通过 `root.findColumnName(fieldId)` 拿到字段名（含完整路径名）。

### 修改详情

#### orc/src/main/java/org/apache/iceberg/orc/ORCSchemaUtil.java

1. 删除未使用的 `import java.util.Locale;`。

2. 公共入口 `buildOrcProjection(Schema schema, TypeDescription originalOrcSchema)` 调用私有重载时新增传入 `schema` 作为 `root`：
   ```
   return buildOrcProjection(schema, Integer.MIN_VALUE, schema.asStruct(), true, icebergToOrc);
   ```

3. 私有方法 `buildOrcProjection` 签名新增 `Schema root` 作为第一个参数。所有递归调用（STRUCT 子字段、LIST 元素、MAP key/value）都补上 `root` 参数透传，保证任意嵌套层级都能通过 `root.findField(fieldId)` 找到字段定义。

4. 在"字段在文件 schema 中找不到对应 ORC 类型"的分支中，原先的：
   ```
   if (isRequired) {
     throw new IllegalArgumentException(
         String.format(Locale.ROOT, "Field %d of type %s is required and was not found.", fieldId, type));
   }
   orcType = convert(fieldId, type, false);
   ```
   改为：
   ```
   Types.NestedField field = root.findField(fieldId);
   if (isRequired) {
     Preconditions.checkArgument(
         field.initialDefault() != null,
         "Missing required field: %s (%s)",
         root.findColumnName(fieldId), type);
   }
   if (field.initialDefault() != null) {
     throw new UnsupportedOperationException(
         String.format(
             "ORC cannot read default value for field %s (%s): %s",
             root.findColumnName(fieldId), type, field.initialDefault()));
   }
   orcType = convert(fieldId, type, false);
   ```

   逻辑解读：
   - `field.initialDefault() != null` 既用于 `Preconditions.checkArgument`（required 且无默认值时报错），也用于决定是否抛 `UnsupportedOperationException`。等价于：required + 无默认值 → IllegalArgumentException；required + 有默认值 → UnsupportedOperationException；optional + 有默认值 → 也抛 UnsupportedOperationException（因为 ORC 读取器尚未支持任何默认值填充，即使 optional 也无法填充，必须明确告知用户）；optional + 无默认值 → 走 `convert(fieldId, type, false)` 填充 null。
   - 错误消息用 `root.findColumnName(fieldId)` 输出字段名（如 `b.d` 表示 struct 字段 b 下的子字段 d），比原先只输出数字字段 ID 更友好。
   - `Preconditions.checkArgument` 是 Iceberg 通用的参数校验工具，condition 为 false 时抛 `IllegalArgumentException`，与原有异常类型一致但消息更清晰。

#### orc/src/test/java/org/apache/iceberg/orc/TestBuildOrcProjection.java

1. 更新原有 `testRequiredNestedFieldMissingInFile` 测试的断言：
   - 旧消息 `"Field 4 of type long is required and was not found."` → 新消息 `"Missing required field: b.d (long)"`。
   - 异常类型仍为 `IllegalArgumentException`，因为该测试场景下 evolved schema 的字段 `d` 没有 `initialDefault`，命中"required + 无默认值"分支。

2. 新增 `testRequiredNestedFieldWithDefaultMissingInFile` 测试：
   - base schema：`a INT` + `b STRUCT<c LONG>`；
   - evolved schema：在 b 下新增 required 字段 `d LONG`，并通过 builder 链式 API 声明 `withInitialDefault(34L)`；
   - 断言调用 `ORCSchemaUtil.buildOrcProjection(evolvedSchema, baseOrcSchema)` 抛 `UnsupportedOperationException`，消息为 `"ORC cannot read default value for field b.d (long): 34"`。
   - 这覆盖了"required + 有默认值"分支，验证 ORC 读取器在尚未实现默认值填充时给出明确错误而非静默错误数据。

## 小结

此次改动让 ORC 读取路径在 schema 演进场景下的错误处理更符合 v3 表格式语义：required 字段缺失时按是否有默认值分别抛出 `IllegalArgumentException`（用户 schema 问题）或 `UnsupportedOperationException`（ORC 读取器限制），错误消息从字段 ID 升级为字段路径名，便于定位。

影响范围：仅 ORC 模块的 schema 投影工具与对应单测。对正常读取（字段在文件中存在）无任何影响；只影响"读取旧 ORC 文件遇到 schema 演进新增 required 字段"这一场景的错误行为。Parquet/Avro 读取路径不受影响（它们已实现默认值填充）。

回迁到 1.4.x 分支的注意事项：1.4.x 通常较老，可能尚未引入 v3 表格式与 `initial-default` 语义，`Types.NestedField.initialDefault()` API 在 1.4.x 中可能不存在。回迁前必须先确认 1.4.x 的 core 模块是否已具备 `Types.NestedField#initialDefault()`、`NestedField` builder 链式 API（`withInitialDefault`、`withId`、`ofType`、`withDoc`、`build`）以及 `Schema#findColumnName` 等基础设施。若 1.4.x 尚未引入这些 API，则不能单独回迁本 ORC 提交，需要先回迁 core 侧的 v3 默认值支持。此外，`Preconditions.checkArgument` 的消息格式（`%s` 占位符）需确认 1.4.x 的 Preconditions 实现支持。最后，错误消息文本的改变（从字段 ID 改为字段名）属于行为兼容性变化——若有下游代码依赖具体异常消息文本，回迁后需同步更新。
