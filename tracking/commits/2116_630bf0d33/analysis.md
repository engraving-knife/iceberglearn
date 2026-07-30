# 提交 2116：OpenAPI: Add retries when finding free port for REST server

## 提交信息

- **序号**：2116 / 4088
- **哈希**：630bf0d33b11f55d1702a54759227bb07d5cd7fc
- **短哈希**：630bf0d33
- **日期**：2025-05-13 18:19:54 +0800
- **作者**：Manu Zhang
- **提交说明**：OpenAPI: Add retries when finding free port for REST server (#13017)
- **PR/Issue**：#13017

## 总体目的

本提交解决了 REST Catalog 测试服务器在启动时因端口冲突而失败的问题。在并发测试环境中，`RCKUtils.findFreePort()` 找到的"空闲端口"可能在服务器实际绑定之前被其他进程占用，导致 `BindException`。原实现在构造函数中一次性查找端口，如果后续启动时端口已被占用则直接失败。本提交通过引入重试机制，在端口绑定失败时重新查找空闲端口并重试启动，最多尝试 10 次，显著提高了测试的稳定性。

## 如何达成设计目的

1. 将端口查找的时机从构造函数延迟到 `beforeAll` 方法中，避免构造和启动之间的时间窗口问题
2. 引入 `findFreePort` 布尔标志，在构造函数中记录是否需要查找空闲端口，而不是立即查找
3. 在 `beforeAll` 中实现重试循环，最多尝试 10 次
4. 每次重试时重新调用 `RCKUtils.findFreePort()` 获取新的空闲端口
5. 仅对 `BindException` 进行重试，其他异常直接抛出
6. 最后一次重试失败时抛出 RuntimeException

## 修改详情

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RESTServerExtension.java` (修改, +24/-6 lines)

**修改目的**：为 REST 测试服务器启动添加端口绑定重试机制。

**工作逻辑**：
- **构造函数变更**：原来在构造函数中立即将 FREE_PORT 替换为实际查找到的端口，现在改为仅设置 `findFreePort` 布尔标志，记录是否需要查找空闲端口
- **新增 findFreePort 字段**：在无参构造函数中设为 false，在带参构造函数中根据配置判断
- **beforeAll 方法重写**：引入最多 10 次的重试循环。每次循环中，如果 `findFreePort` 为 true，则重新调用 `RCKUtils.findFreePort()` 获取新端口并更新 config；然后创建并启动服务器。如果捕获到 `BindException` 且仍在重试次数内（且 findFreePort 为 true），则继续重试；否则抛出 RuntimeException。其他类型异常也直接抛出。
- **新增 import**：引入 `java.net.BindException`

## 总结

本提交通过引入端口绑定的重试机制，解决了 REST Catalog 测试在并发环境下因端口竞争而偶发失败的问题。将端口查找延迟到启动时并支持多次重试，有效提升了测试的稳定性和可靠性，属于测试基础设施的健壮性改进。
