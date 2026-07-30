# 提交 2476：Kafka Connect: Add manifests for the transformations (#13531)

## 提交信息

- **序号**：2476 / 4088
- **哈希**：252a4d034c47def12399a83b517fed7ca378385b
- **短哈希**：252a4d034
- **日期**：2025-08-08 08:00:52 -0700
- **作者**：Mickael Maison
- **提交说明**：Kafka Connect: Add manifests for the transformations (#13531)
- **PR/Issue**：#13531

## 总体目的

该提交为 Kafka Connect transforms 模块添加了 SPI（Service Provider Interface）服务注册清单文件，使 Kafka Connect 框架能够通过 Java 的 ServiceLoader 机制自动发现并加载 Iceberg 提供的各种 Transformation 实现。

Kafka Connect 的 Transformation（单消息转换，SMT）机制依赖 Java 的 ServiceLoader 来发现可用的转换器实现。这要求在 `META-INF/services/` 目录下放置以接口全限定名命名的清单文件，文件内容列出所有实现类的全限定名。Iceberg 的 `kafka-connect-transforms` 模块虽然定义了多个 Transformation 实现类（如 CopyValue、DebeziumTransform 等），但缺少对应的 SPI 清单文件，导致 Kafka Connect 无法自动发现这些转换器，用户无法通过 connector 配置直接引用它们。该提交补全了这一缺失。

## 如何达成设计目的

在 `kafka-connect-transforms` 模块的资源目录下创建 SPI 清单文件 `META-INF/services/org.apache.kafka.connect.transforms.Transformation`，文件中逐行列出所有已实现的 Transformation 类的全限定名。这样 Kafka Connect 框架在运行时通过 `ServiceLoader.load(Transformation.class)` 即可自动发现并注册这些转换器。

## 修改详情

### `kafka-connect/kafka-connect-transforms/src/main/resources/META-INF/services/org.apache.kafka.connect.transforms.Transformation` (+21/-0 lines, 新文件)

**修改目的**：创建 SPI 服务注册清单，使 Kafka Connect 能自动发现 Iceberg 的转换器实现。

**工作逻辑**：

文件以 Apache 2.0 许可证开头，随后列出 6 个 Transformation 实现类的全限定名：
- `org.apache.iceberg.connect.transforms.CopyValue`
- `org.apache.iceberg.connect.transforms.DebeziumTransform`
- `org.apache.iceberg.connect.transforms.DmsTransform`
- `org.apache.iceberg.connect.transforms.JsonToMapTransform`
- `org.apache.iceberg.connect.transforms.KafkaMetadataTransform`
- `org.apache.iceberg.connect.transforms.MongoDebeziumTransform`

每行一个类名，符合 Java ServiceLoader 的清单文件格式规范。

## 总结

该提交为 Kafka Connect transforms 模块添加了 Java SPI 服务注册清单文件，使 Kafka Connect 框架能够通过 ServiceLoader 机制自动发现 Iceberg 提供的 6 个 Transformation 实现。这是一个功能性补全提交，解决了转换器无法被自动发现的问题，使用户可以在 connector 配置中直接使用这些转换器。
