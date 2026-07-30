# 提交 0948：Common: Update the version in deprecation messages (#10715)

## 提交信息

- **序号**：0948 / 4088
- **哈希**：229d8f6fcd109e6c8943ea7cbb41dab746c6d0ed
- **短哈希**：229d8f6fc
- **日期**：2024-07-18 02:35:21 +0200
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Common: Update the version in deprecation messages (#10715)
- **PR/Issue**：#10715

## 总体目的

这是对前一个提交（#10543，即本批次 0944 号提交）的紧跟修正。在 #10543 中，作者为 `DynFields`/`DynMethods` 中四个待移除的公共方法添加了 `@Deprecated` 注解，并将弃用版本标注为 `since 1.7.0, will be removed in 2.0.0`。但该弃用标注实际合入的时间点处于 1.6.0 发布周期内（2024-07，1.6.0 尚未发布），因此"自 1.7.0 起弃用"在版本时间线上不准确——应当是"自 1.6.0 起弃用"，并按更紧凑的节奏在下一个 minor 版本 1.7.0 移除，而非拖到 2.0.0。

本提交（PR #10715）修正这四处 Javadoc 中的版本号，使弃用声明与实际引入版本一致，并提前移除时间，给下游一个更明确的迁移窗口。

## 如何达成设计目的

实现方式是逐处替换 Javadoc 中的版本字符串：将 `since 1.7.0, will be removed in 2.0.0` 改为 `since 1.6.0, will be removed in 1.7.0`。涉及 `DynFields.buildStaticChecked()` 一处与 `DynMethods` 三处（`invokeChecked`、`ctorImpl(Class, Class...)`、`ctorImpl(String, Class...)`）。`@Deprecated` 注解本身不动，仅修订注解上方 Javadoc 中的版本文本。

## 修改详情

### `common/src/main/java/org/apache/iceberg/common/DynFields.java`

**修改目的**：将 `buildStaticChecked()` 弃用 Javadoc 中的版本号由 `1.7.0/2.0.0` 修正为 `1.6.0/1.7.0`。

**工作逻辑**：

```diff
-     * @deprecated since 1.7.0, will be removed in 2.0.0
+     * @deprecated since 1.6.0, will be removed in 1.7.0
      */
     @Deprecated
     public <T> StaticField<T> buildStaticChecked() throws NoSuchFieldException {
```

### `common/src/main/java/org/apache/iceberg/common/DynMethods.java`

**修改目的**：将 `UnboundMethod.invokeChecked()` 与 `Builder.ctorImpl(...)` 两个重载共三处弃用 Javadoc 的版本号统一修正为 `1.6.0/1.7.0`。

**工作逻辑**：三处均为相同的字符串替换：

```diff
-    /** @deprecated since 1.7.0, will be removed in 2.0.0 */
+    /** @deprecated since 1.6.0, will be removed in 1.7.0 */
     @Deprecated // will become private
     ...
     public <R> R invokeChecked(Object target, Object... args) throws Exception {
```

以及两个 `ctorImpl` 重载上方同样的 `@deprecated` 行替换。`@Deprecated` 注解、`// will become private` 注释、方法体均保持不变。

## 小结

- **成效**：修正了 #10543 中弃用版本号与实际合入版本不符的问题，将四处弃用声明统一为"自 1.6.0 起弃用、1.7.0 移除"，给下游更明确的迁移预期。
- **影响范围**：仅 `common` 模块的 `DynFields.java`、`DynMethods.java` 两个文件，共 4 行字符串替换，无功能行为变更。
- **回迁到 1.4.x 的注意事项**：本提交是对 1.6.0 路线图弃用标注的版本号修正。**1.4.x 分支不应回迁**——1.4.x 早于 1.6.0，且 1.4.x 分支并未引入 #10543 的 `@Deprecated` 注解（参见 0944 号提交的回迁建议），因此本修正在 1.4.x 上没有可作用的对象。1.4.x 应保留其原有的弃用策略，不引入针对 1.6.0/1.7.0 的版本声明。
