# 提交 2201：Core: Improve pagination logic to handle null pageToken (#13129)

## 提交信息

- **序号**：2201 / 4088
- **哈希**：959351677b96bace2852154bcb49098538a87981
- **短哈希**：959351677
- **日期**：2025-06-03 20:00:49 +0100
- **作者**：Elphas Toringepi
- **提交说明**：Core: Improve pagination logic to handle null pageToken (#13129)
- **PR/Issue**：#13129

## 总体目的

这个提交修复了 REST Catalog 分页逻辑中对 null pageToken 处理不当的问题。在 `CatalogHandlers.paginate` 方法中，分页逻辑根据 `pageToken` 决定从列表的哪个位置开始读取下一页数据。此前的代码使用 `INITIAL_PAGE_TOKEN.equals(pageToken)` 判断是否为首页，但 `INITIAL_PAGE_TOKEN` 是一个非 null 字符串，如果传入的 `pageToken` 为 null，`equals` 方法会返回 false（不会 NPE），随后代码会走到 `Integer.parseInt(pageToken)` 分支，对 null 调用 `parseInt` 会抛出 `NumberFormatException`，导致分页请求失败。实际上，null pageToken 在某些场景下是合法的（例如客户端首次请求或某些 REST 客户端实现可能传 null），应当被视为首页。本提交修改判断逻辑，将 null 也视为首页 token，避免 parseInt 对 null 抛异常。

## 如何达成设计目的

- 修改 `paginate` 方法中的首页判断逻辑，从 `INITIAL_PAGE_TOKEN.equals(pageToken)` 改为先判断 `pageToken == null || pageToken.equals(INITIAL_PAGE_TOKEN)`，将 null 和初始 token 都视为首页（pageStart = 0）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java` (修改, +2/-1 lines)

**修改目的**：修复 null pageToken 导致 NumberFormatException 的问题。

**工作逻辑**：将原代码 `int pageStart = INITIAL_PAGE_TOKEN.equals(pageToken) ? 0 : Integer.parseInt(pageToken);` 改为 `boolean isFirstPage = pageToken == null || pageToken.equals(INITIAL_PAGE_TOKEN); int pageStart = isFirstPage ? 0 : Integer.parseInt(pageToken);`。新逻辑先判断是否为首页（pageToken 为 null 或等于初始 token），是则 pageStart 为 0，否则才解析为整数。这样 null pageToken 不再走到 parseInt 分支，避免了异常。

## 总结

该提交修复了 REST Catalog 分页逻辑的一个边界问题：当 pageToken 为 null 时不再抛出 NumberFormatException，而是视为首页处理。修复简洁精准，提升了 REST 分页接口的健壮性和兼容性。
