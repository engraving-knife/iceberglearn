# 提交 3943：Remove encryption key from keysById first to avoid removeIf (#16860)

## 提交信息

- **序号**：3943 / 4088
- **哈希**：1ba42d46eb868dfd2f5a12ad4caee0ea8f0606a0
- **短哈希**：1ba42d46e
- **日期**：2026-06-24 14:40:46 -0700
- **作者**：Thomas Powell
- **提交说明**：Remove encryption key from keysById first to avoid removeIf (#16860)
- **PR/Issue**：#16860

## 总体目的

这次提交优化了 `TableMetadata.Builder.removeEncryptionKey` 方法的实现，改变加密密钥移除的顺序和方式。原实现先使用 `encryptionKeys.removeIf(key -> key.keyId().equals(keyId))` 从列表中移除，再从 `keysById` 映射中移除。

问题在于 `removeIf` 在列表上进行线性搜索和条件判断，效率较低且语义不够清晰。更重要的是，原实现中两个数据结构（`encryptionKeys` 列表和 `keysById` 映射）的移除是独立进行的，如果 `keysById` 中不存在该 key 但 `encryptionKeys` 中存在（或反之），可能导致不一致。

优化后的实现先从 `keysById` 映射中移除（O(1) 操作），如果成功则用返回的对象直接从 `encryptionKeys` 列表中移除（精确对象匹配，避免再次按 keyId 搜索），确保两个数据结构的一致性。

## 如何达成设计目的

将移除逻辑从"先 removeIf 列表再 remove 映射"改为"先 remove 映射获取被移除的对象，再用该对象从列表中移除"。利用 `Map.remove(key)` 返回被移除值的特性，避免重复搜索。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadata.java` (+3/-3 lines)

**修改目的**：优化加密密钥移除逻辑。

**工作逻辑**：
原代码：
```java
boolean removed = encryptionKeys.removeIf(key -> key.keyId().equals(keyId));
keysById.remove(keyId);
if (removed) {
    changes.add(new MetadataUpdate.RemoveEncryptionKey(keyId));
}
```
新代码：
```java
EncryptedKey removedKey = keysById.remove(keyId);
if (removedKey != null) {
    encryptionKeys.remove(removedKey);
    changes.add(new MetadataUpdate.RemoveEncryptionKey(keyId));
}
```
先从 `keysById` 映射移除并获取被移除的 `EncryptedKey` 对象，若非 null 则用该对象直接从 `encryptionKeys` 列表中按对象引用移除（`List.remove(Object)` 使用 `equals` 匹配），并记录变更。

## 总结

这次提交优化了加密密钥移除的实现，通过先从映射中移除再利用返回对象从列表中移除，避免了 `removeIf` 的线性搜索，同时保证了两个数据结构的一致性。这是一个小而有效的代码质量改进。
