# 提交 2953：Nit: Prefer `Preconditions` in `StandardEncryptionManager` (#14753)

## 提交信息

- **序号**：2953 / 4088
- **哈希**：52d6a79e93f2f820787ad27e67ac795991ff8fa4
- **短哈希**：52d6a79e9
- **日期**：2025-12-04
- **作者**：Sreesh Maheshwar
- **提交说明**：Nit: Prefer `Preconditions` in `StandardEncryptionManager` (#14753)
- **PR/Issue**：#14753

## 总体目的

`StandardEncryptionManager` 中有若干处参数/状态校验，原本写成手写的 `if (cond) throw new IllegalStateException(...)` / `throw new IllegalArgumentException(...)` 模式。Iceberg 项目统一的校验惯例是使用 Guava 风格的 `Preconditions.checkState` / `checkArgument`（来自 `org.apache.iceberg.relocated.com.google.common.base.Preconditions`），它把"条件 + 错误消息"压缩成一行、并支持 `%s` 占位符格式化，可读性更好、风格更一致。本提交是一次纯风格重构（标题里 "Nit" 即表明是小改进），把该类里散落的手写校验统一改写为 `Preconditions` 调用，让加密管理器的代码风格与项目其余部分保持一致，同时利用 `%s` 占位符让错误消息更清晰。

值得注意的是，这次改写还顺带修正了一处语义细节：`encryptedByKey` 中对"找不到 manifest list key metadata"的校验，原本用 `IllegalStateException`（语义偏向"状态错误"），改写后用 `checkState` 保留原语义；而"传入的 id 是 KEK 而非 manifest list key metadata"原本用 `IllegalStateException`，改写后改为 `checkArgument`（`!encryptedById().equals(tableKeyId)`），因为这是参数语义错误而非内部状态错误，分类更准确。

## 如何达成设计目的

逐处把 `if (!cond) throw new XException(msg)` 替换为 `Preconditions.checkX(cond, msg)`，并对带变量的消息用 `%s` 占位符。改动局限在单个文件、纯等价改写（除上述一处异常类型微调外），无逻辑变更。

## 修改详情

### `core/src/main/java/org/apache/iceberg/encryption/StandardEncryptionManager.java` (+20/-27 lines)

**修改目的**：把手写参数/状态校验统一为 `Preconditions` 调用。

**工作逻辑**：
共改写 6 处方法：
- `wrapKey(ByteBuffer)`：`if (transientState == null) throw new IllegalStateException("Cannot wrap key after called after serialization (missing KMS client)")` 改为 `Preconditions.checkState(transientState != null, "Cannot wrap key after called after serialization (missing KMS client)")`。这里 `transientState` 在序列化后会丢失，是状态校验，故用 `checkState`。
- `unwrapKey(ByteBuffer)`：同样改为 `Preconditions.checkState(transientState != null, "Cannot unwrap key after serialization")`。
- `encryptionKeys()`：改为 `checkState(transientState != null, "Cannot return the encryption keys after serialization")`。
- `keyEncryptionKeyID()`：改为 `checkState(transientState != null, "Cannot return the current key after serialization")`。
- `encryptedByKey(String manifestListKeyID)`：两处校验。
  - 第一处"找不到 metadata"：改为 `checkState(encryptedKeyMetadata != null, "Cannot find manifest list key metadata with id %s", manifestListKeyID)`，用 `%s` 占位符把 id 拼进消息，比原字符串拼接更安全清晰。
  - 第二处"id 是 KEK 而非 manifest list key metadata"：原为 `throw new IllegalArgumentException`，改为 `Preconditions.checkArgument(!encryptedKeyMetadata.encryptedById().equals(tableKeyId), "%s is a key encryption key, not manifest list key metadata", manifestListKeyID)`。这是把"传入的 id 实际是 KEK"视为参数错误，用 `checkArgument` 比 `checkState` 更贴切，同时也用 `%s` 占位。
- `addManifestListKeyMetadata(NativeEncryptionKeyMetadata)`：改为 `checkState(transientState != null, "Cannot add key metadata after serialization")`。

所有改写都保持原消息文案不变（仅第一/第二处 `encryptedByKey` 增加了 `%s` 占位），并删掉了原本 `if` 块后的空行，使方法体更紧凑。

## 总结

这是一次纯风格统一的小重构：把 `StandardEncryptionManager` 中 6 处手写的 `if/throw` 校验改写为项目惯用的 `Preconditions.checkState`/`checkArgument`，并借助 `%s` 占位符让错误消息更清晰；同时把一处 `IllegalStateException` 调整为更贴切的 `checkArgument`。无行为变化，主要价值是代码风格一致性与可读性。
