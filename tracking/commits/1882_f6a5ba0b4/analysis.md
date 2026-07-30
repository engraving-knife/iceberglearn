# 提交 1882：Core: Use InternalData when reading manifests in FileCleanupStrategy (#12575)

## 提交信息

- **序号**：1882 / 4088
- **哈希**：f6a5ba0b443438db9888501173d96f8dc7230e2c
- **短哈希**：f6a5ba0b4
- **日期**：2025-03-19 10:04:12 -0600
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Use InternalData when reading manifests in FileCleanupStrategy (#12575)
- **PR/Issue**：#12575

## 总体目的

本提交将 `FileCleanupStrategy.readManifests` 中读取 manifest 列表的方式从直接使用 `Avro.read(...)` 改为使用 `InternalData.read(...)`。

背景：Iceberg 正在统一数据读取入口，`InternalData` 提供了与文件格式无关的读取抽象，支持 Avro/Parquet/ORC 等多种格式。此前 `FileCleanupStrategy` 直接调用 `Avro.read`，硬编码了 Avro 格式。manifest list 虽然当前确实是 Avro 格式，但使用 `InternalData.read` 可以：
1. 与项目中其他读取路径保持一致（其他地方已迁移到 InternalData）。
2. 为未来可能的格式变化提供灵活性。
3. 简化 API 调用（`setRootType`、`reuseContainers` 等方法签名更统一）。

## 如何达成设计目的

将 `readManifests` 方法中的读取构建从 `Avro.read(...).rename(...).classLoader(...).project(...).reuseContainers(true).build()` 替换为 `InternalData.read(FileFormat.AVRO, ...).setRootType(GenericManifestFile.class).project(...).reuseContainers().build()`。

- `InternalData.read` 接受 `FileFormat.AVRO` 和 InputFile，返回一个通用的读取构建器。
- `setRootType(GenericManifestFile.class)` 替代原来的 `rename` + `classLoader`，指定反序列化的目标类型。
- `reuseContainers()`（无参）替代 `reuseContainers(true)`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/FileCleanupStrategy.java` (修改, +4/-5 lines)

**修改目的**：将 manifest 读取迁移到 InternalData 抽象。

**工作逻辑**：
- 移除 `import org.apache.iceberg.avro.Avro;`
- `readManifests` 方法中，当 `snapshot.manifestListLocation() != null` 时：
  - 原：`Avro.read(fileIO.newInputFile(snapshot.manifestListLocation())).rename("manifest_file", GenericManifestFile.class.getName()).classLoader(GenericManifestFile.class.getClassLoader()).project(MANIFEST_PROJECTION).reuseContainers(true).build();`
  - 新：`InternalData.read(FileFormat.AVRO, fileIO.newInputFile(snapshot.manifestListLocation())).setRootType(GenericManifestFile.class).project(MANIFEST_PROJECTION).reuseContainers().build();`
- 其余逻辑（manifestListLocation 为 null 时使用 `snapshot.allManifests(fileIO)`）不变。

## 总结

本提交将 `FileCleanupStrategy` 读取 manifest list 的方式从直接 `Avro.read` 迁移到 `InternalData.read` 抽象，与项目统一的数据读取入口对齐，简化 API 调用并为未来格式扩展提供灵活性。功能行为不变。
