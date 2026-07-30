# 提交 1058：Core: Fix metadata table test to set partition to the right PartitionKey (#10925)

## 提交信息

- **序号**：1058 / 4088
- **哈希**：4b57cf8d930460251cfc2f9b4ef3ed90ac07e741
- **短哈希**：4b57cf8d9
- **日期**：2024-08-13 20:32:38 +0200
- **作者**：hsiang-c
- **提交说明**：Core: Fix metadata table test to set partition to the right PartitionKey (#10925)
- **PR/Issue**：#10925

## 总体目的

在 `TestMetadataTableFilters` 和 `TestMetadataTableScans` 这两个元数据表测试中，构建 `data11` 数据文件的分区键时存在变量名写错的 bug。代码声明了一个新的 `PartitionKey data11Key`，但随后却把值设置到了上一次使用的 `data10Key` 上，导致 `data11Key` 实际上从未被赋值（保持默认/空状态），而 `data10Key` 被错误地再次修改。这样 `data11` 文件用到的 `data11Key` 与测试预期不符，使测试要么意外通过（掩盖真实问题），要么产生不稳定的测试行为。

本提交将赋值目标修正为正确的 `data11Key`，使测试真正按照预期构造分区键，确保元数据表测试的有效性。

## 如何达成设计目的

逐处把测试代码中误写到 `data10Key` 上的 `set` 调用改为写到 `data11Key` 上，并修正一处注释中错误的 `data=0`（应为 `data=1`）。改动只涉及测试代码，不影响产品代码逻辑。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestMetadataTableFilters.java` (+3/-3 lines)

**修改目的**：修正 `data11Key` 分区键赋值目标错误的 bug。

**工作逻辑**：
该文件中有两处类似问题（对应两个不同测试方法）。

第一处（约 317 行附近）：
```java
PartitionKey data11Key = new PartitionKey(newSpec, table.schema());
- data10Key.set(1, 11);
+ data11Key.set(1, 11);
```
原代码声明了 `data11Key` 却把值设到了 `data10Key`，修正后正确设置到 `data11Key`。

第二处（约 465 行附近）：
```java
PartitionKey data11Key = new PartitionKey(newSpec, table.schema());
- data11Key.set(0, 1); // data=0
- data10Key.set(1, 11); // id=11
+ data11Key.set(0, 1); // data=1
+ data11Key.set(1, 11); // id=11
```
这里第一行赋值目标本来就是 `data11Key`，但注释写错为 `data=0`（实际设置 1 应是 `data=1`）；第二行赋值目标错误地写成了 `data10Key`，修正为 `data11Key`。修正后两个 set 调用都作用于 `data11Key`，注释也与实际值一致。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScans.java` (+2/-2 lines)

**修改目的**：修正同样存在的 `data11Key` 赋值目标错误。

**工作逻辑**：
```java
PartitionKey data11Key = new PartitionKey(newSpec, table.schema());
- data11Key.set(0, 1); // data=0
- data10Key.set(1, 11); // id=11
+ data11Key.set(0, 1); // data=1
+ data11Key.set(1, 11); // id=11
```
与上一文件第二处修改完全一致：修正赋值目标和注释，确保 `data11Key` 被正确构造。

## 总结

这是一次测试 bug 修复提交。原测试代码因变量名笔误导致 `data11` 文件的分区键未被正确设置，可能掩盖元数据表相关功能的真实行为。本提交把赋值目标修正到正确的 `data11Key` 并同步修正注释，使测试真正验证预期场景，提升了测试的可靠性和有效性。
