# 提交 2338：Core: Fix a cast that is too narrow (#12743)

## 提交信息

- **序号**：2338 / 4088
- **哈希**：26048839cb795a1e2c38ed592b45021fe37fdd51
- **短哈希**：26048839cb
- **日期**：2025-07-11 08:10:26 +0200
- **作者**：Angelo Genovese
- **提交说明**：Core: Fix a cast that is too narrow (#12743)
- **PR/Issue**：#12743

## 总体目的

本提交修复了 `ClientPoolImpl` 中异常类型转换过于狭窄（too narrow）的 bug。

`ClientPoolImpl` 是 Iceberg 中客户端连接池的基类，用于管理对外部系统（如 JDBC 数据库）的连接。当连接操作抛出异常时，`isConnectionException` 方法判断该异常是否为连接异常，如果是则进行重连重试；重试耗尽后，需要将异常重新抛出。

问题出在重试耗尽后的异常抛出逻辑：原代码使用 `reconnectExc.cast(exc)` 来转换异常类型。`reconnectExc` 是 `ClientPoolImpl` 的泛型参数 `E`（异常类型）对应的 `Class` 对象。然而，在 `JdbcClientPool` 中，`isConnectionException` 方法会对任何具有正确 SQL 状态码的 `SQLException` 返回 `true`，这些异常不一定恰好是 `reconnectExc` 指定的类型，而是其子类或其他兼容类型。

当 `isConnectionException` 对一个非 `reconnectExc` 精确类型的异常返回 `true` 时，`reconnectExc.cast(exc)` 会抛出 `ClassCastException`，因为 `cast` 方法要求对象必须是目标类的实例。但实际上，被处理的异常 `exc` 始终是泛型参数 `E` 的子类实例，直接强转为 `E` 即可。

## 如何达成设计目的

将 `reconnectExc.cast(exc)` 改为直接强制类型转换 `(E) exc`。由于 `exc` 始终是 `E` 的子类实例（`ClientPoolImpl` 的泛型约束 `E extends Exception`），直接强转是安全的。这避免了 `Class.cast()` 方法对精确类型匹配的严格要求。

同时，新增了多个测试用例验证修复的正确性，包括自定义异常类型的重试和非重试场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ClientPoolImpl.java` (+1/-1 lines)

**修改目的**：修复异常类型转换过于狭窄的问题。

**工作逻辑**：将 `throw reconnectExc.cast(exc)` 改为 `throw (E) exc`。由于 `exc` 是 `isConnectionException` 判定为连接异常的异常，它始终是泛型 `E` 的子类实例，直接强转即可，无需通过 `Class.cast()` 做精确类型检查。

### `core/src/test/java/org/apache/iceberg/TestClientPoolImpl.java` (+97/-6 lines)

**修改目的**：新增测试覆盖自定义异常类型的重试行为。

**工作逻辑**：

1. **新增 `CustomException` 类**：继承 `NonRetryableException`，包含 `retryable` 布尔字段和 `isRetryable()` 方法，用于模拟可配置是否可重试的自定义异常。

2. **`customExceptionIsRetried` 测试**：使用 `CustomException`（retryable=true）作为可重试异常，验证连接池正确重试并在成功后替换客户端。

3. **`nonRetryableExceptionAfterRetryableException` 测试**：验证先重试可重试异常、再遇到不可重试异常时正确停止重试并抛出异常。

4. **`customNonRetryableExceptionIsNotRetried` 测试**：验证自定义不可重试异常不会被重试。

5. **MockClient 增强**：新增 `succeedAfter(List<RuntimeException>)` 方法支持按异常列表依次抛出；新增 `succeedAfter(int, Supplier<RuntimeException>)` 方法支持自定义异常供应器；重命名 `failWithNonRetryable` 为 `throwNonRetryableException` 并新增 `throwCustomNonRetryableException`。

6. **MockClientPoolImpl 增强**：重写 `isConnectionException` 方法，在父类判断基础上，对 `CustomException` 根据 `isRetryable()` 判断是否为连接异常。

7. **既有测试适配**：`testRetryableConnectionException` 中断言改为 AssertJ 的 `assertThat(...).first().isNotEqualTo(...)` 风格；`testNoRetryingNonRetryableException` 中方法引用名更新。

## 总结

本提交修复了 `ClientPoolImpl` 中异常类型转换的 bug，将 `reconnectExc.cast(exc)` 改为 `(E) exc`，解决了 JDBC 客户端池中因异常类型不完全匹配导致 `ClassCastException` 的问题。新增的多组测试确保了修复的正确性和对不同异常类型的健壮处理。
