# 提交 3237：API: Implement properties method in EncryptingFileIO (#15289)

## 提交信息

- **序号**：3237 / 4088
- **哈希**：473d46a5c76ce7fc776199034650749572b3b1d5
- **短哈希**：473d46a5c
- **日期**：2026-02-10
- **作者**：Yuya Ebihara
- **提交说明**：API: Implement properties method in EncryptingFileIO (#15289)
- **PR/Issue**：#15289

## 总体目的

`EncryptingFileIO` 是一个包装类，它在底层委托 `FileIO`（字段 `io`）的基础上叠加了透明加解密能力。`FileIO` 接口定义了一个 `default Map<String, String> properties()` 方法，用于暴露该 FileIO 实例的配置属性。该默认实现会抛出 `UnsupportedOperationException`，表示该 FileIO 不暴露配置属性；而真正的实现类（如 `HadoopFileIO`、`S3FileIO` 等）会覆盖此方法返回实际的配置 Map。

问题在于，`EncryptingFileIO` 此前并未覆盖 `properties()` 方法。由于它 `implements FileIO`，调用 `EncryptingFileIO` 实例的 `properties()` 时会直接命中接口的默认实现，从而抛出 `UnsupportedOperationException`——即使其内部包装的委托 `FileIO` 实际上支持暴露属性。这意味着任何通过 `EncryptingFileIO` 获取配置属性的下游调用方都会失败，无法读取到底层 FileIO 的配置（例如用于诊断、日志、或传递给其他需要相同配置的组件）。

本提交通过为 `EncryptingFileIO` 添加 `properties()` 覆盖方法、将其委托给底层 `io.properties()` 来修复此缺陷，使加密包装层对配置属性透明传递。

## 如何达成设计目的

整体思路遵循 `EncryptingFileIO` 作为委托包装器（delegate wrapper）的一贯设计模式：该类中几乎所有 `FileIO` 接口方法（`newInputFile`、`newOutputFile`、`deleteFile`、`deletePrefix`、`close` 等）都是直接或间接委托给底层 `io`。因此 `properties()` 同样应委托给 `io.properties()`。改动仅涉及在 `EncryptingFileIO` 中新增一个 `@Override` 方法，并补充对应的单元测试验证委托行为。

## 修改详情

### `api/src/main/java/org/apache/iceberg/encryption/EncryptingFileIO.java` (+5/-0 lines)

**修改目的**：覆盖 `properties()` 方法，将调用委托给底层 `FileIO`。

**工作逻辑**：新增方法 `@Override public Map<String, String> properties() { return io.properties(); }`。`io` 是 `EncryptingFileIO` 持有的委托 `FileIO` 实例（通过 `combine(io, em)` 静态工厂传入）。这样当外部调用 `EncryptingFileIO` 实例的 `properties()` 时，不再命中接口默认实现抛出 `UnsupportedOperationException`，而是透传到底层 `FileIO` 的 `properties()`，返回真实的配置属性 Map。这与该类中其他委托方法（如 `deleteFile`、`close`）的模式完全一致。

### `api/src/test/java/org/apache/iceberg/encryption/TestEncryptingFileIO.java` (+11/-0 lines)

**修改目的**：验证 `properties()` 正确委托给底层 `FileIO`。

**工作逻辑**：新增 `properties()` 测试方法。使用 Mockito mock 一个 `EncryptionManager` 和一个 `FileIO`，通过 `when(io.properties()).thenReturn(Map.of("key", "value"))` 设定底层返回值，然后用 `EncryptingFileIO.combine(io, em).properties()` 获取属性，并断言结果 `containsExactly(Map.entry("key", "value"))`。这验证了包装层正确地透传了底层 FileIO 的属性，而非抛出异常。该测试与文件中其他测试（如 `deletePrefix` 的委托测试）风格一致。

## 总结

本提交为 `EncryptingFileIO` 补全了缺失的 `properties()` 委托方法，修复了通过加密包装层获取配置属性时抛出 `UnsupportedOperationException` 的问题。改动虽小（仅 5 行实现代码），但填补了委托模式的遗漏，使 `EncryptingFileIO` 在属性暴露方面与底层 `FileIO` 行为一致，对需要读取 FileIO 配置的下游场景（如诊断、属性传递）具有实际价值。
