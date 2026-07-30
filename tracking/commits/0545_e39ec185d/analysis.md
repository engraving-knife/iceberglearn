# 提交 0545：回退 AWS DynamoDB Catalog 的弃用声明（针对 1.5.0）

## 提交信息

- **序号**：0545 / 4088
- **哈希**：e39ec185d7879c1a310769d33e0b1b6ad12486a9
- **短哈希**：e39ec185d
- **日期**：2024-02-27（AuthorDate 2024-02-27 14:11:51 +0530，CommitDate 2024-02-27 09:41:51 +0100）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：AWS: Revert DynamoDb deprecation for 1.5.0 (#9815)
- **PR/Issue**：#9815。本提交回退 0532（PR #9783，"AWS: Deprecate DynamoDB catalog"）与 0533（PR #9788，"AWS: Adjust Deprecation Version for DynamoDB Catalog to 1.5.0"）两个提交联合引入的弃用声明。

## 总体目的

本提交要撤销 0532 + 0533 在 1.5.0 版本线上为 AWS DynamoDB Catalog 引入的弃用标记，使 DynamoDB Catalog 在 1.5.0 发布时**不再处于弃用状态**。

**背景**：0532（2024-02-23）首次为 `DynamoDbCatalog` 与 `DynamoDbTableOperations` 两个类添加 `@Deprecated` 注解与 Javadoc 弃用声明（初始版本号 `since 1.6.0`）。0533（同日稍晚）把弃用起始版本从 1.6.0 调整为 1.5.0，并在用户文档 `docs/docs/aws.md` 补充了弃用提示。两个提交的意图是正式启动 DynamoDB Catalog 的弃用周期，引导用户迁移到 Glue Catalog 或 REST Catalog。

然而仅 4 天后（2024-02-27），本提交就把这两处弃用声明全部回退。回退的直接原因（从提交说明"Revert DynamoDb deprecation for 1.5.0"可推断）是：弃用决定在 1.5.0 发布前夕被重新评估并认为过于仓促——DynamoDB Catalog 仍有实际使用群体，在尚未提供充分迁移路径与替代方案的情况下将其标记为弃用，会对现有用户造成不必要的紧迫感与编译期警告噪音。因此社区决定 1.5.0 不弃用 DynamoDB Catalog，留待后续版本再评估。

## 如何达成设计目的

设计思路是精确反向操作 0532 + 0533 的改动，把三个文件恢复到弃用声明引入之前的状态：

1. **`DynamoDbCatalog.java`**：删除 `@Deprecated` 注解，把多行 Javadoc（含 `@deprecated since 1.5.0, will be removed in 2.0.0`）回退为原始的单行注释 `/** DynamoDB implementation of Iceberg catalog */`。
2. **`DynamoDbTableOperations.java`**：删除 `@Deprecated` 注解与单行弃用 Javadoc `/** @deprecated since 1.5.0, will be removed in 2.0.0 */`（该类原本没有类级 Javadoc，回退后同样不带 Javadoc）。
3. **`docs/docs/aws.md`**：删除 0533 在 `### DynamoDB Catalog` 标题下插入的 `**Deprecated:** As of version 1.5.0, the DynamoDB Catalog is planned for deprecation in version 2.0.0.` 一行。

三处改动合计 +1/-9 行（净 -8 行），与 0532 + 0533 引入的弃用标记在量级上对应。回退后，DynamoDB Catalog 在代码层与文档层均不再有任何弃用信号，编译期不再产生弃用警告，用户文档也不再提示弃用计划。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/dynamodb/DynamoDbCatalog.java`

**修改目的**：移除 DynamoDB Catalog 入口类的弃用标记，恢复其正常（非弃用）状态。

**工作逻辑**：把类头从

```java
/**
 * DynamoDB implementation of Iceberg catalog
 *
 * @deprecated since 1.5.0, will be removed in 2.0.0
 */
@Deprecated
public class DynamoDbCatalog extends BaseMetastoreCatalog
    implements SupportsNamespaces, Configurable {
```

回退为

```java
/** DynamoDB implementation of Iceberg catalog */
public class DynamoDbCatalog extends BaseMetastoreCatalog
    implements SupportsNamespaces, Configurable {
```

即：删除 `@Deprecated` 注解一行，把多行 Javadoc 压回为单行 `/** DynamoDB implementation of Iceberg catalog */`。回退后该类不再触发编译器弃用警告，API 文档也不再显示弃用标识。

### `aws/src/main/java/org/apache/iceberg/aws/dynamodb/DynamoDbTableOperations.java`

**修改目的**：移除内部表操作类的弃用标记，与 `DynamoDbCatalog` 保持一致。

**工作逻辑**：把类头从

```java
/** @deprecated since 1.5.0, will be removed in 2.0.0 */
@Deprecated
class DynamoDbTableOperations extends BaseMetastoreTableOperations {
```

回退为

```java
class DynamoDbTableOperations extends BaseMetastoreTableOperations {
```

即：删除 `@Deprecated` 注解与单行弃用 Javadoc 共两行。回退后该类不再有类级 Javadoc（与弃用声明引入前的原始状态一致）。

### `docs/docs/aws.md`

**修改目的**：移除用户文档中的 DynamoDB Catalog 弃用提示。

**工作逻辑**：删除 `### DynamoDB Catalog` 标题正下方的弃用声明行

```markdown
**Deprecated:** As of version 1.5.0, the DynamoDB Catalog is planned for deprecation in version 2.0.0.
```

回退后该章节直接进入正文"Iceberg supports using a [DynamoDB](https://aws.amazon.com/dynamodb) table to record and manage database and table information."，不再有弃用提示。

## 小结

**成效**：本提交以精确的反向操作（+1/-9 行）撤销了 0532 + 0533 引入的 DynamoDB Catalog 弃用声明，使 1.5.0 发布版本不包含对该 Catalog 的弃用标记。回退后 DynamoDB Catalog 恢复为正常维护状态，用户在 1.5.0 上使用该 Catalog 不会收到编译期弃用警告，文档也不再提示弃用计划。

**影响范围**：
- 代码层面：仅移除注解与 Javadoc 文本，无任何运行时行为变化，无 API 兼容性影响。
- 文档层面：用户文档不再提及 DynamoDB Catalog 弃用。
- 用户影响：使用 DynamoDB Catalog 的用户不会在 1.5.0 上被弃用警告打扰，可继续正常使用。

**与 0532/0533 的关联**：0545 是 0532（PR #9783，引入弃用）+ 0533（PR #9788，调整版本号到 1.5.0 并补文档）的完整回退。三个提交构成一条"引入弃用 → 校正版本 → 撤回弃用"的完整轨迹，时间跨度仅 4 天（2024-02-23 引入，2024-02-27 回退）。0532 与 0533 由 Drew Gallardo 提交，0545 由 Ajantha Bhat 提交，作者变更也反映了这是一次社区层面的重新评估而非原作者的自我修正。回退后，DynamoDB Catalog 的弃用问题被搁置，留待后续版本（可能在 2.0.0 周期）再讨论。

**回迁到 1.4.x 的注意事项**：

1. **1.4.x 无需回迁本提交**：1.4.x 是早于 1.5.0 的维护分支，0532/0533 的弃用声明从未回迁到 1.4.x（如 0532/0533 分析所述，回迁到 1.4.x 会带来版本号语义倒置问题）。既然 1.4.x 上从未引入弃用标记，也就不存在需要回退的弃用声明。本提交对 1.4.x 而言是 no-op。
2. **回迁决策的印证**：0532 与 0533 的分析均已指出"上游存在回退提交 e39ec185d，回迁到 1.4.x 时应考虑这一背景"。本提交正是那个回退提交，它印证了 0532/0533 分析中的建议——不在 1.4.x 上引入该弃用声明是正确决策。
3. **若 1.4.x 误已回迁 0532/0533**：则需同样回迁本提交以保持与上游一致。但正常情况下 1.4.x 不应包含弃用标记，无需此操作。
4. **未来观察**：DynamoDB Catalog 的弃用问题在上游被搁置，若后续版本重新启动弃用流程，1.4.x 作为更老的维护分支大概率仍不需跟进。维护者应关注上游在 1.6.0/2.0.0 周期是否重新讨论此问题。
