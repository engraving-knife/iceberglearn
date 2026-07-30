# 提交 1284：Core: Update TableMetadataParser to ensure all streams closed (#11220)

## 提交信息

- **序号**：1284 / 4088
- **哈希**：2b55fef7cc2a249d864ac26d85a4923313d96a59
- **短哈希**：2b55fef7c
- **日期**：2024-10-25（Fri Oct 25 22:57:24 2024 -0700）
- **作者**：erik-grepr <erik@grepr.ai>
- **提交说明**：Core: Update TableMetadataParser to ensure all streams closed (#11220)
- **PR/Issue**：#11220

## 总体目的

`TableMetadataParser` 负责把表元数据（`TableMetadata`）序列化写出 / 反序列化读取，支持可选的 GZIP 压缩。旧实现中，底层流的创建放在 try-with-resources **之外**，导致在某些异常路径下底层流不会被关闭，造成资源（文件句柄）泄漏。

具体两处问题：

1. **写出（`internalWrite`）**：旧代码先在 try 之外创建 `stream = overwrite ? outputFile.createOrOverwrite() : outputFile.create();`，再在 try-with-resources 里用 `ou = isGzip ? new GZIPOutputStream(stream) : stream`。若 `new GZIPOutputStream(stream)` 构造抛异常，`stream`（已打开的底层 `OutputStream`）不会被任何 try 资源管理，发生泄漏。

2. **读取（`read`）**：旧代码 `try (InputStream is = codec == Codec.GZIP ? new GZIPInputStream(file.newStream()) : file.newStream())` 把 `file.newStream()` 直接嵌在三元表达式里。若 `new GZIPInputStream(底层流)` 构造抛异常，`file.newStream()` 返回的底层 `InputStream` 不在 try 资源里，发生泄漏。

本提交把这两处的**所有**流（底层流 + 包装流）都纳入 try-with-resources，确保任何构造异常都能关闭已打开的底层流，消除资源泄漏。

## 如何达成设计目的

思路是**把底层流也声明为 try-with-resources 的资源变量**，使其生命周期由 try 管理：

1. **写出**：把底层 `OutputStream` 的创建移入 try 头部（`os`），再在 try 头部声明包装流 `gos = isGzip ? new GZIPOutputStream(os) : os`，最后是 `OutputStreamWriter writer = new OutputStreamWriter(gos, UTF-8)`。这样无论 `GZIPOutputStream` 构造是否抛异常，`os` 都会被关闭。try-with-resources 按声明逆序关闭：先 `writer`、再 `gos`、再 `os`（`os` 的二次关闭是幂等的，无副作用）。

2. **读取**：把 `file.newStream()` 赋给 try 头部的 `is`，再在 try 头部声明 `gis = codec == Codec.GZIP ? new GZIPInputStream(is) : is`，读取用 `gis`。`new GZIPInputStream(is)` 抛异常时 `is` 仍会被关闭。

两处都保留了原有的 gzip 判定与异常处理逻辑，仅调整资源声明位置。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java`

**修改目的**：消除写出与读取路径上的底层流泄漏。

**工作逻辑**：

- `internalWrite(TableMetadata metadata, OutputFile outputFile, boolean overwrite)`：

  旧：
  ```java
  OutputStream stream = overwrite ? outputFile.createOrOverwrite() : outputFile.create();
  try (OutputStream ou = isGzip ? new GZIPOutputStream(stream) : stream;
      OutputStreamWriter writer = new OutputStreamWriter(ou, StandardCharsets.UTF_8)) {
  ```

  新：
  ```java
  try (OutputStream os = overwrite ? outputFile.createOrOverwrite() : outputFile.create();
      OutputStream gos = isGzip ? new GZIPOutputStream(os) : os;
      OutputStreamWriter writer = new OutputStreamWriter(gos, StandardCharsets.UTF_8)) {
  ```

  `JsonGenerator` 的创建与 `toJson` 调用不变。底层流变量名由 `stream`/`ou` 改为 `os`/`gos`。

- `read(FileIO io, InputFile file)`：

  旧：
  ```java
  try (InputStream is =
      codec == Codec.GZIP ? new GZIPInputStream(file.newStream()) : file.newStream()) {
    return fromJson(file, JsonUtil.mapper().readValue(is, JsonNode.class));
  ```

  新：
  ```java
  try (InputStream is = file.newStream();
      InputStream gis = codec == Codec.GZIP ? new GZIPInputStream(is) : is) {
    return fromJson(file, JsonUtil.mapper().readValue(gis, JsonNode.class));
  ```

  即底层 `file.newStream()` 始终赋给 `is`（受 try 管理），gzip 时再用 `gis` 包装并读取。

> 行为零变更：净增/净减各 6 行（重排），无新增 import，无逻辑分支变化。

## 小结

- **成效**：`TableMetadataParser` 的写出与读取路径现在把底层流与包装流都纳入 try-with-resources，杜绝了 GZIP 包装流构造失败时底层流泄漏的问题，提升元数据读写的健壮性（尤其在大量表/频繁元数据刷新场景下避免文件句柄耗尽）。
- **影响范围**：改动 1 个文件，新增 6 行、删除 6 行（均为重排），无 API/逻辑变化。
- **回迁到 1.4.x 的注意事项**：
  - **1.4.x 仍是旧写法**：1.4.x 的 `TableMetadataParser.internalWrite` 仍为 `OutputStream stream = ...; try (OutputStream ou = isGzip ? new GZIPOutputStream(stream) : stream; ...)`，`read` 仍为 `try (InputStream is = codec == GZIP ? new GZIPInputStream(file.newStream()) : file.newStream())`，即存在同样的泄漏隐患。本提交属缺陷修复，建议回迁。
  - **回迁简单**：改动自包含、无新依赖、无前置提交要求，可直接 cherry-pick；但需注意 1.4.x 该文件可能带有本地化的中文注释（本仓库 1.4.x 分支的 `TableMetadataParser` 含中文注释），cherry-pick 时需处理可能的注释冲突，以 main 的资源声明结构为准。
  - **风险**：低。建议回迁后跑 `TestTableMetadata` 等元数据读写测试，覆盖 gzip 与非 gzip 两种路径。
