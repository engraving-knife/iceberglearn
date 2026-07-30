# 提交 0767：Avoid adding a closed client to the pool (#10337)

## 提交信息

- **序号**：0767 / 4088
- **哈希**：2cd6d0d475a782b23bf53077703873d1c593f2c4
- **短哈希**：2cd6d0d47
- **日期**：2024-05-15 14:11:53 -0700
- **作者**：Yufei Gu
- **提交说明**：Avoid adding a closed client to the pool (#10337)
- **PR/Issue**：#10337

## 总体目的

修复 `ClientPoolImpl` 在连接异常重试路径上的一个缺陷：当 `reconnect` 返回的是**新的客户端对象**（而非复用原对象）时，重试成功后会被归还到池中的仍是**已被关闭的旧客户端**，而真正可用的新客户端则被丢弃泄漏。本提交将重试逻辑从独立的 `retryAction` 方法内联回 `run` 方法，并通过 `client = reconnect(client)` 重新赋值局部变量，使 `finally { release(client); }` 归还的始终是最新且可用的客户端，从而避免向池中注入已关闭的客户端。

## 如何达成设计目的

### Bug 成因

`ClientPoolImpl` 是 Iceberg 通用的客户端连接池抽象（被 `HiveClientPool`、`JdbcClientPool` 等继承），其 `run` 方法封装"借出客户端 → 执行 action → 归还客户端"的流程，并在连接异常时重试。修复前的代码结构如下（简化）：

```java
public <R> R run(Action<R, C, E> action, boolean retry) throws E, InterruptedException {
  C client = get();                    // 1. 从池中借出 client
  try {
    return action.run(client);         // 2. 执行 action
  } catch (Exception exc) {
    if (!retry || !isConnectionException(exc)) {
      throw exc;
    }
    return retryAction(action, exc, client);   // 3. 委托重试，传入原 client
  } finally {
    release(client);                   // 4. finally 归还的是局部变量 client —— 即"原 client"
  }
}

private <R> R retryAction(Action<R, C, E> action, Exception originalFailure, C client) {
  int retryAttempts = 0;
  while (retryAttempts < maxRetries) {
    try {
      C reconnectedClient = reconnect(client);   // 5. reconnect 返回新对象 reconnectedClient
      return action.run(reconnectedClient);      // 6. 用新对象执行 action
    } catch (Exception exc) {
      ...
    }
  }
  throw reconnectExc.cast(originalFailure);
}
```

关键在于 `reconnect` 的两种实现语义不同：

- **`HiveClientPool.reconnect`**：对**同一个** `IMetaStoreClient` 对象先 `client.close()` 再 `client.reconnect()`，返回的是**原对象**（已重新打开）。此时 `reconnectedClient == client`，归还原对象没问题。
- **`JdbcClientPool.reconnect`**：`close(client)` 关闭旧连接，`return newClient()` 返回一个**全新的 `Connection` 对象**。此时 `reconnectedClient != client`，且原 `client` 已被 `close`。

对 `JdbcClientPool` 这类实现，bug 流程为：

1. `client = get()` 从池借出旧连接。
2. `action.run(client)` 抛连接异常。
3. 进入 `retryAction`，`reconnectedClient = reconnect(client)` 关闭旧 `client`、返回新 `reconnectedClient`。
4. `action.run(reconnectedClient)` 在新连接上成功，方法返回。
5. 回到 `run` 的 `finally`，`release(client)` 把**已被关闭的旧 client** 放回池中。
6. 新的、可用的 `reconnectedClient` 既不在池中、`currentSize` 也未调整 —— 被泄漏。

后果：池里现在躺着一个已关闭的连接。下一次 `get()` 取到它，`action.run` 立即失败。这会导致 JDBC Catalog 等场景下，一次连接抖动后池被"毒化"，后续请求持续失败。

### 修复逻辑

修复将 `retryAction` 的循环逻辑内联回 `run`，并在每次重连时**重新赋值局部变量 `client`**：

```java
public <R> R run(Action<R, C, E> action, boolean retry) throws E, InterruptedException {
  C client = get();
  try {
    return action.run(client);
  } catch (Exception exc) {
    if (retry && isConnectionException(exc)) {
      int retryAttempts = 0;
      while (retryAttempts < maxRetries) {
        try {
          client = reconnect(client);      // 关键：重新赋值 client 指向新对象
          return action.run(client);
        } catch (Exception e) {
          if (isConnectionException(e)) {
            retryAttempts++;
            Thread.sleep(connectionRetryWaitPeriodMs);
          } else {
            throw reconnectExc.cast(exc);
          }
        }
      }
    }
    throw exc;
  } finally {
    release(client);    // 现在归还的是最新（重连后、可用）的 client
  }
}
```

核心改动是 `client = reconnect(client)`：无论 `reconnect` 返回的是原对象（Hive）还是新对象（JDBC），`client` 局部变量都指向"当前最新且可用"的客户端。`finally` 中的 `release(client)` 因此始终归还可用客户端：

- 对 `JdbcClientPool`：归还的是新创建、刚执行成功（或最后一次重连）的连接，旧连接已被 `reconnect` 内部 `close` 且不再入池。
- 对 `HiveClientPool`：归还的是同一个重连后的对象，行为与修复前一致。

同时移除了独立的 `retryAction` 私有方法（其逻辑已内联），消除了"重试在另一方法中、归还却在原方法 finally 中"的作用域割裂——这正是 bug 的根源：`reconnectedClient` 是 `retryAction` 的局部变量，无法影响 `run` 中的 `client`。

### 异常语义保持

内联后的异常处理语义与原 `retryAction` 一致：
- 重连后 action 成功 → 返回结果。
- 重连后 action 抛**非连接异常** → `throw reconnectExc.cast(exc)`（抛原始失败异常，`exc` 是首次的连接异常）。注意这里 cast 的是外层捕获的 `exc` 而非内层 `e`，与原逻辑一致。
- 重连后 action 抛**连接异常** → `retryAttempts++`、`sleep` 后继续循环。
- 循环耗尽 → 落到方法末尾 `throw exc`（抛原始失败异常）。

### 测试验证

测试做了三处关键改动来覆盖该 bug：

1. **`reconnect` 返回新对象**：原 `MockClientPoolImpl.reconnect` 返回 `client`（原对象，模拟 Hive 行为，无法暴露 bug）；改为 `return new MockClient(reconnectionAttempts)`（返回新对象，模拟 JDBC 行为，正是触发 bug 的语义）。新增 `MockClient(int retryableFailures)` 构造函数支持。

2. **预先放入一个客户端**：`MockClient firstClient = mockClientPool.newClient(); mockClientPool.clients().add(firstClient);` —— 让池初始就有一个 client，使 `get()` 借出的就是 `firstClient`，便于断言重连后池顶不再是它。

3. **断言池顶已替换**：`assertThat(mockClientPool.clients().peekFirst().equals(firstClient)).isFalse();` —— 验证重连成功后池中第一个客户端**不是**原来的 `firstClient`（即旧 client 没被放回，新 client 被放回）。若无此修复，`firstClient` 会被 `release` 放回池顶，断言失败。

为支持测试访问内部队列，新增 `@VisibleForTesting Deque<C> clients()` 方法暴露 `clients` 字段。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ClientPoolImpl.java`

**修改目的**：修复重连后归还已关闭客户端的 bug。

**import 新增**：`org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting`。

**`run(Action, boolean)` 方法重构**：
- 原 catch 块中 `if (!retry || !isConnectionException(exc)) { throw exc; } return retryAction(action, exc, client);` 改为：`if (retry && isConnectionException(exc))` 时内联 while 循环重试，循环内 `client = reconnect(client)` 重新赋值；循环外 `throw exc`。
- `finally { release(client); }` 不变，但因 `client` 已被重新赋值，归还的是新客户端。

**移除 `retryAction` 私有方法**：其逻辑已内联到 `run`。

**新增 `clients()` 方法**：`@VisibleForTesting Deque<C> clients() { return clients; }`，供测试断言池内容。

### `core/src/test/java/org/apache/iceberg/TestClientPoolImpl.java`

**修改目的**：复现并验证 bug 修复。

**`testRetrySucceedsWithinMaxAttempts` 改动**：
- 预先 `newClient()` 并 `clients().add(firstClient)` 注入初始客户端。
- 末尾新增 `assertThat(mockClientPool.clients().peekFirst().equals(firstClient)).isFalse();` 断言池顶非旧客户端。
- 注释说明：初始化池中放一个 client，以验证重连后 client 被替换。

**`MockClient` 改动**：新增 `MockClient(int retryableFailures)` 构造函数；整理字段格式。

**`MockClientPoolImpl.reconnect` 改动**：`return client` 改为 `return new MockClient(reconnectionAttempts)`，模拟"reconnect 返回新对象"的语义（与 `JdbcClientPool` 一致），这是触发原 bug 的必要条件。

## 小结

- **成效**：修复了 `ClientPoolImpl` 在 `reconnect` 返回新客户端对象时，将已关闭旧客户端归还入池、新客户端泄漏的 bug。修复后 `JdbcClientPool` 等实现不再因单次连接抖动而毒化整个连接池。修复方式简洁——通过重新赋值 `client` 局部变量，使 `finally` 归还的始终是最新可用客户端，并消除了原 `retryAction` 与 `run` 之间的作用域割裂。测试通过让 `reconnect` 返回新对象、断言池顶客户端已替换，精准覆盖了该场景。
- **影响范围**：影响 `iceberg-core` 的 `ClientPoolImpl`，进而影响所有继承它的连接池（`JdbcClientPool`、`HiveClientPool` 以及未来任何 `reconnect` 返回新对象的实现）。对 `HiveClientPool`（reconnect 返回原对象）行为不变；对 `JdbcClientPool`（reconnect 返回新对象）是关键修复。该类是 Catalog 层基础组件，但改动前后对外契约（`run` 签名、异常语义）不变，仅修正了错误的重试归还行为。
- **回迁注意事项**：
  1. 此提交位于 `core` 模块，是 1.4.x 分支必备的基础修复，强烈建议回迁。
  2. 回迁时注意：上一提交序号 0753（`ClientPoolImpl` 引入 `retryAction` 与 `maxRetries` 多次重试循环）已将 `retryAction` 提取为独立方法。本提交恰恰是把 `retryAction` 内联回 `run`。若 1.4.x 分支已含 0753 的多次重试逻辑，则本提交的 cherry-pick 应较为顺滑（结构一致）；若 1.4.x 仍是更早期的单次重试版本，需手动对齐重试循环结构。
  3. 新增的 `clients()` 测试访问器是 `@VisibleForTesting`，回迁时需一并带上以支持测试断言。
  4. 测试中 `MockClient(int)` 构造函数与 `reconnect` 返回新对象的改动是测试能暴露 bug 的关键，回迁测试时必须同时带上。
  5. 内联后异常处理中 `throw reconnectExc.cast(exc)` 抛的是外层原始 `exc`（而非内层 `e`），回迁手合并时需保持此语义，勿误改为 `e`。
