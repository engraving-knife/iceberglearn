# 提交 1200：Core: Update REST CatalogHandlers to handle page sizes exceeding number of Namespaces/Tables/Views (#11143)

## 提交信息

- **序号**：1200 / 4088
- **哈希**：97c9c535fb7ea8e768fafc285202872e9b525a6c
- **短哈希**：97c9c535f
- **日期**：2024-10-01（Tue Oct 1 07:07:20 2024 +0900）
- **作者**：rcjverhoef <30408627+rcjverhoef@users.noreply.github.com>
- **提交说明**：Core: Update REST CatalogHandlers to handle page sizes exceeding number of Namespaces/Tables/Views (#11143)
- **PR/Issue**：#11143

## 总体目的

`CatalogHandlers` 是 Iceberg REST Catalog 服务端的核心工具类，负责把 `Catalog`/`SupportsNamespaces`/`ViewCatalog` 的调用包装成 REST 响应。其中 `listNamespaces`、`listTables`、`listViews` 三个方法支持分页（接受 `pageToken` 与 `pageSize` 参数）。原分页实现有一个明显缺陷：

```java
int start = INTIAL_PAGE_TOKEN.equals(pageToken) ? 0 : Integer.parseInt(pageToken);
int end = start + Integer.parseInt(pageSize);
subResults = results.subList(start, end);  // 若 end > results.size() 会抛 IndexOutOfBoundsException
```

当客户端传入的 `pageSize` 超过剩余元素数量时（例如还剩 5 个但请求 pageSize=10），`end = start + pageSize` 会越过列表边界，`subList(start, end)` 直接抛 `IndexOutOfBoundsException`，导致 REST 接口返回 500 错误。这在实际场景中非常常见——客户端通常不知道列表总长度，会以一个保守的较大 pageSize 请求。

本提交修复该缺陷：抽出统一的 `paginate` 方法，使用 `Math.min(pageStart + pageSize, list.size())` 限制 `end` 不越界，并正确处理"pageToken 已越过列表末尾"的边界（返回空列表 + `nextPageToken = null`）。同时顺手修复了常量名拼写错误 `INTIAL_PAGE_TOKEN` → `INITIAL_PAGE_TOKEN`，并把三个分页方法重构为共用 `paginate`，消除重复代码。

此外，测试侧把 `testPaginationForListNamespaces/Tables/Views` 由固定 30 个元素改为参数化（`@ValueSource(ints = {21, 30})`），其中 `21` 用于覆盖"最后一页不满 pageSize"的边界（pageSize=10，21 个元素会分成 10/10/1 三页），`30` 用于覆盖"刚好整除"的场景。

## 如何达成设计目的

1. **新增 `paginate` 静态泛型方法**：把分页逻辑统一收敛到一个 `<T>` 泛型方法中，输入 `(List<T> list, String pageToken, int pageSize)`，输出 `Pair<List<T>, String>`（子列表 + 下一页 token，无下一页时为 `null`）。内部先用 `INITIAL_PAGE_TOKEN` 判断起始，再判断 `pageStart >= list.size()` 直接返回空列表，最后用 `Math.min` 计算 `end`；
2. **三个分页方法改用 `paginate`**：`listNamespaces`、`listTables`、`listViews` 各自删除原有的 `start/end/subList/nextToken` 重复逻辑，统一调用 `paginate(results, pageToken, pageSize)`，从返回的 `Pair` 取 `first()` 作为子列表、`second()` 作为 nextToken；
3. **修正常量名**：`INTIAL_PAGE_TOKEN` → `INITIAL_PAGE_TOKEN`（拼写错误，且同步更新所有引用处）；
4. **测试参数化**：三个分页测试方法改为 `@ParameterizedTest` + `@ValueSource(ints = {21, 30})`，把原来硬编码的 `int numberOfItems = 30;` 改为方法参数。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java`

**修改目的**：修复分页越界、消除重复代码、修正常量拼写。

**工作逻辑**：

1. 新增 import：`java.util.Collections` 与 `org.apache.iceberg.util.Pair`；
2. 把常量 `INTIAL_PAGE_TOKEN = ""` 改名为 `INITIAL_PAGE_TOKEN = ""`；
3. 新增 `paginate` 方法：

   ```java
   private static <T> Pair<List<T>, String> paginate(List<T> list, String pageToken, int pageSize) {
     int pageStart = INITIAL_PAGE_TOKEN.equals(pageToken) ? 0 : Integer.parseInt(pageToken);
     if (pageStart >= list.size()) {
       return Pair.of(Collections.emptyList(), null);
     }
     int end = Math.min(pageStart + pageSize, list.size());
     List<T> subList = list.subList(pageStart, end);
     String nextPageToken = end >= list.size() ? null : String.valueOf(end);
     return Pair.of(subList, nextPageToken);
   }
   ```

   关键点：
   - `pageStart >= list.size()` 时返回空列表 + `null` token（避免 `subList` 越界）；
   - `end = Math.min(pageStart + pageSize, list.size())` 保证不越界；
   - `end >= list.size()` 时 `nextPageToken = null`，表示已是最后一页。

4. `listNamespaces(parent, pageToken, pageSize)`：删除原有 `subResults`、`start`、`end`、`nextToken` 计算与 `end >= results.size()` 判断，改为：

   ```java
   Pair<List<Namespace>, String> page = paginate(results, pageToken, Integer.parseInt(pageSize));
   return ListNamespacesResponse.builder()
       .addAll(page.first())
       .nextPageToken(page.second())
       .build();
   ```

5. `listTables(namespace, pageToken, pageSize)` 与 `listViews(namespace, pageToken, pageSize)`：同样的重构模式，调用 `paginate` 后用 `page.first()`/`page.second()` 构建响应。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`

**修改目的**：扩展分页测试覆盖"最后一页不满"与"整除"两种边界。

**工作逻辑**：

- 把 `testPaginationForListNamespaces` 与 `testPaginationForListTables` 从 `@Test` 改为 `@ParameterizedTest` + `@ValueSource(ints = {21, 30})`，方法签名新增 `int numberOfItems` 参数，删除方法体内 `int numberOfItems = 30;` 一行；
- `21` 对应 `pageSize=10` 时分页为 `10/10/1`（最后一页只有 1 个），验证 `Math.min` 越界保护；
- `30` 对应 `10/10/10`（整除），验证原有正常路径未回归。
- 测试方法其余逻辑（创建 namespaces/tables、断言每页内容、断言 nextToken）保持不变。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java`

**修改目的**：对 `listViews` 的分页测试做同样的参数化扩展。

**工作逻辑**：

- import 调整：移除 `org.junit.jupiter.api.Test`，新增 `org.junit.jupiter.params.ParameterizedTest` 与 `org.junit.jupiter.params.provider.ValueSource`；
- `testPaginationForListViews` 同样改为 `@ParameterizedTest` + `@ValueSource(ints = {21, 30})`，方法签名加 `int numberOfItems` 参数，删除 `int numberOfItems = 30;`。

## 小结

- **成效**：REST Catalog 的 `listNamespaces`/`listTables`/`listViews` 分页接口现在能正确处理 `pageSize` 超过剩余元素数量的情况，不再抛 `IndexOutOfBoundsException`；分页逻辑收敛到单一 `paginate` 方法，可读性与可维护性提升；常量拼写错误一并修正；测试覆盖了"最后一页不满"与"整除"两种边界。
- **影响范围**：`CatalogHandlers.java` 净减 3 行（41 删 38 增，重复代码消除多于新增 `paginate`），`TestRESTCatalog.java` 改 12 行，`TestRESTViewCatalog.java` 改 9 行。涉及 REST Catalog 服务端分页行为，是行为修复型改动。
- **回迁到 1.4.x 的注意事项**：这是一个 bug 修复，影响 REST Catalog 在生产环境下的稳定性（客户端传大 pageSize 会触发 500），**强烈建议回迁到 1.4.x**。回迁时需确认：
  1. 1.4.x 的 `CatalogHandlers` 是否已有分页方法（1.4.x 应已具备，因为 View REST 支持在 1.4 已落地）；
  2. `org.apache.iceberg.util.Pair` 在 1.4.x 中是否存在（应存在）；
  3. 测试侧需同步引入 `@ParameterizedTest` + `@ValueSource` 的依赖（1.4.x 已使用 JUnit 5，应已具备）；
  4. 回迁后建议跑 `TestRESTCatalog`、`TestRESTViewCatalog` 的分页测试确认无回归。
