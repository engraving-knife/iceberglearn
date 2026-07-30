# 提交 0951：Core: Remove unnecessary class-level synchronized in ManifestFiles (#10544)

## 提交信息

- **序号**：0951 / 4088
- **哈希**：cb6540c8c9897968127e8fef02950259efed8fcf
- **短哈希**：cb6540c8c
- **日期**：2024-07-19 03:30:01 +0200
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Core: Remove unnecessary class-level synchronized in ManifestFiles (#10544)
- **PR/Issue**：#10544

## 总体目的

`ManifestFiles` 是 core 模块中读写 manifest 文件的工具类，内部维护一个 `FileIO -> ContentCache` 的 Caffeine 缓存 `CONTENT_CACHES`，用于按 FileIO 实例缓存 manifest 内容。该类中的 `dropCache(FileIO)` 方法原先带有 `public static synchronized` 修饰符，即在 `ManifestFiles` 的 `Class` 对象上加类级锁。

这个类级 `synchronized` 是不必要的，原因在于：
- `CONTENT_CACHES` 是 Caffeine 的 `Cache` 实例，其 `invalidate(...)` 与 `cleanUp()` 方法本身就是线程安全的，内部已有自己的并发控制；
- 类级 `synchronized` 会让所有 `dropCache` 调用串行化，且锁的是 `ManifestFiles.class` 这一全局对象，与 `contentCache(FileIO)` 等其他访问缓存的路径（直接调用 `CONTENT_CACHES.get(...)`，并不经过该锁）不一致，既无法提供额外的线程安全保证，反而引入不必要的锁竞争与潜在死锁/等待风险（例如与其它在 `ManifestFiles.class` 上同步的代码冲突）。

本提交（PR #10544）移除该方法的 `synchronized` 关键字，使 `dropCache` 直接依赖 Caffeine 缓存自身的线程安全实现，消除冗余的类级锁，简化并发语义并减少不必要的争用。

## 如何达成设计目的

实现方式：把 `dropCache(FileIO)` 方法签名上的 `synchronized` 关键字去掉，方法体保持不变（仍调用 `CONTENT_CACHES.invalidate(fileIO)` 与 `CONTENT_CACHES.cleanUp()`）。由于 Caffeine `Cache` 的这两个操作在内部已正确处理并发，去掉外层类锁后线程安全性不受影响，反而避免了无意义的全局串行化。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestFiles.java`

**修改目的**：移除 `dropCache(FileIO)` 上冗余的类级 `synchronized` 锁，依赖 Caffeine 缓存自身的线程安全。

**工作逻辑**：改动仅一处方法修饰符：

```diff
   /** Drop manifest file cache object for a FileIO if exists. */
-  public static synchronized void dropCache(FileIO fileIO) {
+  public static void dropCache(FileIO fileIO) {
     CONTENT_CACHES.invalidate(fileIO);
     CONTENT_CACHES.cleanUp();
   }
```

`CONTENT_CACHES` 是 `private static final Cache<FileIO, ContentCache>`，由 `newManifestCacheBuilder().build()` 创建，使用 `weakKeys()`/`softValues()` 与 `maximumSize` 等策略。Caffeine 的 `invalidate(key)` 与 `cleanUp()` 均为线程安全操作，因此去掉 `synchronized` 后：
- 多线程并发调用 `dropCache` 不再在 `ManifestFiles.class` 上排队，直接由 Caffeine 内部并发结构处理；
- 与 `contentCache(FileIO)` 中 `CONTENT_CACHES.get(io, ...)` 的并发访问语义一致（后者本就不持有类锁），整体并发模型更一致、更清晰。

## 小结

- **成效**：移除了 `ManifestFiles.dropCache` 上多余的类级 `synchronized`，消除不必要的全局锁争用，简化并发语义，行为正确性由 Caffeine 缓存自身的线程安全保证。
- **影响范围**：仅 `core/src/main/java/org/apache/iceberg/ManifestFiles.java` 一个文件，1 行修饰符删除，无功能行为变化。
- **回迁到 1.4.x 的注意事项**：这是一次低风险的并发清理，可安全回迁到 1.4.x。回迁前确认 1.4.x 的 `ManifestFiles.dropCache` 仍带 `synchronized` 且 `CONTENT_CACHES` 仍为 Caffeine `Cache`（若是则同样冗余，可直接移除）；若 1.4.x 该缓存实现与 main 有差异（例如不同的缓存库或并发策略），需重新评估移除 `synchronized` 后的线程安全性。正常情况下 cherry-pick 即可，无下游兼容性问题。
