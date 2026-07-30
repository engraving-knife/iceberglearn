# 提交 2994：Handle SupportsWithPrefix in EncryptingFileIO (#14727)

## 提交信息

- **序号**：2994 / 4088
- **哈希**：361131336622223c546187ff5369512343414a03
- **短哈希**：361131336
- **日期**：2025-12-10
- **作者**：Thomas Powell
- **提交说明**：Handle SupportsWithPrefix in EncryptingFileIO (#14727)
- **PR/Issue**：#14727

## 总体目的

Iceberg 的 `EncryptingFileIO` 是一个装饰器，它包装底层 `FileIO` 并在读写时透明地进行加解密。其 `combine(io, em)` 静态工厂在底层 `io` 自身已经是 `EncryptingFileIO`（即已具备加密层）时直接复用，否则包装成新的 `EncryptingFileIO`。

问题在于：Iceberg 的 `FileIO` 接口有多个扩展 mixin，其中 `SupportsPrefixOperations` 提供了按前缀列举（`listPrefix`）与按前缀删除（`deletePrefix`）的能力，常用于清理临时目录、过期文件等维护操作。当底层 `FileIO` 同时实现了 `SupportsPrefixOperations` 时，原 `combine` 方法只会返回普通的 `EncryptingFileIO`，而 `EncryptingFileIO` 仅实现 `FileIO`，导致 `SupportsPrefixOperations` 能力在装饰后"丢失"——调用方若按 `SupportsPrefixOperations` 强转或调用就会失败，前缀操作无法穿透加密层。

本提交修复该缺陷：在 `combine` 中检测底层 `io` 是否为 `SupportsPrefixOperations`，若是则返回新增的内部类 `WithSupportsPrefixOperations`（同时继承 `EncryptingFileIO` 并实现 `SupportsPrefixOperations`），将 `listPrefix`/`deletePrefix` 委托给底层 io 执行。由于前缀列举/删除操作作用于"目录"层面（不涉及单文件内容的加解密），直接委托是安全且语义正确的。

## 如何达成设计目的

在 `EncryptingFileIO.combine` 中增加一个 `instanceof SupportsPrefixOperations` 分支，返回新内部类 `WithSupportsPrefixOperations`；该内部类持有底层 `SupportsPrefixOperations` 引用，并实现 `listPrefix`/`deletePrefix` 委托。配套新增 `TestEncryptingFileIO` 验证：无 mixin 的 FileIO 返回普通 `EncryptingFileIO`，带 `SupportsPrefixOperations` mixin 的返回 `WithSupportsPrefixOperations`，且前缀操作正确委托。

## 修改详情

### `api/src/main/java/org/apache/iceberg/encryption/EncryptingFileIO.java` (+29/-1 lines)

**修改目的**：让加密装饰器保留底层的前缀操作能力。

**工作逻辑**：

`combine(io, em)` 在原有"已是 EncryptingFileIO 则复用"判断之后，新增分支：若 `io instanceof SupportsPrefixOperations`，则返回 `new WithSupportsPrefixOperations((SupportsPrefixOperations) io, em)`；否则仍返回普通 `new EncryptingFileIO(io, em)`。

新增内部类 `WithSupportsPrefixOperations extends EncryptingFileIO implements SupportsPrefixOperations`：

- 持有 `private final SupportsPrefixOperations prefixIo`，构造时调用 `super(io, em)` 并保存强转后的 `prefixIo`。
- `listPrefix(String prefix)` 直接 `return prefixIo.listPrefix(prefix)`。
- `deletePrefix(String prefix)` 直接 `prefixIo.deletePrefix(prefix)`。

由于继承自 `EncryptingFileIO`，单文件的 `newInputFile`/`newOutputFile` 等仍走加密路径；而前缀级操作（列举/删除目录）不涉及文件内容加解密，直接委托底层，语义清晰。新增了对 `FileInfo` 与 `SupportsPrefixOperations` 的导入。

### `api/src/test/java/org/apache/iceberg/encryption/TestEncryptingFileIO.java` (+68/-0 lines, 新增)

**修改目的**：验证组合逻辑与前缀操作委托。

**工作逻辑**：

`delegateEncryptingIOWithAndWithoutMixins`：用 Mockito 构造一个不带 mixin 的 `FileIO` mock，断言 `combine` 返回 `EncryptingFileIO` 类型且 `encryptionManager()` 等于传入的 em；再用 `withSettings().extraInterfaces(SupportsPrefixOperations.class)` 构造一个带 mixin 的 mock，断言 `combine` 返回 `WithSupportsPrefixOperations` 类型且 em 一致。

`prefixOperationsDelegation`：构造一个 `SupportsPrefixOperations` mock，组合得到 `WithSupportsPrefixOperations`，对 `listPrefix("prefix")` stub 返回一个 mock `Iterable`，断言 `fileIO.listPrefix(prefix)` 等于该 stub；调用 `fileIO.deletePrefix(prefix)` 并 `verify(delegate).deletePrefix(prefix)` 确认委托发生。两个测试共同保证能力识别与委托的正确性。

## 总结

本提交修复了 `EncryptingFileIO` 在包装支持前缀操作的底层 FileIO 时丢失 `SupportsPrefixOperations` 能力的问题，通过新增 `WithSupportsPrefixOperations` 子类按需暴露并委托前缀操作，使加密装饰器成为透明的能力透传者。改动聚焦于 api 模块，配套的 mock 测试覆盖了能力识别与委托两条关键路径，对依赖前缀操作的维护流程（如过期清理）在加密表上的可用性有直接价值。
