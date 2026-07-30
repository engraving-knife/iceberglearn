# 提交 1060：Build: Suppress various build warnings (#10938)

## 提交信息

- **序号**：1060 / 4088
- **哈希**：8c85a5a7f1539b39df65fbaac4c4c6d5d6b97eef
- **短哈希**：8c85a5a7f
- **日期**：2024-08-15 14:51:14 +0200
- **作者**：Naveen Kumar
- **提交说明**：Build: Suppress various build warnings (#10938)
- **PR/Issue**：#10938

## 总体目的

Iceberg 项目使用 error-prone 和 checkstyle 等静态检查工具保障代码质量。最近 error-prone 新增了两个检查规则（`DangerousJavaDeserialization` 和 `ImmutablesReferenceEquality`），在 `baseline.gradle` 中以 `WARN` 级别临时开启，并留有 TODO（#10853、#10855）待决定是调整代码还是永久抑制。同时，多个文件中 `finalize()` 方法上的 `@SuppressWarnings("checkstyle:NoFinalizer")` 只抑制了 checkstyle 警告，但 error-prone 的 `Finalize` 检查仍会报警。

本提交的目的是清理这些构建警告：对 `DangerousJavaDeserialization` 和 `ImmutablesReferenceEquality` 两条规则从 `WARN` 升级为 `ERROR`（即直接决定保留这两条规则并按错误处理），并在涉及 Java 反序列化的地方加上 `@SuppressWarnings("DangerousJavaDeserialization")` 显式抑制（这些反序列化是 Iceberg 既有设计所需）；同时对所有 `finalize()` 方法把 `@SuppressWarnings("checkstyle:NoFinalizer")` 扩展为同时抑制 error-prone 的 `Finalize` 检查。这样既保留了安全检查的严格性，又消除了既有的合法用法产生的噪声警告。

## 如何达成设计目的

两步走：第一，在 `baseline.gradle` 中把两条新规则的级别从 `WARN` 改为 `ERROR`，并删除对应的 TODO 注释，表示项目决定采纳这两条规则。第二，针对这两条规则产生的合法"违规"位置，逐一加注解抑制：
- 对涉及 Java 原生反序列化的 `readObject`、`deserializeFromBytes`、`readFileForCommit` 方法加 `@SuppressWarnings("DangerousJavaDeserialization")`，因为这些都是 Iceberg 既有的序列化机制，不存在不可信数据源风险。
- 对所有覆盖 `finalize()` 的方法，把抑制注解扩展为 `@SuppressWarnings({"checkstyle:NoFinalizer", "Finalize"})`，同时抑制 checkstyle 和 error-prone 的告警。

## 修改详情

### `baseline.gradle` (+2/-4 lines)

**修改目的**：把两条 error-prone 新规则的级别从 WARN 升为 ERROR，并移除 TODO 注释。

**工作逻辑**：
```groovy
- // TODO (https://github.com/apache/iceberg/issues/10853) this is a recently added check. Figure out whether we adjust the code or suppress for good
- '-Xep:DangerousJavaDeserialization:WARN',
+ '-Xep:DangerousJavaDeserialization:ERROR',
  ...
- // TODO (https://github.com/apache/iceberg/issues/10855) this is a recently added check. Figure out whether we adjust the code or suppress for good
- '-Xep:ImmutablesReferenceEquality:WARN',
+ '-Xep:ImmutablesReferenceEquality:ERROR',
```
升级为 ERROR 后，新代码若违反这两条规则将直接导致构建失败，从而强制后续提交遵循规则；已有的合法用法通过下文的 `@SuppressWarnings` 显式豁免。

### 各 `*InputStream.java` / `*OutputStream.java` / `*FileIO.java` 等流类 (各 +1/-1 line)

涉及文件：
- `aliyun/src/main/java/org/apache/iceberg/aliyun/oss/OSSInputStream.java`
- `aliyun/src/main/java/org/apache/iceberg/aliyun/oss/OSSOutputStream.java`
- `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java`
- `aws/src/main/java/org/apache/iceberg/aws/s3/S3InputStream.java`
- `aws/src/main/java/org/apache/iceberg/aws/s3/S3OutputStream.java`
- `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSInputStream.java`
- `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSOutputStream.java`
- `core/src/main/java/org/apache/iceberg/hadoop/HadoopStreams.java`（两处）
- `core/src/main/java/org/apache/iceberg/io/ResolvingFileIO.java`
- `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSInputStream.java`
- `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSOutputStream.java`

**修改目的**：抑制 `finalize()` 方法上 error-prone 的 `Finalize` 警告。

**工作逻辑**：
这些类都覆盖了 `finalize()` 用于资源兜底回收，原有注解只抑制 checkstyle：
```java
- @SuppressWarnings("checkstyle:NoFinalizer")
+ @SuppressWarnings({"checkstyle:NoFinalizer", "Finalize"})
  @Override
  protected void finalize() throws Throwable { ... }
```
改为数组形式同时抑制 checkstyle 的 `NoFinalizer` 和 error-prone 的 `Finalize`，消除构建告警。

### `api/src/main/java/org/apache/iceberg/Metrics.java` (+2/-0 lines)

**修改目的**：抑制 `readObject` 和 `readByteBufferMap` 上的 `DangerousJavaDeserialization` 警告。

**工作逻辑**：
`Metrics` 类通过 Java 原生 `ObjectInputStream` 反序列化字段。由于 `baseline.gradle` 已把该规则升为 ERROR，必须显式抑制：
```java
+ @SuppressWarnings("DangerousJavaDeserialization")
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException { ... }

+ @SuppressWarnings("DangerousJavaDeserialization")
  private static Map<Integer, ByteBuffer> readByteBufferMap(ObjectInputStream in) ... { ... }
```
这是 Iceberg 既有序列化机制，反序列化数据来自受控环境，属于合法用法。

### `core/src/main/java/org/apache/iceberg/util/SerializationUtil.java` (+1/-1 line)

**修改目的**：在通用反序列化工具方法上同时抑制 `DangerousJavaDeserialization`。

**工作逻辑**：
```java
- @SuppressWarnings("unchecked")
+ @SuppressWarnings({"DangerousJavaDeserialization", "unchecked"})
  public static <T> T deserializeFromBytes(byte[] bytes) { ... }
```
该方法用 `ObjectInputStream` 反序列化任意字节，是项目内通用的序列化工具，原有 `unchecked` 抑制基础上追加 `DangerousJavaDeserialization`。

### `mr/src/main/java/org/apache/iceberg/mr/hive/HiveIcebergOutputCommitter.java` (+1/-0 line)

**修改目的**：抑制 `readFileForCommit` 上的 `DangerousJavaDeserialization` 警告。

**工作逻辑**：
```java
+ @SuppressWarnings("DangerousJavaDeserialization")
  private static DataFile[] readFileForCommit(String fileForCommitLocation, FileIO io) {
    try (ObjectInputStream ois = new ObjectInputStream(...)) { ... }
  }
```
该方法从 Iceberg 自身写入的提交文件中反序列化 `DataFile[]`，数据源受控，属于合法用法。

## 总结

这是一次构建警告清理提交。核心决策是把 error-prone 两条新规则（`DangerousJavaDeserialization`、`ImmutablesReferenceEquality`）从临时 WARN 升级为 ERROR，明确项目采纳这两条安全/质量规则；同时对既有代码中合法的 Java 反序列化和 `finalize()` 用法加注解显式抑制，消除构建噪声。改动不涉及任何业务逻辑变更，纯粹是注解和构建配置调整，但提升了后续代码的安全基线——新代码若再引入危险反序列化将直接构建失败。
