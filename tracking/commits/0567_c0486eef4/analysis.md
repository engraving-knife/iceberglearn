# 提交 0567：Nessie: Gracefully handle empty namespace lookup (#9877)

## 提交信息

- **序号**：0567 / 4088
- **哈希**：c0486eef4d8bce7089f688d95a39ebccd68a7623
- **短哈希**：c0486eef4
- **日期**：2024-03-07 14:28:23 +0530
- **作者**：Ajantha Bhat
- **提交说明**：Nessie: Gracefully handle empty namespace lookup (#9877)
- **PR/Issue**：#9877

## 总体目的

本提交修复 Nessie catalog 客户端 `NessieIcebergClient` 在面对"空命名空间（empty namespace）"操作时行为不一致、错误信息误导用户的问题。

在 Iceberg 中，`Namespace` 由若干层（levels）组成，`Namespace.empty()` 表示一个层级为空的命名空间（即根命名空间）。Nessie 后端并不支持根命名空间作为一个可操作的实体——Nessie 的 `ContentKey` 由 `namespace.levels()` 构造，空 namespace 会得到空 key，无法映射到任何真实的 Nessie 内容。因此所有对空 namespace 的操作都应该被拒绝。

但本提交前，`NessieIcebergClient` 中只有 `createNamespace` 一处对空 namespace 做了检查，且抛出的是 `IllegalArgumentException("Creating empty namespaces is not supported")`。其他几个会触及 namespace 的方法（`loadNamespaceMetadata`、`dropNamespace`、`setProperties`、`removeProperties`，后两者内部调用 `updateProperties`）都没有任何前置检查，会直接把空 key 传给 Nessie API，导致：

- `loadNamespaceMetadata(Namespace.empty())` 不会报"无效"，而是去 Nessie 查询空 key，查不到内容后抛 `NoSuchNamespaceException("Namespace does not exist: ")`（注意末尾是空字符串，因为空 namespace 的 `toString()` 是空），消息具有误导性——用户会以为 namespace "不存在"，而实际上根本就是"无效"。
- `dropNamespace`、`setProperties`、`removeProperties` 行为类似，错误信息或异常类型不一致。

本提交的目标是把"空 namespace 即无效"这一约束集中化、统一化：抽出公共校验方法 `checkNamespaceIsValid`，在所有相关公共方法入口调用，统一抛出 `NoSuchNamespaceException("Invalid namespace: %s", namespace)`，使错误类型与消息在所有操作上保持一致，且语义正确（"无效"而非"不存在"）。

## 如何达成设计目的

整体设计思路是"前置校验 + 统一异常"：

1. **抽出公共校验方法**：新增私有静态方法 `checkNamespaceIsValid(Namespace namespace)`，当 `namespace.isEmpty()` 为真时抛 `NoSuchNamespaceException("Invalid namespace: %s", namespace)`。`NoSuchNamespaceException` 是 Iceberg 的标准异常类型（位于 `org.apache.iceberg.exceptions`），表示命名空间操作失败，比 `IllegalArgumentException` 更贴合语义，且是 catalog 调用方通常已经捕获的异常类型。

2. **在所有相关公共方法入口调用**：把 `checkNamespaceIsValid` 调用插入到 `createNamespace`、`dropNamespace`、`loadNamespaceMetadata`、`updateProperties` 四个方法的入口（`updateProperties` 同时被 `setProperties` 和 `removeProperties` 复用，因此一处插入覆盖两个公共方法）。注意插入位置在 `getRef().checkMutable()` 之前——即先做参数校验再做引用可变性校验，符合"参数合法性优先"的惯例。

3. **替换原有的 `createNamespace` 内联检查**：原 `createNamespace` 中的 `if (namespace.isEmpty()) throw new IllegalArgumentException(...)` 被删除，由统一的 `checkNamespaceIsValid` 取代。这里有两个语义变化：
   - 异常类型从 `IllegalArgumentException` 改为 `NoSuchNamespaceException`；
   - 消息从 "Creating empty namespaces is not supported" 改为 "Invalid namespace: " + namespace.toString()（空 namespace 时为 "Invalid namespace: "）。
   
   这是有意为之的破坏性变更：`NoSuchNamespaceException` 更符合 catalog API 的异常约定（Iceberg 其他 catalog 如 JdbcCatalog、HadoopCatalog 在 namespace 不存在/无效时也抛此异常），统一后调用方只需捕获一种异常。

4. **测试覆盖**：在 `TestNamespace` 中新增 `testEmptyNamespace`，对空 namespace 调用 6 个公共方法（create、namespaceExists、loadNamespaceMetadata、setProperties、removeProperties、dropNamespace），断言除 `namespaceExists` 返回 false 外，其余均抛 `NoSuchNamespaceException` 且消息以 "Invalid namespace: " 开头；`namespaceExists` 因为是"探测"语义，返回 false 而非抛异常。同时更新 `TestNessieIcebergClient` 中已有用例，把对 `createNamespace(empty)` 的期望从 `IllegalArgumentException` + "Creating empty namespaces is not supported" 改为 `NoSuchNamespaceException` + "Invalid namespace: "。

## 修改详情

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieIcebergClient.java`

**修改目的**：集中化空 namespace 校验，统一异常类型与消息。

**工作逻辑**：

- 新增私有静态方法 `checkNamespaceIsValid(Namespace namespace)`：

  ```java
  private static void checkNamespaceIsValid(Namespace namespace) {
    if (namespace.isEmpty()) {
      throw new NoSuchNamespaceException("Invalid namespace: %s", namespace);
    }
  }
  ```
  
  这里用 `NoSuchNamespaceException` 的格式化构造器（`%s` 占位符），把 namespace 拼进消息。对空 namespace 而言，最终消息是 "Invalid namespace: "（末尾空字符串）。

- `createNamespace`：在 `getRef().checkMutable()` 之前调用 `checkNamespaceIsValid(namespace)`，并删除原内联的 `if (namespace.isEmpty()) throw new IllegalArgumentException(...)`。这样异常类型从 `IllegalArgumentException` 升级为 `NoSuchNamespaceException`。

- `dropNamespace`：在 `getRef().checkMutable()` 之前调用 `checkNamespaceIsValid(namespace)`。原本此方法对空 namespace 没有任何前置检查，会直接构造空 `ContentKey` 并尝试 `api.delete()`，行为未定义；现在统一抛 `NoSuchNamespaceException`。

- `loadNamespaceMetadata`：在方法入口（`ContentKey key = ContentKey.of(...)` 之前）调用 `checkNamespaceIsValid(namespace)`。原本空 namespace 会走到 `api.getContent()` 查空 key，查不到后抛 `NoSuchNamespaceException("Namespace does not exist: ")`（消息误导）；现在改为前置抛 `NoSuchNamespaceException("Invalid namespace: ")`，语义正确。

- `updateProperties`（私有，被 `setProperties`、`removeProperties` 调用）：在 `getRef().checkMutable()` 之前调用 `checkNamespaceIsValid(namespace)`。这样两个公共方法都受到保护。

注意：`namespaceExists`、`listNamespaces`、`listTables` 等只读/列举方法没有被加上校验——这与"空 namespace 在只读场景下应被视为合法根"的语义一致（例如 `listNamespaces(Namespace.empty())` 列出顶层命名空间是合理操作），不需要拒绝。`namespaceExists(Namespace.empty())` 在测试中验证返回 false，符合"探测"语义。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNamespace.java`

**修改目的**：新增针对空 namespace 的端到端测试，覆盖所有受影响的公共方法。

**工作逻辑**：新增 `testEmptyNamespace` 测试方法，依次验证：

1. `catalog.createNamespace(Namespace.empty(), Collections.emptyMap())` → `NoSuchNamespaceException` + "Invalid namespace: "。
2. `catalog.namespaceExists(Namespace.empty())` → 返回 `false`（不抛异常，因为是探测语义）。
3. `catalog.loadNamespaceMetadata(Namespace.empty())` → `NoSuchNamespaceException` + "Invalid namespace: "。
4. `catalog.setProperties(Namespace.empty(), ImmutableMap.of("prop2", "val2", "prop", "val"))` → `NoSuchNamespaceException` + "Invalid namespace: "。
5. `catalog.removeProperties(Namespace.empty(), ImmutableSet.of("prop2"))` → `NoSuchNamespaceException` + "Invalid namespace: "。
6. `catalog.dropNamespace(Namespace.empty())` → `NoSuchNamespaceException` + "Invalid namespace: "。

测试用 AssertJ 的 `assertThatThrownBy(...).isInstanceOf(...).hasMessage(...)` 链式断言，消息断言用 `hasMessage("Invalid namespace: ")`（精确匹配，注意末尾空格——因为空 namespace 的 `toString()` 为空字符串）。新增 import `Collections`、`ImmutableSet`。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieIcebergClient.java`

**修改目的**：更新已有用例以匹配新的异常类型与消息。

**工作逻辑**：原用例使用 AssertJ 的 `assertThatIllegalArgumentException().isThrownBy(...).withMessageContaining("Creating empty namespaces is not supported")`，改为 `assertThatThrownBy(() -> client.createNamespace(Namespace.empty(), Map.of())).isInstanceOf(NoSuchNamespaceException.class).hasMessageContaining("Invalid namespace: ")`。这反映了异常类型与消息的变更。

## 小结

本提交通过抽取 `checkNamespaceIsValid` 私有方法并在 4 个公共方法入口统一调用，使 Nessie catalog 对空 namespace 的处理从"仅 createNamespace 检查且抛 IllegalArgumentException"变为"所有写/读操作统一抛 NoSuchNamespaceException 并带 'Invalid namespace: ' 消息"，错误类型与消息在所有操作上保持一致，语义更准确（"无效"而非"不存在"）。新增的 `testEmptyNamespace` 测试覆盖 6 个公共方法，确保行为一致性。

回迁到 1.4.x 的注意事项：
1. **异常类型变更可能破坏调用方**：原 `createNamespace(Namespace.empty())` 抛 `IllegalArgumentException`，回迁后改为 `NoSuchNamespaceException`。若有下游代码（包括 Iceberg 自身其他模块或用户代码）显式捕获 `IllegalArgumentException` 来处理此场景，回迁后会落入未捕获路径。需检查 1.4.x 中是否有此类捕获。
2. **消息变更**：原消息 "Creating empty namespaces is not supported" 变为 "Invalid namespace: "，若下游有基于消息文本的判断（不推荐但可能存在），需同步调整。
3. 本提交仅改 Nessie 模块，与 core 无耦合，回迁范围小、风险低。Nessie 相关基础设施（`NessieIcebergClient`、测试基类 `BaseTestIceberg`）在 1.4.x 应已存在，可直接回迁。
4. 测试用 `hasMessage("Invalid namespace: ")` 精确匹配，回迁时需注意空 namespace 的 `toString()` 行为——若 `Namespace.toString()` 实现在不同版本间有差异（理论上不应有），可能需要调整为 `hasMessageStartingWith("Invalid namespace:")`。
