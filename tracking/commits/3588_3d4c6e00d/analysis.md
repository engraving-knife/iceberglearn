# 提交 3588：test: add ns1/ns2 to RCK view test namespace purge list (#16050)

## 提交信息

- **序号**：3588 / 4088
- **哈希**：3d4c6e00d1864746d99791f160a64b70dbaaa6af
- **短哈希**：3d4c6e00d
- **日期**：2026-04-24 21:00:01 -0700
- **作者**：M Alvee
- **提交说明**：test: add ns1/ns2 to RCK view test namespace purge list (#16050)
- **PR/Issue**：#16050

## 总体目的

该提交将 `ns1`、`ns2` 和 `other_ns` 命名空间添加到 REST Catalog 兼容性套件（RCK, REST Catalog Kit）测试的命名空间清理列表中。RCK 测试在运行前会清理（purge）已知的测试命名空间，以确保测试环境的干净状态。之前清理列表仅包含 `ns` 和 `newdb` 两个命名空间，但视图测试中使用了 `ns1`、`ns2` 和 `other_ns` 等额外命名空间，这些命名空间在测试间未被清理，可能导致测试间的状态污染。

## 如何达成设计目的

在 `RCKUtils.java` 的 `TEST_NAMESPACES` 列表中添加 `ns1`、`ns2` 和 `other_ns` 命名空间。

## 修改详情

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RCKUtils.java` (+7/-1 lines)

**修改目的**：扩展测试命名空间清理列表。

**工作逻辑**：
```java
static final List<Namespace> TEST_NAMESPACES =
    List.of(
        Namespace.of("ns"),
        Namespace.of("newdb"),
        Namespace.of("ns1"),
        Namespace.of("ns2"),
        Namespace.of("other_ns"));
```
RCK 测试运行前会遍历此列表，删除这些命名空间中的所有表和视图，然后删除命名空间本身，确保测试从一个干净的状态开始。

## 总结

该提交扩展了 RCK 测试的命名空间清理列表，新增 `ns1`、`ns2` 和 `other_ns`，确保视图测试使用的命名空间在测试间被正确清理，防止测试状态污染。
