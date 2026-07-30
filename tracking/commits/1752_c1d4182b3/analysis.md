# 提交 1752：Parquet: Fix performance regression in reader init (#12305)

## 提交信息

- **序号**：1752 / 4088
- **哈希**：c1d4182b3fb9fcb162a0233b8aa304a65ffa56f1
- **短哈希**：c1d4182b3
- **日期**：2025-02-19 08:59:52 +0100
- **作者**：Bryan Keller
- **提交说明**：Parquet: Fix performance regression in reader init (#12305)
- **PR/Issue**：#12305

## 总体目的

本提交旨在修复 Parquet 读取器初始化阶段的性能回退问题。该性能回退是由 Parquet 库版本升级引起的——在新版本中，`ParquetReadOptions.builder()` 的无参构造方法默认会触发配置加载逻辑（如从 classpath 资源加载 `parquet.properties` 等默认配置），这在每次创建读取器时都会重复执行，导致读取器初始化变慢。

通过显式传入一个空的 `PlainParquetConfiguration` 实例给 `builder()`，可以绕过默认的配置加载流程，从而恢复到升级前的性能水平。

## 如何达成设计目的

提交修改了 `Parquet.java` 中两处创建 `ParquetReadOptions.builder()` 的位置，将无参的 `ParquetReadOptions.builder()` 替换为 `ParquetReadOptions.builder(new PlainParquetConfiguration())`。`PlainParquetConfiguration` 是 Parquet 库提供的一个不携带任何默认配置的空配置实现，传入它后 builder 不会再去加载默认配置，从而避免了不必要的开销。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java`（修改, +5/-2 lines）

**修改目的**：修复 Parquet 读取器初始化的性能回退。

**工作逻辑**：修改了两处代码：

1. **新增 import**：导入 `org.apache.parquet.conf.PlainParquetConfiguration`。

2. **第一处修改（readBuilder 初始化）**：在构建读取选项时，原代码为 `optionsBuilder = ParquetReadOptions.builder()`，当没有 Hadoop Configuration 时走此分支。改为 `optionsBuilder = ParquetReadOptions.builder(new PlainParquetConfiguration())`，显式传入空配置避免默认配置加载。

3. **第二处修改（解密选项构建）**：在构建用于读取 schema 的解密选项时，原代码为 `ParquetReadOptions.builder().withDecryption(fileDecryptionProperties).build()`，改为 `ParquetReadOptions.builder(new PlainParquetConfiguration()).withDecryption(fileDecryptionProperties).build()`，同样避免默认配置加载。

这两处修改确保在创建 Parquet 读取选项时不会触发不必要的配置加载，从而消除了性能回退。

## 小结

- **成效**：修复了 Parquet 读取器初始化的性能回退，通过显式传入 `PlainParquetConfiguration` 避免了默认配置加载的开销。
- **影响范围**：仅修改 Parquet 模块的读取器初始化代码，影响所有使用 Parquet 读取器的场景（即几乎所有 Parquet 表的读取操作）。
- **回迁到 1.4.x 的注意事项**：回迁前需确认 1.4.x 分支使用的 Parquet 库版本是否已升级到需要此修复的版本。如果 1.4.x 分支使用的 Parquet 库版本较旧（`ParquetReadOptions.builder()` 无参构造不会触发配置加载），则此修复可能不适用甚至无法编译（`PlainParquetConfiguration` 类可能不存在）。需检查 1.4.x 分支的 Parquet 依赖版本。注意后续提交 1756 对此修复做了进一步调整。
