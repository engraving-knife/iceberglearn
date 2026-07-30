# 提交 2096：Build, Core: Let RevAPI compare against 1.9.0 / Fix API breakage around StorageCredential (#12930)

## 提交信息

- **序号**：2096 / 4088
- **哈希**：c730c0b16d384912881b7be9abfbb2be101d462f
- **短哈希**：c730c0b16
- **日期**：2025-05-07 08:09:44 -0700
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Build, Core: Let RevAPI compare against 1.9.0 / Fix API breakage around StorageCredential (#12930)
- **PR/Issue**：#12930

## 总体目的

Iceberg 使用 RevAPI 插件做二进制/源兼容性检查，每次构建时把当前代码与某个基准版本对比，发现破坏性变更即报错。1.9.0 发布后，基准版本需要从 `1.8.0` 提升到 `1.9.0`，否则后续基于 1.9.x 的开发会持续与过旧的 1.8.0 比较，产生大量噪声并遗漏真正相对于 1.9.0 的破坏。

把基准切到 1.9.0 后，RevAPI 立刻暴露出一个之前被掩盖的 API 破坏：`StorageCredential` 接口在 1.8→1.9 期间被改造为 Immutables 注解风格（`@Value.Immutable`），其 `config()` 方法的返回类型从 `Map<String, String>` 变成了 `SerializableMap<String, String>`，`create(...)` 也内部包了一层 `SerializableMap.copyOf`。这对外部实现 `StorageCredential` 接口的代码构成了源与二进制兼容性破坏（方法签名变更）。本次提交通过手写一份 `ImmutableStorageCredential` 实现类、并把接口签名还原回 `Map<String, String>`，来修复这一破坏，同时仍保留 `SerializableMap` 作为内部存储以维持 Kryo 序列化兼容。

## 如何达成设计目的

1. **build.gradle**：把 RevAPI 的 `oldVersion` 从 `"1.8.0"` 改为 `"1.9.0"`，使后续兼容性检查以 1.9.0 为基准。
2. **StorageCredential 接口还原**：移除 `@Value.Immutable` 注解、`@Value.Check` 注解，把 `config()` 返回类型从 `SerializableMap<String, String>` 改回 `Map<String, String>`；`create(...)` 不再在接口里做 `SerializableMap.copyOf`，而是直接 `ImmutableStorageCredential.builder().prefix(...).config(config).build()`，把序列化包装的职责下移到实现类。
3. **手写 ImmutableStorageCredential**：从 Immutables 生成代码复制而来，但把内部 `config` 字段类型改为 `Map<String, String>`，并在 builder 的 `build()` / `withConfig(...)` 中通过自定义 `createSerializableMap(...)` 调用 `SerializableMap.copyOf(...)` 包装，从而既满足接口返回 `Map` 的兼容性，又保证实际运行时对象是 `SerializableMap`（Kryo 友好）。同时保留 `validate(...)` 调用接口默认方法 `validate()`。

## 修改详情

### `build.gradle` (修改, +1/-1 lines)

**修改目的**：把 RevAPI 兼容性基准版本从 1.8.0 提升到 1.9.0。

**工作逻辑**：`revapi { oldVersion = "1.9.0" }`。这样后续构建只检查相对于 1.9.0 的破坏性变更。

### `core/src/main/java/org/apache/iceberg/io/StorageCredential.java` (修改, +2/-9 lines)

**修改目的**：修复 `StorageCredential` 接口相对 1.8.0 的源/二进制兼容性破坏。

**工作逻辑**：
- 移除 `@Value.Immutable` 注解（接口不再是 Immutables 生成目标）、`import org.immutables.value.Value`、`import org.apache.iceberg.util.SerializableMap`。
- 移除 `@Value.Check` 注解（`validate()` 变为普通接口默认方法）。
- `config()` 返回类型由 `SerializableMap<String, String>` 改回 `Map<String, String>`，恢复 1.8.0 时的签名。
- `create(String prefix, Map<String,String> config)` 不再显式调用 `SerializableMap.copyOf(config)`，改为直接 `ImmutableStorageCredential.builder().prefix(prefix).config(config).build()`，由 builder 内部完成 `SerializableMap` 包装。

### `core/src/main/java/org/apache/iceberg/io/ImmutableStorageCredential.java` (新增, +322/-0 lines)

**修改目的**：替代 Immutables 自动生成的实现，手写一份以控制内部 Map 的序列化类型。

**工作逻辑**：
- `final class ImmutableStorageCredential implements StorageCredential`，`@Immutable`，持有 `String prefix` 与 `Map<String, String> config`。
- 类注释说明：从 Immutables 生成代码复制而来，唯一区别是内部 Map 不是不可修改的 `ImmutableMap`，而是 `SerializableMap`，以保证 Kryo 序列化/反序列化正常工作。
- 提供 `prefix()`、`config()`、`withPrefix(String)`、`withConfig(Map)` 等。
- `Builder`：`prefix(String)`、`config(Map)`、`putConfig`、`putAllConfig` 等；`build()` 中调用 `createSerializableMap(false, false, config)` 包装输入 map，再 `validate(new ImmutableStorageCredential(prefix, ...))`。
- `createSerializableMap(boolean checkNulls, boolean skipNulls, Map)`：内部用 `LinkedHashMap` 复制，最后返回 `SerializableMap.copyOf(linkedMap)`，确保运行时类型为 `SerializableMap`。
- `validate(ImmutableStorageCredential)` 调用接口的 `validate()` 默认方法做非空校验。
- 标准 `equals`/`hashCode`/`toString` 实现。

## 总结

本次提交有两件事：一是把 RevAPI 兼容性基准从 1.8.0 升到 1.9.0，使后续开发能正确检测相对 1.9.0 的破坏；二是修复升级后立刻暴露的 `StorageCredential` API 破坏——把接口 `config()` 签名还原为 `Map<String, String>`，并用一份手写的 `ImmutableStorageCredential`（内部仍用 `SerializableMap` 包装以保证 Kryo 序列化）替代 Immutables 生成代码。这样既恢复了对外兼容性，又保留了序列化层面的内部实现细节。
