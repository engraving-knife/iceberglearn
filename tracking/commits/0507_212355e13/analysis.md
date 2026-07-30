# 提交 0507：Core: Add strictness flag to prevent loss of view representation when replacing a view (#9620)

## 提交信息

| 字段 | 内容 |
|------|------|
| 序号 | 0507 |
| 完整哈希 | 212355e13b3a8c40441725a260ae6e69bb1a0a9e |
| 短哈希 | 212355e13 |
| 日期 | 2024-02-15 |
| 作者 | Eduard Tudenhoefner <etudenhoefner@gmail.com> |
| 提交说明 | Core: Add strictness flag to prevent loss of view representation when replacing a view (#9620) |
| PR | #9620 |

## 总体目的

本提交为 Iceberg 视图替换（replace view）操作引入了一个严格性校验机制，防止在替换视图时意外丢失已有的 SQL 方言（dialect）表示。在 Iceberg 中，一个视图版本可以包含多个 `ViewRepresentation`，其中 SQL 表示（`SQLViewRepresentation`）按方言（如 spark、trino）区分。当一个视图同时拥有 spark 和 trino 两种方言的查询时，如果用户仅用 spark 方言替换该视图，原有的 trino 方言查询就会丢失，这可能导致依赖 trino 方言的下游消费者无法使用该视图。

为了防止这种无声的数据丢失，本提交新增了一个视图属性 `replace.drop-dialect.allowed`（默认值为 `false`）。当该属性为 `false` 时，在 `build()` 阶段会检查新版本的方言集合是否是旧版本方言集合的超集——如果新版本缺少了旧版本中的某些方言，则抛出 `IllegalStateException`，阻止替换操作。用户可以通过显式设置 `replace.drop-dialect.allowed=true` 来允许方言丢失，从而兼容那些确实需要删除某些方言的场景。

此外，本提交还将方言比较改为大小写不敏感（使用 `toLowerCase(Locale.ROOT)`），确保 "Spark"、"spark"、"SPARK" 等不同大小写写法被视为同一方言，避免因大小写差异导致重复方言检查误判或方言丢失检查失效。

## 如何达成设计目的

实现路径包括四个方面：在 `ViewProperties` 中定义新的属性常量和默认值；在 `ViewMetadata.Builder` 中新增 `previousViewVersion` 字段，在 `buildFrom(base)` 构造函数中记录替换前的当前版本；在 `build()` 方法中，当 `previousViewVersion` 不为空且属性不允许丢方言时，调用 `checkIfDialectIsDropped` 进行校验；新增 `checkIfDialectIsDropped` 和 `sqlDialectsFor` 两个私有辅助方法提取并比较新旧版本的方言集合。同时将已有的方言去重检查也改为大小写不敏感，确保一致性。测试覆盖了默认拒绝、显式允许、保留旧表示、新增表示、切换版本号、以及允许后再次禁止等多种场景。

## 修改详情

### core/src/main/java/org/apache/iceberg/view/ViewMetadata.java

**修改目的**：实现方言丢失检测逻辑，并在替换视图时进行校验。

**工作逻辑**：

1. 新增 `import java.util.Locale`。

2. 在 `Builder` 中新增字段 `private ViewVersion previousViewVersion = null;`，用于记录替换前的当前视图版本。

3. 在 `buildFrom(ViewMetadata base)` 构造函数中，设置 `this.previousViewVersion = base.currentVersion();`，捕获替换前的版本状态。

4. 将 `addVersionInternal` 中方言去重检查改为大小写不敏感：
```java
Preconditions.checkArgument(
    dialects.add(sql.dialect().toLowerCase(Locale.ROOT)),
    "Invalid view version: Cannot add multiple queries for dialect %s",
    sql.dialect().toLowerCase(Locale.ROOT));
```

5. 在 `build()` 方法中，在 history 处理之后、historySize 处理之前，新增方言丢失检查：
```java
if (null != previousViewVersion
    && !PropertyUtil.propertyAsBoolean(
        properties,
        ViewProperties.REPLACE_DROP_DIALECT_ALLOWED,
        ViewProperties.REPLACE_DROP_DIALECT_ALLOWED_DEFAULT)) {
  checkIfDialectIsDropped(previousViewVersion, versionsById.get(currentVersionId));
}
```
仅当存在前一个版本（即替换场景）且属性未允许丢方言时才执行检查。

6. 新增 `checkIfDialectIsDropped(ViewVersion previous, ViewVersion current)` 方法：提取新旧版本的方言集合，使用 `Preconditions.checkState` 验证新版本方言集合包含旧版本的所有方言，否则抛出包含详细信息的 `IllegalStateException`。

7. 新增 `sqlDialectsFor(ViewVersion viewVersion)` 方法：遍历版本的 representations，提取所有 `SQLViewRepresentation` 的方言并转为小写存入 Set 返回。

### core/src/main/java/org/apache/iceberg/view/ViewProperties.java

**修改目的**：定义新的视图属性常量。

**工作逻辑**：新增两个常量：
- `REPLACE_DROP_DIALECT_ALLOWED = "replace.drop-dialect.allowed"` —— 属性键名
- `REPLACE_DROP_DIALECT_ALLOWED_DEFAULT = false` —— 默认值，即默认不允许丢失方言

### core/src/test/java/org/apache/iceberg/view/TestViewMetadata.java

**修改目的**：全面测试方言丢失检测逻辑。

**工作逻辑**：

1. 修改既有测试中的方言为 `"SpArK"`（混合大小写），验证大小写不敏感处理。

2. 新增 `droppingDialectFailsByDefault`：验证默认情况下从 spark 切换到 trino 会抛出 `IllegalStateException`，并检查错误消息格式。

3. 新增 `droppingDialectDoesNotFailWhenAllowed`：验证设置 `REPLACE_DROP_DIALECT_ALLOWED=true` 后可以成功替换方言。

4. 新增 `droppingDialectDoesNotFailWhenKeepingPreviousRepresentation`：验证新版本同时保留 spark 和新增 trino 时不会失败（方言只增不减）。

5. 新增 `droppingDialectDoesNotFailWhenAddingNewRepresentation`：验证从无表示到有表示不会失败（无前序方言可丢失）。

6. 新增 `droppingDialectFailsWhenSwitchingViewVersionId`：验证通过 `setCurrentVersionId` 切换到方言较少的版本时也会触发检查，且中间的版本切换会被忽略（只看最终状态）。

7. 新增 `droppingDialectAllowedAndThenDisallowed`：验证先允许替换后再禁止替换时的行为——允许替换后 trino 成为当前方言，再尝试切回 spark 时因不允许丢失 trino 而失败。

### core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java

**修改目的**：在共享的视图目录测试基类中，为涉及替换视图的测试添加 `REPLACE_DROP_DIALECT_ALLOWED=true` 属性，使这些测试能通过新的严格检查。

**工作逻辑**：在三个替换视图的测试场景中（约第 863、1093、1554 行），为 `buildView` 调用追加 `.withProperty(ViewProperties.REPLACE_DROP_DIALECT_ALLOWED, "true")`。这是因为这些测试原本就涉及方言变更（如从 trino 替换为 spark，或从单方言替换为双方言），在新默认值下需要显式允许才能通过。

### nessie/src/test/java/org/apache/iceberg/nessie/BaseTestIceberg.java

**修改目的**：为 Nessie 测试基类中创建视图的工具方法添加允许丢方言属性。

**工作逻辑**：在创建视图时追加 `.withProperty(ViewProperties.REPLACE_DROP_DIALECT_ALLOWED, "true")`，确保后续替换操作不受新的严格检查影响。

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

**修改目的**：为 Spark 3.4 扩展测试新增方言丢失保护相关测试。

**工作逻辑**：新增三个测试方法：
- `replacingTrinoViewShouldFail`：创建 trino 方言视图，尝试用 Spark SQL `CREATE OR REPLACE VIEW` 替换，验证抛出 `IllegalStateException`。
- `replacingTrinoAndSparkViewShouldFail`：创建含 trino+spark 双方言视图，尝试仅用 spark 替换，验证失败（丢失 trino）。
- `replacingViewWithDialectDropAllowed`：创建 trino 视图后，通过 `TBLPROPERTIES ('replace.drop-dialect.allowed'='true')` 允许丢方言，成功替换为 spark，并验证历史和版本记录正确。

### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

**修改目的**：为 Spark 3.5 扩展测试新增与 3.4 完全相同的三个方言丢失保护测试。

**工作逻辑**：与 Spark 3.4 的测试内容完全一致，只是位于 v3.5 模块中，确保两个 Spark 版本都有覆盖。

## 小结

本提交为视图替换操作引入了方言丢失保护机制，通过一个默认安全的属性 `replace.drop-dialect.allowed`（默认 false）防止用户在替换视图时意外丢失已有的 SQL 方言表示。核心实现依赖 `previousViewVersion` 字段记录替换前状态，并在 `build()` 时比较新旧方言集合。同时统一了方言比较的大小写不敏感处理。测试覆盖全面，包括核心单元测试、目录测试基类适配、以及 Spark 3.4/3.5 集成测试。
