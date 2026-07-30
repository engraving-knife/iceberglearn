# 提交 3401：Spark: Fix aggregate pushdown (#15070)

## 提交信息

- **序号**：3401 / 4088
- **哈希**：d9749048e38eb947883c5e65e05b12733d35b7d9
- **短哈希**：d9749048e3
- **日期**：2026-03-16 17:19:40 -0700
- **作者**：Vrishabh
- **提交说明**：Spark: Fix aggregate pushdown (#15070)
- **PR/Issue**：#15070

## 总体目的

修复 Spark 聚合下推（aggregate pushdown）在数据文件中包含 NaN 值时产生错误结果的问题。当列中存在 NaN 值时，Min 和 Max 聚合下推使用文件的上下界统计信息会得到错误结果，因为 NaN 的排序行为不确定（可能是 -NaN 或 +NaN）。需要在这种情况下禁用聚合下推，回退到正常计算路径。

## 如何达成设计目的

1. 在 `BoundAggregate` 基类中新增 `containsNan` 方法，通过检查文件的 `nanValueCounts` 判断是否存在 NaN 值
2. 在 `MaxAggregate.hasValue` 和 `MinAggregate.hasValue` 方法开头，先检查文件是否包含 NaN 值，若包含则返回 false（表示无法从元数据确定聚合值）
3. 新增测试验证当数据包含 NaN 时，max 和 min 聚合不被下推

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/BoundAggregate.java` (+5 lines)

**修改目的**：新增 `containsNan` 辅助方法供子类使用。

**工作逻辑**：
```java
boolean containsNan(DataFile file, int fieldId) {
    Long nanCount = safeGet(file.nanValueCounts(), fieldId);
    return nanCount != null && nanCount > 0;
}
```
通过检查文件级别的 NaN 值计数判断该字段是否包含 NaN 值。

### `api/src/main/java/org/apache/iceberg/expressions/MaxAggregate.java` (+4 lines)

**修改目的**：在 `hasValue` 方法中增加 NaN 检查，存在 NaN 时禁用下推。

**工作逻辑**：
- 在 `hasValue` 方法开头添加检查：`if (containsNan(file, fieldId)) { return false; }`
- 注释说明：当存在 NaN 值时无法从元数据确定最大值，因为可能是 -NaN 或 +NaN

### `api/src/main/java/org/apache/iceberg/expressions/MinAggregate.java` (+4 lines)

**修改目的**：在 `hasValue` 方法中增加 NaN 检查，存在 NaN 时禁用下推。

**工作逻辑**：与 MaxAggregate 相同的逻辑，在方法开头检查 NaN 值。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestAggregatePushDown.java` (+45 lines)

**修改目的**：新增 `testNanWithLowerAndUpperBoundMetrics` 测试。

**工作逻辑**：
- 创建包含 float 列的表，插入包含 NaN 值的数据
- 验证所有文件都包含 nan_value_count > 0，且有 lower_bound 和 upper_bound
- 通过 EXPLAIN 验证 max(data)、min(data)、count(data) 聚合未被下推
- 验证查询结果正确：count=7, max=NaN, min=1.0, count(data)=7

## 总结

本提交修复了聚合下推在 NaN 值存在时的正确性问题。通过在 Min/Max 聚合的 `hasValue` 检查中加入 NaN 值检测，确保当文件包含 NaN 时不使用元数据统计信息进行下推，而是回退到实际数据计算。这避免了因 NaN 排序不确定性导致的错误结果。
