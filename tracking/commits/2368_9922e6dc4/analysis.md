# 提交 2368：Docs: Fix the default value of use_caching to false in the rewrite_manifests and add where parameter to rewrite_position_delete_files (#13549)

## 提交信息

- **序号**：2368 / 4088
- **哈希**：9922e6dc4c76c8c3d4ec396b3932b57f48a1d468
- **短哈希**：9922e6dc4
- **日期**：2025-07-17 11:56:09 -0700
- **作者**：Tom Tanaka
- **提交说明**：Docs: Fix the default value of use_caching to false in the rewrite_manifests and add where parameter to rewrite_position_delete_files (#13549)
- **PR/Issue**：#13549

## 总体目的

这个提交修复了 Spark 过程文档中的两处问题：一是修正 `rewrite_manifests` 过程中 `use_caching` 参数的默认值描述（从 `true` 改为 `false`），二是为 `rewrite_position_delete_files` 过程补充缺失的 `where` 参数说明。

背景与具体问题：
1. **use_caching 默认值错误**：`rewrite_manifests` 过程的 `use_caching` 参数文档中描述默认值为 `true`，但实际代码实现中默认值为 `false`。启用缓存会增加 executor 的内存占用，文档将默认值误写为 `true` 会误导用户以为缓存默认开启。
2. **where 参数缺失**：`rewrite_position_delete_files` 过程实际支持 `where` 参数（用于过滤要重写的文件），但文档的参数表中未列出该参数，用户无法从文档中了解此功能。
3. **示例更新**：原示例展示了禁用缓存的用法（`rewrite_manifests('db.sample', false)`），更新为展示按 `spec_id` 重写的用法，更实用。

## 如何达成设计目的

1. 修正 `use_caching` 的描述，将默认值改为 `false` 并补充内存占用说明。
2. 更新示例为按 `spec_id` 重写的用法。
3. 在 `rewrite_position_delete_files` 的参数表中新增 `where` 参数说明。

## 修改详情

### `docs/docs/spark-procedures.md` (+4/-3 lines)

**修改目的**：修正 use_caching 默认值描述、更新示例、补充 where 参数。

**工作逻辑**：三处改动：
- **use_caching 描述**：将 `Use Spark caching during operation (defaults to true)` 改为 `Use Spark caching during operation (defaults to false). Enabling caching can increase memory footprint on executors.`，修正默认值并补充内存影响说明。
- **示例更新**：将原展示禁用缓存的示例 `CALL catalog_name.system.rewrite_manifests('db.sample', false);` 改为展示按 spec_id 重写的示例 `CALL catalog_name.system.rewrite_manifests(table => 'db.sample', spec_id => 1);`，并将说明文字从"禁用缓存"改为"按分区规范 1 重写"。
- **where 参数**：在 `rewrite_position_delete_files` 的参数表中 `options` 行之后新增 `where` 行，类型为 string，描述为 `predicate as a string used for filtering the files.`，即用于过滤文件的谓词字符串。

## 总结

该提交修正了 `rewrite_manifests` 过程文档中 `use_caching` 默认值的错误描述（true → false）并补充了内存影响说明，更新了示例为更实用的 spec_id 用法，同时为 `rewrite_position_delete_files` 过程补充了缺失的 `where` 参数说明。纯文档修正，使文档与实际代码实现保持一致。
