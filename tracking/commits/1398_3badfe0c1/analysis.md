# 提交 1398：Revert "Core: Use encoding/decoding methods for namespaces and deprecate Spli…" (#11574)

## 提交信息

- **序号**：1398 / 4088
- **哈希**：3badfe0c1fcf0c0adfc7aa4a10f0b50365c48cf9
- **短哈希**：3badfe0c1
- **日期**：2024-11-19（Tue Nov 19 15:38:46 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Revert "Core: Use encoding/decoding methods for namespaces and deprecate Spli…" (#11574)
- **PR/Issue**：#11574
- **被回退的提交**：5fc1413a5efc4419ccc081f3031325f107ccddab（PR #10858，2024-08-05 合入）

## 总体目的

PR #10858（提交 5fc1413a）曾对 `RESTUtil` 中的命名空间（Namespace）编码/解码逻辑做了一次重构：它把原来用 `NAMESPACE_ESCAPED_SEPARATOR = "%1F"`（URL 编码的 Unit Separator 字符）的 `Joiner`/`Splitter` 标记为 `@Deprecated`（计划 1.8.0 设为 private），并引入新的 `encodeNamespace` / `decodeNamespace` 方法。新方法改用真实字符 `\u001f`（Unit Separator 控制字符本身，而非其 URL 编码 `%1F`）作为分隔符。

这一改动引入了行为变化：`encodeNamespace` 输出的是含 `\u001f` 的字符串，而旧的 `NAMESPACE_JOINER` 输出的是含 `%1F` 的字符串。两者在网络传输与 URL 解析上的行为不同——`\u001f` 是控制字符，在 HTTP URL/查询参数中需要进一步 URL 编码才能安全传输；而 `%1F` 已经是 URL 编码形式，可直接放在查询参数中。

社区发现该变化在 REST Catalog 的 `parent` 查询参数场景中导致兼容性问题或测试失败，因此本提交把 PR #10858 完整回退，恢复以 `%1F` 作为命名空间分隔符的原始行为，待后续重新设计更稳妥的迁移方案。

## 如何达成设计目的

直接执行 `git revert 5fc1413a`，把以下三处改动反向应用：

1. `RESTUtil.java`：恢复 `NAMESPACE_JOINER` 与 `NAMESPACE_SPLITTER` 为公开静态 final 字段，使用 `NAMESPACE_ESCAPED_SEPARATOR = "%1F"`；删除 `encodeNamespace` / `decodeNamespace` 方法和相关 deprecated 注解。
2. `RESTSessionCatalog.java`：把 `parent` 查询参数的构造从 `RESTUtil.encodeNamespace(namespace)` 改回 `RESTUtil.NAMESPACE_JOINER.join(namespace.levels())`。
3. `RESTCatalogAdapter.java`（测试）：把 `parent` 参数的解析从 `RESTUtil.decodeNamespace(vars.get("parent"))` 改回用 `RESTUtil.NAMESPACE_SPLITTER.splitToStream(...).toArray(String[]::new)` + `Namespace.of(...)`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTUtil.java`

**修改目的**：恢复使用 `%1F` 作为命名空间分隔符的原始 `NAMESPACE_JOINER` / `NAMESPACE_SPLITTER`，删除新增的 `encodeNamespace` / `decodeNamespace`。

**工作逻辑**：

修改前（被回退后的状态）：
```java
public class RESTUtil {
  private static final String NAMESPACE_ESCAPED_SEPARATOR = "%1F";
  private static final Joiner NAMESPACE_ESCAPED_JOINER = Joiner.on(NAMESPACE_ESCAPED_SEPARATOR);
  private static final Splitter NAMESPACE_ESCAPED_SPLITTER =
      Splitter.on(NAMESPACE_ESCAPED_SEPARATOR);

  /**
   * @deprecated since 1.7.0, will be made private in 1.8.0; use {@link
   *     RESTUtil#encodeNamespace(Namespace)} instead.
   */
  @Deprecated public static final Joiner NAMESPACE_JOINER = Joiner.on(NAMESPACE_ESCAPED_SEPARATOR);

  /**
   * @deprecated since 1.7.0, will be made private in 1.8.0; use {@link
   *     RESTUtil#decodeNamespace(String)} instead.
   */
  @Deprecated
  public static final Splitter NAMESPACE_SPLITTER = Splitter.on(NAMESPACE_ESCAPED_SEPARATOR);
  ...
}
```

修改后（恢复后）：
```java
public class RESTUtil {
  private static final char NAMESPACE_SEPARATOR = '\u001f';
  public static final Joiner NAMESPACE_JOINER = Joiner.on(NAMESPACE_SEPARATOR);
  public static final Splitter NAMESPACE_SPLITTER = Splitter.on(NAMESPACE_SEPARATOR);
  private static final String NAMESPACE_ESCAPED_SEPARATOR = "%1F";
  private static final Joiner NAMESPACE_ESCAPED_JOINER = Joiner.on(NAMESPACE_ESCAPED_SEPARATOR);
  private static final Splitter NAMESPACE_ESCAPED_SPLITTER =
      Splitter.on(NAMESPACE_ESCAPED_SEPARATOR);
  ...
}
```

注意：回退后保留了 `NAMESPACE_SEPARATOR = '\u001f'` 字符常量与基于它的 `NAMESPACE_JOINER` / `NAMESPACE_SPLITTER`（这是 #10858 之前就存在的形式，使用真实控制字符 `\u001f`，而非 URL 编码 `%1F`）。同时保留了 `NAMESPACE_ESCAPED_*` 系列作为内部使用。`NAMESPACE_JOINER` / `NAMESPACE_SPLITTER` 不再带 `@Deprecated`。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：恢复用 `NAMESPACE_JOINER.join(namespace.levels())` 构造 `parent` 查询参数。

**工作逻辑**（第 615 行附近）：

修改前：`queryParams.put("parent", RESTUtil.encodeNamespace(namespace));`
修改后：`queryParams.put("parent", RESTUtil.NAMESPACE_JOINER.join(namespace.levels()));`

`namespace.levels()` 返回 `String[]`，`NAMESPACE_JOINER` 用 `\u001f` 把它们连接成单个字符串作为 `parent` 参数值。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java`

**修改目的**：恢复用 `NAMESPACE_SPLITTER` 解析 `parent` 参数。

**工作逻辑**（第 298 行附近）：

修改前：
```java
if (vars.containsKey("parent")) {
  ns = RESTUtil.decodeNamespace(vars.get("parent"));
} else {
  ns = Namespace.empty();
}
```

修改后：
```java
if (vars.containsKey("parent")) {
  ns =
      Namespace.of(
          RESTUtil.NAMESPACE_SPLITTER
              .splitToStream(vars.get("parent"))
              .toArray(String[]::new));
} else {
  ns = Namespace.empty();
}
```

`NAMESPACE_SPLITTER.splitToStream` 用 `\u001f` 分割字符串得到各 level，再 `Namespace.of(...)` 重建 Namespace。

## 小结

- **成效**：回退 PR #10858 引入的命名空间编码/解码方法重构，恢复 `RESTUtil` 使用 `\u001f` 字符（通过 `NAMESPACE_JOINER`/`NAMESPACE_SPLITTER`）的原始行为，避免 `\u001f` vs `%1F` 在 REST `parent` 查询参数中导致的兼容性问题。
- **影响范围**：3 个文件、9 处新增、15 处删除；纯回退，恢复到 #10858 之前的行为。
- **回迁到 1.4.x 的注意事项**：
  - 由于这是回退操作，1.4.x 上是否需要"回迁回退"取决于 1.4.x 是否已经包含了被回退的 #10858。
  - 如果 1.4.x 的分支历史中**没有**合入 #10858（即 1.4.x 一直使用原始的 `NAMESPACE_JOINER`/`NAMESPACE_SPLITTER`），则 1.4.x 已经处于"回退后"的状态，**无需回迁**本提交。
  - 如果 1.4.x **已合入** #10858（不太可能，因为 #10858 在 2024-08-05 合入 main，而 1.4.x 是更早的维护分支），则需要回迁本回退以避免同样的兼容性问题。
  - 关键检查点：1.4.x 的 `RESTUtil.java` 中是否存在 `encodeNamespace`/`decodeNamespace` 方法或 `@Deprecated` 标注的 `NAMESPACE_JOINER`。若不存在，则无需回迁；若存在，必须回迁本回退。
  - 该回退不影响与旧客户端的互操作性，因为恢复后的行为（`\u001f` 分隔符）是 Iceberg 长期以来的默认行为。
