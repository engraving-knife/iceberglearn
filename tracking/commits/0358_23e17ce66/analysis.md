# 提交 0358：Nessie: Add table() and view() API to NessieIcebergClient (#9477)

## 提交信息

- **序号**：0358
- **哈希**：23e17ce665031557f4fc918e2fcff9c18904dfb1
- **短哈希**：23e17ce66
- **日期**：2024-01-15 14:49:08 +0100
- **作者**：Ajantha Bhat
- **提交说明**：Nessie: Add table() and view() API to NessieIcebergClient (#9477)
- **PR/Issue**：#9477

## 总体目的

本提交为 Iceberg 的 Nessie 集成模块（`nessie/src/main/java/org/apache/iceberg/nessie/NessieIcebergClient.java`）新增两个便捷访问方法 `table(TableIdentifier)` 与 `view(TableIdentifier)`，分别返回 Nessie 内容模型中的 `IcebergTable` 与 `IcebergView` 类型化包装对象。在此之前，`NessieIcebergClient` 已经有一个 `fetchContent(TableIdentifier)` 方法，返回通用的 `IcebergContent`（Nessie 内容树的基类），调用方需要自己调用 `unwrap(IcebergTable.class)` 或 `unwrap(IcebergView.class)` 做类型转换与判空。本提交把"取内容 + unwrap 到特定子类型 + 判空"这一常用三步操作封装成 `table()` / `view()` 一行调用，提升 API 的可用性、减少重复样板代码、并在调用点明确表达意图（"我要取的是一个表"还是"我要取的是一个视图"）。

这一改动在 Iceberg 视图（View）支持逐步完善的大背景下尤为关键。Iceberg 在主分支上已经引入了 `View` / `ViewCatalog` 抽象（见提交 0335 等历史），Nessie 作为支持视图的 catalog 实现之一，需要在客户端层提供"按标识符直接取视图内容"的能力。`table()` 与 `view()` 这对对称的 API 让上层代码（如 `NessieCatalog` 的 `loadTable` / `loadView` 实现、或外部工具直接操作 Nessie 内容树）能以类型安全的方式访问 Nessie 中存储的 Iceberg 表/视图元数据，而不必反复写 `fetchContent(...).unwrap(...)` 样板。

值得注意的是，这两个方法都通过 `unwrap(...).orElse(null)` 处理"内容存在但类型不符"的情况——例如调用 `table(ident)` 但 Nessie 中该 key 实际存的是视图，则 `unwrap(IcebergTable.class)` 返回 `Optional.empty()`，方法返回 `null`。这与 `fetchContent` 返回 null 表示"内容不存在"的语义保持一致，调用方只需统一判 null 即可，无需区分"不存在"与"类型不匹配"两种情况。

## 如何达成设计目的

实现路径非常直接：在 `NessieIcebergClient` 类中紧邻已有的 `fetchContent(TableIdentifier)` 方法之前，新增两个 public 方法 `table(TableIdentifier)` 与 `view(TableIdentifier)`。两者都委托给 `fetchContent` 取底层 `IcebergContent`，然后通过 `unwrap(IcebergTable.class)` / `unwrap(IcebergView.class)` 转型到具体的 Iceberg 内容子类型。判空逻辑用三元表达式 `icebergContent == null ? null : icebergContent.unwrap(...).orElse(null)` 处理两种 null 来源：`fetchContent` 返回 null（Nessie 中无此 key 或类型不是 IcebergContent）与 `unwrap` 返回 empty（Nessie 中此 key 存在但不是 IcebergTable/IcebergView）。`unwrap` 是 Nessie `Content` 类的 API，返回 `Optional<T>`，匹配时按 Nessie 内容类型注册的 `Content.Type` 判断，确保类型安全。这两个方法直接复用 `fetchContent` 的异常处理（其内部捕获 `NessieNotFoundException` 返回 null），不抛额外异常，对调用方而言是纯查询 API。

## 修改详情

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieIcebergClient.java`

**修改目的**：为 `NessieIcebergClient` 新增 `table(TableIdentifier)` 与 `view(TableIdentifier)` 两个便捷方法，封装"取内容 + 类型化 unwrap + 判空"三步，提升 API 易用性。

**工作逻辑**：在类中（位于已有 `toIdentifier(EntriesResponse.Entry)` 私有方法之后、`fetchContent(TableIdentifier)` public 方法之前）新增两段对称代码：

```java
public IcebergTable table(TableIdentifier tableIdentifier) {
  IcebergContent icebergContent = fetchContent(tableIdentifier);
  return icebergContent == null ? null : icebergContent.unwrap(IcebergTable.class).orElse(null);
}

public IcebergView view(TableIdentifier tableIdentifier) {
  IcebergContent icebergContent = fetchContent(tableIdentifier);
  return icebergContent == null ? null : icebergContent.unwrap(IcebergView.class).orElse(null);
}
```

两个方法结构完全对称，仅在 unwrap 的目标类型上不同（`IcebergTable.class` vs `IcebergView.class`）。`fetchContent(tableIdentifier)` 是已有的私有/包级查询方法，内部通过 `withReference(api.getContent().key(key)).get().get(key)` 向 Nessie 服务端发起 `getContent` 请求，取回 `Content`（Nessie 通用内容模型），再用 `content.unwrap(IcebergContent.class).orElse(null)` 转型为 Iceberg 内容基类；若 `NessieNotFoundException` 则返回 null。新增的 `table()` / `view()` 在此基础上再做一次 `unwrap` 到具体子类型——`IcebergContent.unwrap(IcebergTable.class)` 利用 Nessie 内容树的 `Type` 注册信息做类型匹配，匹配成功返回 `Optional.of(IcebergTable)`，否则 `Optional.empty()`。`.orElse(null)` 把 empty 转为 null，与 `fetchContent` 的 null 语义对齐。这样调用方写 `client.table(ident)` 即可拿到 `IcebergTable`（或 null），无需关心中间的 `IcebergContent` 基类。返回类型 `IcebergTable` / `IcebergView` 是 Nessie 提供的 Iceberg 内容包装类，内含 Iceberg 表/视图的 metadata location 等信息，供 `NessieCatalog` 进一步加载 `TableMetadata` / `ViewMetadata`。

## 小结

本次提交为 `NessieIcebergClient` 新增 `table()` 与 `view()` 两个对称的便捷方法，把"取 Nessie 内容 + 类型化 unwrap 到 IcebergTable/IcebergView + 判空"三步封装为一行调用。改动仅 10 行新增、无修改、无删除，但显著提升了 Nessie 客户端 API 的易用性，并呼应了 Iceberg 视图（View）支持在 Nessie catalog 上落地的整体演进——为上层（`NessieCatalog.loadView` 等）以类型安全方式访问 Nessie 中的视图内容提供了基础。`unwrap(...).orElse(null)` 的处理统一了"内容不存在"与"类型不匹配"两种 null 语义，调用方只需判一次 null，API 表达力与简洁性都得到提升。
