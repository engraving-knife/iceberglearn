# 提交 1380：API, Core, Spark: Ignore schema merge updates from long -> int (#11419)

## 提交信息

- **序号**：1380 / 4088
- **哈希**：4c0288a4ca86d0cf400405777808e2eb706d8d25
- **短哈希**：4c0288a4c
- **日期**：2024-11-14（Thu Nov 14 22:07:04 2024 -0800）
- **作者**：Rocco Varela <rocco.varela@gmail.com>
- **提交说明**：API, Core, Spark: Ignore schema merge updates from long -> int (#11419)
- **PR/Issue**：#11419

## 总体目的

Iceberg 的 `UpdateSchema.unionByNameWith(newSchema)` 用于按字段名合并两个 schema，是 Spark 等引擎在写入时启用 `merge-schema=true` 进行 schema 演化的底层机制。当新 schema 中某字段类型与现有字段不同时，会尝试 `updateColumn` 改类型。

此前的问题是：当**新数据类型比现有表类型更窄**时（例如表里是 `long`，新数据是 `int`；表里是 `double`，新数据是 `float`；表里是 `decimal(20,1)`，新数据是 `decimal(10,1)`），schema merge 会尝试把列类型从宽类型"更新"为窄类型。这种窄化更新既不被 Iceberg 类型提升规则允许（`updateColumn` 会抛 `IllegalArgumentException: Cannot change column type: aCol: double -> float`），即便允许也会破坏既有数据的精度。

从语义上看，窄类型数据天然可以被宽类型容纳（int 可写入 long 列、float 可写入 double 列、低精度 decimal 可写入高精度 decimal 列），因此最合理的行为是**忽略这种窄化类型差异，保持现有宽类型不变**，让新数据按宽类型写入即可。

本提交修改 `UnionByNameVisitor` 的类型更新判定逻辑：当新类型相对现有类型是窄化（即反向 `newType → existingType` 是合法的类型提升）时，跳过该字段的类型更新，保留现有类型。同时在 `UpdateSchema` 接口 Javadoc 中补充该行为说明，并在 Core 与 Spark 三个版本中补充测试。

## 如何达成设计目的

核心是引入 `isIgnorableTypeUpdate(existingType, newType)` 判定，替换原来"任意原始类型不同就触发更新"的简单判定：

- **原始类型**：用 `TypeUtil.isPromotionAllowed(newType, existingType)` 检查反向是否合法提升。若合法，说明 newType 比 existingType 窄（如 newType=int, existingType=long，int→long 是合法提升），此时返回 true（可忽略），保留现有宽类型；若不合法（如 newType=long, existingType=int，long→int 非法提升，或 newType=string 与 existingType=int 完全不兼容），返回 false（不可忽略），仍尝试更新（合法 widening 会成功，不兼容会抛异常，行为同前）。
- **复杂类型（struct/list/map）**：若 existing 与 new 都是复杂类型，返回 true（忽略本层类型差异，由 visitor 递归处理子字段，与原行为一致）；若一个是复杂、一个是原始，返回 false（不可忽略，会尝试更新并失败）。

这样既实现了"窄化忽略"，又不影响"宽化更新"和"不兼容报错"的既有行为。

## 修改详情

### `api/src/main/java/org/apache/iceberg/UpdateSchema.java`

修改目的：在接口 Javadoc 中补充窄化忽略行为说明。

工作逻辑：在 `unionByNameWith` 方法的 Javadoc 中，于"For fields with same canonical names ... supported using updateColumn"一句后补充："Differences in type are ignored if the new type is narrower than the existing type (e.g. long to int, double to float)."，明确告知调用方窄化差异会被忽略。无代码逻辑变化。

### `core/src/main/java/org/apache/iceberg/schema/UnionByNameVisitor.java`

修改目的：实现窄化类型更新忽略逻辑。

工作逻辑：
- 新增 `import org.apache.iceberg.types.TypeUtil;`。
- 在 `primitiveField`（处理同名原始类型字段对比的方法）中，将
  `boolean needsTypeUpdate = field.type().isPrimitiveType() && !field.type().equals(existingField.type());`
  改为
  `boolean needsTypeUpdate = !isIgnorableTypeUpdate(existingField.type(), field.type());`
- 新增私有方法 `isIgnorableTypeUpdate(Type existingType, Type newType)`：
  - 若 `existingType.isPrimitiveType()`：返回 `newType.isPrimitiveType() && TypeUtil.isPromotionAllowed(newType, existingType.asPrimitiveType())`。注释说明用反向提升检查判定窄化：long→int 返回 true（可忽略），int→long 返回 false（不可忽略，需更新）。
  - 否则（existingType 为复杂类型）：返回 `!newType.isPrimitiveType()`（即 newType 也是复杂类型时可忽略，交由递归处理子字段）。

该方法内含详尽注释解释反向提升判定的语义。

### `core/src/test/java/org/apache/iceberg/TestSchemaUnionByFieldName.java`

修改目的：覆盖窄化忽略行为。

工作逻辑：
- 将原 `testInvalidTypePromoteDoubleToFloat`（断言抛 `IllegalArgumentException`）重命名为 `testIgnoreTypePromoteDoubleToFloat`，改为断言 `unionByNameWith` 成功且结果 schema 仍为 `DoubleType`。
- 新增 `testIgnoreTypePromoteLongToInt`：现有 `LongType`、新 `IntegerType`，断言合并后仍为 `LongType`。
- 新增 `testIgnoreTypePromoteDecimalToNarrowerPrecision`：现有 `DecimalType.of(20, 1)`、新 `DecimalType.of(10, 1)`，断言合并后仍为 `decimal(20, 1)`。

### `spark/v3.3`、`spark/v3.4`、`spark/v3.5` 的 `spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWriterV2.java`

修改目的：在 Spark 端到端写入场景下验证 `merge-schema=true` 时窄化类型被忽略。

工作逻辑（三个版本各新增 3 个 `@TestTemplate` 方法，内容基本一致）：
- `testMergeSchemaIgnoreCastingLongToInt`：先建表写入 `id bigint` 数据，再以 `id int` 数据 `merge-schema=true` append，断言不抛异常、数据正确合并、且 `id` 列类型仍为 `LONG`。需先 `ALTER TABLE SET TBLPROPERTIES ('spark.write.accept-any-schema'='true')` 以允许 schema 不一致的写入。
- `testMergeSchemaIgnoreCastingDoubleToFloat`：`id double` 表写入 `id float` 数据，断言列类型仍为 `DOUBLE`。
- `testMergeSchemaIgnoreCastingDecimalToDecimalWithNarrowerPrecision`：`id decimal(6,2)` 表写入 `id decimal(4,2)` 数据，断言列精度仍为 6。
- 每个测试最后用 `Spark3Util.loadIcebergTable(...).schema().findField("id")` 验证列类型未变。

三个 Spark 版本的测试方法体相同（v3.3/v3.4 各 135 行新增，v3.5 为 134 行，差异为细微格式）。

## 小结

- 成效：`unionByNameWith`（及 Spark `merge-schema=true` 写入）现可正确处理新数据类型窄于现有表类型的情况——保留现有宽类型，不再抛异常或错误窄化列类型。这对实际写入场景非常关键：不同批次/源系统的数据可能用不同精度（如某次 int、某次 long），合并写入时表 schema 应保持最宽类型，而非因窄类型数据失败或被错误窄化。
- 影响范围：`UnionByNameVisitor`（core）一处判定逻辑改动 + `UpdateSchema` Javadoc + 6 个测试方法（core 3 个 + spark 3 个版本各 3 个）。**行为变化**：原本会抛 `IllegalArgumentException` 的窄化场景（double→float、long→int、decimal 高精度→低精度）现在静默忽略并保留宽类型。原本就合法的 widening（int→long）行为不变；原本不兼容的（int→string）仍会尝试更新并失败，行为不变。
- 设计亮点：用反向 `TypeUtil.isPromotionAllowed(newType, existingType)` 判定窄化，复用了 Iceberg 既有的类型提升规则表，无需硬编码 long/int、double/float 等具体对，自动覆盖所有合法的窄化情形（包括 decimal 精度收窄）。
- 回迁到 1.4.x 的注意事项：**建议回迁**。理由：
  1. 这是 bug 修复性质的行为改进（窄化场景从"抛异常"变为"正确忽略"），对实际用户写入 merge-schema 场景有直接价值，风险低。
  2. 改动局部（`UnionByNameVisitor` 一个方法 + 一个新私有方法），不涉及 API 签名变化（`UpdateSchema` 接口仅 Javadoc 变化），不破坏二进制兼容性。
  3. 依赖 `TypeUtil.isPromotionAllowed`，该方法是 Iceberg 核心既有 API，1.4.x 应已具备，无前置依赖问题。
  4. 回迁时需注意：此改变是**行为可见的**——原本对 long→int 抛异常的用户代码，回迁后会静默成功。若用户依赖异常来检测 schema 不一致，需调整。但更可能的是用户原本就被该异常困扰，回迁是改善。
  5. 建议回迁时连同 core 与对应 Spark 版本的测试一起回迁，确保 `TestSchemaUnionByFieldName` 与 `TestDataFrameWriterV2` 覆盖。若 1.4.x 的 Spark 版本范围与 main 不同（如不含 v3.5），按 1.4.x 实际支持的版本回迁对应测试。
