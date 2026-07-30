# 提交 0519：API: Extend FileIO and add EncryptingFileIO (#9592)

## 提交信息

- **序号**：0519 / 4088
- **哈希**：4c5208a42382a2888a76af85eb52b5d69eca6f7e
- **短哈希**：4c5208a42
- **日期**：2024-02-19 14:42:51 -0800
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：API: Extend FileIO and add EncryptingFileIO. (#9592)
- **PR/Issue**：#9592

## 总体目的

这是 Iceberg 加密体系的一次架构性重构。提交前，Iceberg 的加密/解密逻辑分散在多个调用点（Spark `BaseReader`、`ManifestFiles` 等），每个需要读取加密文件的调用方都必须自己：

1. 调用 `table.io().newInputFile(path)` 拿到原始（加密字节）`InputFile`；
2. 用 `EncryptedFiles.encryptedInput(inputFile, file.keyMetadata())` 包装成 `EncryptedInputFile`；
3. 调用 `table.encryption().decrypt(encryptedInputFile)` 得到可读明文的 `InputFile`。

这条链路有几个问题：

- **`FileIO` 对加密无感知**。`FileIO` 接口只有 `newInputFile(String path)` 等路径级方法，不知道传入的 `DataFile`/`DeleteFile`/`ManifestFile` 是否带密钥元数据（`keyMetadata`）。调用方必须自己判断"这个文件是不是加密的"，一旦漏判就会读到密文字节而不报错，产生静默数据损坏。
- **加密逻辑散落**。每个引擎集成（Spark、Flink、Trino 等）和 core 内部（manifest 读取）都要重复"包装 → 解密"的样板代码，维护成本高，且各处行为容易不一致。
- **缓存与解密耦合不清晰**。`ContentCache.tryCache(FileIO, String, long)` 接收的是 `FileIO + 路径 + 长度`，内部再用 FileIO 重新创建 InputFile 来下载。如果文件是加密的，缓存层拿到的就是密文字节，语义错误——缓存应当作用于解密后的 InputFile，而不是反过来。

本提交的目标是：**让 `FileIO` 接口本身具备"按文件对象读取"的能力，并通过新的 `EncryptingFileIO` 装饰器把加密/解密收敛到 FileIO 层**，使下游调用方只需 `io.newInputFile(dataFile)` 即可获得明文输入流，不再需要关心文件是否加密。

## 如何达成设计目的

整体设计分三步：

**第一步：扩展 `FileIO` 接口，新增按文件对象创建 InputFile 的方法。** 在 `FileIO` 中增加三个 `default` 方法：`newInputFile(DataFile)`、`newInputFile(DeleteFile)`、`newInputFile(ManifestFile)`。这三个方法的默认实现**故意拒绝解密**——如果文件带了 `keyMetadata`，直接抛 `IllegalArgumentException` 并提示"use EncryptingFileIO"。这是一种"快速失败"安全策略：普通的 `FileIO`（如 `S3FileIO`、`HadoopFileIO`）没有加密能力，与其返回密文字节让下游静默出错，不如立即报错。只有 `EncryptingFileIO` 装饰器才会重写这些方法去做真正的解密。

**第二步：新增 `EncryptingFileIO` 装饰器。** 它同时实现 `FileIO` 和 `Serializable`，内部持有一个被包装的 `FileIO io` 和一个 `EncryptionManager em`。对路径级方法（`newInputFile(String)`、`newOutputFile(String)`、`deleteFile(String)`）它直接转发给 `io`；对新增的文件对象级方法，它根据 `keyMetadata` 是否存在决定是解密还是直接转发。这样，调用方只要拿到的是 `EncryptingFileIO`，调用 `newInputFile(dataFile)` 就能自动得到明文流，无需自己组装 `EncryptedInputFile`。

**第三步：改造下游调用方，让它们走新接口。** `ManifestFiles` 改为调用 `io.newInputFile(manifest)`；Spark `BaseReader` 改为用 `EncryptingFileIO.create(...).bulkDecrypt(...)`；`ContentCache` 的 `tryCache` 改为接收 `InputFile` 而非 `FileIO + path + length`，使缓存作用于正确的（已解密的）InputFile。旧的 `tryCache(FileIO, String, long)` 保留为 `@Deprecated`，计划 1.7 移除。

下面分别详述各文件改动。

## 修改详情

### `api/src/main/java/org/apache/iceberg/io/FileIO.java`（接口扩展）

**修改目的**：为 `FileIO` 增加按文件对象（`DataFile`/`DeleteFile`/`ManifestFile`）创建 `InputFile` 的能力，并以"拒绝解密"的默认实现作为安全护栏。

**工作逻辑**：新增三个 `default` 方法，模式一致——检查 `keyMetadata` 是否为 null，非 null 则抛异常，null 则委托路径级方法：

```java
default InputFile newInputFile(DataFile file) {
  Preconditions.checkArgument(
      file.keyMetadata() == null,
      "Cannot decrypt data file: {} (use EncryptingFileIO)",
      file.path());
  return newInputFile(file.path().toString());
}
```

`DeleteFile` 和 `ManifestFile` 版本同理，只是错误信息分别写"Cannot decrypt delete file"和"Cannot decrypt manifest"。`ManifestFile` 版本用的是 `manifest.path()`，且没有调用带 `length` 的重载（默认实现委托到 `newInputFile(path)`）。

新增 import：`DataFile`、`DeleteFile`、`ManifestFile`、`Preconditions`。注意这三个类来自 `org.apache.iceberg` 包（api 模块自身），不引入跨模块循环依赖。

### `api/src/main/java/org/apache/iceberg/encryption/EncryptingFileIO.java`（新文件，214 行）

**修改目的**：提供加密感知的 `FileIO` 装饰器，把加密/解密逻辑从各调用点收敛到 FileIO 层。

**工作逻辑**：

**类结构**：`EncryptingFileIO implements FileIO, Serializable`，持有两个 `final` 字段：`FileIO io`（被装饰的底层 IO）和 `EncryptionManager em`（加密管理器）。构造器包级可见，对外通过静态工厂 `create(FileIO, EncryptionManager)` 暴露。

**工厂方法 `create`**：
```java
public static EncryptingFileIO create(FileIO io, EncryptionManager em) {
  if (io instanceof EncryptingFileIO) {
    return (EncryptingFileIO) io;
  }
  return new EncryptingFileIO(io, em);
}
```
幂等性保证：如果传入的 `io` 已经是 `EncryptingFileIO`，直接返回，避免重复包装。注意这种情况会**丢弃传入的 `em`**，沿用原有 `EncryptingFileIO` 的 em——这通常是对的，因为已经在加密上下文里。

**路径级方法（转发）**：`newInputFile(String)`、`newInputFile(String, long)`、`newOutputFile(String)`、`deleteFile(String)` 全部直接转发给 `io`。这些方法处理的是"裸路径"，没有 keyMetadata 信息，无法解密，也不应该解密（调用方可能是要读元数据 JSON 等本就不加密的文件）。

**文件对象级方法（条件解密）**：

`newInputFile(DataFile)` 和 `newInputFile(DeleteFile)` 都委托给私有方法 `newInputFile(ContentFile<?>)`：
```java
private InputFile newInputFile(ContentFile<?> file) {
  if (file.keyMetadata() != null) {
    return newDecryptingInputFile(file.path().toString(), file.fileSizeInBytes(), file.keyMetadata());
  } else {
    return newInputFile(file.path().toString(), file.fileSizeInBytes());
  }
}
```
逻辑清晰：有 keyMetadata 走解密路径，没有则走普通路径（且用上 `fileSizeInBytes` 避免底层再 HEAD 一次）。

`newInputFile(ManifestFile)` 类似，但内联了逻辑（因为 `ManifestFile` 不是 `ContentFile` 的子类型）：
```java
if (manifest.keyMetadata() != null) {
  return newDecryptingInputFile(manifest.path(), manifest.length(), manifest.keyMetadata());
} else {
  return newInputFile(manifest.path(), manifest.length());
}
```

**解密实现 `newDecryptingInputFile`**：
```java
public InputFile newDecryptingInputFile(String path, long length, ByteBuffer buffer) {
  return em.decrypt(wrap(io.newInputFile(path, length), buffer));
}
```
工作链路：先用底层 `io` 创建读取密文字节的 `InputFile`，再和 keyMetadata 一起包装成 `EncryptedInputFile`，最后交给 `EncryptionManager.decrypt` 得到明文 InputFile。`wrap(InputFile, ByteBuffer)` 是私有静态方法，返回一个 `SimpleEncryptedInputFile`（内部类，实现 `EncryptedInputFile` 接口）。

**批量解密 `bulkDecrypt`**：
```java
public Map<String, InputFile> bulkDecrypt(Iterable<? extends ContentFile<?>> files) {
  Iterable<InputFile> decrypted = em.decrypt(Iterables.transform(files, this::wrap));
  ImmutableMap.Builder<String, InputFile> builder = ImmutableMap.builder();
  for (InputFile in : decrypted) {
    builder.put(in.location(), in);
  }
  return builder.buildKeepingLast();
}
```
工作链路：先用 `Iterables.transform` 把每个 `ContentFile` 转成 `EncryptedInputFile`（通过 `wrap(ContentFile)`），再调用 `em.decrypt(Iterable<EncryptedInputFile>)` 的批量重载——这个批量方法默认是逐个解密，但 `EncryptionManager` 实现可以重写它做批量预取密钥（减少 KMS 往返）。最后把结果收集成 `location → InputFile` 的 map，用 `buildKeepingLast()` 处理重复 location（保留最后一个）。

`wrap(ContentFile)` 私有方法：
```java
private SimpleEncryptedInputFile wrap(ContentFile<?> file) {
  InputFile encryptedInputFile = io.newInputFile(file.path().toString(), file.fileSizeInBytes());
  return new SimpleEncryptedInputFile(encryptedInputFile, toKeyMetadata(file.keyMetadata()));
}
```

**加密输出 `newEncryptingOutputFile`**：
```java
public EncryptedOutputFile newEncryptingOutputFile(String path) {
  OutputFile plainOutputFile = io.newOutputFile(path);
  return em.encrypt(plainOutputFile);
}
```
注意这个方法返回的是 `EncryptedOutputFile` 而非 `OutputFile`，所以它不是对 `FileIO.newOutputFile` 的重写，而是 EncryptingFileIO 独有的新方法。调用方写加密文件时需要显式调用它，而不是 `newOutputFile`。

**资源管理 `close()`**：
```java
public void close() {
  io.close();
  if (em instanceof Closeable) {
    try {
      ((Closeable) em).close();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to close encryption manager", e);
    }
  }
}
```
同时关闭底层 IO 和加密管理器（如果 em 实现了 Closeable，比如持有 KMS 连接）。

**内部辅助类**：
- `SimpleEncryptedInputFile`：`EncryptedInputFile` 的简单实现，持有 `encryptedInputFile` 和 `keyMetadata`。
- `SimpleKeyMetadata`：`EncryptionKeyMetadata` 的简单实现，包装一个 `ByteBuffer`。`copy()` 返回 `duplicate()` 的副本。
- `EmptyKeyMetadata`：单例，`buffer()` 返回 null，`copy()` 返回自身。用于 keyMetadata 为 null 的场景（虽然实际解密路径里 null 不会走到这里，但 `toKeyMetadata` 做了 null 防护）。

`toKeyMetadata(ByteBuffer)` 静态方法：buffer 非 null 时构造 `SimpleKeyMetadata`，否则返回 `EmptyKeyMetadata.get()`。

### `core/src/main/java/org/apache/iceberg/ManifestFiles.java`（简化 manifest 读取）

**修改目的**：让 manifest 读取走新的 `FileIO.newInputFile(ManifestFile)` 接口，简化缓存逻辑。

**工作逻辑**：

1. `read(...)` 和 `readDeletes(...)` 两处，把 `newInputFile(io, manifest.path(), manifest.length())` 改为 `newInputFile(io, manifest)`。方法签名从 `(FileIO, String, long)` 变为 `(FileIO, ManifestFile)`。

2. 私有方法 `newInputFile(FileIO, ManifestFile)` 重写为：
```java
private static InputFile newInputFile(FileIO io, ManifestFile manifest) {
  InputFile input = io.newInputFile(manifest);
  if (cachingEnabled(io)) {
    return contentCache(io).tryCache(input);
  }
  return input;
}
```
对比旧逻辑（try-catch 包裹 `cachingEnabled`、再 `cache.tryCache(io, path, length)`），新逻辑更简洁：先让 FileIO 自己决定怎么读这个 manifest（可能解密、可能不解密），拿到 InputFile 后再决定是否缓存。这保证了缓存的是"正确的 InputFile"——如果 io 是 EncryptingFileIO，`io.newInputFile(manifest)` 返回的就是解密后的 InputFile，缓存它才是对的。

3. `cachingEnabled(FileIO)` 重写，把 try-catch 从调用点移进方法内部：
```java
static boolean cachingEnabled(FileIO io) {
  try {
    return PropertyUtil.propertyAsBoolean(
        io.properties(),
        CatalogProperties.IO_MANIFEST_CACHE_ENABLED,
        CatalogProperties.IO_MANIFEST_CACHE_ENABLED_DEFAULT);
  } catch (UnsupportedOperationException e) {
    return false;
  }
}
```
有些 FileIO 实现的 `properties()` 会抛 `UnsupportedOperationException`（默认实现就是抛），旧代码在调用点 catch，现在收敛到 `cachingEnabled` 内部，调用点更干净。

### `core/src/main/java/org/apache/iceberg/encryption/NativeEncryptionOutputFile.java`（接口扩展）

**修改目的**：让 `NativeEncryptionOutputFile` 同时是 `OutputFile`，使其能直接作为 OutputFile 使用。

**工作逻辑**：

接口声明从 `extends EncryptedOutputFile` 改为 `extends EncryptedOutputFile, OutputFile`。新增四个 default 方法，全部委托给 `encryptingOutputFile()`：

- `create()` → `encryptingOutputFile().create()`
- `createOrOverwrite()` → `encryptingOutputFile().createOrOverwrite()`
- `location()` → `encryptingOutputFile().location()`
- `toInputFile()` → `encryptingOutputFile().toInputFile()`

`NativeEncryptionOutputFile` 原本就声明了 `plainOutputFile()`（返回底层明文 OutputFile）和 `keyMetadata()`。现在它既是 `EncryptedOutputFile`（有 `encryptingOutputFile()` 方法）又是 `OutputFile`（有 `create/createOrOverwrite/location/toInputFile` 方法），后者通过委托前者实现。这让调用方可以把 `NativeEncryptionOutputFile` 直接当 `OutputFile` 用，而不必显式调用 `.encryptingOutputFile()`。

### `core/src/main/java/org/apache/iceberg/encryption/StandardEncryptionManager.java`（短路优化）

**修改目的**：避免对已经是 `NativeEncryptionInputFile` 的输入重复包装。

**工作逻辑**：在 `decrypt(EncryptedInputFile)` 方法开头加了一个短路判断：
```java
if (encrypted instanceof NativeEncryptionInputFile) {
  return (NativeEncryptionInputFile) encrypted;
}
return new StandardDecryptedInputFile(encrypted);
```
如果传入的已经是 `NativeEncryptionInputFile`（即格式原生加密的输入文件，本身已经知道如何解密），直接返回，不再用 `StandardDecryptedInputFile` 再包一层。这避免了双重包装，也说明 `NativeEncryptionInputFile` 自身已经具备解密能力。

### `core/src/main/java/org/apache/iceberg/io/ContentCache.java`（重构缓存入口）

**修改目的**：让缓存作用于 `InputFile` 而非 `FileIO + path + length`，与新接口对齐。

**工作逻辑**：

1. **类型重命名**：内部类 `CacheEntry` 改名为 `FileContent`，但保留 `CacheEntry` 作为空的 `@Deprecated` 父类（`FileContent extends CacheEntry`），以兼容旧的 `get`/`getIfPresent` 方法签名。计划 1.7 移除。

2. **`tryCache` 新签名**：
```java
public InputFile tryCache(InputFile input) {
  if (input.getLength() <= maxContentLength) {
    return new CachingInputFile(this, input);
  }
  return input;
}
```
旧的 `tryCache(FileIO, String, long)` 保留为 `@Deprecated`，内部委托新方法：
```java
@Deprecated
public InputFile tryCache(FileIO io, String location, long length) {
  return tryCache(io.newInputFile(location, length));
}
```
注意旧方法现在会用 `io.newInputFile(location, length)` 创建 InputFile 再缓存——如果 io 是 EncryptingFileIO，这里会自动解密（但旧调用方本来期望的是缓存原始字节，行为有微妙变化；不过旧方法已废弃，不鼓励使用）。

3. **`CachingInputFile` 简化**：旧实现持有 `FileIO io`、`String location`、`long length`，并有一个懒加载的 `fallbackInputFile` 字段。新实现只持有一个 `InputFile input`：
```java
private CachingInputFile(ContentCache cache, InputFile input) {
  this.contentCache = cache;
  this.input = input;
}
```
所有方法（`getLength`、`location`、`exists`、`newStream`）都直接用 `input`，不再需要 `wrappedInputFile()` 懒加载方法。`getLength()` 优先查缓存，缓存未命中时用 `input.getLength()`。

4. **下载逻辑提取为静态方法 `download(InputFile)`**：原来在 `CachingInputFile.cacheEntry()` 里的下载逻辑（按 4MB chunk 读流、组装 ByteBuffer 列表）被提到 `ContentCache` 的静态方法 `download(InputFile)`，逻辑不变，只是入参从隐式的 `wrappedInputFile()` 变成显式的 `input`。

5. **`newStream()` 简化**：旧实现在 `getLength() > maxContentLength` 时回退到 `wrappedInputFile().newStream()`，并有复杂的 `RuntimeException` 处理。新实现因为 `tryCache` 已经做了长度判断（超限的不包装），所以 `CachingInputFile.newStream()` 只需走 `cachedStream()`，异常处理也简化为只 catch `UncheckedIOException`。IOException 的 catch 改为回退到 `input.newStream()`。

6. **`get`/`getIfPresent` 标记 `@Deprecated`**：计划 1.7 移除，鼓励外部调用方改用 `tryCache(InputFile)`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java`（简化批量解密）

**修改目的**：用 `EncryptingFileIO.bulkDecrypt` 替代手写的批量解密逻辑。

**工作逻辑**：

`inputFiles()` 方法旧实现：
```java
Stream<EncryptedInputFile> encryptedFiles =
    taskGroup.tasks().stream().flatMap(this::referencedFiles).map(this::toEncryptedInputFile);
Iterable<InputFile> decryptedFiles = table.encryption().decrypt(encryptedFiles::iterator);
Map<String, InputFile> files = Maps.newHashMapWithExpectedSize(taskGroup.tasks().size());
decryptedFiles.forEach(decrypted -> files.putIfAbsent(decrypted.location(), decrypted));
this.lazyInputFiles = ImmutableMap.copyOf(files);
```

新实现：
```java
this.lazyInputFiles =
    EncryptingFileIO.create(table().io(), table().encryption())
        .bulkDecrypt(
            () -> taskGroup.tasks().stream().flatMap(this::referencedFiles).iterator());
```

- 旧代码需要 `toEncryptedInputFile(ContentFile)` 私有方法（用 `EncryptedFiles.encryptedInput` 包装），新代码删除了它。
- `bulkDecrypt` 接收 `Iterable<? extends ContentFile<?>>`。这里传入的 `() -> ...iterator()` 是一个 lambda——`Iterable` 是函数式接口（单方法 `iterator()`），所以该 lambda 实现了 `Iterable<ContentFile<?>>`，每次调用 `iterator()` 时重新生成流。这样既满足签名又保持懒求值。
- `bulkDecrypt` 内部用 `buildKeepingLast()` 处理重复 location，等价于旧代码的 `putIfAbsent`（保留首个；注意 `buildKeepingLast` 保留最后一个，语义略有差异，但在 location 唯一的场景下无影响）。
- 移除了 `EncryptedFiles`、`EncryptedInputFile`、`ImmutableMap`、`Maps` 的 import，新增 `EncryptingFileIO` import。

### `core/src/test/java/org/apache/iceberg/hadoop/TestCatalogUtilDropTable.java`（测试适配）

**修改目的**：测试中 mock 的 `FileIO` 需要 stub 新增的三个文件对象级方法，否则 mock 返回 null 会导致 `CatalogUtil.dropTableData` 出错。

**工作逻辑**：

三处重复的 mock 创建代码被提取为 `createMockFileIO(FileIO wrapped)` 静态方法，除了原有的 `newInputFile(String)` 和 `newInputFile(String, long)` stub，还新增三个 stub：
```java
Mockito.when(mockIO.newInputFile(Mockito.any(ManifestFile.class)))
    .thenAnswer(invocation -> wrapped.newInputFile((ManifestFile) invocation.getArgument(0)));
Mockito.when(mockIO.newInputFile(Mockito.any(DataFile.class)))
    .thenAnswer(invocation -> wrapped.newInputFile((DataFile) invocation.getArgument(0)));
Mockito.when(mockIO.newInputFile(Mockito.any(DeleteFile.class)))
    .thenAnswer(invocation -> wrapped.newInputFile((DeleteFile) invocation.getArgument(0)));
```
全部委托给真实的 `wrapped`（即 `table.io()`），保证 mock IO 在新接口下也能正确工作。第二处测试还移除了 `Mockito.doThrow(new RuntimeException()).when(fileIO).deleteFile(...)` 之外的多余 mock 设置（统一走 helper）。

## 小结

这是 5 个提交中最大的一个（8 文件、+384/-153 行），属于 API 层的架构性改动。核心是把加密/解密从"调用方手工组装"模式迁移到"FileIO 装饰器自动处理"模式：

- `FileIO` 接口新增按文件对象读取的能力，默认实现拒绝解密（快速失败）。
- `EncryptingFileIO` 装饰器统一处理加解密，提供单文件和批量两种入口。
- `ContentCache` 改为缓存 `InputFile` 而非从 `FileIO+path` 重建，保证缓存的是解密后的内容。
- `ManifestFiles`、Spark `BaseReader` 等调用点大幅简化，删除了样板代码。
- 旧 API（`ContentCache.tryCache(FileIO, String, long)`、`CacheEntry`）保留为 `@Deprecated`，计划 1.7 移除，提供迁移窗口。

**影响范围**：触及 api 模块（`FileIO`、`EncryptingFileIO`）、core 模块（`ManifestFiles`、`ContentCache`、`NativeEncryptionOutputFile`、`StandardEncryptionManager`）、spark v3.5 模块（`BaseReader`）和测试。由于 `FileIO` 是核心接口，新增的 default 方法对现有实现（`S3FileIO`、`HadoopFileIO`、`ResolvingFileIO` 等）二进制兼容——不重写就用默认的"拒绝解密"实现。

**回迁到 1.4.x 的注意事项**：

1. **二进制兼容性**：`FileIO` 新增的三个方法是 `default`，1.4.x 上现有的 FileIO 实现不需要改动即可编译通过。但行为上，如果 1.4.x 有代码读带 `keyMetadata` 的文件并走默认 `FileIO`（而非 EncryptingFileIO），会从"静默读密文"变成"抛 IllegalArgumentException"——这是正确的行为修正，但需要确认没有依赖旧错误行为的代码。
2. **`ContentCache` 重构是破坏性的**：`CacheEntry` 从具体类变成空父类、`tryCache` 签名变化（虽然有 deprecated 旧签名）。如果 1.4.x 有外部代码直接用 `CacheEntry` 或旧 `tryCache`，回迁后仍能编译（deprecated），但建议尽快迁移。
3. **`NativeEncryptionOutputFile` 多继承 `OutputFile`**：这是接口层面的多继承，1.4.x 的实现类如果只实现了 `EncryptedOutputFile` 那一侧，现在还需要补上 `OutputFile` 的方法——但因为有 default 实现，实际不需要改代码。
4. **`StandardEncryptionManager.decrypt` 的短路**：如果 1.4.x 有代码故意传非 `NativeEncryptionInputFile` 的 `EncryptedInputFile` 给 `decrypt` 并期望得到 `StandardDecryptedInputFile`，行为不变（短路只对 `NativeEncryptionInputFile` 生效）。
5. **Spark v3.5 BaseReader 改动**：1.4.x 如果只支持到 Spark 3.4 或更低，这条改动不适用，需要对应版本做等价改造。但本提交只改了 v3.5，说明 v3.4 及以下的 BaseReader 可能在后续提交中才同步。
6. **依赖关系**：`EncryptingFileIO` 在 api 模块，依赖 `DataFile`/`DeleteFile`/`ManifestFile`（也在 api）、`ContentFile`（api）、`EncryptionManager`/`EncryptedInputFile`/`EncryptedOutputFile`/`EncryptionKeyMetadata`（api），无跨模块新依赖。回迁时确认 1.4.x 的 api 模块这些类都存在即可。
