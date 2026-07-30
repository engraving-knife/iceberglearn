# 提交 3792：Core: Fix flaky test by ensuring generateContentLength returns positive value (#16539)

## 提交信息

- **序号**：3792 / 4088
- **哈希**：141e5be17c56a6c13d560e122f2ca46fab2dea14
- **短哈希**：141e5be17
- **日期**：2026-05-27 21:12:50 -0600
- **作者**：sanshi
- **提交说明**：Core: Fix flaky test by ensuring generateContentLength returns positive value (#16539)
- **PR/Issue**：#16539

## 总体目的

这个提交修复了一个 flaky 测试，问题出在 `FileGenerationUtil` 的 `generateFileSize()` 和 `generateContentLength()` 方法可能返回 0。这两个方法使用 `random().nextInt(bound)` 生成随机值，`nextInt(bound)` 返回 0 到 bound-1 之间的值（包含 0）。

当生成的文件大小或内容长度为 0 时，依赖这些值的测试可能因为除零、空文件处理等逻辑而失败。修复方式是确保返回值至少为 1。

## 如何达成设计目的

在两个方法的随机数生成中加 1，确保返回值始终为正数。

## 修改详情

### `core/src/test/java/org/apache/iceberg/FileGenerationUtil.java` (+2/-2 lines)

**修改目的**：确保生成的文件大小和内容长度始终为正数。

**工作逻辑**：
```java
// 修改前
private static long generateFileSize() {
  return random().nextInt(50_000);  // 可能返回 0
}
private static long generateContentLength() {
  return random().nextInt(10_000);  // 可能返回 0
}

// 修改后
private static long generateFileSize() {
  return 1 + random().nextInt(50_000);  // 返回 1 到 50_000
}
private static long generateContentLength() {
  return 1 + random().nextInt(10_000);  // 返回 1 到 10_000
}
```

`nextInt(bound)` 返回 [0, bound) 范围的值，加 1 后范围变为 [1, bound]，确保不会返回 0。

## 总结

这个提交修复了 `FileGenerationUtil` 中随机生成文件大小和内容长度可能返回 0 导致的 flaky 测试问题。通过在随机值上加 1 确保返回值始终为正数，消除了因 0 值导致的边界条件失败。这是一个简洁但有效的 flaky 测试修复。
