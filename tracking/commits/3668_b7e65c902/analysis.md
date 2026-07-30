# 提交 3668：Core: Add test to validate we can't delete map value during schema evolution (#15767)

## 提交信息

- **序号**：3668 / 4088
- **哈希**：b7e65c902936141666d56acad62c0eacf6aea3c5
- **短哈希**：b7e65c902
- **日期**：2026-05-08 17:16:52 +0200
- **作者**：Mukund Thakur
- **提交说明**：Core: Add test to validate we can't delete map value during schema evolution (#15767)
- **PR/Issue**：#15767

## 总体目的

这个提交新增了一个测试用例，验证在 schema 演进过程中不能删除 map 类型的 value 字段。

Iceberg 的 schema 演进支持删除列，但对 map 类型的 key 和 value 有特殊限制：不能单独删除 map 的 key 或 value 字段，因为它们是 map 类型的结构组成部分。此前 `TestSchemaUpdate` 中已有测试验证不能删除 map key（`testDeleteMapKey`），但缺少验证不能删除 map value 的测试。本提交补充了这一测试覆盖，确保 `SchemaUpdate.deleteColumn("locations.value")` 会抛出 `IllegalArgumentException`，消息以 "Cannot delete value type from map" 开头。

## 如何达成设计目的

在 `TestSchemaUpdate` 测试类中新增 `testDeleteMapValue` 测试方法，尝试通过 `SchemaUpdate.deleteColumn("locations.value")` 删除 map value 字段，断言抛出 `IllegalArgumentException`。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestSchemaUpdate.java` (+11 lines)

**修改目的**：新增删除 map value 的测试。

**工作逻辑**：
```java
@Test
public void testDeleteMapValue() {
  assertThatThrownBy(
          () ->
              new SchemaUpdate(SCHEMA, SCHEMA_LAST_COLUMN_ID)
                  .deleteColumn("locations.value")
                  .apply())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageStartingWith("Cannot delete value type from map");
}
```
测试使用已有的 `SCHEMA`（含 `locations` map 字段），尝试删除其 value，验证抛出正确的异常。

## 总结

这是一个纯测试提交，补充了 schema 演进中"不能删除 map value"的测试覆盖，与已有的"不能删除 map key"测试形成对称。这确保了 `SchemaUpdate` 对 map 类型结构完整性的保护机制被正确测试。
