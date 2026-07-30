# 提交 1314：API: Add compatibility checks for Schemas with default values (#11434)

## 提交信息

- **序号**：1314 / 4088
- **哈希**：91e04c9c88b63dc01d6c8e69dfdc8cd27ee811cc
- **短哈希**：91e04c9c8
- **日期**：2024-10-30（Wed Oct 30 14:58:25 2024 -0700）
- **作者**：Ryan Blue <blue@apache.org>（Co-authored-by: Russell Spitzer、Fokko Driesprong）
- **提交说明**：API: Add compatibility checks for Schemas with default values (#11434)
- **PR/Issue**：#11434

## 总体目的

Iceberg 表格式在 v3 引入了字段默认值（`initial-default` / `write-default`）能力，允许在 schema 中为字段声明写入或新行时的默认值。`Schema.checkCompatibility(Schema, int formatVersion)` 是写表元数据前用来校验"该 schema 在指定 format version 下是否合法"的统一入口。原实现仅校验类型层面的最低格式版本（如 `TIMESTAMP_NANO` 需要 v3），并未对默认值做任何兼容性检查，因此一个含 `initial-default` 的 schema 可以被静默写入到 v1/v2 表中，这违反了 spec——旧版本读者无法识别这种字段，会在读取时抛错或行为异常。

本提交做两件事：

1. 在 `checkCompatibility` 中新增"非空 `initial-default` 至少需要 v3"的校验；
2. 顺带把原本"遇到第一个问题就抛出"的实现，改为"收集所有问题后统一抛出"，方便用户一次看清全部不兼容项。

注意：只有 `initial-default`（即新行插入时使用的字段默认值）被纳入前向兼容性检查；`write-default`（写入时未指定该列所用的回退值）不算前向不兼容，因此即便 v1/v2 也能通过校验。

## 如何达成设计目的

1. 在 `Schema` 类中新增常量 `DEFAULT_VALUES_MIN_FORMAT_VERSION = 3`，集中表达"默认值特性需要 v3"。
2. 重写 `checkCompatibility` 方法：
   - 引入 `TreeMap<Integer, String> problems`，按字段 ID 排序累积错误信息，确保输出顺序稳定、易读；
   - 对每个 `NestedField` 同时检查类型最低版本和 `initialDefault` 非空时的最低版本；
   - 所有字段检查完毕后，若 `problems` 非空，使用 `Joiner.on("\n- ")` 拼接成多行错误消息一次性抛出 `IllegalStateException`。
3. 新增 `api/src/test/java/org/apache/iceberg/TestSchema.java` 单元测试覆盖各种兼容场景。
4. 修正 `core/src/test/java/org/apache/iceberg/TestTableMetadata.java` 中已有的"时间戳 nanos 不兼容"断言，使其匹配新的多行错误消息格式。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Schema.java`

**修改目的**：扩展 `checkCompatibility` 校验默认值，并把单错误抛出改为多错误聚合。

**工作逻辑**：

- 新增常量：
  ```java
  private static final int DEFAULT_VALUES_MIN_FORMAT_VERSION = 3;
  ```
- 重写 `checkCompatibility(Schema schema, int formatVersion)`：
  - 用 `TreeMap<Integer, String> problems` 累积错误（按 fieldId 排序输出）；
  - 类型检查：若 `MIN_FORMAT_VERSIONS` 中类型对应最低版本存在且当前 `formatVersion` 低于它，则记一条 "Invalid type for %s: %s is not supported until v%s"；
  - 默认值检查：若 `field.initialDefault() != null` 且 `formatVersion < 3`，记一条 "Invalid initial default for %s: non-null default (%s) is not supported until v3"；
  - 最终若 `problems` 非空，抛 `IllegalStateException`，消息形如 "Invalid schema for v%s:\n- 问题1\n- 问题2..."。

要点：
- 使用 `TreeMap` 而非 `HashMap` 是为了让错误按 fieldId 升序输出，方便定位；
- `field.initialDefault() != null` 才触发检查——空默认值（未声明）允许在任何版本使用；
- 没有对 `writeDefault` 做相同检查，因为 write-default 只是写入时的回退值，不影响旧版本读者的能力，故不算前向不兼容。

### `api/src/test/java/org/apache/iceberg/TestSchema.java`（新增）

**修改目的**：为新行为提供完整单元测试覆盖。

**工作逻辑**：定义两个 schema 常量：

- `TS_NANO_CASES`：覆盖嵌套结构中各种位置的 `TimestampNanoType`（顶层、数组元素、struct 字段、struct 数组字段），用于验证嵌套字段也能被遍历检查。
- `INITIAL_DEFAULT_SCHEMA`：含 `initialDefault("--")` 与 `writeDefault("--")` 的字符串字段。
- `WRITE_DEFAULT_SCHEMA`：只含 `writeDefault("--")`，不含 `initialDefault`。

测试方法：
- `testUnsupportedTimestampNano`（参数化 v1、v2）：断言抛出多行错误，列出 4 个位置的不兼容类型；
- `testSupportedTimestampNano`（v3）：断言通过；
- `testUnsupportedInitialDefault`（v1、v2）：断言抛出 "Invalid initial default for has_default: non-null default (--) is not supported until v3"；
- `testSupportedInitialDefault`（v3）：通过；
- `testSupportedWriteDefault`（v1、v2、v3）：验证 write-default 在所有版本均通过，即仅 initial-default 才前向不兼容。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`

**修改目的**：跟随新错误格式调整既有断言。

**工作逻辑**：原来在测试中校验 `TIMESTAMP_NANO` 不兼容时使用的单行错误消息：
```
Invalid type in v%s schema: struct.ts_nanos timestamptz_ns is not supported until v3
```
被替换为新格式的多行消息：
```
Invalid schema for v%s:
- Invalid type for struct.ts_nanos: timestamptz_ns is not supported until v3
```
（仅消息文案变化，断言语义不变。）

## 小结

- **成效**：`Schema.checkCompatibility` 现在能正确阻止把含 `initial-default` 的 schema 写入到 v1/v2 表，避免下游旧版本读者读到无法理解的字段；同时把错误消息升级为"一次列出全部不兼容项"，提升诊断体验。
- **影响范围**：3 个文件，共 143 行（含新增 111 行测试和 1 行常量、约 30 行业务逻辑改动）。属于行为收窄（更严格的校验）和错误消息格式不兼容变更。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 若已支持 v3 format version 与字段默认值特性，**建议回迁**此校验，以防止用户把含默认值的 schema 误用于 v1/v2 表，避免数据落盘后旧引擎读不动。
  - 错误消息格式变化属破坏性变更：若 1.4.x 下游有测试或自动化脚本依赖原单行消息字符串，回迁后需同步调整。
  - 校验逻辑更严格可能使原本能"通过校验"的 schema 在新版本中被拒绝。回迁前应评估线上是否已存在"v1/v2 表 + 字段含 initial-default"的脏数据，避免回迁后元数据更新被拒导致业务受阻。
  - 注意只检查 `initialDefault`、不检查 `writeDefault`，这一区分需在回迁时保留，否则可能误伤 v1/v2 表使用 write-default 的合法场景。
