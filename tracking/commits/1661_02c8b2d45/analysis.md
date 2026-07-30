# 提交 1661 02c8b2d45 分析

## 提交信息
- 哈希：02c8b2d45bef4f74c7fb9e36a9b3148d9901f44c
- 日期：2025-01-30 17:43:29 +0100
- 作者：Willi Raschkowski
- 消息：Core: Support removing keys from EnvironmentContext (#12103)

## 总体目的

本提交为 Iceberg 的 `EnvironmentContext` 类增加删除键值对的能力。`EnvironmentContext` 是一个全局的、进程级别的属性容器，用于存储和传递 Iceberg 运行时的环境信息（如 iceberg-version、引擎信息、部署环境等），这些信息会被写入提交的元数据中用于追踪和调试。

此前 `EnvironmentContext` 仅支持 `put(key, value)` 添加/更新键值对和 `get()` 获取整个属性 map，但不支持删除已存在的键。这在某些场景下造成不便：
- 测试场景：测试中临时 put 一个属性，测试结束后需要清理，否则会污染后续测试（因为 EnvironmentContext 是全局静态的）。
- 运行时场景：某些环境属性在特定阶段后不再适用（例如临时设置的部署标识），需要清除。

本次新增 `remove(key)` 方法，使 EnvironmentContext 的 API 与标准 Map 操作保持完整（put/get/remove 三件套），提升可测试性和灵活性。

## 如何达成设计目的

`EnvironmentContext` 内部维护一个静态的 `PROPERTIES` Map（`Properties` 类型）。`put` 方法直接调用 `PROPERTIES.put(key, value)`。为保持一致性，`remove` 方法直接调用 `PROPERTIES.remove(key)`，返回被移除的旧值（与 `Map.remove` 语义一致）。这样实现简单且与现有 `put` 行为对称。

### 修改详情

#### core/src/main/java/org/apache/iceberg/EnvironmentContext.java
新增静态方法：
```java
/**
 * Remove the key from the global properties map.
 *
 * @param key The key whose value to remove
 * @return The previous value associated with the key or null
 */
public static String remove(String key) {
  return PROPERTIES.remove(key);
}
```
- 方法签名 `public static String remove(String key)`，返回被删除键的旧值，若键不存在则返回 null。
- 直接委托给内部 `PROPERTIES` map 的 `remove` 方法。
- 完整的 Javadoc 注释说明参数和返回值语义。
- 注意：`Properties.remove(Object)` 返回 Object，但此处声明返回 String，存在隐式的向下转型。由于 put 时只接受 String 值，此转型在实际使用中是安全的。

#### core/src/test/java/org/apache/iceberg/TestEnvironmentContext.java
新增测试方法 `testPutAndRemove`：
1. `EnvironmentContext.put("test-key", "test-value")`：添加一个测试键值对。
2. 断言 `EnvironmentContext.get()` 包含该条目。
3. `EnvironmentContext.remove("test-key")`：删除该键，断言返回值为 "test-value"（旧值）。
4. 断言 `EnvironmentContext.get()` 不再包含该键。
5. 再次 `EnvironmentContext.remove("test-key")`：删除不存在的键，断言返回 null。

该测试覆盖了正常删除和删除不存在键两种情况，验证了 `remove` 方法与 `Map.remove` 标准语义一致。

## 小结

本次改动成效：
- 补全了 EnvironmentContext 的 Map 操作能力（put/get/remove），API 更完整。
- 提升了可测试性：测试中可以方便地清理临时设置的属性，避免测试间污染。
- 改动极小、风险极低：仅新增一个方法，不改变现有行为。

影响范围：Core 模块的 EnvironmentContext 类及其测试。EnvironmentContext 是被广泛使用的全局组件，但新增方法不影响现有调用方。

回迁到 1.4.x 注意事项：
- 这是一个极小的功能增强，向后兼容，回迁零风险。
- 1.4.x 若存在 EnvironmentContext 类（1.x 系列都应有），可直接回迁。
- 回迁仅需新增一个方法和一个测试，无依赖和冲突风险。
- 此类小工具方法回迁对 1.4.x 的价值在于：让 1.4.x 上的测试代码也能利用 remove 清理环境属性，或支持需要动态移除环境属性的特定用例。
