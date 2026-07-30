# 提交 1026：Aliyun: Replace assert usage with assertThat (#10880)

## 提交信息

- **序号**：1026 / 4088
- **哈希**：04c2533f1de5fdaf23b1dca8227a82d2b84b349d
- **短哈希**：04c2533f1
- **日期**：2024-08-05 20:40:26 +0200
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Aliyun: Replace assert usage with assertThat (#10880)
- **PR/Issue**：#10880

## 总体目的

Aliyun OSS 模块的测试辅助类 `AliyunOSSMockLocalStore` 是一个本地模拟 OSS 存储实现，用于在测试中模拟阿里云 OSS 的对象存储行为。该类中此前使用 Java 原生 `assert` 语句来校验文件/目录状态（如目录是否存在、文件是否删除成功等）。

使用原生 `assert` 存在两个问题：第一，`assert` 默认在 JVM 未启用 `-ea`（enable assertions）时会被完全忽略，校验形同虚设，可能在测试中遗漏问题；第二，`assert` 失败时抛出的是 `AssertionError`，错误信息不够友好、缺乏上下文，调试困难。Iceberg 测试体系统一使用 AssertJ（`assertThat`）作为断言库，能提供流式 API、丰富的失败描述和更好的可读性。

本提交将 `AliyunOSSMockLocalStore` 中的所有 `assert` 用法替换为 AssertJ 的 `assertThat`，使该测试类的断言始终生效且失败信息更清晰，与项目其它测试保持一致。

## 如何达成设计目的

新增 `import static org.assertj.core.api.Assertions.assertThat;`，然后将各处 `assert <表达式>` 改写为等价的 AssertJ 断言：
- 对于简单的存在性校验（`assert bucketDir.exists()`），直接改为 `assertThat(bucketDir).exists()`；
- 对于"存在或创建成功"这类"或"语义（`assert bucketDir.exists() || bucketDir.mkdirs()`），使用 AssertJ 的 `satisfiesAnyOf`，分别断言"存在"或"mkdirs() 为 true"两条分支之一成立；
- 对于"不存在或删除成功"（`assert !dataFile.exists() || dataFile.delete()`），同样用 `satisfiesAnyOf` 断言"不存在"或"delete() 为 true"。

这样既保留了原有的校验语义，又让断言在测试运行时强制生效，并能在失败时给出可读性强的描述。

## 修改详情

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/AliyunOSSMockLocalStore.java`

**修改目的**：将测试辅助类中的原生 `assert` 替换为 AssertJ 的 `assertThat`，确保断言始终生效且失败信息友好。

**工作逻辑**：改动涉及四处断言，分布于三个方法：

1. 新增导入：
```diff
+import static org.assertj.core.api.Assertions.assertThat;
```

2. `putObject`（保存对象时校验 bucket 目录存在或可创建）：
```diff
-    assert bucketDir.exists() || bucketDir.mkdirs();
+    assertThat(bucketDir)
+        .satisfiesAnyOf(
+            bucket -> assertThat(bucket).exists(), bucket -> assertThat(bucket.mkdirs()).isTrue());
```
原语义"目录已存在，或 mkdirs 成功创建"通过 `satisfiesAnyOf` 拆成两条互斥分支断言。

3. `deleteObject`（删除对象时校验 bucket 目录存在、数据/元数据文件不存在或删除成功）：
```diff
-    assert bucketDir.exists();
+    assertThat(bucketDir).exists();
...
-    assert !dataFile.exists() || dataFile.delete();
-    assert !metaFile.exists() || metaFile.delete();
+    assertThat(dataFile)
+        .satisfiesAnyOf(
+            file -> assertThat(file).doesNotExist(), file -> assertThat(file.delete()).isTrue());
+    assertThat(metaFile)
+        .satisfiesAnyOf(
+            file -> assertThat(file).doesNotExist(), file -> assertThat(file.delete()).isTrue());
```
对数据文件和元数据文件分别断言"不存在，或 delete() 成功"。

4. `getObjectMetadata`（获取元数据时校验 bucket 目录存在）：
```diff
-    assert bucketDir.exists();
+    assertThat(bucketDir).exists();
```

所有改写都保持了原有的校验意图，仅改变断言风格。

## 小结

- **成效**：消除 `AliyunOSSMockLocalStore` 中所有原生 `assert` 用法，改用 AssertJ 的 `assertThat`，使测试断言不再依赖 `-ea` 标志即始终生效，并在失败时提供更清晰的上下文信息，与项目整体测试规范一致。
- **影响范围**：仅 `aliyun/src/test/java/.../AliyunOSSMockLocalStore.java` 一个测试辅助类，1 个导入新增 + 4 处断言改写，不涉及生产代码。
- **回迁到 1.4.x 的注意事项**：属于测试代码质量改进，对运行时无影响，回迁风险极低。若 1.4.x 分支存在同样的 `assert` 用法且希望统一测试风格，可安全回迁；需确认 1.4.x 已引入 AssertJ 依赖（通常已具备）。回迁优先级低。
