# 提交 1319：Core: Add validation for table commit properties (#11437)

## 提交信息

- **序号**：1319 / 4088
- **哈希**：fe23584fc3af9f0ea1371989030b0a99affb233f
- **短哈希**：fe23584fc
- **日期**：2024-11-01（Fri Nov 1 17:22:44 2024 -0700）
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Core: Add validation for table commit properties (#11437)
- **PR/Issue**：#11437

## 总体目的

Iceberg 表的提交重试行为由 4 个表属性控制：

- `commit.retry.num-retries`（重试次数）
- `commit.retry.min-wait-ms`（最小退避时间）
- `commit.retry.max-wait-ms`（最大退避时间）
- `commit.retry.total-timeout-ms`（总重试超时时间）

这些属性值在 `PropertiesUpdate.commit()` 中被读取并用于 `Tasks.foreach(...).retry(...).exponentialBackoff(...)`。原实现使用 `propertyAsInt` 解析这些属性：当属性值无法解析为整数（如被错误地设置为 `"foo"`、`"bar"`）时，`propertyAsInt` 会直接抛出 `NumberFormatException`，导致后续的属性更新 commit 本身都跑不起来——也就是说，一旦表属性被写脏了，连"把它改回去"这一操作都无法提交，表会被永久卡死。

本提交从两个层面修复这一问题：

1. **预防**：在新建表（`TableMetadata.newTableMetadata`）阶段新增 `PropertyUtil.validateCommitProperties(properties)` 校验，确保这 4 个 commit 属性是"非负整数"，否则在表创建时就抛出 `ValidationException`，避免脏属性进入表元数据。
2. **救济**：把 `PropertiesUpdate.commit()` 中读取 base 表的 4 个 commit 属性从 `propertyAsInt` 换成新增的 `propertyTryAsInt`——后者在解析失败时记一条 warn 日志并回退到默认值，而不是抛异常。这样即便 base 表元数据已损坏，用户仍能成功发起一次属性更新 commit 来修正它（注释明确："If existing table commit properties in base are corrupted, allow rectification"）。

## 如何达成设计目的

1. 在 `PropertyUtil` 中新增：
   - 常量 `COMMIT_PROPERTIES`（一个 `ImmutableSet<String>`，列出 4 个 commit 属性键）；
   - 静态方法 `propertyTryAsInt(Map, String, int)`：解析失败时记 warn 并返回默认值；
   - 静态方法 `validateCommitProperties(Map)`：遍历 4 个属性，若存在则要求能解析为整数且非负，否则抛 `ValidationException`。
2. 在 `TableMetadata.newTableMetadata(...)`（创建新表的入口）中、紧接 `MetricsConfig.validateReferencedColumns(...)` 之后调用 `PropertyUtil.validateCommitProperties(properties)`，与 metrics 校验同样的"仅作用于新表"原则，避免破坏存量表。
3. 在 `TableMetadata` 中新增实例方法 `propertyTryAsInt(String, int)`，转调 `PropertyUtil.propertyTryAsInt(properties, ...)`，以便 `PropertiesUpdate` 通过 `base.propertyTryAsInt(...)` 软解析。
4. 修改 `PropertiesUpdate.commit()`：把对 base 表的 4 个 commit 属性读取从 `propertyAsInt` 改为 `propertyTryAsInt`，并加上注释说明意图。
5. 新增/更新测试覆盖两条路径：
   - `TestTransaction#testCommitProperties`：验证即便把 `commit.retry.max-wait-ms` 设为 `"foo"`、`commit.retry.num-retries` 设为 `"bar"`（脏值），仍能通过 `updateProperties().remove(...).commit()` 修正表，且修正后属性确实被移除；
   - `TestCreateTable#testCreateTableCommitProperties`（Spark 端）：验证建表时若 commit 属性为非整数（`'x'`）或负整数（`'-1'`），会抛 `ValidationException` 并带相应消息；合法值则能成功建表并写入属性。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/PropertyUtil.java`

**修改目的**：新增 commit 属性的"软解析"与"严格校验"两个工具方法。

**工作逻辑**：

- 新增 import：`TableProperties`、`ValidationException`、`ImmutableSet`、`Logger`/`LoggerFactory`。
- 新增静态字段：
  ```java
  private static final Logger LOG = LoggerFactory.getLogger(PropertyUtil.class);

  private static final Set<String> COMMIT_PROPERTIES =
      ImmutableSet.of(
          TableProperties.COMMIT_NUM_RETRIES,
          TableProperties.COMMIT_MIN_RETRY_WAIT_MS,
          TableProperties.COMMIT_MAX_RETRY_WAIT_MS,
          TableProperties.COMMIT_TOTAL_RETRY_TIME_MS);
  ```
- 新增 `propertyTryAsInt(Map, String, int)`：
  ```java
  String value = properties.get(property);
  if (value == null) return defaultValue;
  try {
    return Integer.parseInt(value);
  } catch (NumberFormatException e) {
    LOG.warn("Failed to parse value of {} as integer, default to {}", property, defaultValue, e);
    return defaultValue;
  }
  ```
  与既有 `propertyAsInt` 的区别：失败时不抛，而是 warn 后回退默认值。
- 新增 `validateCommitProperties(Map)`：
  ```java
  for (String commitProperty : COMMIT_PROPERTIES) {
    String value = properties.get(commitProperty);
    if (value != null) {
      int parsedValue;
      try {
        parsedValue = Integer.parseInt(value);
      } catch (NumberFormatException e) {
        throw new ValidationException("Table property %s must have integer value", commitProperty);
      }
      ValidationException.check(parsedValue >= 0,
          "Table property %s must have non negative integer value", commitProperty);
    }
  }
  ```
  仅校验已设置的属性；未设置则跳过（保留默认行为）。负整数与非整数分别给出不同错误消息。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：在建表入口添加 commit 属性校验；新增实例方法 `propertyTryAsInt` 供 `PropertiesUpdate` 使用。

**工作逻辑**：

- 在 `newTableMetadata(Schema, PartitionSpec, SortOrder, String, Map, int formatVersion)` 中、`MetricsConfig.fromProperties(properties).validateReferencedColumns(schema)` 之后插入：
  ```java
  PropertyUtil.validateCommitProperties(properties);
  ```
  与 metrics 校验同理——注释里说"仅作用于新表，避免破坏存量表"。这意味着：
  - 建表（CREATE TABLE）阶段会校验；
  - 已存在的表加载时不会触发校验（即历史脏属性仍能被读出来），但可以通过属性更新来修正。
- 新增实例方法：
  ```java
  public int propertyTryAsInt(String property, int defaultValue) {
    return PropertyUtil.propertyTryAsInt(properties, property, defaultValue);
  }
  ```
  封装对内部 `properties` map 的软解析，提供给 `PropertiesUpdate.commit()` 使用。

### `core/src/main/java/org/apache/iceberg/PropertiesUpdate.java`

**修改目的**：让属性更新 commit 对 base 表已有的脏 commit 属性具备容错能力。

**工作逻辑**：在 `commit()` 方法中：

```java
// If existing table commit properties in base are corrupted, allow rectification
Tasks.foreach(ops)
    .retry(base.propertyTryAsInt(COMMIT_NUM_RETRIES, COMMIT_NUM_RETRIES_DEFAULT))
    .exponentialBackoff(
        base.propertyTryAsInt(COMMIT_MIN_RETRY_WAIT_MS, COMMIT_MIN_RETRY_WAIT_MS_DEFAULT),
        base.propertyTryAsInt(COMMIT_MAX_RETRY_WAIT_MS, COMMIT_MAX_RETRY_WAIT_MS_DEFAULT),
        base.propertyTryAsInt(COMMIT_TOTAL_RETRY_TIME_MS, COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT),
        2.0 /* exponential */)
    .onlyRetryOn(CommitFailedException.class)
    .run(...);
```

将 4 处 `propertyAsInt` 全部改为 `propertyTryAsInt`。注释说明：若 base 中现有的 commit 属性已损坏（被写脏），允许通过属性更新来纠正。这样即使用户之前把 `commit.retry.num-retries` 误写成 `"bar"`，也能用 `updateProperties().remove(...).commit()` 把它移除并恢复默认。

### `core/src/test/java/org/apache/iceberg/TestTransaction.java`

**修改目的**：验证救济路径。

**工作逻辑**：新增 `testCommitProperties` 测试方法：

- 通过 `updateProperties().set(...)` 把 `commit.retry.max-wait-ms` 设为 `"foo"`、`commit.retry.num-retries` 设为 `"bar"`、`commit.retry.total-timeout-ms` 设为合法的 `3600000`，然后 commit；
- 随后 `updateProperties().remove(COMMIT_MAX_RETRY_WAIT_MS).commit()` 与 `remove(COMMIT_NUM_RETRIES).commit()` 分别移除两个脏值；
- 断言最终 `table.properties()` 中 `COMMIT_NUM_RETRIES` 与 `COMMIT_MAX_RETRY_WAIT_MS` 已不存在，`COMMIT_TOTAL_RETRY_TIME_MS` 仍为 `"3600000"`。

这条测试覆盖了"先成功写入脏值 → 再用更新移除脏值"的完整救济链路。注意：因为这是属性更新（而非新建表），不触发 `validateCommitProperties`，所以即便值是 `"foo"` 也能写入——这正符合"救济存量"的设计意图。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestCreateTable.java`

**修改目的**：验证建表阶段（CREATE TABLE）的严格校验。

**工作逻辑**：新增 `testCreateTableCommitProperties` 测试方法，包含三组用例：

1. `CREATE TABLE ... TBLPROPERTIES ('commit.retry.num-retries'='x', p2='x')` 应抛 `ValidationException`，消息为 `"Table property commit.retry.num-retries must have integer value"`；
2. `CREATE TABLE ... TBLPROPERTIES ('commit.retry.max-wait-ms'='-1')` 应抛 `ValidationException`，消息为 `"Table property commit.retry.max-wait-ms must have non negative integer value"`；
3. `CREATE TABLE ... TBLPROPERTIES ('commit.retry.num-retries'='1', 'commit.retry.max-wait-ms'='3000')` 应成功，并验证 `table.properties()` 中确实包含这两个合法值。

## 小结

- **成效**：建表时严格校验 commit 属性为非负整数，从源头杜绝脏属性进入元数据；属性更新 commit 改用软解析，使已经卡死的表也能通过更新修正，避免一坏俱坏。
- **影响范围**：5 个文件、共 121 行（含测试约 60 行）。属于行为变更：建表阶段对非法 commit 属性由"静默接受 → 后续 commit 失败"变为"建表即拒绝"；属性更新阶段对 base 中非法 commit 属性由"直接抛"变为"warn 后回退默认"。
- **回迁到 1.4.x 的注意事项**：
  - **建议回迁**：此为重要的健壮性修复，1.4.x 也可能遇到用户写脏 commit 属性导致表卡死的情况，回迁能让运维有救济手段。
  - 注意校验只在新建表路径触发，**不影响存量表加载**，不会因历史脏数据导致 1.4.x 已有表无法读取。安全。
  - 行为变更可能影响下游：若有用户在建表时通过 `TBLPROPERTIES` 设置诸如 `'commit.retry.num-retries'='10.0'`（带小数点）这类原本被静默接受但实际无效的值，回迁后会建表失败。建议在 release notes 中提示这一行为变化。
  - `propertyTryAsInt` 是新增 API，无破坏性；回迁时需把 `PropertyUtil`、`TableMetadata.propertyTryAsInt`、`PropertiesUpdate.commit` 三处一并带回，并配套回迁两个测试用例以验证行为。
  - Spark 测试路径为 `spark/v3.5`；若 1.4.x 还维护其他 Spark 版本目录（如 v3.3、v3.4），可考虑同步对相应测试做同样补充，但非必须（功能改动在 core 层，Spark 测试只是覆盖端到端）。
