# 提交 1990：Core: Update RewriteFiles tests to test against V3

## 提交信息

- **序号**：1990 / 4088
- **哈希**：97d1107d60b42d29306c663f23f869fc1d439e6b
- **短哈希**：97d1107d6
- **日期**：2025-04-12 09:39:34 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Update RewriteFiles tests to test against V3 (#12777)
- **PR/Issue**：#12777

## 总体目的

本提交更新 `TestRewriteFiles` 测试类，使其能够针对所有表格式版本（包括 V3）进行测试。Iceberg 表格式有 V1、V2、V3 三个版本，此前的测试仅硬编码覆盖 V1 和 V2 两个版本，未覆盖最新的 V3 格式。

随着 Iceberg V3 规范的推进，确保核心的文件重写（RewriteFiles）操作在 V3 下也能正确工作非常重要。RewriteFiles 是用于替换表中数据文件和删除文件的核心操作，涉及数据压缩（compaction）等维护场景。通过扩展测试参数化范围，可以自动验证 V3 下 rewrite 的行为，包括删除文件（delete files）的重写逻辑。

此外，本提交还将测试中对删除文件的引用从静态常量 `FILE_A_DELETES`/`FILE_B_DELETES` 改为方法调用 `fileADeletes()`/`fileBDeletes()`，以避免在多版本参数化测试中共享可变状态导致的测试间干扰。

## 如何达成设计目的

主要通过两个方面的修改达成目的：

1. **扩展参数化版本范围**：将 `parameters()` 方法从硬编码的 V1/V2 改为使用 `TestHelpers.ALL_VERSIONS` 常量动态生成所有版本的测试参数组合，确保未来新增版本时测试自动覆盖。

2. **消除测试间共享状态**：将删除文件的引用从静态字段改为实例方法调用，确保每次测试使用独立的文件对象，避免在参数化测试的不同运行之间产生状态污染。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestRewriteFiles.java` (修改, +37/-36 lines)

**修改目的**：扩展测试覆盖到 V3 版本，并消除测试间共享状态。

**工作逻辑**：

1. **参数化方法改造**：
   原代码使用 `Arrays.asList` 硬编码了 4 组参数（V1/V2 × main/testBranch）。新代码改为：
   ```java
   return Ints.asList(TestHelpers.ALL_VERSIONS).stream()
       .flatMap(i -> Stream.of(new Object[] {i, "main"}, new Object[] {i, "branch"}))
       .collect(Collectors.toList());
   ```
   这样会为每个版本生成 main 和 branch 两个分支的测试组合，自动覆盖所有版本（含 V3）。同时将分支名 "testBranch" 简化为 "branch"。

2. **删除文件引用改造**：
   将所有对静态常量 `FILE_A_DELETES`、`FILE_B_DELETES`、`FILE_A2_DELETES` 的引用替换为方法调用 `fileADeletes()`、`fileBDeletes()`、`fileA2Deletes()`。这些方法（来自父类 TestBase）每次调用返回独立的文件对象，避免不同参数化运行之间共享同一静态文件对象导致的状态污染。

3. **错误消息改造**：
   将硬编码的错误消息字符串 `"Missing required files to delete: /path/to/data-a-deletes.parquet"` 改为使用 `String.format("Missing required files to delete: %s", fileADeletes().location())` 动态拼接，使消息与实际使用的文件路径一致。

## 总结

本提交通过将 `TestRewriteFiles` 的参数化范围扩展到所有格式版本（含 V3），并消除测试间共享的静态文件状态，确保 RewriteFiles 核心操作在 V3 表格式下得到充分测试覆盖，提升了测试的健壮性和未来版本的可扩展性。
