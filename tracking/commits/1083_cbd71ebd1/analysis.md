# 提交 1083：Core,AWS: Fix NPE in ResolvingFileIO when HadoopConf is not set (#10872)

## 提交信息

- **序号**：1083 / 4088
- **哈希**：cbd71ebd12f70da9449a6a1755b141054bbfb389
- **短哈希**：cbd71ebd1
- **日期**：2024-08-21 22:05:23 -0600
- **作者**：S N Munendra
- **提交说明**：Core,AWS: Fix NPE in ResolvingFileIO when HadoopConf is not set (#10872)
- **PR/Issue**：#10872

## 总体目的

`ResolvingFileIO` 是 Iceberg core 模块中一个按 location scheme 动态加载具体 `FileIO`（如 `S3FileIO`、`HadoopFileIO` 等）的代理实现，同时实现 `HadoopConfigurable`，会在内部用一个 `SerializableSupplier<Configuration>` 持有 Hadoop `Configuration`，以便在序列化/反序列化（如 Spark/Flink 任务分发到 executor）后仍能恢复配置。

问题在于：当 `ResolvingFileIO` 被初始化时没有设置 HadoopConf（即 `hadoopConf` 字段为 null，例如用户直接 `new ResolvingFileIO()` 后 `initialize(ImmutableMap.of())` 而未调用 `setConf`），随后调用任何依赖 `hadoopConf` 的方法（`serializeConfWith`、`getConf`、内部 IO 实例创建与配置回填等）都会触发 `NullPointerException`——代码直接 `hadoopConf.get()`，没有判空。该 NPE 在 #10872 中被报告，常见于测试场景或某些只设置 properties 而不设置 Hadoop 配置的部署中。

本提交的目标是让 `ResolvingFileIO` 在 `hadoopConf` 为 null 时也能优雅工作：`getConf()` 返回 null 而非抛 NPE，依赖 `getConf()` 的其它路径也随之安全降级。

## 如何达成设计目的

核心思路是把原先对 `hadoopConf.get()` 的直接调用统一替换为通过 `getConf()` 方法访问，并让 `getConf()` 自身判空：

- `getConf()` 改为 `Optional.ofNullable(hadoopConf).map(Supplier::get).orElse(null)`，当 `hadoopConf` 为 null 时返回 null 而非抛 NPE。
- `serializeConfWith()`、`reinitializeIOInstances()`（私有方法，用于在 Kryo 反序列化后回填配置）、以及 `io()`（按 scheme 创建/获取 IO 实例）中所有 `hadoopConf.get()` 全部替换为 `getConf()`，统一走判空逻辑。

这样即便 `hadoopConf` 未设置，这些路径也只是拿到 null 配置并按"无 Hadoop 配置"处理（例如不回填 conf），而不会因为 `Supplier` 自身为 null 而抛 NPE。同时新增测试 `testResolvingFileIOLoadWithoutConf`，验证不设置任何 conf 时仍能正常解析出 `S3FileIO`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/ResolvingFileIO.java`

**修改目的**：修复 `hadoopConf` 为 null 时的 NPE。

**工作逻辑**：
- `getConf()`：由 `return hadoopConf.get();` 改为 `return Optional.ofNullable(hadoopConf).map(Supplier::get).orElse(null);`，使 null `hadoopConf` 安全返回 null。
- `serializeConfWith(Function)`：`confSerializer.apply(hadoopConf.get())` 改为 `confSerializer.apply(getConf())`。
- `reinitializeIOInstances()`（私有方法，遍历已加载的 IO 实例，在 Kryo 反序列化后其 conf 为 null 时回填）：`((HadoopConfigurable) io).setConf(hadoopConf.get())` 改为 `setConf(getConf())`。
- `io(String location)`（按 scheme 查找/创建 FileIO）：`Configuration conf = hadoopConf.get()` 改为 `Configuration conf = getConf()`。

四处改动统一从"直接访问字段"切换为"通过 getter 访问"，使判空集中在 `getConf()` 一处。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIO.java`

**修改目的**：新增回归测试，确保无 HadoopConf 时 `ResolvingFileIO` 仍能加载 `S3FileIO`。

**工作逻辑**：新增 `testResolvingFileIOLoadWithoutConf`：创建 `ResolvingFileIO` 后只调用 `initialize(ImmutableMap.of())`（不 `setConf`），通过反射 `DynMethods` 调用其 `io("s3://foo/bar")`，断言返回的 `FileIO` 是 `S3FileIO` 实例。修复前该测试会因 `hadoopConf.get()` 抛 NPE 而失败。

## 小结

- **成效**：修复 `ResolvingFileIO` 在未设置 HadoopConf 时的 NPE，使 `getConf()` 安全返回 null，相关路径（序列化配置、IO 实例回填、按 scheme 创建 IO）不再抛异常；新增回归测试覆盖该场景。
- **影响范围**：仅 `core` 模块 `ResolvingFileIO.java`（4 处改动）和 `aws` 模块测试（新增 1 个测试方法），无 API 变更，行为兼容。
- **回迁到 1.4.x 的注意事项**：这是一个纯 bug fix，风险低，适合回迁到 1.4.x。回迁时需确认 1.4.x 的 `ResolvingFileIO` 同样存在直接 `hadoopConf.get()` 调用（结构应一致），按相同方式替换为 `getConf()` 即可。注意 `Optional`/`Supplier` 的 import 需一并补齐。
