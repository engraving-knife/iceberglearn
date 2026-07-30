# 提交 3743：Core: Remove redundant string concatenation (#16409)

## 提交信息

- **序号**：3743 / 4088
- **哈希**：8e049f02ef4d2275024f7eeb3803c3c2fb27cd96
- **短哈希**：8e049f02e
- **日期**：2026-05-18 23:53:31 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Core: Remove redundant string concatenation (#16409)
- **PR/Issue**：#16409

## 总体目的

本提交清理了测试代码中冗余的字符串拼接。在 `KeyStoreKmsClient`（一个测试用的 KMS 客户端）中，有两处 `Preconditions.checkNotNull` 的错误消息使用了将两个字符串字面量拼接的写法，例如 `"must be set in hadoop or table " + "properties"`。这种将两个相邻字符串字面量用 `+` 拼接的写法是冗余的——它们本就是一个连续的字符串，分开写反而降低了可读性，且可能让人误以为拼接的是变量。本提交将其合并为单一字符串字面量，提升代码清晰度。

## 如何达成设计目的

直接将两处 `"string1 " + "string2"` 形式的冗余拼接合并为 `"string1 string2"` 单一字面量。

## 修改详情

### `core/src/test/java/org/apache/iceberg/encryption/KeyStoreKmsClient.java` (+2/-2 lines)

**修改目的**：移除冗余的字符串字面量拼接。

**工作逻辑**：
- 第一处：`KEYSTORE_FILE_PATH_PROP + " must be set in hadoop or table " + "properties"` 改为 `KEYSTORE_FILE_PATH_PROP + " must be set in hadoop or table properties"`。原本 `"must be set in hadoop or table " + "properties"` 是两个相邻字面量的冗余拼接，合并后更清晰。
- 第二处：`KEYSTORE_PASSWORD_ENV_VAR + " environment variable " + "must be set"` 改为 `KEYSTORE_PASSWORD_ENV_VAR + " environment variable must be set"`。同样合并冗余拼接。

## 总结

本提交是一处小的代码清理，将 `KeyStoreKmsClient` 测试类中两处冗余的字符串字面量拼接（`"str1 " + "str2"`）合并为单一字面量。改动不影响运行时行为（编译后字节码相同），仅提升代码可读性。属于代码质量改善类提交。
