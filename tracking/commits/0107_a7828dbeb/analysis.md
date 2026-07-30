# 提交 0107：API, Core: Add uuid API to Table (#8800)

## 提交信息

- **序号**：0107 / 4088
- **哈希**：a7828dbeb2ef644169f83de1288a4e00f6c6cdca
- **短哈希**：a7828dbeb
- **日期**：2023-10-30 15:02:16 +0100
- **作者**：Amogh Jahagirdar
- **提交说明**：API, Core: Add uuid API to Table (#8800)
- **PR/Issue**：#8800

## 总体目的

这个提交在 Iceberg 的 `Table` 接口上引入了一个新的 `uuid()` API，用于以 `java.util.UUID` 的形式获取表的唯一标识符。

在此之前，表的 UUID 只能通过较底层的方式访问：要么强转为 `BaseTable` 再调用 `operations().current().uuid()` 得到字符串形式的 UUID，要么直接读取 `TableMetadata#uuid()`。这种访问方式既不统一（不同 `Table` 实现暴露 UUID 的方式不一致），也不安全（需要依赖具体实现类），更不适合在序列化、跨进程或引擎集成场景下使用。引擎（如 Spark、Flink）和上层工具往往需要一个稳定、类型友好的表标识，用于日志关联、缓存键、表身份校验等。

本提交通过在 `Table` 接口层提供一个 `default UUID uuid()` 方法，把“取表 UUID”这一能力统一暴露到公共 API 层。`default` 实现默认抛出 `UnsupportedOperationException`，以保持与既有第三方 `Table` 实现的二进制兼容性，同时要求 Iceberg 自家的核心实现去覆盖它。这对 Iceberg API 的演进有意义：它把原先散落在 `TableMetadata`/`TableOperations` 里的表身份信息提升为一等公民的 `Table` 接口能力，便于后续在 REST catalog、引擎集成、表引用比对等场景统一使用。

## 如何达成设计目的

整体设计分为三部分：在 API 层的 `Table` 接口新增带默认异常实现的 `uuid()` 方法；在 Core 层的各个 `Table` 实现类中分别覆盖 `uuid()`，按各自能拿到的元数据返回正确的 `UUID`；在通用 Catalog 测试基类 `CatalogTests` 中补充断言，确保所有 Catalog 实现创建的表都能返回与底层元数据一致的 UUID。

各实现按其元数据来源分别处理：`BaseTable` 和 `BaseTransaction` 的内部表视图直接从 `TableOperations#current().uuid()`（字符串）解析为 `UUID`；`SerializableTable` 在构造时把 `table.uuid()` 缓存为 `final` 字段，序列化后直接返回该字段；`BaseMetadataTable`（元数据表，如 `files`、`history` 等）由于并不对应一张真实的物理表，返回 `UUID.randomUUID()`，即每次调用生成一个新的随机 UUID，避免与底层物理表的 UUID 混淆。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Table.java`

**修改目的**：在 `Table` 接口上新增 `uuid()` API，作为获取表唯一标识的统一入口。

**工作逻辑**：

- 新增 `import java.util.UUID;`。
- 在 `refs()` 方法之后新增 `default UUID uuid()` 方法：

  ```java
  /**
   * Returns the UUID of the table
   *
   * @return the UUID of the table
   */
  default UUID uuid() {
    throw new UnsupportedOperationException(this.getClass().getName() + " doesn't implement uuid");
  }
  ```

  使用 `default` 方法并默认抛出 `UnsupportedOperationException`，是 Iceberg 在新增接口方法时保持二进制兼容性的惯用做法：既有的第三方 `Table` 实现无需立即改造即可继续编译/运行，而异常信息中包含具体类名，便于在未实现时快速定位。返回类型选用 `java.util.UUID` 而非字符串，提供更强的类型安全和校验（构造时即校验格式）。

### `core/src/main/java/org/apache/iceberg/BaseTable.java`

**修改目的**：为 Iceberg 最主要的物理表实现 `BaseTable` 提供 `uuid()` 的真正实现。

**工作逻辑**：

- 新增 `import java.util.UUID;`。
- 在 `refs()` 之后覆盖 `uuid()`：

  ```java
  @Override
  public UUID uuid() {
    return UUID.fromString(ops.current().uuid());
  }
  ```

  `BaseTable` 持有 `TableOperations ops`，通过 `ops.current()` 拿到当前 `TableMetadata`，其 `uuid()` 返回字符串形式的 UUID（来自元数据文件中的 `table-uuid` 字段），再用 `UUID.fromString(...)` 解析为强类型 `UUID`。这是最标准的“物理表 UUID”获取路径。

### `core/src/main/java/org/apache/iceberg/BaseMetadataTable.java`

**修改目的**：为元数据表基类 `BaseMetadataTable` 提供 `uuid()` 实现。

**工作逻辑**：

- 新增 `import java.util.UUID;`。
- 覆盖 `uuid()`：

  ```java
  @Override
  public UUID uuid() {
    return UUID.randomUUID();
  }
  ```

  `BaseMetadataTable` 是 `files`、`history`、`snapshots` 等元数据表的基类，它们是基于某张物理表派生出的“虚拟视图”，并不拥有自己的稳定 UUID。这里返回 `UUID.randomUUID()`，意味着每次调用都会得到一个新的随机 UUID。这种实现是有意为之：元数据表没有持久的、可比对的身份标识，用随机 UUID 既满足了接口契约（返回非 null 的 `UUID`），又避免与底层物理表的 UUID 产生混淆或被误用于身份校验。

### `core/src/main/java/org/apache/iceberg/BaseTransaction.java`

**修改目的**：为事务中的表视图 `BaseTransaction.TableTransaction` 提供 `uuid()` 实现。

**工作逻辑**：

- 新增 `import java.util.UUID;`。
- 在内部类 `TableTransaction`（`BaseTransaction` 实现的 `Transaction` 内部持有的 `Table` 视图）中覆盖 `uuid()`：

  ```java
  @Override
  public UUID uuid() {
    return UUID.fromString(current.uuid());
  }
  ```

  其中 `current` 是事务当前阶段的 `TableMetadata`。这与 `BaseTable` 的实现逻辑一致——从当前元数据取出字符串 UUID 并解析。保证在事务进行中调用 `uuid()` 也能拿到与当前元数据一致的身份标识。

### `core/src/main/java/org/apache/iceberg/SerializableTable.java`

**修改目的**：为可序列化的表实现 `SerializableTable` 提供 `uuid()` 实现，并确保序列化前后 UUID 一致。

**工作逻辑**：

- 新增 `import java.util.UUID;`。
- 新增 `private final UUID uuid;` 字段。
- 在构造函数 `protected SerializableTable(Table table)` 中追加 `this.uuid = table.uuid();`，在构造时就把源表的 UUID 取出并缓存为 `final` 字段。
- 覆盖 `uuid()`：

  ```java
  @Override
  public UUID uuid() {
    return uuid;
  }
  ```

  `SerializableTable` 的设计目标是被序列化后分发到 executor（如 Spark 任务），序列化后不再持有原始 `Table`/`TableOperations` 引用，无法再现场读取元数据。因此必须在构造时把 UUID 物化进对象，序列化后直接返回该字段。这与该类对 `schema`、`specs` 等其他元数据的处理方式（构造时缓存或懒加载 + transient）保持一致，但因为 UUID 是轻量且不可变的，直接用 `final` 字段最简单可靠。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java`

**修改目的**：在通用 Catalog 测试基类中补充对 `uuid()` 的断言，确保所有 Catalog 实现创建的表都能返回与底层元数据一致的 UUID。

**工作逻辑**：

- 新增 `import java.util.UUID;`。
- 在已有的建表测试方法中（断言表属性是请求属性超集之后）追加：

  ```java
  Assertions.assertThat(table.uuid())
      .isEqualTo(UUID.fromString(((BaseTable) table).operations().current().uuid()));
  ```

  这里把 `table.uuid()` 的返回值与“通过底层 `TableOperations.current().uuid()` 取到的字符串再解析”做对比。由于 `CatalogTests` 是所有 Catalog（Hive、JDBC、REST、Hadoop、 Nessie 等）的通用测试基类，这一断言会强制每种 Catalog 在建表后都正确实现了 `uuid()`，防止后续实现遗漏。注意断言里把 `table` 强转为 `BaseTable` 仅为测试校验用途（拿底层元数据当“真值”），并不代表 API 层的推荐用法。

## 小结

这个提交把“取表 UUID”这一能力从底层 `TableMetadata` 提升为 `Table` 接口的一等 API，并在 Iceberg 全部核心 `Table` 实现（物理表、元数据表、事务视图、可序列化表）中给出正确实现，辅以通用 Catalog 测试基类的断言兜底，统一了表身份标识的访问方式，为引擎集成和后续 REST/序列化场景打下基础。
