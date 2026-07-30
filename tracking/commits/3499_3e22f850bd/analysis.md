# 提交 3499：Docs: Fix missing semicolons in Java API Quickstart imports (#15864)

## 提交信息

- **序号**：3499 / 4088
- **哈希**：3e22f850bd21f3f8f4ecb340a5d4ffe468fb1ced
- **短哈希**：3e22f850bd
- **日期**：2026-04-01 21:32:29 -0700
- **作者**：Atsuo Yamaguchi
- **提交说明**：Docs: Fix missing semicolons in Java API Quickstart imports (#15864)
- **PR/Issue**：#15864

## 总体目的

修复 Java API 快速入门文档中 import 语句缺失分号的问题。文档中的 Java 代码示例 import 语句缺少分号结尾，这在 Java 语法中是必须的，会导致新用户复制代码时编译失败。

## 如何达成设计目的

在两行 import 语句末尾添加分号。

## 修改详情

### `docs/docs/java-api-quickstart.md` (+2/-2 lines)

**修改目的**：修复 import 语句缺少分号。

**工作逻辑**：
```diff
-import java.util.HashMap
-import java.util.Map
+import java.util.HashMap;
+import java.util.Map;
```

## 总结

文档修复提交，为 Java API 快速入门文档中的两行 import 语句添加缺失的分号，确保代码示例语法正确。
