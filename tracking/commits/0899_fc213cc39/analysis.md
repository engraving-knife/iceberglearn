# 提交 0899：Core: Handle possible heap data corruption in OAuth2Util.AuthSession#headers (#10615)

## 提交信息

- **序号**：0899 / 4088
- **哈希**：fc213cc3937b4cefbc21beb6d5b47f564c439810
- **短哈希**：fc213cc39
- **日期**：2024-07-05（Fri Jul 5 00:34:22 2024 +0700）
- **作者**：Tai Le Manh <49281946+tlm365@users.noreply.github.com>
- **提交说明**：Core: Handle possible heap data corruption in OAuth2Util.AuthSession#headers (#10615)
- **PR/Issue**：#10615

## 总体目的

`OAuth2Util.AuthSession` 是 Iceberg REST Catalog 的认证会话类，它持有两个 `volatile` 字段：`headers`（认证请求头 Map）和 `config`（认证配置）。在 token refresh 流程中，`refresh(RESTClient client)` 方法会更新这两个字段：先用新的 token 构建 `AuthConfig` 赋值给 `this.config`，再用 `RESTUtil.merge(headers, authHeaders(config.token()))` 把旧 headers 与新 token 对应的 `Authorization` 头合并，赋值给 `this.headers`。

原始代码的问题是：在 `this.headers = RESTUtil.merge(headers, authHeaders(config.token()))` 这一行中，`headers`（即 `this.headers`）既被读取（作为 `RESTUtil.merge` 的第一个参数）又被写入（作为赋值目标）。虽然 Java JLS 保证 RHS 在赋值前求值，但 `this.headers` 是 `volatile` 字段，而 `refresh()` 方法本身不是 `synchronized` 的——它通常由 `ScheduledExecutorService` 调度触发，但在某些路径下可能与并发的 `headers()` 读取（来自其他发起 REST 请求的线程）发生交错。在这种并发场景下，`RESTUtil.merge` 内部对 `headers` map 做 `forEach` 遍历时，如果另一个线程恰好替换了 `this.headers` 的引用，虽然旧的 `ImmutableMap` 本身不会被破坏，但读取-合并-写入的序列并非原子操作，可能导致合并结果基于一个"过期"的 headers 快照，进而使认证会话状态不一致——PR 标题将其描述为"possible heap data corruption"。

本提交的目的是通过先将 `this.headers` 的当前值快照到一个局部变量 `currentHeaders`，再用该局部变量参与 merge，确保整个 merge 过程使用一致的 headers 快照，避免在 read-merge-write 序列中因 `this.headers` 被其他线程修改而产生不一致。

## 如何达成设计目的

实现方式非常简洁：在 `refresh()` 方法中，将原来的单行赋值：

```java
this.headers = RESTUtil.merge(headers, authHeaders(config.token()));
```

改为两行：

```java
Map<String, String> currentHeaders = this.headers;
this.headers = RESTUtil.merge(currentHeaders, authHeaders(config.token()));
```

这样 `this.headers` 的当前值在 `RESTUtil.merge` 调用前就被读取并固定到 `currentHeaders` 局部变量中。即使 `RESTUtil.merge` 执行期间另一个线程修改了 `this.headers`，本线程使用的仍然是 `currentHeaders` 快照，保证 merge 的输入一致。最终结果再原子地写入 `this.headers`（volatile 写）。

注意：该修复并没有彻底解决并发 refresh 的竞态（例如两个 refresh 同时执行可能导致 lost update），也没有给 `refresh()` 加 `synchronized`。它只是确保单次 refresh 内部对 `headers` 的读取与使用是一致的，避免 merge 过程中 `this.headers` 引用变化导致的潜在问题。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java`

**修改目的**：在 `AuthSession.refresh()` 方法中，把对 `this.headers` 的读取快照到局部变量，避免在 read-merge-write 序列中使用不一致的 headers 引用。

**工作逻辑**：`AuthSession.refresh(RESTClient client)` 方法在 token refresh 成功后更新 `this.config` 与 `this.headers`。改动位于 `this.headers` 赋值处。

修改前：

```java
this.config =
    AuthConfig.builder()
        .from(config())
        .token(response.token())
        .tokenType(response.issuedTokenType())
        .build();
this.headers = RESTUtil.merge(headers, authHeaders(config.token()));
```

修改后：

```java
this.config =
    AuthConfig.builder()
        .from(config())
        .token(response.token())
        .tokenType(response.issuedTokenType())
        .build();
Map<String, String> currentHeaders = this.headers;
this.headers = RESTUtil.merge(currentHeaders, authHeaders(config.token()));
```

`RESTUtil.merge(target, updates)` 的实现是：遍历 `target` 中不在 `updates` 的 key，再遍历 `updates` 的所有 key，构建一个新的 `ImmutableMap` 返回。它不会修改输入的 Map。

改动前 `headers`（即 `this.headers`）作为 `target` 传入 `RESTUtil.merge`，在 merge 的 `forEach` 遍历期间，`this.headers` 仍指向同一个 Map 对象。改动后 `currentHeaders` 局部变量持有该 Map 引用，语义上等价但更明确：即使 `this.headers` 在 merge 期间被其他线程修改，`currentHeaders` 仍指向原来的 Map，保证遍历一致性。

需要注意，`this.headers` 是 `volatile Map<String, String>`，`volatile` 保证引用的可见性，但不保证 read-merge-write 的原子性。`config.token()` 读取的是上一行刚设置的 `this.config`（也是 `volatile`），所以 `authHeaders(config.token())` 用的是新 token。最终 `this.headers` 被设为旧 headers + 新 `Authorization` 头的合并结果。

## 小结

- **成效**：在 `AuthSession.refresh()` 中把对 `this.headers` 的读取显式快照到局部变量 `currentHeaders`，确保 `RESTUtil.merge` 过程中使用一致的 headers 快照，避免并发场景下 `this.headers` 引用变化导致的潜在数据不一致。改动量极小（2 行新增、1 行删除），语义在单线程下等价。
- **影响范围**：1 个文件，2 行新增、1 行删除，仅修改 `OAuth2Util.AuthSession.refresh()` 方法中 `this.headers` 赋值处的 3 行。无 API 签名变更，不影响其他方法（如 `refreshExpiredToken` 中类似的 `RESTUtil.merge(headers(), ...)` 模式未改）。
- **回迁到 1.4.x 的注意事项**：该提交是并发安全修复，**建议回迁到 1.4.x**。理由：
  1. 1.4.x 的 `OAuth2Util.AuthSession.refresh()` 应有同样的 `this.headers = RESTUtil.merge(headers, ...)` 模式，存在同样的潜在问题；
  2. 改动极小（3 行），cherry-pick 冲突概率低；
  3. 不影响正常流程，仅在并发场景下提供防御性保护，回迁风险低；
  4. 注意：该修复只是局部防御，并非完整的并发解决方案。如果 1.4.x 对 `AuthSession` 的并发有更严格要求，可能需要额外加 `synchronized` 或使用 `AtomicReference`；但本提交本身不引入新的并发风险，是纯粹的防御性改进；
  5. 该修复与 0896（#10314，`issued_token_type` 兜底）在同一文件 `OAuth2Util.java` 中，若 1.4.x 已回迁 0896，cherry-pick 本提交时需注意 `refresh` 方法中 `response.issuedTokenType()` 的调用——0896 修改的是 `fromTokenResponse` 方法，本提交修改的是 `refresh` 方法，两者位置不同，不会冲突。
