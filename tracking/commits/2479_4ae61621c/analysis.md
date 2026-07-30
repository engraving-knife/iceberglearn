# 提交 2479：core: remove duplicate lines (#13770)

## 提交信息

- **序号**：2479 / 4088
- **哈希**：4ae61621c1b5e7f245961b3de48d6d8a3856d012
- **短哈希**：4ae61621c
- **日期**：2025-08-09 12:07:08 -0700
- **作者**：Huaxin Gao
- **提交说明**：core: remove duplicate lines (#13770)
- **PR/Issue**：#13770

## 总体目的

该提交移除了 `ExponentialHttpRequestRetryStrategy` 中重复的 `SC_SERVICE_UNAVAILABLE (503)` 条目，包括 Javadoc 中的重复列表项和实际代码中 `ImmutableSet.of(...)` 中的重复常量。

`ExponentialHttpRequestRetryStrategy` 类定义了在哪些 HTTP 状态码下进行重试。在代码和 Javadoc 中，`HttpStatus.SC_SERVICE_UNAVAILABLE (503)` 被重复列出了两次。虽然 `ImmutableSet` 会自动去重，重复元素不会导致运行时错误，但重复的代码条目是明显的疏忽，影响代码整洁度，也可能让阅读者产生困惑。该提交清理了这一重复。

## 如何达成设计目的

分别在两处移除重复的 `SC_SERVICE_UNAVAILABLE` 条目：
1. Javadoc 注释中移除多余的 `<li>SC_SERVICE_UNAVAILABLE (503)</li>` 行。
2. `ImmutableSet.of(...)` 构造调用中移除多余的 `HttpStatus.SC_SERVICE_UNAVAILABLE` 参数。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ExponentialHttpRequestRetryStrategy.java` (+0/-2 lines)

**修改目的**：移除重复的 SC_SERVICE_UNAVAILABLE 条目。

**工作逻辑**：

1. Javadoc 部分（第 73 行附近）：移除重复的 `<li>SC_SERVICE_UNAVAILABLE (503)</li>`。Javadoc 中列出了会触发重试的 HTTP 状态码，503 被列了两次，移除一次。

2. 代码部分（第 97 行附近）：`ImmutableSet.of(...)` 中移除重复的 `HttpStatus.SC_SERVICE_UNAVAILABLE`。该集合定义了可重试的状态码，移除重复后集合内容不变（ImmutableSet 本身会去重），仅清理代码。

## 总结

这是一个简单的代码清理提交，移除了 `ExponentialHttpRequestRetryStrategy` 中重复的 `SC_SERVICE_UNAVAILABLE (503)` 条目。该提交不影响任何运行时行为（ImmutableSet 会自动去重），仅提升代码整洁度和可读性。
