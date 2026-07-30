# 提交 3402：Core: Fix rewriting delete manifests in RewriteTablePathUtil (#15155)

## 提交信息

- **序号**：3402 / 4088
- **哈希**：e822643ef06e8164c5d2314321b1278620b2ff97
- **短哈希**：e822643ef0
- **日期**：2026-03-16 17:23:53 -0700
- **作者**：wobu
- **提交说明**：Core: Fix rewriting delete manifests in RewriteTablePathUtil (#15155)
- **PR/Issue**：#15155

## 总体目的

修复 `RewriteTablePathUtil` 在重写删除清单（delete manifests）时的 bug。当清单文件中包含多个位置删除条目（position delete entries）时，原有的代码直接将同一个 `file` 对象添加到 `toRewrite` 列表中，但由于 `file` 对象在循环中被修改（位置和路径被更新），导致后续条目会覆盖前面的条目，最终 `toRewrite` 列表中只包含最后一个条目的状态。需要通过 `file.copy()` 创建副本来保留每个条目的独立状态。

## 如何达成设计目的

1. 将 `result.toRewrite().add(file)` 改为 `result.toRewrite().add(file.copy())`，确保每个删除文件的副本被独立存储
2. 新增参数化测试验证包含多个位置删除条目的清单文件能正确重写，期望 `toRewrite` 列表大小为 2

## 修改详情

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java` (+1/-1 lines)

**修改目的**：修复位置删除条目在 toRewrite 列表中被覆盖的问题。

**工作逻辑**：
- 在处理 POSITION_DELETES 分支时，`file` 对象在每次循环迭代中会被修改（更新 location 等属性）
- 原有代码 `result.toRewrite().add(file)` 添加的是对象引用，后续迭代修改 `file` 会影响已添加的条目
- 改为 `result.toRewrite().add(file.copy())` 创建副本，确保每个条目的状态独立保存

### `core/src/test/java/org/apache/iceberg/TestRewriteTablePathUtil.java` (+38/-2 lines)

**修改目的**：新增测试验证多位置删除条目的重写。

**工作逻辑**：
- 将测试类改为继承 `TestBase` 并使用 `@ExtendWith(ParameterizedTestExtension.class)` 以支持 format version 参数化
- 新增 `testRewritingMultiplePositionDeleteEntriesWithinManifestFile` 测试
- 使用 `assumeThat(formatVersion).isGreaterThanOrEqualTo(2)` 确保仅在 format v2 下运行（删除文件仅 v2 支持）
- 写入包含 FILE_A_DELETES 和 FILE_B_DELETES 两个位置删除条目的清单文件
- 调用 `RewriteTablePathUtil.rewriteDeleteManifest` 重写清单
- 验证 `deleteFileRewriteResult.toRewrite()` 大小为 2（修复前为 1）

## 总结

本提交修复了 `RewriteTablePathUtil` 重写删除清单时的对象引用共享 bug。通过 `file.copy()` 创建副本，确保清单中多个位置删除条目的状态不会被后续迭代覆盖。新增的参数化测试验证了包含两个删除条目的清单能正确重写。
