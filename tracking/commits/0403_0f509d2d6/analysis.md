# 提交 0403：Parquet: Add system config for unsafe Parquet ID fallback. (#9324)

## 提交信息

- **序号**：0403
- **哈希**：0f509d2d678db2d7322dafded58ec0ca6d7fb268
- **短哈希**：0f509d2d6
- **日期**：2024-01-22（AuthorDate 与 CommitDate 均为 2024-01-22 10:00:21 -0800）
- **作者**：Ryan Blue <blue@apache.org>（Co-authored-by: Fokko Driesprong <fokko@apache.org>）
- **提交说明**：Parquet: Add system config for unsafe Parquet ID fallback. (#9324)
- **PR/Issue**：#9324

## 总体目的

Iceberg 通过字段 ID（而非字段名/序号）来追踪 schema 演化下的列，这是它相对 Parquet/ORC 原生 reader 的核心优势之一。但在历史上，当 Parquet 文件本身没有写出字段 ID、且调用方也没有提供 `NameMapping`（外部名字到 ID 的映射）时，Iceberg 的 Parquet reader 会回退到一种"不安全的 ID 分配"逻辑——这套逻辑最初来自 Netflix 内部对老文件的兼容读路径，所以配置名带 `netflix` 前缀。这种回退是不安全的：它可能按字段名或文件列序号猜 ID，一旦 schema 发生过列增删/重命名，就会把数据读到错误的列上，造成静默数据错误。

这个提交的目的，是给这个长期潜伏的"unsafe fallback"行为加一个显式的系统开关，默认保持开启（向后兼容），但每次取值都打 WARN 日志提醒使用者尽快迁移到 NameMapping；同时让用户可以把它关掉，关掉后 reader 在缺 NameMapping 时会传入一个 `NameMapping.empty()`（"明确无映射"）而不是 `null`（"触发不安全回退"），从而把"猜 ID"的行为彻底关上。整套机制标注 `@Deprecated will be removed in 2.0.0`，相当于给它设了一个明确的下线时间表。

换句话说，本提交不是修复一个 bug，而是把一个"危险但被广泛依赖的隐式行为"显式化、可配置化、并预告下线，推动生态向 NameMapping 迁移。

## 如何达成设计目的

实现分三块：(1) 在 `SystemConfigs` 中新增一个 `ConfigEntry<Boolean>` 类型的开关 `NETFLIX_UNSAFE_PARQUET_ID_FALLBACK_ENABLED`，默认 `true`，并在取值 lambda 里打 WARN；(2) 把 `ConfigEntry.getValue()` 改名为 `produceValue()`，让"取值即可能有副作用（打日志）"这个语义在方法名上更清晰（这是为开关的 WARN 副作用做的配套命名调整）；(3) 在 `NameMapping` 上新增 `empty()` 静态工厂（带缓存的单例），并在 `Parquet.java` 的 read builder 里根据"是否传了 nameMapping + 开关是否开"三选一地决定传给底层 reader 的 mapping：传了用传入的；没传且开关开则传 `null`（保留旧行为）；没传且开关关则传 `NameMapping.empty()`（明确无映射、不走 fallback）。

## 修改详情

### core/src/main/java/org/apache/iceberg/SystemConfigs.java

**修改目的**：新增 unsafe Parquet ID fallback 的系统开关，并把 ConfigEntry 的内部取值方法改名以体现"取值可能产生副作用"。

**工作逻辑**：新增 `NETFLIX_UNSAFE_PARQUET_ID_FALLBACK_ENABLED` 配置项，property key 为 `iceberg.netflix.unsafe-parquet-id-fallback.enabled`，env key 为 `ICEBERG_NETFLIX_UNSAFE_PARQUET_ID_FALLBACK_ENABLED`，默认值 `true`。它的取值函数不只是 `Boolean.parseBoolean(s)`，而是先 `LOG.warn("Fallback ID assignment in Parquet is UNSAFE and will be removed in 2.0.0. Use name mapping instead.")` 再返回布尔值——也就是说，任何进程只要真的去读这个配置（且值被解析），就会在日志里看到告警。类级 `@Deprecated` 注释指向 "use name mapping instead"，预告 2.0.0 移除。

配套地把 `ConfigEntry` 的私有方法 `getValue()` 改名为 `produceValue()`（`value()` 方法的懒加载逻辑同步调用 `produceValue()`）。改名动机：原来的 `getValue` 名字暗示纯函数式取值，但新的 fallback 开关在取值时要打日志，"produce" 比 "get" 更能体现"可能产生副作用/懒构造"的语义。方法体本身（先查 `System.getProperty`，再查 `System.getenv`，再用 default）没变。

### core/src/main/java/org/apache/iceberg/mapping/NameMapping.java

**修改目的**：提供 `NameMapping.empty()` 工厂，作为"明确无映射"的语义化空对象。

**工作逻辑**：新增一个静态常量 `EMPTY = NameMapping.of()`（`of()` 无参版本返回空 `MappedFields` 的 NameMapping），并通过 `empty()` 暴露。这样调用方可以区分三种状态：`null`（未指定，可能触发历史 fallback）、`NameMapping.empty()`（明确指定"没有映射"）、非空 NameMapping（有具体映射）。这是空对象模式（Null Object Pattern）的标准用法，让 Parquet reader 能据 `empty()` 与 `null` 的差异选择不同读路径。

### parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java

**修改目的**：在 read builder 中根据开关决定传给底层 reader 的 NameMapping，把 unsafe fallback 显式化。

**工作逻辑**：在 `build()` 构造 `ParquetReadOptions` 之后、构造 reader 之前，新增一段三选一逻辑：

```java
NameMapping mapping;
if (nameMapping != null) {
  mapping = nameMapping;                       // 调用方显式传入，直接用
} else if (SystemConfigs.NETFLIX_UNSAFE_PARQUET_ID_FALLBACK_ENABLED.value()) {
  mapping = null;                              // 开关开：传 null，底层走历史 unsafe fallback
} else {
  mapping = NameMapping.empty();               // 开关关：传空映射，底层不再 fallback
}
```

随后把构造 `VectorizedParquetReader` 和 `ParquetReader` 时原本传入的 `nameMapping` 局部变量替换为 `mapping`。注意 `value()` 是懒加载且首次调用打 WARN——所以只要走到 `else if` 分支（即未传 nameMapping 且开关默认开），就会触发一次告警日志，正好把"你正在用 unsafe fallback"这件事暴露给运维。新增了对 `org.apache.iceberg.SystemConfigs` 的 import。

## 小结

本提交是一次"为危险隐式行为建立下线通道"的典型治理：不动现有正确路径（传了 NameMapping 的用户完全无感），但把没传 NameMapping 时的 `null` 路径拆成"开/关"两态，默认保留旧行为以兼容历史 Netflix 风格用法，同时每次触发都打 WARN，并给整条路径贴上 2.0.0 移除标签。技术上它综合运用了系统配置（property + env 双通道）、空对象模式（`NameMapping.empty()`）、以及"取值即告警"的副作用钩子。对 1.4.x 用户而言：如果生产环境读老 Parquet 文件依赖了 fallback，升级含此提交的版本后会开始看到 WARN 日志，应尽快为表配置 NameMapping 并可在准备就绪后把 `iceberg.netflix.unsafe-parquet-id-fallback.enabled` 设为 false 验证、最终在 2.0.0 前完成迁移。
