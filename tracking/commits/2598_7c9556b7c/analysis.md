# 提交 2598：REST: Fix port binding problem with TIME_WAIT for RESTCatalogServer in test fixture (#13992)

## 提交信息

- **序号**：2598 / 4088
- **哈希**：7c9556b7c0f7bcaba0c2ec66f9edb4d23580920c
- **短哈希**：7c9556b7c
- **日期**：2025-09-04 19:32:24 -0700
- **作者**：jackylee
- **提交说明**：REST: Fix port binding problem with TIME_WAIT for RESTCatalogServer in test fixture (#13992)
- **PR/Issue**：#13992

## 总体目的

本次提交修复了 `RESTCatalogServer` 测试固件在测试中遇到的端口绑定失败问题，该问题由 TCP TIME_WAIT 状态引起。

在测试场景中，`RESTCatalogServer` 会启动一个嵌入式的 Jetty HTTP 服务器来提供 REST Catalog 服务。当测试频繁启动和停止服务器时（例如每个测试类或测试方法启动一个新的服务器实例），前一次停止的服务器端口可能仍处于 TCP TIME_WAIT 状态。

TCP TIME_WAIT 是 TCP 协议的一个正常状态：当主动关闭连接的一方在发送完最后一个 ACK 后，会进入 TIME_WAIT 状态并持续一段时间（通常为 2*MSL，约 60-120 秒），以确保迟到的数据包能被正确处理。在此期间，默认情况下同一端口不能被重新绑定（bind），导致新启动的服务器报 "Address already in use" 错误。

通过设置 `SO_REUSEPORT` socket 选项，允许同一端口在 TIME_WAIT 状态下被重新绑定，从而解决测试中的端口冲突问题。

## 如何达成设计目的

在 Jetty Server 启动前，遍历其所有 Connector，将每个 `ServerConnector` 的 `reusePort` 属性设置为 true。这会底层设置 socket 的 `SO_REUSEPORT` 选项，允许多个 socket 绑定到同一端口（或至少允许在 TIME_WAIT 状态下重用端口）。

## 修改详情

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RESTCatalogServer.java` (+5/-0 lines)

**修改目的**：启用端口重用以避免 TIME_WAIT 导致的绑定失败。

**工作逻辑**：

1. 新增导入 `org.eclipse.jetty.server.Connector` 和 `org.eclipse.jetty.server.ServerConnector`。

2. 在 `httpServer.setHandler(context)` 之后、`httpServer.start()` 之前，添加如下代码：
```java
for (Connector connector : httpServer.getConnectors()) {
    ((ServerConnector) connector).setReusePort(true);
}
```

这段代码遍历服务器的所有连接器，将每个 `ServerConnector` 的 `reusePort` 设为 true。Jetty 的 `ServerConnector.setReusePort(true)` 会在底层 channel 上设置 `SO_REUSEPORT` 选项（在 Linux 上）或 `SO_REUSEADDR` 选项，允许端口在 TIME_WAIT 状态下被重新绑定。

之所以在 `start()` 之前设置，是因为端口绑定发生在 `start()` 过程中，必须在绑定前配置好 socket 选项。

## 总结

这是一个测试稳定性修复，解决了 REST Catalog 测试固件因 TCP TIME_WAIT 导致的端口绑定失败问题。通过启用 `SO_REUSEPORT`，测试可以更可靠地重复启动和停止服务器，减少因端口冲突导致的偶发测试失败。这类问题在 CI 环境中尤其常见，因为 CI 机器上可能频繁运行和重试测试。
