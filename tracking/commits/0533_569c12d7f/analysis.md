# 提交 0533：将 DynamoDB Catalog 的弃用版本调整为 1.5.0

## 提交信息

- **序号**：0533 / 4088
- **哈希**：569c12d7fe415bfba086458b3668f9deadfb17ec
- **短哈希**：569c12d7f
- **日期**：2024-02-23（Fri Feb 23 11:29:54 2024 -0800）
- **作者**：Drew Gallardo <dru@amazon.com>
- **提交说明**：AWS: Adjust Deprecation Version for DynamoDB Catalog to 1.5.0 (#9788)
- **PR/Issue**：#9788

## 总体目的

本提交是 0532（#9783，弃用 DynamoDB Catalog）的紧后继修复。0532 在 `DynamoDbCatalog` 与 `DynamoDbTableOperations` 两个类上添加了 `@Deprecated` 注解，但 Javadoc 中声明的弃用起始版本写成了 `since 1.6.0`。根据 Iceberg 实际的发布节奏，引入弃用标记的版本将是 1.5.0 而非 1.6.0。若保留错误版本号，会向用户传递不准确的时间线（让用户误以为弃用从更晚的版本才开始），从而削弱弃用信号的及时性。

本提交的目的有二：

1. **修正版本号**：将两个类 Javadoc 中的 `since 1.6.0` 改为 `since 1.5.0`，使弃用声明与实际包含该标记的发布版本一致。
2. **补全用户文档**：在面向用户的 AWS 文档 `docs/docs/aws.md` 的 DynamoDB Catalog 章节添加醒目的弃用提示，让不查看 Javadoc 的用户也能在文档站点获悉弃用计划。

## 如何达成设计目的

设计思路是对 0532 的"编码已完成、信息有偏差、用户触达不足"做三点补强：

1. **版本号对齐**：直接修改两处 Javadoc 的 `@deprecated` 文本，将 `1.6.0` 替换为 `1.5.0`。`@Deprecated` 注解本身不携带版本信息，版本语义完全由 Javadoc 文本承载，因此只需改文本即可。
2. **文档侧告知**：在 `docs/docs/aws.md` 的 `### DynamoDB Catalog` 小节标题正下方插入一行加粗的弃用声明，采用文档站点常见的"Deprecated:"前缀写法，与文档其余部分的提示风格一致，确保用户在阅读该 Catalog 配置说明前就先看到弃用信息。
3. **保持移除版本不变**：`will be removed in 2.0.0` 保持不变，移除时间线未调整，仅修正起始版本。

这种"注解 + Javadoc + 用户文档"的三层弃用信号体系，是 Iceberg 处理弃用功能的通行做法，确保编译期、API 文档、用户手册三个渠道都能传递一致的弃用信息。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/dynamodb/DynamoDbCatalog.java`

**修改目的**：修正 Catalog 入口类 Javadoc 中的弃用起始版本，使其与实际发布版本一致。

**工作逻辑**：单行文本替换，将 `@deprecated since 1.6.0, will be removed in 2.0.0` 改为 `@deprecated since 1.5.0, will be removed in 2.0.0`。`@Deprecated` 注解保留不动，仅修正 Javadoc 文本中的版本数字。修改后类头为：

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

### `aws/src/main/java/org/apache/iceberg/aws/dynamodb/DynamoDbTableOperations.java`

**修改目的**：同步修正内部表操作类的弃用起始版本，保持两个类的弃用声明口径一致。

**工作逻辑**：单行文本替换，将单行 Javadoc `/** @deprecated since 1.6.0, will be removed in 2.0.0 */` 改为 `/** @deprecated since 1.5.0, will be removed in 2.0.0 */`。与 `DynamoDbCatalog` 的修改保持完全一致，避免两个相关类出现版本号分歧。

### `docs/docs/aws.md`

**修改目的**：在 AWS 文档的 DynamoDB Catalog 章节添加面向用户的弃用声明，扩大弃用信息的触达面。

**工作逻辑**：在 `### DynamoDB Catalog` 标题行之后、原有说明正文之前，插入一行加粗的弃用提示：

```markdown
### DynamoDB Catalog
**Deprecated:** As of version 1.5.0, the DynamoDB Catalog is planned for deprecation in version 2.0.0.

Iceberg supports using a [DynamoDB](https://aws.amazon.com/dynamodb) table to record and manage database and table information.
```

该提示采用 `**Deprecated:**` 加粗前缀，紧跟在章节标题下，是用户进入该章节后看到的第一行内容，确保弃用信息优先于配置说明被注意到。措辞与 Javadoc 中的版本号（1.5.0 起弃用、2.0.0 移除）严格一致，避免用户在文档与 API 之间看到矛盾信息。

## 小结

**成效**：以 3 行改动完成对 0532 弃用声明的一次性校正——修正两处 Javadoc 版本号、补一处用户文档弃用提示，使 DynamoDB Catalog 的弃用信号在版本口径与触达渠道上同时完备。

**影响范围**：
- 代码层面：仅改动注释文本，无任何运行时行为变化，无 API 兼容性影响。
- 文档层面：用户在阅读 AWS 文档时将明确看到 DynamoDB Catalog 的弃用计划，有助于推动迁移。
- 与 0532 配合：本提交是对 0532 的补丁，二者共同构成 DynamoDB Catalog 弃用声明的完整形态。

**回迁到 1.4.x 的注意事项**：
1. **版本号语义问题（关键）**：1.4.x 早于 1.5.0。若将本提交（声明 `since 1.5.0`）回迁到 1.4.x，会出现"在 1.4.x 中已被标记弃用，但 Javadoc 声称弃用自 1.5.0"的语义倒置——标注的弃用版本晚于实际包含弃用标记的版本。这与 0532 回迁面临的同类问题一致且更尖锐（1.5.0 比 1.6.0 离 1.4.x 更近，但仍是倒置）。
2. **历史回退背景**：上游存在 `e39ec185d AWS: Revert DynamoDb deprecation for 1.5.0 (#9815)` 提交，表明该弃用声明（含本提交的 1.5.0 版本号）在 1.5.0 发布分支上曾被整体回退。这意味着即便在 1.5.0 上，弃用声明最终也未被保留。回迁到 1.4.x 时应充分考虑这一上游决策：在一个比 1.5.0 更早且上游已撤回弃用的分支上重新引入弃用声明，可能并不合适。
3. **建议**：若 1.4.x 确需提示用户 DynamoDB Catalog 的长期退役计划，可考虑只回迁 `docs/docs/aws.md` 的文档提示（不带具体版本号，或标注为"上游已计划弃用"），而不回迁代码层的 `@Deprecated` 注解，以避免版本号语义倒置与编译期警告对 1.4.x 现有用户造成困扰。
4. 文档改动 `docs/docs/aws.md` 与代码改动相互独立，可单独回迁文档部分。
