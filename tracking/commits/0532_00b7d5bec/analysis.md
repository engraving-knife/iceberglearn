# 提交 0532：弃用 AWS DynamoDB Catalog

## 提交信息

- **序号**：0532 / 4088
- **哈希**：00b7d5bec31d1215eeac662ed522675447a5b359
- **短哈希**：00b7d5bec
- **日期**：2024-02-23（Fri Feb 23 08:41:01 2024 -0800）
- **作者**：Drew Gallardo <dru@amazon.com>
- **提交说明**：AWS: Deprecate DynamoDB catalog (#9783)
- **PR/Issue**：#9783

## 总体目的

Iceberg 早期提供了基于 AWS DynamoDB 的 Catalog 实现（`DynamoDbCatalog`），将表元数据存储在 DynamoDB 中。随着 AWS Glue Catalog 成为主推的 AWS 环境元数据存储方案，DynamoDB Catalog 的使用场景萎缩，维护成本相对其价值已不划算。本提交正式启动 DynamoDB Catalog 的弃用流程：在 Java 层面为相关类打上 `@Deprecated` 注解并在 Javadoc 中声明弃用版本与移除版本，向用户传递明确的迁移信号——该 Catalog 将在未来主版本中移除，新部署应改用 Glue Catalog 或 REST Catalog 等替代方案。

## 如何达成设计目的

设计思路采用 Java 标准的弃用机制，分两个层面表达弃用意图：

1. **编译期信号**：通过 `@Deprecated` 注解，使所有引用被弃用类的代码在编译时产生警告，迫使开发者在 IDE 与构建输出中注意到弃用提示，从而尽早规划迁移。
2. **文档期信号**：通过 Javadoc `@deprecated` 标签说明弃用起始版本与计划移除版本，为用户提供时间线预期，便于评估迁移紧迫性。

本次为两个紧密耦合的类同时打标：面向用户的 Catalog 入口类 `DynamoDbCatalog`，以及其内部使用的表操作实现类 `DynamoDbTableOperations`。两者一同弃用，保证弃用标记覆盖完整调用链，避免出现"入口已弃用但内部实现仍标榜可用"的矛盾状态。

> 注意：本提交初始声明的弃用版本为 `since 1.6.0, will be removed in 2.0.0`。该版本号在后续提交 0533（#9788）中被调整为 `since 1.5.0, will be removed in 2.0.0`，因为实际发布节奏使 1.5.0 才是引入弃用标记的版本。本分析以本提交实际写入的内容为准。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/dynamodb/DynamoDbCatalog.java`

**修改目的**：为 DynamoDB Catalog 的主入口类添加弃用标记，向所有使用者声明该实现即将移除。

**工作逻辑**：将原本单行的类注释 `/** DynamoDB implementation of Iceberg catalog */` 扩展为多行 Javadoc，新增 `@deprecated since 1.6.0, will be removed in 2.0.0` 标签；并在类声明 `public class DynamoDbCatalog extends BaseMetastoreCatalog` 之上添加 `@Deprecated` 注解。

修改后的类头如下：

```java
/**
 * DynamoDB implementation of Iceberg catalog
 *
 * @deprecated since 1.6.0, will be removed in 2.0.0
 */
@Deprecated
public class DynamoDbCatalog extends BaseMetastoreCatalog
    implements SupportsNamespaces, Configurable {
```

`@Deprecated` 注解无参数版本足以触发编译器警告；Javadoc 中的版本信息则供生成 API 文档时展示。`DynamoDbCatalog` 实现了 `SupportsNamespaces`（命名空间管理）与 `Configurable`（Hadoop 配置注入），是用户通过 `Catalog` 接口加载 DynamoDB 实现的入口类，弃用标记在此处曝光度最高。

### `aws/src/main/java/org/apache/iceberg/aws/dynamodb/DynamoDbTableOperations.java`

**修改目的**：为 DynamoDB Catalog 内部使用的表操作类同步添加弃用标记，保证弃用语义在实现链路上一致。

**工作逻辑**：在该类的类声明之上新增单行 Javadoc 弃用标签与 `@Deprecated` 注解：

```java
/** @deprecated since 1.6.0, will be removed in 2.0.0 */
@Deprecated
class DynamoDbTableOperations extends BaseMetastoreTableOperations {
```

`DynamoDbTableOperations` 是包级可见（`class`，无 `public` 修饰符）的实现类，继承自 `BaseMetastoreTableOperations`，负责具体的表元数据读写（commit、load 等）逻辑。虽然外部用户通常不直接引用此类，但为它打标可让维护者在内部调用点也收到弃用提示，并为未来真正移除时的代码清理提供完整覆盖。

两处弃用标签内容一致，均声明"since 1.6.0, will be removed in 2.0.0"，保持口径统一。

## 小结

**成效**：以最小改动（8 行新增、1 行删除）正式开启 DynamoDB Catalog 的弃用周期，通过 `@Deprecated` 注解与 Javadoc 双重信号向社区传递迁移预期，为后续版本移除该实现铺路。

**影响范围**：
- 对 API 兼容性无破坏：仅添加注解与文档，不改变任何运行时行为，现有使用 DynamoDB Catalog 的代码仍可正常工作，仅在编译时产生弃用警告。
- 对用户的影响：使用者将在编译日志与 IDE 提示中看到弃用警告，应开始评估迁移到 Glue Catalog 或 REST Catalog 的计划。
- 对维护者的影响：内部代码引用点也会收到警告，便于后续清理。

**回迁到 1.4.x 的注意事项**：
1. **版本号语义问题**：本提交声明的弃用版本是 `since 1.6.0`，但 1.4.x 分支早于该版本。若直接回迁到 1.4.x，会出现"在 1.4.x 中已被弃用，但标注为 since 1.6.0"的语义倒置（标注的弃用版本晚于实际包含弃用标记的版本）。建议结合 0533 提交（调整为 `since 1.5.0`）一并评估，必要时将版本号调整为适合 1.4.x 的值，或干脆不在 1.4.x 回迁此弃用标记。
2. 历史上存在 `e39ec185d AWS: Revert DynamoDb deprecation for 1.5.0 (#9815)` 这一回退提交，说明该弃用标记在 1.5.0 发布分支上曾被回退。回迁到 1.4.x 时需考虑这一背景，避免在维护版本中引入已被上游撤回的弃用声明。
3. 该改动纯增量、无逻辑变更，回迁冲突风险极低，唯一需要决策的是"是否应在 1.4.x 这个更早的维护分支上声明弃用"。
