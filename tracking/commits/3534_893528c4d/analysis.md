# 提交 3534：Fix typos in javadoc/comment: 'intialize', 'seperated' (#15978)

## 提交信息

- **序号**：3534 / 4088
- **哈希**：893528c4d2cdded74fc440e1293604234cc5f565
- **短哈希**：893528c4d
- **日期**：2026-04-15 09:09:59 -0700
- **作者**：Mukunda Rao Katta
- **提交说明**：Fix typos in javadoc/comment: 'intialize', 'seperated' (#15978)
- **PR/Issue**：#15978

## 总体目的

修正两处代码注释中的拼写错误：
1. `S3FileIOAwsClientFactories.java` 的 Javadoc 中 "intialize" 应为 "initialize"（少了一个 i）
2. `TestLocationProvider.java` 的注释中 "seperated" 应为 "separated"（应为 a 而非 e）

这是纯文档/注释修复，不影响代码逻辑，属于提升代码文档质量的 nit 修复。

## 如何达成设计目的

直接修改两处注释中的拼写错误，无逻辑改动。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/S3FileIOAwsClientFactories.java` (+1/-1 lines)

**修改目的**：修正 Javadoc 拼写错误。

**工作逻辑**：
```java
-   * AwsClientFactories#from(Map) to intialize an AWS client factory class}
+   * AwsClientFactories#from(Map) to initialize an AWS client factory class}
```
"intialize" → "initialize"。

### `core/src/test/java/org/apache/iceberg/TestLocationProvider.java` (+1/-1 lines)

**修改目的**：修正测试注释拼写错误。

**工作逻辑**：
```java
-    // no partition values included in the path and last part of entropy is seperated with "-"
+    // no partition values included in the path and last part of entropy is separated with "-"
```
"seperated" → "separated"。

## 总结

本提交修正了两处代码注释中的拼写错误（"intialize"→"initialize"、"seperated"→"separated"），属于文档质量改进，不影响任何代码逻辑。
