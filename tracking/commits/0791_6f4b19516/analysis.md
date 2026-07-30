# 提交 0791：Prevent deadlock in Jackson (#10379)

## 提交信息

- **序号**：0791 / 4088
- **哈希**：6f4b19516bc1cc4d4a89fd2018b918b6f7e6d625
- **短哈希**：6f4b19516
- **日期**：2024-05-27 16:21:56 +0200
- **作者**：Robert Stupp <snazy@snazy.de>
- **提交说明**：Prevent deadlock in Jackson (#10379)
- **PR/Issue**：#10379

## 总体目的

Iceberg 的 REST 客户端和 AWS S3 签名模块在使用 Jackson 进行 JSON 序列化/反序列化时，使用了已废弃的 `PropertyNamingStrategy.KebabCaseStrategy` 类。Jackson 官方在该类的使用中会始终打印一条警告信息，提示该类存在死锁风险，建议改用 `PropertyNamingStrategies.KebabCaseStrategy`。

具体警告信息如下：
```
PropertyNamingStrategy.KebabCaseStrategy is used but it has been deprecated due to risk of deadlock. Consider using PropertyNamingStrategies.KebabCaseStrategy instead. See https://github.com/FasterXML/jackson-databind/issues/2715 for more details.
```

这个提交的目的就是将所有使用旧版 `PropertyNamingStrategy.KebabCaseStrategy` 的地方替换为新版 `PropertyNamingStrategies.KebabCaseStrategy`，从而消除死锁风险并消除烦人的警告信息。`PropertyNamingStrategies` 是 Jackson 2.12 引入的替代类，Iceberg 项目使用的 Jackson 版本（jackson-bom 2.14.2）已支持该类，因此替换是安全的。

## 如何达成设计目的

提交通过将 `PropertyNamingStrategy.KebabCaseStrategy`（已废弃，有死锁风险）替换为 `PropertyNamingStrategies.KebabCaseStrategy`（推荐替代类）来达成目的。两个类的功能完全等价，都是将 JSON 属性名转换为 kebab-case 格式（如 `propertyName` -> `property-name`），但新版类的内部实现避免了死锁风险。

修改涉及两处：AWS S3 签器使用的 `S3ObjectMapper` 和 REST 客户端使用的 `RESTObjectMapper`。这两个类是 Iceberg 中仅有的使用 KebabCaseStrategy 的地方，替换后即可完全消除死锁隐患。

值得注意的是，代码中保留了一段注释提到"Spark 仍依赖 jackson 2.13.x，因此不能使用 `PropertyNamingStrategies.KebabCaseStrategy.INSTANCE`（jackson 2.14 引入）"。但实际代码使用的是 `new PropertyNamingStrategies.KebabCaseStrategy()`（构造函数形式），而非 `INSTANCE` 单例，因为构造函数形式在 Jackson 2.12+ 即可用。该注释描述的是为何不使用更推荐的 `INSTANCE` 单例，而非不能使用 `PropertyNamingStrategies` 类本身。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3ObjectMapper.java`

**修改目的**：将 AWS S3 签名模块的 Jackson ObjectMapper 配置从已废弃的 PropertyNamingStrategy 替换为新版 PropertyNamingStrategies，消除死锁风险。

**工作逻辑**：`S3ObjectMapper` 是 AWS S3 签名相关的 JSON 序列化器，用于序列化/反序列化 `S3SignRequest`、`S3SignResponse` 等对象。在 `mapper()` 方法的静态初始化块中，原来使用 `new PropertyNamingStrategy.KebabCaseStrategy()` 设置命名策略，现替换为 `new PropertyNamingStrategies.KebabCaseStrategy()`。同时 import 语句从 `com.fasterxml.jackson.databind.PropertyNamingStrategy` 改为 `com.fasterxml.jackson.databind.PropertyNamingStrategies`。两行修改，功能完全等价。

### `core/src/main/java/org/apache/iceberg/rest/RESTObjectMapper.java`

**修改目的**：将 REST 客户端模块的 Jackson ObjectMapper 配置从已废弃的 PropertyNamingStrategy 替换为新版 PropertyNamingStrategies，消除死锁风险。

**工作逻辑**：`RESTObjectMapper` 是 Iceberg REST 协议的 JSON 序列化器，用于所有 REST API 请求和响应的序列化/反序列化。在 `mapper()` 方法的静态初始化块中，做了与 S3ObjectMapper 完全相同的替换：import 从 `PropertyNamingStrategy` 改为 `PropertyNamingStrategies`，实例化从 `new PropertyNamingStrategy.KebabCaseStrategy()` 改为 `new PropertyNamingStrategies.KebabCaseStrategy()`。

## 小结

- **成效**：消除了 Jackson 死锁风险，不再打印废弃警告信息。所有 Iceberg REST 客户端和 AWS S3 签名模块的 JSON 处理都使用了线程安全的命名策略实现。
- **影响范围**：AWS S3 签名模块（`aws` 模块）和 REST 客户端模块（`core` 模块）的 JSON 序列化行为。由于新旧策略功能完全等价，不影响序列化结果的兼容性。
- **回迁注意事项**：`PropertyNamingStrategies` 类在 Jackson 2.12+ 引入，1.4.x 分支使用 jackson-bom 2.14.2，完全支持该类，回迁无兼容性问题。注意代码中保留了关于 Spark 依赖 jackson 2.13.x 的注释，说明不能使用 `INSTANCE` 单例（jackson 2.14 引入），但构造函数形式 `new PropertyNamingStrategies.KebabCaseStrategy()` 在 jackson 2.12+ 即可用，因此回迁是安全的。修改量极小（每文件仅改 2 行），冲突风险低。
