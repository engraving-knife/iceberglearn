# 提交 1250：Core: lazily load default Hadoop Configuration to avoid NPE with HadoopFileIO because FileIOParser doesn't serialize Hadoop configuration (#10926)

## 提交信息

- **序号**：1250 / 4088
- **哈希**：f4ffe138968bc25b876033f36744e0573e875920
- **短哈希**：f4ffe1389
- **日期**：2024-10-17（Thu Oct 17 14:31:02 2024 -0700）
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Core: lazily load default Hadoop Configuration to avoid NPE with HadoopFileIO because FileIOParser doesn't serialize Hadoop configuration (#10926)
- **Co-author**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **PR/Issue**：#10926

## 总体目的

修复 `HadoopFileIO` 在经过 `FileIOParser` JSON 序列化/反序列化后使用时抛 `NullPointerException` 的问题。

背景：`FileIOParser.toJson` / `fromJson` 是 Iceberg 用来把 `FileIO` 实例序列化为 JSON 字符串（便于在 Spark/Flink 任务间传递、或持久化到 catalog 元数据）的工具。它只序列化 `FileIO` 的 `properties()`（一个 `Map<String, String>`），**不会**序列化 `HadoopFileIO` 内部持有的 `SerializableConfiguration`（即 Hadoop `Configuration` 对象）。原因有二：Hadoop `Configuration` 体积大且包含大量不可序列化的资源引用；设计上也希望 `Configuration` 由执行环境（如 Spark executor 的 Hadoop 配置）注入而非通过 JSON 携带。

因此，反序列化得到的 `HadoopFileIO` 实例中 `hadoopConf` 字段（一个 `Supplier<Configuration>`）为 `null`。原实现在 `newInputFile`/`newOutputFile`/`deleteFile`/`listPrefix`/`deletePrefix` 等方法里直接调用 `hadoopConf.get()`，于是抛 NPE。

本提交在 `getConf()` 中加入"懒加载默认 Configuration"逻辑：当 `hadoopConf` 为 null 时，通过 double-checked locking 创建一个默认的 `new Configuration()` 并包装为 `SerializableConfiguration`，从而让反序列化后的 `HadoopFileIO` 仍能正常工作（使用执行环境的默认 Hadoop 配置，而不是序列化前对象上的配置）。同时把所有内部对 `hadoopConf.get()` 的直接调用改为 `getConf()`，统一走懒加载入口。

## 如何达成设计目的

1. 把 `HadoopFileIO` 中所有 `hadoopConf.get()` 调用点（`conf()`、`newInputFile`、`newInputFile(path, length)`、`newOutputFile`、`deleteFile`、`listPrefix`、`deletePrefix`）替换为 `getConf()`，使任何对配置的访问都经过懒加载入口。
2. 在 `getConf()` 中加入 null 检查 + synchronized double-checked locking：若 `hadoopConf` 为 null，则创建 `new SerializableConfiguration(new Configuration())::get` 赋给字段，再返回。
3. 新增两个测试 `testJsonParserWithoutHadoopConf` 与 `testJsonParserWithHadoopConf`，验证序列化/反序列化后 `HadoopFileIO` 仍可创建 `InputFile`，且 `properties` 正确保留、而 Hadoop 配置项不保留（符合预期）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopFileIO.java`

**修改目的**：让 `getConf()` 在 `hadoopConf` 为 null 时懒加载默认配置，并把所有调用点改为走 `getConf()`。

**工作逻辑**：

调用点替换（共 7 处）：

| 方法 | 原调用 | 新调用 |
|------|--------|--------|
| `conf()` | `hadoopConf.get()` | `getConf()` |
| `newInputFile(String)` | `HadoopInputFile.fromLocation(path, hadoopConf.get())` | `HadoopInputFile.fromLocation(path, getConf())` |
| `newInputFile(String, long)` | `HadoopInputFile.fromLocation(path, length, hadoopConf.get())` | `HadoopInputFile.fromLocation(path, length, getConf())` |
| `newOutputFile(String)` | `HadoopOutputFile.fromPath(new Path(path), hadoopConf.get())` | `HadoopOutputFile.fromPath(new Path(path), getConf())` |
| `deleteFile(String)` | `Util.getFs(toDelete, hadoopConf.get())` | `Util.getFs(toDelete, getConf())` |
| `listPrefix(String)` | `Util.getFs(prefixToList, hadoopConf.get())` | `Util.getFs(prefixToList, getConf())` |
| `deletePrefix(String)` | `Util.getFs(prefixToDelete, hadoopConf.get())` | `Util.getFs(prefixToDelete, getConf())` |

`getConf()` 新逻辑：

```java
@Override
public Configuration getConf() {
  // Create a default hadoopConf as it is required for the object to be valid.
  // E.g. newInputFile would throw NPE with getConf() otherwise.
  if (hadoopConf == null) {
    synchronized (this) {
      if (hadoopConf == null) {
        this.hadoopConf = new SerializableConfiguration(new Configuration())::get;
      }
    }
  }
  return hadoopConf.get();
}
```

要点：
- 双重检查锁定（double-checked locking）保证线程安全且只创建一次。
- `new Configuration()` 会从 classpath 读取 `core-default.xml`/`core-site.xml` 等默认资源，得到执行环境的默认 Hadoop 配置。
- `SerializableConfiguration(new Configuration())::get` 是用方法引用把 `SerializableConfiguration.get()` 适配为 `Supplier<Configuration>`，与 `setConf` 路径下 `hadoopConf` 的赋值方式一致，保持字段类型不变。
- 懒加载只在 `hadoopConf == null`（即反序列化路径）触发；正常 `setConf` 路径下字段非 null，行为不变。

### `core/src/test/java/org/apache/iceberg/hadoop/HadoopFileIOTest.java`

**修改目的**：覆盖 `FileIOParser` 序列化/反序列化后的行为。

**工作逻辑**：

新增导入 `java.nio.file.Files` 与 `org.apache.iceberg.io.FileIOParser`。

新增两个测试：

`testJsonParserWithoutHadoopConf`：
- `new HadoopFileIO()`（不调用 `setConf`，模拟反序列化后的状态）。
- `initialize(ImmutableMap.of("properties-bar", "2"))` 设置 properties。
- 调用 `testJsonParser(hadoopFileIO, tempDir)` 验证序列化往返。

`testJsonParserWithHadoopConf`：
- `new HadoopFileIO()` 后 `setConf(new Configuration())`，并在配置中设置 `hadoop-conf-foo=1`。
- `initialize` 设置 properties。
- 调用 `testJsonParser`。

共享私有方法 `testJsonParser(HadoopFileIO, File tempDir)`：
- `FileIOParser.toJson(hadoopFileIO)` 序列化。
- `FileIOParser.fromJson(json)` 反序列化得到 `HadoopFileIO`。
- 断言 `deserializedHadoopFileIO.properties()` 与原对象相等（properties 被保留）。
- 断言 `deserializedHadoopFileIO.conf().get("hadoop-conf-foo")` 为 null（Hadoop 配置不序列化，符合预期）。
- 调用 `deserializedHadoopFileIO.newInputFile(...)` 验证不会抛 NPE（懒加载生效）。

关键：第二个断言明确记录了"FileIOParser 不序列化 Hadoop 配置"这一设计事实；第三个断言验证懒加载修复有效。

## 小结

- **成效**：修复 `HadoopFileIO` 在 `FileIOParser` 反序列化后使用时抛 NPE 的问题。修复后，反序列化得到的 `HadoopFileIO` 会自动用执行环境的默认 Hadoop `Configuration`，可正常创建 InputFile/OutputFile、删除文件、列举前缀等。这对通过 JSON 传递 `FileIO`（如 Spark Iceberg 在 driver 与 executor 间传递 `ResolvingFileIO` 内部包裹的 `HadoopFileIO`）的场景至关重要。
- **影响范围**：仅 `core` 模块的 `HadoopFileIO` 主代码与对应测试，属于运行时 bug 修复。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个高价值 bug fix，**强烈建议回迁**到 1.4.x。任何通过 `FileIOParser` 序列化 `HadoopFileIO` 的场景（包括 `ResolvingFileIO` 在内）都会受此 NPE 影响。
  - 回迁时需确认 1.4.x 的 `HadoopFileIO` 字段名与 `SerializableConfiguration` 用法一致；本提交假设字段名为 `hadoopConf`、类型为 `Supplier<Configuration>`（由 `SerializableConfiguration::get` 适配）。若 1.4.x 上字段类型不同，需要相应调整懒加载赋值语句。
  - `getConf()` 是 `HadoopConfigurable` 接口方法，1.4.x 上同样存在，回迁安全。
  - 测试依赖 `FileIOParser` 与 `Files.createTempDirectory`，1.4.x 上均可用。
  - 注意：懒加载用的是 `new Configuration()`（默认配置），**不会**恢复序列化前对象上的自定义 Hadoop 配置项。如果 1.4.x 用户依赖通过 JSON 携带 Hadoop 配置，需要另行解决（这超出本提交范围，本提交明确不解决该问题）。
