# 提交 0889：Core: Handle potential NPE in RESTSessionCatalog#newSessionCache (#10607)

## 提交信息

- **序号**：0889 / 4088
- **哈希**：f4ddaea56b7039ecf4e94273aa037e52da934de4
- **短哈希**：f4ddaea56
- **日期**：2024-07-01（Tue Jul 2 07:23:30 2024 +0700）
- **作者**：Tai Le Manh <49281946+tlm365@users.noreply.github.com>
- **提交说明**：Core: Handle potential NPE in RESTSessionCatalog#newSessionCache (#10607)
- **PR/Issue**：#10607

## 总体目的

`RESTSessionCatalog` 是 Iceberg REST Catalog 的核心实现类，负责通过 REST API 与远端 catalog 服务交互并管理客户端会话。其 `newSessionCache()` 方法构建一个 Caffeine 缓存，用于缓存 `AuthSession`（认证会话），并配置 `expireAfterAccess` 过期策略与 `removalListener`——当缓存条目被移除时，调用 `auth.stopRefreshing()` 停止该会话的 token 刷新任务。

原代码的 `removalListener` 直接调用 `auth.stopRefreshing()` 而未对 `auth` 做 null 检查：

```java
(id, auth, cause) -> auth.stopRefreshing()
```

根据 Caffeine `RemovalListener` 的契约，`value`（此处即 `auth`）参数**可以为 null**。例如当条目因弱引用/软引用被 GC 回收，或在计算失败等边缘场景下，传入的 value 为 null。此时直接调用 `auth.stopRefreshing()` 会抛出 `NullPointerException`，导致移除监听器异常。Caffeine 的移除监听器异常会被框架捕获记录但不会中断缓存操作，但仍属于不应发生的错误，可能在日志中产生噪声或在某些 Caffeine 版本中导致意外行为。

本提交的目的是对 `auth` 做防御性 null 检查，消除潜在的 NPE。

## 如何达成设计目的

实现方式为在 `removalListener` 的 lambda 体内增加 `if (auth != null)` 守卫，仅当 value 非 null 时才调用 `auth.stopRefreshing()`。改动极小，不改变缓存的整体行为，仅在 value 为 null 的边缘场景下跳过 `stopRefreshing` 调用（此时本就没有会话需要停止刷新）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：对 `newSessionCache()` 中 `removalListener` 的 `auth` 参数做 null 检查，防止 NPE。

**工作逻辑**：将原来的单表达式 lambda：

```java
(id, auth, cause) -> auth.stopRefreshing()
```

改为带花括号的块 lambda，加入 null 守卫：

```java
(id, auth, cause) -> {
    if (auth != null) {
        auth.stopRefreshing();
    }
}
```

当 Caffeine 因任何原因传入 null value 时，监听器安全跳过，不再抛出 NPE。对于正常的非 null 移除场景，行为与原先完全一致——仍会调用 `auth.stopRefreshing()` 停止 token 刷新。

## 小结

- **成效**：修复了 `RESTSessionCatalog.newSessionCache()` 的 `removalListener` 中因未检查 `AuthSession` 是否为 null 而可能抛出 NPE 的隐患，提升了 REST Catalog 会话缓存管理的健壮性。
- **影响范围**：仅 `core` 模块的 `RESTSessionCatalog.java` 一处（约 5 行改动），无测试新增。影响 REST Catalog 的会话缓存移除路径。
- **回迁到 1.4.x 的注意事项**：**建议回迁**。这是一个防御性 bug 修复，改动极小、风险极低，且不改变任何正常路径行为。1.4.x 若使用 REST Catalog，在边缘场景同样可能触发该 NPE。回迁时只需同步这一处 null 检查即可。
