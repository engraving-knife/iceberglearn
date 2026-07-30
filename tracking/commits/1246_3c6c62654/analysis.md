# 提交 1246：Kafka Connect: Add regex for property file match (#11303)

## 提交信息

- **序号**：1246 / 4088
- **哈希**：3c6c62654c50f7f5b093f82b92cfb81f6ed8ef33
- **短哈希**：3c6c62654
- **日期**：2024-10-16（Wed Oct 16 17:51:40 2024 -0500）
- **作者**：RyanJClark <39035478+ryanjclark@users.noreply.github.com>
- **提交说明**：Kafka Connect: Add regex for property file match (#11303)
- **PR/Issue**：#11303

## 总体目的

修复 Iceberg Kafka Connect Sink 在加载 worker 属性文件时的 worker 进程识别逻辑。`IcebergSinkConfig` 会从 `sun.java.command` 系统属性中解析 Java 启动命令，判断当前是否运行在 Kafka Connect worker（`ConnectDistributed` 或 `ConnectStandalone`）下，若是则从命令行第二个参数读取 worker 配置文件路径并加载其中的属性（Kafka Connect 不直接把 worker 属性暴露给 connector，所以 Iceberg 需要自己解析）。

原实现用 `args.get(0).endsWith(".ConnectDistributed")` / `endsWith(".ConnectStandalone")` 判断，存在两个问题：
1. `endsWith` 对类名前缀没有"边界"约束，例如名为 `MyConnectDistributed` 的自定义类也会被误判命中。
2. 当类名后跟额外字符（如某些打包工具生成的 `ConnectDistributedWrapper`）时，原 `endsWith(".ConnectDistributed")` 因为 `.ConnectDistributed` 后还有 `Wrapper` 而不匹配，导致合法 worker 进程被漏判，属性文件不会被加载。

本提交把判断改为正则 `.*\\.ConnectDistributed.*` / `.*\\.ConnectStandalone.*`，既要求前面有包路径分隔符 `.`，又允许类名后跟任意后缀（如 `Wrapper`、`Main`），更鲁棒地覆盖各类 worker 启动器。

## 如何达成设计目的

1. 抽取一个 `@VisibleForTesting` 静态方法 `checkClassName(String className)`，内部用两条正则 `.*\\.ConnectDistributed.*` 和 `.*\\.ConnectStandalone.*` 做匹配，返回布尔。
2. 在原调用点用 `checkClassName(args.get(0))` 替换 `args.get(0).endsWith(...)`。
3. 新增单测 `testCheckClassName`，覆盖标准类名、不同包路径、带后缀（`ConnectDistributedWrapper`）以及非 worker 类（`KafkaProducer`）。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConfig.java`

**修改目的**：用正则替换 `endsWith` 判断 worker 类名。

**工作逻辑**：

新增方法：

```java
@VisibleForTesting
static boolean checkClassName(String className) {
  return (className.matches(".*\\.ConnectDistributed.*")
      || className.matches(".*\\.ConnectStandalone.*"));
}
```

原调用点改为：

```java
if (args.size() > 1 && checkClassName(args.get(0))) {
  Properties result = new Properties();
  try (InputStream in = Files.newInputStream(Paths.get(args.get(1)))) {
    result.load(in);
    ...
```

正则语义：
- `.*\\.ConnectDistributed.*` 要求字符串中存在 `.ConnectDistributed` 子串（前面必须是 `.`，即包分隔符），后面可跟任意字符。
- 这同时满足"必须有包路径前缀"和"允许类名后缀"两个约束，例如 `org.apache.kafka.connect.cli.ConnectDistributed`、`some.other.package.ConnectDistributed`、`some.package.ConnectDistributedWrapper` 都会命中；而 `MyConnectDistributed`（无前置 `.`）和 `org.apache.kafka.clients.producer.KafkaProducer` 都不命中。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/IcebergSinkConfigTest.java`

**修改目的**：覆盖 `checkClassName` 的各种边界情况。

**工作逻辑**：新增 `testCheckClassName` 测试，断言：
- `org.apache.kafka.connect.cli.ConnectDistributed` → true
- `org.apache.kafka.connect.cli.ConnectStandalone` → true
- `some.other.package.ConnectDistributed` → true
- `some.other.package.ConnectStandalone` → true
- `some.package.ConnectDistributedWrapper` → true（带后缀）
- `org.apache.kafka.clients.producer.KafkaProducer` → false

## 小结

- **成效**：worker 进程识别更鲁棒，覆盖带 `Wrapper`/`Main` 后缀的自定义启动类；同时通过要求前置 `.` 避免误判非 worker 类。这让 Iceberg Sink Connector 在更多部署形态下能正确加载 worker 配置（如 auth、commit 重试等依赖 worker 配置的功能）。
- **影响范围**：仅 `kafka-connect` 模块，影响 `IcebergSinkConfig.loadWorkerProperties`（推断）路径，行为更宽松，向后兼容。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个小修复，**建议回迁**到 1.4.x（前提是 1.4.x 已包含 `IcebergSinkConfig` 与对应加载逻辑）。
  - 回迁时需同步引入 `@VisibleForTesting` 导入（`org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting`）。
  - 正则使用 `String.matches`，无需额外依赖，回迁无兼容性风险。
  - 若 1.4.x 上 `IcebergSinkConfig` 类结构差异较大，需要根据实际调用点调整。
