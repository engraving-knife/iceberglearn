# 提交 0569：Core: Make constants in CatalogTests protected (#9894)

## 提交信息

- **序号**：0569 / 4088
- **哈希**：80c92cd75e9fc71130c3ae0f847eee2852336368
- **短哈希**：80c92cd75
- **日期**：2024-03-07 16:47:09 +0100
- **作者**：Robert Stupp
- **提交说明**：Core: Make constants in CatalogTests protected (#9894)
- **PR/Issue**：#9894

## 总体目的

本提交将 `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` 中 13 个静态常量的访问修饰符统一改为 `protected`，使子类能够引用这些常量。

`CatalogTests<C extends Catalog & SupportsNamespaces>` 是 Iceberg core 模块中所有 catalog 实现共享的抽象测试基类（约 3189 行），定义了标准 catalog 必须通过的一整套测试用例（创建/加载/删除表、命名空间管理、事务、分支标签等）。各具体 catalog 实现（HadoopCatalog、JdbcCatalog、HiveCatalog、RESTCatalog、Nessie 等）的测试类都继承它，自动获得这套测试覆盖。

本提交前，该类中有部分常量是 `private`（8 个）或包级私有（无修饰符，5 个），子类无法引用。这给"子类想复用基类定义的标准 schema、partition spec、sort order、data file 等测试夹具来编写 catalog 专属补充测试"带来不便——子类要么自己重新构造等价的 schema/spec/file，要么通过 `protected` getter 方法间接访问，两者都增加了样板代码与维护成本。本提交把所有这些常量统一改为 `protected`，让子类直接复用，减少重复定义。

## 如何达成设计目的

整体设计思路是"放宽可见性，零行为变更"：

1. **保持 `static final` 不变**：所有常量仍然是 `static final`，即"常量"语义不变，只是放宽谁能读到它们。
2. **统一为 `protected`**：把 `private`（仅本类可见）和无修饰符（包级私有，仅同包可见）两种都改为 `protected`（包级 + 子类可见）。`protected` 是抽象基类暴露给子类的标准可见性，比 `public` 更收敛（不暴露给外部包），比包级私有更开放（允许跨包子类访问）。
3. **不改变常量名、类型、初始值**：所有常量的定义本身（`Namespace.of("newdb")`、`Schema(...)`、`PartitionSpec.builderFor(...)` 等）一字未改，纯可见性变更。

变更覆盖 13 个常量：
- 原 `private`（8 个）：`NS`、`RENAMED_TABLE`、`TABLE_SCHEMA`、`REPLACE_SCHEMA`、`OTHER_SCHEMA`、`SPEC`、`TABLE_SPEC`、`REPLACE_SPEC`
- 原包级私有（5 个）：`WRITE_ORDER`、`TABLE_WRITE_ORDER`、`REPLACE_WRITE_ORDER`、`FILE_B`、`FILE_C`

注意：另外几个常量（`TABLE`、`SCHEMA`、`FILE_A`）原本就是 `protected`，本次未改动，保持一致。

## 修改详情

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java`

**修改目的**：放宽 13 个静态常量的可见性为 `protected`，使子类可直接引用。

**工作逻辑**：每个常量仅修改访问修饰符，定义内容不变。下表列出全部 13 处变更：

| 常量名 | 类型 | 原 `private` | 原无修饰符 | 现 `protected` |
|---|---|---|---|---|
| `NS` | `Namespace` | 是 | — | 是 |
| `RENAMED_TABLE` | `TableIdentifier` | 是 | — | 是 |
| `TABLE_SCHEMA` | `Schema` | 是 | — | 是 |
| `REPLACE_SCHEMA` | `Schema` | 是 | — | 是 |
| `OTHER_SCHEMA` | `Schema` | 是 | — | 是 |
| `SPEC` | `PartitionSpec` | 是 | — | 是 |
| `TABLE_SPEC` | `PartitionSpec` | 是 | — | 是 |
| `REPLACE_SPEC` | `PartitionSpec` | 是 | — | 是 |
| `WRITE_ORDER` | `SortOrder` | — | 是 | 是 |
| `TABLE_WRITE_ORDER` | `SortOrder` | — | 是 | 是 |
| `REPLACE_WRITE_ORDER` | `SortOrder` | — | 是 | 是 |
| `FILE_B` | `DataFile` | — | 是 | 是 |
| `FILE_C` | `DataFile` | — | 是 | 是 |

变更无任何逻辑改动，纯属可见性升级。例如：

```java
// 变更前
private static final Namespace NS = Namespace.of("newdb");
// 变更后
protected static final Namespace NS = Namespace.of("newdb");
```

```java
// 变更前
static final SortOrder WRITE_ORDER =
    SortOrder.builderFor(SCHEMA).asc(Expressions.bucket("id", 16)).asc("id").build();
// 变更后
protected static final SortOrder WRITE_ORDER =
    SortOrder.builderFor(SCHEMA).asc(Expressions.bucket("id", 16)).asc("id").build();
```

这些常量在 `CatalogTests` 中被多个测试方法引用，子类（如 `TestHadoopCatalog`、`TestJdbcCatalog`、`TestHiveCatalog`、`TestRESTCatalog`、nessie 的 `TestNamespace` 等）继承后，原本只能引用 `protected` 的 `TABLE`、`SCHEMA`、`FILE_A`，现在可以一致地引用全部 13 个夹具常量，从而在编写 catalog 专属补充测试时无需重复构造等价对象。

## 小结

本提交通过把 `CatalogTests` 抽象基类中 13 个测试夹具常量统一改为 `protected`，使所有 catalog 测试子类能直接复用基类定义的标准 schema、partition spec、sort order、data file 等夹具，减少重复定义，提升测试代码一致性。变更零行为风险——所有常量仍是 `static final`，仅放宽可见性。

回迁到 1.4.x 的注意事项：
1. 本提交是纯测试基础设施可见性调整，无生产代码变更，回迁零风险。
2. 回迁后若有 1.4.x 中的 catalog 子类测试已经依赖这些常量（通过反射或自构造等价对象绕过可见性限制），可以简化为直接引用；若没有，本提交仅为未来扩展铺路，不影响现有测试。
3. 注意 `CatalogTests` 在 main 分支已迁移到 JUnit 5（使用 `org.junit.jupiter.api.Test`、`Assumptions`），若 1.4.x 仍是 JUnit 4 版本，回迁此可见性变更时需确认 `CatalogTests` 类结构一致（常量定义位置、名称、初始值）；可见性变更本身与 JUnit 版本无关，应可独立回迁。
