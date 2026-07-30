# 提交 3374：Core: Rename tableIo to tableIO (#15582)

## 提交信息

- **序号**：3374 / 4088
- **哈希**：4b4eb38cf6dda7b43faeb40eb00aa5db424d2ecb
- **短哈希**：4b4eb38cf
- **日期**：2026-03-11
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Rename tableIo to tableIO (#15582)
- **PR/Issue**：#15582

## 总体目的

这是一个代码风格（命名规范）的清理提交。在 `RESTTableScan` 类中，表示表 FileIO 的字段名为 `tableIo`，其中 `Io` 的第二个字母应大写为 `IO`（因为 `IO` 是 Input/Output 的缩写，是 Iceberg 项目中通用的命名惯例，如 `FileIO`、`HadoopFileIO` 等）。`tableIo` 不符合该惯例，容易在阅读时产生困惑。

本次提交将 `RESTTableScan` 中的字段 `tableIo` 统一重命名为 `tableIO`，涉及字段声明、构造函数参数、赋值语句和方法返回值中的引用。这是一次纯命名重构，不改变任何功能逻辑。

## 如何达成设计目的

改动仅涉及一个文件 `RESTTableScan.java`，共 4 处替换：字段声明、构造函数参数名、构造函数中的赋值语句、以及 `io()` 方法中的返回表达式。所有改动都是将 `tableIo` 替换为 `tableIO`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+4/-4 lines)

**修改目的**：将 `tableIo` 重命名为 `tableIO` 以符合命名规范。

**工作逻辑**：
四处改动均为纯名称替换，逻辑不变：
1. 字段声明：`private final FileIO tableIo;` → `private final FileIO tableIO;`
2. 构造函数参数：`FileIO tableIo` → `FileIO tableIO`
3. 构造函数赋值：`this.tableIo = tableIo;` → `this.tableIO = tableIO;`
4. `io()` 方法返回值：`return null != fileIOForPlanId ? fileIOForPlanId : tableIo;` → `return null != fileIOForPlanId ? fileIOForPlanId : tableIO;`

## 总结

本次提交是一次纯命名重构，将 `RESTTableScan` 中的 `tableIo` 统一为 `tableIO`，使其与 Iceberg 项目中 `FileIO` 类相关的命名惯例保持一致。改动虽小但有助于代码可读性和一致性。
