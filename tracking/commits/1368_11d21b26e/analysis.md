# 提交 1368：Kafka Connect: fix Hadoop dependency exclusion (#11516)

## 提交信息

- **序号**：1368 / 4088
- **哈希**：11d21b26ecbb30361b2b2eee0c335d6cd9560c8d
- **短哈希**：11d21b26e
- **日期**：2024-11-11（Mon Nov 11 18:55:42 2024 -0500）
- **作者**：Bryan Keller <bryanck@gmail.com>
- **提交说明**：Kafka Connect: fix Hadoop dependency exclusion (#11516)
- **PR/Issue**：#11516

## 总体目的

`iceberg-kafka-connect-runtime` 模块在 `build.gradle` 中依赖 `libs.hadoop3.common`（Hadoop Common），并对该依赖做了一系列 `exclude` 以剥离不需要的传递依赖（log4j、slf4j、avro、guava、zookeeper、jetty 等），目的是减小分发包体积并避免依赖冲突。

此前的 exclude 列表中包含两条 Woodstox StAX 实现：
- `exclude group: 'com.fasterxml.woodstox'`（Woodstox 项目迁移到 FasterXML 后的新 groupId）
- `exclude group: 'org.codehaus.woodstox'`（Woodstox 项目在 Codehaus 时代的旧 groupId）

把这两个组都排除掉，意味着 Kafka Connect runtime 分发包中**完全没有 Woodstox StAX 实现**。但 Hadoop Common（以及通过 Hadoop 间接拉入的 Hive、ORC 等组件）在运行时需要 StAX XML 解析器来解析 Hadoop 配置文件（`core-site.xml`、`hdfs-site.xml` 等）以及部分 XML 格式的元数据。缺失 StAX 实现会导致用户在 Kafka Connect runtime 中尝试读取 HDFS/Hive 表时遇到 `ClassNotFoundException` 或 `XMLConstants`/`XMLInputFactory` 相关的运行时错误。

本提交修复该问题：
1. 移除两条 Woodstox exclude（让 Woodstox 重新作为传递依赖进入 classpath）；
2. 在 `resolutionStrategy` 中 `force 'com.fasterxml.woodstox:woodstox-core:6.7.0'`，固定使用现代 FasterXML 坐标的 6.7.0 版本（推测该版本包含 CVE 修复或与 Hadoop 3 兼容性更好），避免被传递依赖解析到旧版本。

## 如何达成设计目的

通过 `kafka-connect/build.gradle` 中两处局部修改：

1. 在 `configurations.all { resolutionStrategy { ... } }` 块中新增 `force 'com.fasterxml.woodstox:woodstox-core:6.7.0'`，与已有的 jettison、snappy-java、commons-compress、hadoop-shaded-guava 强制版本并列，统一管控已知 CVE 的依赖版本。
2. 在 `implementation(libs.hadoop3.common) { exclude ... }` 块中删除 `exclude group: 'com.fasterxml.woodstox'` 与 `exclude group: 'org.codehaus.woodstox'` 两行，让 Woodstox 重新随 Hadoop Common 进入 runtime classpath。

这样既恢复了 Woodstox 的可用性，又通过 `force` 锁定版本避免传递依赖解析到旧 Codehus 时代的 Woodstox 或不受控的新版本。

## 修改详情

### `kafka-connect/build.gradle`（修改，+1 -2 行）

**修改目的**：恢复 Kafka Connect runtime 分发包中的 Woodstox StAX 实现，并固定其版本为 6.7.0。

**工作逻辑**：

1. **resolutionStrategy 新增强制版本**（`project(':iceberg-kafka-connect:iceberg-kafka-connect-runtime')` 内的 `configurations.all` 块）：
   ```groovy
   force 'org.codehaus.jettison:jettison:1.5.4'
   force 'org.xerial.snappy:snappy-java:1.1.10.7'
   force 'org.apache.commons:commons-compress:1.27.1'
   force 'org.apache.hadoop.thirdparty:hadoop-shaded-guava:1.3.0'
   force 'com.fasterxml.woodstox:woodstox-core:6.7.0'   // 新增
   ```
   将 `com.fasterxml.woodstox:woodstox-core` 强制为 6.7.0，与其它安全补丁版本并列管理。

2. **移除 Woodstox exclude**（`implementation(libs.hadoop3.common) { ... }` 块）：
   ```groovy
   // 旧
   exclude group: 'org.slf4j'
   exclude group: 'ch.qos.reload4j'
   exclude group: 'org.apache.avro', module: 'avro'
   exclude group: 'com.fasterxml.woodstox'   // 删除
   exclude group: 'com.google.guava'
   ...
   exclude group: 'org.apache.hadoop.thirdparty', module: 'hadoop-shaded-protobuf_3_7'
   exclude group: 'org.codehaus.woodstox'   // 删除
   exclude group: 'org.eclipse.jetty'

   // 新（移除上述两行 woodstox exclude）
   ```
   删除两条 woodstox exclude 后，`com.fasterxml.woodstox:woodstox-core` 与（若 Hadoop 仍引用旧坐标）`org.codehaus.woodstox:woodstox-core` 都会作为传递依赖进入 classpath，但前者被 `force` 锁定为 6.7.0；后者若出现则保留其传递版本（实践中 Hadoop 3 已迁移到 fasterxml 坐标，旧坐标通常不会出现）。

## 小结

- **成效**：修复 Kafka Connect runtime 分发包缺失 Woodstox StAX 实现导致 Hadoop/Hive 相关 XML 解析在运行时失败的问题；同时通过 `force` 把 woodstox-core 锁定到 6.7.0，统一版本管理。改动极小（+1/-2 行），定位精准。
- **影响范围**：仅 `kafka-connect/build.gradle` 一个构建脚本，影响 `iceberg-kafka-connect-runtime` 模块的依赖解析与分发包内容。不影响 Java 源代码，不影响其它模块。
- **回迁到 1.4.x 的注意事项**：构建/依赖修复类变更，回迁安全且推荐（若 1.4.x 上同样存在该 exclude 问题）。需注意：
  1. 1.4.x 上的 `kafka-connect/build.gradle` 若 hadoop3-common 的 exclude 列表与本提交基线一致，可直接 cherry-pick；
  2. 若 1.4.x 上 `libs.hadoop3.common` 对应的 Hadoop 版本不同，需确认 Hadoop 是否仍依赖 woodstox（较新版 Hadoop 可能改用其它 StAX 实现），以及 `force` 的 6.7.0 版本是否与该 Hadoop 版本兼容；
  3. 本提交与 #1277（LICENSE/NOTICE 第三方声明）有关联：若 1.4.x 已回迁 #1277 的 LICENSE/NOTICE，需同步在 NOTICE 中确认 woodstox 的声明仍准确（woodstox 通常随 Hadoop NOTICE 一并声明，无需单独追加）。
