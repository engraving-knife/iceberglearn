# 提交 3319：API: Use correct method name in exception message (#15453)

## 提交信息

- **序号**：3319 / 4088
- **哈希**：2e94af5d1f7d71848518348c81d858b2209c3751
- **短哈希**：2e94af5d1
- **日期**：2026-02-26
- **作者**：Xiang Li
- **提交说明**：API: Use correct method name in exception message (#15453)
- **PR/Issue**：#15453

## 总体目的

`RowDelta` 是 Iceberg API 中描述"行级变更"（同时添加数据文件与删除文件）的更新接口。其中 `removeRows(DataFile file)` 是一个带有默认实现的接口方法：默认抛出 `UnsupportedOperationException`，提示某实现类未实现该方法。然而该默认实现抛出的异常消息文本写错了——它声称 `getClass().getName() + " does not implement deleteFile"`，但当前方法名是 `removeRows`，并不存在名为 `deleteFile` 的方法。

这是一个典型的复制粘贴遗留错误：很可能该方法早期曾叫 `deleteFile`，重命名为 `removeRows` 后异常消息没有同步更新（同接口中的兄弟方法 `removeDeletes` 的默认实现则正确地写了 `"does not implement removeDeletes"`）。错误的消息会误导开发者：当某个 `RowDelta` 实现类未覆盖 `removeRows` 时，用户看到的报错指向了一个根本不存在的 `deleteFile` 方法，无法定位真正需要实现的方法，增加排障成本。本提交把消息中的 `deleteFile` 更正为 `removeRows`，使异常文本与实际方法签名一致。

## 如何达成设计目的

改动极其局部，仅修改 `RowDelta.java` 中 `removeRows` 默认实现里抛出异常的字符串字面量，把 `does not implement deleteFile` 改为 `does not implement removeRows`，与该方法名及同接口中 `removeDeletes` 的写法保持一致。无逻辑变更，不涉及行为或 API 签名变化。

## 修改详情

### `api/src/main/java/org/apache/iceberg/RowDelta.java` (+1/-1 lines)

**修改目的**：修正 `removeRows` 默认实现中异常消息里的方法名。

**工作逻辑**：
`removeRows(DataFile file)` 是接口默认方法，用于从表中移除一个数据文件，默认实现直接抛出 `UnsupportedOperationException` 以提示子类未实现该能力。修改前消息为 `getClass().getName() + " does not implement deleteFile"`，修改后为 `getClass().getName() + " does not implement removeRows"`。该类中相邻的 `removeDeletes(DeleteFile deletes)` 默认实现写的是 `"does not implement removeDeletes"`，本次更正使二者风格统一、消息与各自方法名准确对应，便于在实现类缺少该方法时给出正确指引。

## 总结

本次提交修复了 `RowDelta.removeRows` 默认异常消息中的方法名笔误（`deleteFile` -> `removeRows`），使报错文本与实际未实现的方法名一致，避免误导开发者。改动虽小但提升了 API 错误信息的准确性与可维护性。
