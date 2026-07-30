# 提交 1622 84c8db40f 分析

## 提交信息
- 哈希：84c8db40f9500aa804b0428baeb51b1041c64a94
- 日期：2025-01-23 07:48:22 +0100
- 作者：Yuya Ebihara
- 消息：AWS, Core, Delta: Remove redundant charset lookup (#12057)

## 总体目的

本提交清理了仓库中三处对 `URLDecoder`/`URLEncoder` 的冗余字符集查找调用，移除了因使用 `String` 形式字符集名称而必须的 `try/catch UnsupportedEncodingException` 处理逻辑。

Java 10 起，`URLDecoder.decode(String s, Charset charset)` 和 `URLEncoder.encode(String s, Charset charset)` 这两个直接接收 `Charset` 对象的重载方法已经稳定可用。相比接收 `String` 字符集名称的旧重载，新重载不会抛出 `UnsupportedEncodingException`——因为 `Charset` 对象本身已经验证过字符集的存在性。原代码却仍然通过 `StandardCharsets.UTF_8.name()` 取字符串名再传入，导致编译器强制要求捕获一个在传入 UTF-8 时根本不可能发生的受检异常。

这种样板化的 try/catch 包裹既增加了代码噪音，又掩盖了真实业务逻辑。本提交将所有调用点改为直接传入 `StandardCharsets.UTF_8` 这个 `Charset` 实例，同时删除对应的异常处理块和不再使用的 `UnsupportedEncodingException`、`UncheckedIOException` 的导入。

## 如何达成设计目的

设计思路是逐文件、逐调用点替换：

1. 将 `StandardCharsets.UTF_8.name()`（返回 "UTF-8" 字符串）替换为 `StandardCharsets.UTF_8`（返回 `Charset` 对象）。
2. 删除因此变得多余的 `try { ... } catch (UnsupportedEncodingException e) { ... }` 包装。
3. 清理不再使用的 `import` 语句：`java.io.UnsupportedEncodingException`、`java.io.UncheckedIOException`（如适用）。
4. 保持原有错误处理语义：原本在异常分支抛出 `RuntimeException`/`UncheckedIOException`/`IllegalArgumentException` 的位置，由于新调用不再抛出该异常，这些分支自然被移除；而调用前的 `Preconditions.checkArgument` 校验等业务校验保持不变。

### 修改详情

#### aws/src/integration/java/org/apache/iceberg/aws/lakeformation/LakeFormationTestBase.java

这是 AWS Lake Formation 集成测试基类。修改点有两处：

1. 在策略文档解码处，原代码 `URLDecoder.decode(existingPolicy.document(), StandardCharsets.UTF_8.name())` 被包在 try/catch 中以应对 `UnsupportedEncodingException`。改为直接调用 `URLDecoder.decode(existingPolicy.document(), StandardCharsets.UTF_8)`，简化为单行赋值。该调用用于解码从 AWS IAM 返回的 URL 编码后的策略文档 JSON，以便与本地构造的 `policyDocument` 比较是否一致。
2. 删除了 `catch (UnsupportedEncodingException e) { throw new RuntimeException(e); }` 分支，并移除 `import java.io.UnsupportedEncodingException`。注意：同方法中 `catch (NoSuchEntityException e)` 分支保留不变，因为那是 AWS SDK 抛出的真实业务异常。

#### core/src/main/java/org/apache/iceberg/rest/RESTUtil.java

这是 Iceberg REST 客户端/服务端共用的工具类，提供 URL 编解码能力。

1. `encodeString(String toEncode)` 方法：原实现用 `URLEncoder.encode(toEncode, StandardCharsets.UTF_8.name())` 并捕获 `UnsupportedEncodingException` 转抛为 `UncheckedIOException`。改为直接 `URLEncoder.encode(toEncode, StandardCharsets.UTF_8)`。该方法用于 REST 请求中表单/路径参数的 URL 编码。
2. `decodeString(String encoded)` 方法：对称地用 `URLDecoder.decode(encoded, StandardCharsets.UTF_8)` 替换原字符串名版本，删除 try/catch。
3. 移除 `import java.io.UncheckedIOException` 和 `import java.io.UnsupportedEncodingException`。原有的 `Preconditions.checkArgument(... != null, ...)` 前置非空校验保留不变。

#### delta-lake/src/main/java/org/apache/iceberg/delta/BaseSnapshotDeltaLakeTableAction.java

这是 Delta Lake 表快照迁移到 Iceberg 的核心动作类。

修改集中在私有静态方法 `getFullFilePath(String path, String tableRoot)`。该方法负责把 Delta Lake 的 AddFile 路径解析为完整文件系统路径：先 URL 解码，再判断是否为绝对 URI（是则原样返回，否则拼接到 `tableRoot` 下）。原实现把整个解码逻辑包在 try/catch 中，捕获 `UnsupportedEncodingException` 后抛 `IllegalArgumentException`。改为直接调用 `URLDecoder.decode(path, StandardCharsets.UTF_8)`，去掉了 try/catch 包装，让 `if/else` 分支结构更扁平可读。`URI.create(path)` 的调用顺序保持不变——注意原代码先创建 URI 再解码，新代码也是先创建 URI 再解码，行为一致。

## 小结

成效：减少了约 21 行样板代码（净删除 21 行），三处工具/测试代码更加简洁，调用链意图更清晰。语义完全等价——`StandardCharsets.UTF_8` 这个常量本身保证 UTF-8 一定可用，所以原 try/catch 分支实际上是不可达代码。

影响范围：仅影响三处内部工具方法（REST 编解码、Delta 路径解析、Lake Formation 测试），属纯重构，无功能行为变化，无 API 签名变化。

回迁到 1.4.x 的注意事项：
- 此改动依赖 Java 10+ 的 `URLDecoder/URLEncoder` 接收 `Charset` 的重载。1.4.x 分支的最低 Java 版本需确认 ≥ 10（Iceberg 主线已要求 Java 8 仅为兼容旧运行时，但实际编译目标可能更高，回迁前需检查 1.4.x 的 `build.gradle` 中 `sourceCompatibility`/`release` 配置）。
- 若 1.4.x 仍需兼容 Java 8，则不能直接回迁，需要保留旧写法。建议先核实 1.4.x 的 Java 基线。
- 改动是纯机械替换，冲突概率低，可逐文件 cherry-pick。
