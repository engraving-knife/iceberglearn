# 提交 1414：Revert "Core: Update TableMetadataParser to ensure all streams closed (#11220)" (#11621)

## 提交信息

- **序号**：1414 / 4088
- **哈希**：12845d4edc0dcb65c7049509bbd54dc16396c6d1
- **短哈希**：12845d4ed
- **日期**：2024-11-21（Thu Nov 21 21:09:10 2024 +0100）
- **作者**：Hussein Awala <hussein@awala.fr>
- **提交说明**：Revert "Core: Update TableMetadataParser to ensure all streams closed (#11220)" (#11621)
- **PR/Issue**：#11621
- **被回退提交**：2b55fef7cc2a249d864ac26d85a4923313d96a59（#11220，2024-10-25 由 erik-grepr 提交）

## 总体目的

此前提交 #11220 试图改进 `TableMetadataParser`，确保在写入与读取 metadata 文件时所有流（包括底层 `OutputStream`/`InputStream` 与上层 `GZIPOutputStream`/`GZIPInputStream`、`OutputStreamWriter`）都被正确关闭，方式是把最底层的原始流也纳入 `try-with-resources`。但该改动引入了回归问题，因此本提交将其完整回退，恢复到改动前的实现。

典型问题场景在于：当包装流（如 `GZIPOutputStream`）在 `close()` 时会向底层流写入尾数据并 close 底层流，而 `try-with-resources` 的逆序关闭机制会先关闭外层包装流、再关闭底层流；若外层关闭过程中已经抛出异常，底层流可能未按预期关闭或导致重复关闭，进而引发 metadata 文件写入不完整或读取异常。回退后恢复原先"只关闭最外层包装流，由它负责关闭底层流"的简洁模式。

## 如何达成设计目的

直接 `git revert` 提交 `2b55fef7`，把 `TableMetadataParser` 的 `internalWrite` 与 `read` 两个方法恢复到 #11220 之前的版本，即不再把原始 `OutputStream`/`InputStream` 单独放进 `try-with-resources`，而是只关闭包装后的 `GZIPOutputStream`/`GZIPInputStream` 与 `OutputStreamWriter`。这是纯粹的代码回退，无新增逻辑。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java`

**修改目的**：恢复 `internalWrite` 与 `read` 中流的关闭方式。

**工作逻辑**：

- `internalWrite` 方法（写 metadata）：
  - #11220 版本（被回退）：
    ```java
    try (OutputStream os = overwrite ? outputFile.createOrOverwrite() : outputFile.create();
        OutputStream gos = isGzip ? new GZIPOutputStream(os) : os;
        OutputStreamWriter writer = new OutputStreamWriter(gos, StandardCharsets.UTF_8)) {
    ```
    把原始 `os`、包装后的 `gos`、`writer` 全部纳入 `try-with-resources`。
  - 回退后版本：
    ```java
    OutputStream stream = overwrite ? outputFile.createOrOverwrite() : outputFile.create();
    try (OutputStream ou = isGzip ? new GZIPOutputStream(stream) : stream;
        OutputStreamWriter writer = new OutputStreamWriter(ou, StandardCharsets.UTF_8)) {
    ```
    原始流 `stream` 在 `try` 之外声明，仅把包装流 `ou`（gzip 或原 stream）与 `writer` 纳入 `try-with-resources`。`GZIPOutputStream` 关闭时会自动关闭底层 `stream`；非 gzip 情形下 `ou` 即 `stream`，关闭 `ou` 即关闭原始流。

- `read` 方法（读 metadata）：
  - #11220 版本（被回退）：
    ```java
    try (InputStream is = file.newStream();
        InputStream gis = codec == Codec.GZIP ? new GZIPInputStream(is) : is) {
      return fromJson(file, JsonUtil.mapper().readValue(gis, JsonNode.class));
    ```
  - 回退后版本：
    ```java
    try (InputStream is =
        codec == Codec.GZIP ? new GZIPInputStream(file.newStream()) : file.newStream()) {
      return fromJson(file, JsonUtil.mapper().readValue(is, JsonNode.class));
    ```
    只保留一个 `try-with-resources` 资源 `is`：gzip 情形下 `is` 是 `GZIPInputStream(file.newStream())`，由 `GZIPInputStream.close()` 关闭底层 `file.newStream()`；非 gzip 情形下 `is` 直接是 `file.newStream()`。

## 小结

- **成效**：恢复 `TableMetadataParser` 在 #11220 之前的流关闭实现，消除该改动引入的回归问题，恢复 metadata 读写稳定性。
- **影响范围**：仅 `core/src/main/java/org/apache/iceberg/TableMetadataParser.java` 一个文件，6 行改动（+6/-6），无新增测试。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支通常不会主动合入 #11220（如果 1.4.x 一直没有该改动，则本回退也无意义）。**回迁的关键是判断 1.4.x 是否已经包含 #11220**：若已合入 #11220 并观察到 metadata 写入/读取相关回归，则应回迁此 revert；若 1.4.x 从未合入 #11220，则无需回迁此 revert。在确认 1.4.x 当前实现后决定即可，该回退本身风险极低。
