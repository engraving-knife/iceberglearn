# 提交 1573 95424abc6 分析

## 提交信息
- 哈希：95424abc6ca98dfa164c5fc0ec055a9d2c3b7964
- 日期：2025-01-13（Mon Jan 13 20:10:51 2025 +0800）
- 作者：feng xiaohang <43104233+engraving-knife@users.noreply.github.com>
- 消息：API: Fix `sizeBytes` parameter of `ScanTask` (#11941)
- 共同作者：xiaosefeng <xiaosefeng@tencent.com>

## 总体目的

本提交修复 `ScanTask` 接口中 `sizeBytes()` 默认实现的数值错误。`ScanTask` 是 Iceberg API 模块定义的最小扫描任务单元接口，其 `sizeBytes()` 方法用于返回该任务需要读取的字节总数，供引擎在做扫描计划估算、split 大小计算、并行度决策时参考。

原默认实现为 `return 4 * 1028 * 1028; // 4 MB`，注释标注为"4 MB"，但 `1028 * 1028` 显然是手误：正确的 1 MB 应为 `1024 * 1024`。因此原默认值实际等于 `4 * 1056784 = 4227136` 字节约 4.03 MB，而并非准确的 4 MB（`4194304` 字节）。虽然偏差约 0.8%，对功能不构成致命影响，但作为公开 API 的默认值，它应该精确匹配注释声明，避免下游依赖该数值做精确换算时出现误差累积。

这是一个低风险但高价值的精确性修复，由社区贡献者发现并提交（PR #11941）。

## 如何达成设计目的

直接修改 `api/src/main/java/org/apache/iceberg/ScanTask.java` 中 `sizeBytes()` 方法的实现，把 `1028` 改为 `1024`，让计算结果严格等于注释声明的 4 MB。

### 修改详情

#### `api/src/main/java/org/apache/iceberg/ScanTask.java`

**修改目的**：修正 `sizeBytes()` 默认实现的字节数换算。

**工作逻辑**：
```java
   default long sizeBytes() {
-    return 4 * 1028 * 1028; // 4 MB
+    return 4 * 1024 * 1024; // 4 MB
   }
```

修改后默认返回 `4 * 1024 * 1024 = 4194304` 字节，正好等于 4 MiB。这是 Iceberg `ScanTask` 接口的默认实现，当具体扫描任务类型（如 `FileScanTask`、`ContentScanTask` 等）未覆盖该方法时使用。对已经覆盖 `sizeBytes()` 的实现（大多数真实扫描任务基于文件大小计算）无影响；只有走默认实现的场景（如某些聚合任务、占位任务）才会受到这次修正的影响，使其返回值与注释一致。

## 小结

- **成效**：`ScanTask.sizeBytes()` 的默认返回值从约 4.03 MB 修正为精确的 4 MB（4194304 字节），与代码注释一致，消除了公开 API 中的数值瑕疵。
- **影响范围**：仅 `api` 模块一个文件一行，属 API 层的精确性修复，对绝大多数真实扫描任务无行为影响（它们通常覆盖该方法）。
- **回迁到 1.4.x 的注意事项**：这是 API 模块的默认值修正，1.4.x 作为维护分支同样包含该默认实现，**建议回迁**，让 1.4.x 发布包也获得精确的默认值。回迁风险极低（仅数值更正，无逻辑变化），且不破坏二进制兼容性。
