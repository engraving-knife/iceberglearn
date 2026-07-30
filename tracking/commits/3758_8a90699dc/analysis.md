# 提交 3758：Encrypting IO as a `DelegateFileIO` (#14876)

## 提交信息

- **序号**：3758 / 4088
- **哈希**：8a90699dcf692a3c5f517baae352c4648bb77c30
- **短哈希**：8a90699dc
- **日期**：2026-05-20 20:43:04 -0700
- **作者**：Sreesh Maheshwar
- **提交说明**：Encrypting IO as a `DelegateFileIO` (#14876)
- **PR/Issue**：#14876

## 总体目的

这个提交解决了 `EncryptingFileIO` 在包装 `DelegateFileIO` 时丢失批量操作能力的问题。`EncryptingFileIO` 是 Iceberg 中用于在读写文件时进行透明加解密的包装层，它通过 `combine(io, em)` 静态方法创建，会根据底层 `FileIO` 实现的能力（是否支持前缀操作等）选择不同的包装类。

问题在于：当底层 `FileIO` 是 `DelegateFileIO`（继承自 `SupportsBulkOperations`，提供 `deleteFiles` 批量删除、`listPrefix`、`deletePrefix` 等高级能力）时，`EncryptingFileIO.combine` 方法没有识别这种类型，只会将其作为普通 `FileIO` 或 `SupportsPrefixOperations` 来包装，导致批量删除等能力丢失。这意味着使用加密 IO 时，文件删除操作无法利用底层实现的高效批量删除接口，只能逐个删除，影响性能。

## 如何达成设计目的

设计思路是在 `EncryptingFileIO.combine` 的类型判断链中，优先检查 `DelegateFileIO` 类型（因为 `DelegateFileIO` 继承自 `SupportsPrefixOperations`，需要在 `SupportsPrefixOperations` 判断之前检查），并创建新的 `WithDelegateFileIO` 内部类来包装。该内部类继承 `EncryptingFileIO` 并实现 `DelegateFileIO` 接口，将批量操作方法委托给底层 IO。

由于 `DelegateFileIO` 继承自 `SupportsBulkOperations`，后者又继承自 `SupportsPrefixOperations`，所以 `WithDelegateFileIO` 通过实现 `DelegateFileIO` 接口自动获得了所有这些能力的类型标识，同时委托具体实现给底层 IO。

## 修改详情

### `api/src/main/java/org/apache/iceberg/encryption/EncryptingFileIO.java` (+30/-2 lines)

**修改目的**：新增 `DelegateFileIO` 类型识别和包装类。

**工作逻辑**：
1. 修改 `combine` 方法中的类型判断逻辑，在 `SupportsPrefixOperations` 判断之前加入 `DelegateFileIO` 判断：

```java
if (io instanceof DelegateFileIO) {
  return new WithDelegateFileIO((DelegateFileIO) io, em);
} else if (io instanceof SupportsPrefixOperations) {
  return new WithSupportsPrefixOperations((SupportsPrefixOperations) io, em);
} else {
  return new EncryptingFileIO(io, em);
}
```

注意判断顺序很重要：因为 `DelegateFileIO extends SupportsBulkOperations extends SupportsPrefixOperations`，如果先判断 `SupportsPrefixOperations`，`DelegateFileIO` 实例会被错误地包装为 `WithSupportsPrefixOperations`，丢失批量删除能力。

2. 新增 `WithDelegateFileIO` 内部类，继承 `EncryptingFileIO` 并实现 `DelegateFileIO` 接口，委托 `deleteFiles`、`listPrefix`、`deletePrefix` 三个方法给底层 IO：

```java
static class WithDelegateFileIO extends EncryptingFileIO implements DelegateFileIO {
  private final DelegateFileIO delegateFileIO;

  WithDelegateFileIO(DelegateFileIO io, EncryptionManager em) {
    super(io, em);
    this.delegateFileIO = io;
  }

  @Override
  public void deleteFiles(Iterable<String> pathsToDelete) throws BulkDeletionFailureException {
    delegateFileIO.deleteFiles(pathsToDelete);
  }

  @Override
  public Iterable<FileInfo> listPrefix(String prefix) {
    return delegateFileIO.listPrefix(prefix);
  }

  @Override
  public void deletePrefix(String prefix) {
    delegateFileIO.deletePrefix(prefix);
  }
}
```

注意这些委托方法直接调用底层 IO，不经过加解密处理，因为删除和列举操作不涉及文件内容本身的加解密。

### `api/src/test/java/org/apache/iceberg/encryption/TestEncryptingFileIO.java` (+88/-3 lines)

**修改目的**：全面测试新的 `DelegateFileIO` 包装行为和关闭逻辑。

**工作逻辑**：
1. 修改 `combine()` 测试，新增对 `DelegateFileIO` 的类型断言，验证包装后仍实现 `DelegateFileIO` 和 `SupportsBulkOperations` 接口。
2. 新增 `reWrappingDelegateFileIOPreservesType()` 测试，验证对已包装的 `EncryptingFileIO` 再次包装时，类型和加密管理器正确传递。
3. 新增 `delegateFileIODelegation()` 测试，验证 `listPrefix`、`deletePrefix`、`deleteFiles` 方法正确委托给底层 IO。
4. 新增 `closeClosesUnderlyingFileIO()` 测试，验证关闭 `EncryptingFileIO` 时会正确关闭底层 `FileIO`（覆盖三种类型：普通、前缀操作、Delegate）。
5. 新增 `closeClosesEncryptionManagerWhenCloseable()` 测试，验证当 `EncryptionManager` 实现 `Closeable` 时，关闭 `EncryptingFileIO` 也会关闭加密管理器。

## 总结

这个提交通过在 `EncryptingFileIO` 中新增 `WithDelegateFileIO` 包装类，确保了当底层 IO 支持 `DelegateFileIO` 接口时，批量删除、前缀列举和前缀删除等高级能力不会因加密包装而丢失。这对于使用加密存储的 Iceberg 表的性能很重要，特别是在需要大量删除文件的操作（如快照过期、孤儿文件清理）中，可以利用底层存储系统（如 S3）的批量删除 API 提高效率。同时新增的关闭测试也确保了资源管理的正确性。
