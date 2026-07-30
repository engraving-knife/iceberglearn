# 提交 0936：Nit: fix/suppress false-positivie errorprone warning (#10690)

## 提交信息

- **序号**：0936 / 4088
- **哈希**：5479545e900a604e10eb52312e65bca18fc3ceb4
- **短哈希**：5479545e9
- **日期**：2024-07-15（Mon Jul 15 21:38:03 2024 +0200）
- **作者**：Robert Stupp \<snazy@snazy.de\>
- **提交说明**：Nit: fix/suppress false-positivie errorprone warning (#10690)
- **PR/Issue**：#10690

## 总体目的

本提交处理一个 Error Prone 静态分析工具的误报告警。Iceberg 的 Hive3 集成模块（`iceberg-hive3`）在 `IcebergTimestampObjectInspectorHive3` 类的 `getPrimitiveJavaObject` 方法中，将 Iceberg 内部的 `java.time.LocalDateTime` 转换为 Hive 的 `org.apache.hadoop.hive.common.type.Timestamp`。该转换需要保留纳秒精度，因此代码先用 `time.toInstant(ZoneOffset.UTC).toEpochMilli()` 构造毫秒级时间戳，再通过 `timestamp.setNanos(time.getNano())` 把纳秒补回。

Error Prone 的 `JavaLocalDateTimeGetNano` 检查会对 `LocalDateTime.getNano()` 的调用告警，提示纳秒值在经过毫秒级转换时可能丢失。然而在本场景中，开发者已经显式地用 `setNanos` 单独保留了纳秒分量，告警并不适用，属于误报（false-positive）。持续出现的误报告警会污染构建输出、干扰真正的代码审查，因此需要将其抑制。

## 如何达成设计目的

实现方式非常简洁：在 `getPrimitiveJavaObject` 方法上添加 `@SuppressWarnings("JavaLocalDateTimeGetNano")` 注解，明确告知 Error Prone 该处告警已被审查确认属于误报，无需再次报告。这是处理 Error Prone 误报的标准做法——当确信代码逻辑正确且告警不适用时，用注解局部抑制，而非关闭全局检查或改动正确逻辑。

不调整任何业务逻辑，因为现有写法（毫秒 + 纳秒分别处理）正是保留纳秒精度的正确方式。

## 修改详情

### `hive3/src/main/java/org/apache/iceberg/mr/hive/serde/objectinspector/IcebergTimestampObjectInspectorHive3.java`

**修改目的**：抑制 Error Prone 对 `getPrimitiveJavaObject` 方法中 `LocalDateTime.getNano()` 调用的误报告警。

**工作逻辑**：在 `getPrimitiveJavaObject` 方法的 `@Override` 注解之后新增一行 `@SuppressWarnings("JavaLocalDateTimeGetNano")`：

```diff
   @Override
+  @SuppressWarnings("JavaLocalDateTimeGetNano")
   public Timestamp getPrimitiveJavaObject(Object o) {
     if (o == null) {
       return null;
     }
     LocalDateTime time = (LocalDateTime) o;
     Timestamp timestamp = Timestamp.ofEpochMilli(time.toInstant(ZoneOffset.UTC).toEpochMilli());
     timestamp.setNanos(time.getNano());
     return timestamp;
   }
```

被抑制的方法逻辑保持不变：先用毫秒构造 Hive `Timestamp`，再用 `setNanos` 补回纳秒，从而完整保留 Iceberg 时间戳的纳秒精度。`JavaLocalDateTimeGetNano` 检查本意是提醒"通过 `getNano()` 取到的纳秒在毫秒级 API 中可能被截断"，但此处纳秒被独立保存，告警不成立，故用 `@SuppressWarnings` 局部关闭。

## 小结

- **成效**：消除了 `IcebergTimestampObjectInspectorHive3.getPrimitiveJavaObject` 上的 Error Prone 误报告警（`JavaLocalDateTimeGetNano`），使构建输出更干净，避免误报干扰真正的静态分析问题排查。
- **影响范围**：仅 `hive3` 模块下一个文件、一行注解新增；不改变任何运行时行为，对功能零影响。
- **回迁到 1.4.x 的注意事项**：回迁风险极低，仅是注解层面的调整。是否回迁取决于 1.4.x 分支是否启用了 Error Prone 且同样出现该误报。若 1.4.x 构建未启用该检查或未报告此告警，则无需回迁；若启用且报错，则应回迁以保持构建通过。该注解需要 Error Prone 在编译 classpath 中可用，否则普通 `javac` 会忽略未知 warning 名称，不会造成编译失败。
