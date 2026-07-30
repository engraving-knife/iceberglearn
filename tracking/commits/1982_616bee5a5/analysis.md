# 提交 1982：Api: Deprecate CredentialSupplier (#12763)

## 提交信息

- **序号**：1982 / 4088
- **哈希**：616bee5a5979d8971f54c62cfb7888ff73fbbe6c
- **短哈希**：616bee5a5
- **日期**：2025-04-11 10:05:50 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Api: Deprecate CredentialSupplier (#12763)
- **PR/Issue**：#12763

## 总体目的

本提交将 `org.apache.iceberg.io.CredentialSupplier` 接口标记为 `@Deprecated`，计划在 2.0.0 移除。

根据提交说明，该 API 在代码库中已无任何使用处（"This API isn't used anywhere"）。`CredentialSupplier` 原本设计用于在不使用 `FileIO` 的系统中，以字符串形式获取访问表文件所需的凭证。但由于实际上没有代码使用它，保留一个无人使用的公共 API 会增加维护负担并让用户产生可以依赖它的错觉。先标记弃用、后续在 2.0.0 移除，是清理无用公共 API 的标准流程，给下游用户一个迁移/知晓的过渡期。

## 如何达成设计目的

在接口上添加 `@Deprecated` 注解与 Javadoc 说明，明确弃用起始版本与移除版本。

## 修改详情

### `api/src/main/java/org/apache/iceberg/io/CredentialSupplier.java` (修改, +3/-0 lines)

**修改目的**：标记接口为废弃。

**工作逻辑**：在 `CredentialSupplier` 接口的 Javadoc 中追加 `@deprecated since 1.10.0, will be removed in 2.0.0`，并在接口声明上添加 `@Deprecated` 注解。接口体（`String getCredential();`）不变。

## 总结

本提交将无人使用的 `CredentialSupplier` 公共接口标记为 `@Deprecated`（自 1.10.0 起，2.0.0 移除），为后续移除做铺垫。仅 3 行 Javadoc/注解新增，无逻辑变更。
