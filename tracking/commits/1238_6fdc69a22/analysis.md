# 提交 1238：Core: Deprecate ContentCache.invalidateAll (#10494)

## 提交信息

- **序号**：1238 / 4088
- **哈希**：6fdc69a221123d17982f78304c5cf19f60e76cb9
- **短哈希**：6fdc69a22
- **日期**：2024-10-14（Mon Oct 14 21:57:03 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Core: Deprecate ContentCache.invalidateAll (#10494)
- **PR/Issue**：#10494

## 总体目的

`ContentCache` 是 Iceberg `core` 模块中对文件内容（典型场景：manifest 文件、metadata 文件）的缓存，底层使用 Caffeine 缓存。它对外暴露了两个失效方法：

- `invalidate(String key)`：失效单个 key。
- `invalidateAll()`：失效全部缓存项。

`invalidateAll()` 存在语义陷阱：它只做"best-effort"失效，并且存在 race condition。如果调用方先修改了可能被缓存的状态（例如存储上的文件内容/版本），再调用 `invalidateAll()`，方法返回后**并不能保证**缓存中不再有过期条目——因为在 `invalidateAll()` 执行期间或之后，Caffeine 可能仍有正在进行的 load 操作把旧值重新填回缓存。这类似于 Guava Cache 的 [issue #1881](https://github.com/google/guava/issues/1881) 所描述的问题。`ContentCache` 用的是 Caffeine，Caffeine 对此问题提供了部分解决方案（针对单 key 的 `invalidate`），但对 `invalidateAll` 并不能彻底解决。

为了避免调用方误以为 `invalidateAll()` 是同步、确定性的全量失效而错误使用，本提交：

1. 把 `ContentCache.invalidateAll()` 标记为 `@Deprecated`（计划自 1.6.0 弃用，1.7.0 移除）；
2. 在 Javadoc 中说明其 best-effort 性质与 race condition；
3. 同时为 `invalidate(String key)` 补充 Javadoc，明确其阻塞性质：若该 key 当前有进行中的 load，`invalidate` 会等待 load 完成再失效。

后续会在 1.7.0 实际移除 `invalidateAll()`（参见紧随其后的提交 #11325，对版本号做了修正）。

## 如何达成设计目的

只修改 `ContentCache.java` 一个文件，给 `invalidateAll()` 加 `@Deprecated` 注解与 Javadoc；给 `invalidate(String key)` 补 Javadoc。无任何代码逻辑变更，纯文档/注解改动。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/ContentCache.java`

**修改目的**：标记 `invalidateAll()` 为弃用并补充文档；为 `invalidate(String)` 补充阻塞性文档。

**工作逻辑**：

- 对 `public void invalidate(String key)` 新增 Javadoc：
  ```java
  /**
   * Invalidate the cache entry for the given key.
   *
   * <p>Note: if there is ongoing load, this is a blocking operation, i.e. it will wait for the load
   * to complete before invalidating the entry.
   */
  ```
  说明该方法在有进行中 load 时是阻塞的——会先等待 load 完成，再失效。这与 `invalidateAll` 的非阻塞 best-effort 形成对比，提示调用方优先使用 `invalidate(key)`。

- 对 `public void invalidateAll()` 添加 `@Deprecated` 注解与 Javadoc：
  ```java
  /**
   * @deprecated since 1.6.0, will be removed in 1.7.0; This method does only best-effort
   *     invalidation and is susceptible to a race condition. If the caller changed the state that
   *     could be cached (perhaps files on the storage) and calls this method, there is no guarantee
   *     that the cache will not contain stale entries some time after this method returns.
   */
  @Deprecated
  public void invalidateAll() {
    cache.invalidateAll();
  }
  ```
  方法体未变。该 Javadoc 把"弃用版本、计划移除版本、为何弃用、race condition 描述"全部写清。

注：后续提交 #11325（短哈希 32b1ab6ea）会把这里的版本号 "1.6.0/1.7.0" 改为 "1.7.0/2.0.0"。

## 小结

- **成效**：`ContentCache.invalidateAll()` 被标记为 `@Deprecated`，提示调用方该方法存在 race condition 不可靠；`invalidate(String key)` 的阻塞性质被文档化。为后续在 2.0.0 移除该方法做铺垫。
- **影响范围**：仅 `ContentCache.java` 一个文件、13 行新增（Javadoc + 注解），无代码逻辑变更。所有调用 `invalidateAll()` 的位置在编译时会出现 deprecation 警告，但不影响行为。
- **回迁到 1.4.x 的注意事项**：
  - 本提交是文档/注解级别的弃用声明，安全且无副作用，可回迁。
  - 但需注意：1.4.x 的版本号与 main 不同。本提交原始声明为 "since 1.6.0, will be removed in 1.7.0"，提交 #11325 又改为 "since 1.7.0, will be removed in 2.0.0"。回迁到 1.4.x 时应按 1.4.x 的实际版本节奏调整版本号（例如改为 "since 1.4.x, will be removed in 1.5.0/2.0.0"，或保守起见与 main 对齐用 1.7.0/2.0.0），避免对用户产生错误的弃用承诺。
  - 若 1.4.x 仍有调用 `invalidateAll()` 的代码，回迁后会产生 deprecation 警告，建议同步评估是否能改用 `invalidate(key)` 逐个失效。
