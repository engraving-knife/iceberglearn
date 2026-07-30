# 提交 1244：Make connect compatable with kafka plugin.discovery (#10536)

## 提交信息

- **序号**：1244 / 4088
- **哈希**：22a6b19c2e226eacc0aa78c1f2ffbdbb168b13be
- **短哈希**：22a6b19c2
- **日期**：2024-10-17（Thu Oct 17 03:04:08 2024 +0900）
- **作者**：SeungwanJo <wh7923@gmail.com>
- **提交说明**：Make connect compatable with kafka plugin.discovery (#10536)
- **PR/Issue**：#10536

## 总体目的

让 Iceberg Kafka Connect Sink 连接器能够被 Kafka Connect 的"插件发现"（plugin.discovery）机制识别。Kafka Connect 在启动时会扫描 classpath 下 `META-INF/services/org.apache.kafka.connect.sink.SinkConnector`（以及 source 对应文件）这类 SPI 服务描述文件，列出可用的 Connector 实现。Iceberg Connect 模块此前缺少该文件，导致通过 Kafka Connect 标准 plugin discovery 机制（如 `plugin.discovery=hybrid` 或 `service_load`）加载时找不到 `IcebergSinkConnector`，必须依赖旧的扫描机制（`plugin.discovery=scan`）才能工作。

本提交新增该 SPI 服务描述文件，使 Iceberg Sink Connector 兼容 Kafka 3.x 引入的现代 plugin discovery 模式，便于在 Confluent Cloud、Kafka Connect REST API 等场景下被自动注册和发现。

## 如何达成设计目的

在 `kafka-connect/kafka-connect/src/main/resources/META-INF/services/` 目录下新增一个名为 `org.apache.kafka.connect.sink.SinkConnector` 的文件，文件内容为单行 Connector 实现类的全限定名 `org.apache.iceberg.connect.IcebergSinkConnector`，并附带 Apache License 头。这是 Java SPI 标准格式，Kafka Connect 的 plugin discovery 会读取该文件并实例化其中声明的类。

## 修改详情

### `kafka-connect/kafka-connect/src/main/resources/META-INF/services/org.apache.kafka.connect.sink.SinkConnector`（新增）

**修改目的**：声明 Iceberg 的 Sink Connector 实现类，供 Kafka Connect plugin discovery 机制加载。

**工作逻辑**：新文件，内容为 Apache 2.0 许可证头注释加上一行：

```
org.apache.iceberg.connect.IcebergSinkConnector
```

Kafka Connect 启动时（特别是 `plugin.discovery=service_load` 或 `hybrid` 模式）会通过 `ServiceLoader` 机制读取 `META-INF/services/org.apache.kafka.connect.sink.SinkConnector`，把列出的类作为可用的 SinkConnector 实现注册到 plugin registry。用户在创建 connector 时 `connector.class` 设为 `org.apache.iceberg.connect.IcebergSinkConnector` 即可被找到并实例化。

## 小结

- **成效**：Iceberg Kafka Connect Sink 现在能被 Kafka Connect 标准 plugin discovery 机制（`service_load`/`hybrid` 模式）识别，无需依赖已废弃的 `scan` 模式；部署体验更接近其他标准 Kafka Connect 插件。
- **影响范围**：仅新增一个 SPI 服务描述文件，不涉及任何代码逻辑变更，对运行时行为无副作用。
- **回迁到 1.4.x 的注意事项**：
  - 该改动是纯资源文件新增，安全且向后兼容，**建议回迁**到 1.4.x，前提是 1.4.x 分支已包含 `kafka-connect` 模块与 `IcebergSinkConnector` 类（路径与全限定名需一致）。
  - 回迁时只需新建同名文件并写入 `org.apache.iceberg.connect.IcebergSinkConnector`，无依赖冲突风险。
  - 如果 1.4.x 的 Kafka Connect 模块结构不同（例如类名或包名不同），需要相应调整文件内容中的类全限定名。
