# 提交 2784：Core: Remove usage of deprecated TableProperties.MANIFEST_LISTS_ENABLED (#14347)

## 提交信息

- **序号**：2784 / 4088
- **哈希**：6b86a75d3a0f0123d1a19f2db0b266bef05c2a5b
- **短哈希**：6b86a75d3
- **日期**：2025-10-21 10:22:22 -0700
- **作者**：gaborkaszab
- **提交说明**：Core: Remove usage of deprecated TableProperties.MANIFEST_LISTS_ENABLED (#14347)
- **PR/Issue**：#14347

## 总体目的

本提交移除对已废弃的 `TableProperties.MANIFEST_LISTS_ENABLED` 属性的使用，并清理相关的测试代码。

`MANIFEST_LISTS_ENABLED` 属性用于控制是否写入 manifest list 文件。该属性已被标记为 `@Deprecated`，因为写入 manifest list 已成为始终启用的默认行为。然而在 `TestFastAppend` 中仍有两个测试（`testRecoveryWithManifestList` 和 `testRecoveryWithoutManifestList`）使用该属性来测试不同配置下的恢复行为。

提交作者发现这两个测试无论将该属性设为 `true` 还是 `false`，测试行为都完全相同——因为 manifest list 写入已始终启用，该属性不再产生实际影响。这意味着这两个测试是无效的（测试了不存在的功能差异），且 `testWriteNewManifestsIdempotency` 测试已覆盖了相同的恢复场景。因此本提交删除这两个无效测试。

此外，本提交还将 `MANIFEST_LISTS_ENABLED` 和 `MANIFEST_LISTS_ENABLED_DEFAULT` 的 `@Deprecated` 注解中的移除版本从 2.0.0 更正为 1.12.0，与 Iceberg 当前的版本规划一致。

## 如何达成设计目的

1. **删除无效测试**：移除 `TestFastAppend` 中的 `testRecoveryWithManifestList` 和 `testRecoveryWithoutManifestList` 两个测试方法（共 44 行），因为它们测试的属性已无效，且 `testWriteNewManifestsIdempotency` 已覆盖相同场景。

2. **更正废弃版本**：将 `TableProperties.MANIFEST_LISTS_ENABLED` 和 `MANIFEST_LISTS_ENABLED_DEFAULT` 的 Javadoc 中 `@deprecated will be removed in 2.0.0` 改为 `@deprecated will be removed in 1.12.0`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (+2/-2 lines)

**修改目的**：更正废弃属性的移除版本。

**工作逻辑**：将 `MANIFEST_LISTS_ENABLED` 和 `MANIFEST_LISTS_ENABLED_DEFAULT` 的 `@deprecated` Javadoc 从 "will be removed in 2.0.0" 改为 "will be removed in 1.12.0"，与当前版本规划保持一致。

### `core/src/test/java/org/apache/iceberg/TestFastAppend.java` (-44 lines)

**修改目的**：删除使用已废弃属性且无效的两个测试。

**工作逻辑**：删除 `testRecoveryWithManifestList`（设置 `MANIFEST_LISTS_ENABLED=true`）和 `testRecoveryWithoutManifestList`（设置 `MANIFEST_LISTS_ENABLED=false`）两个测试方法。这两个测试注入 3 次提交失败后重试，验证 manifest 文件在恢复后仍然存在。但由于 manifest list 写入已始终启用，两个测试行为完全相同，且 `testWriteNewManifestsIdempotency` 已覆盖相同的恢复场景。

## 总结

本提交清理了对已废弃的 `MANIFEST_LISTS_ENABLED` 属性的使用，删除了两个因属性失效而变得无效的测试。同时更正了废弃注解中的移除版本从 2.0.0 到 1.12.0。这是代码清理和废弃属性移除准备工作的一部分，为 1.12.0 完全移除该属性铺平道路。
