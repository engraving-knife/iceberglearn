# 提交 0945：DynFields, DynMethods code cleanup (#10543)

## 提交信息

- **序号**：0945 / 4088
- **哈希**：5a562bb078d30ac0633c2960e2558780cb183e89
- **短哈希**：5a562bb07
- **日期**：2024-07-17 13:49:02 +0200
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：DynFields, DynMethods code cleanup  (#10543)
- **PR/Issue**：#10543

## 总体目的

`common` 模块中的 `DynFields` 与 `DynMethods` 是 Iceberg 用于反射式访问字段与方法的工具类（动态字段/动态方法封装），在跨版本 Spark/Hive 等模块适配不同 API 时被广泛使用。随着项目演进，这两个类中存在一些不再被外部使用、计划在 2.0.0 移除的方法，以及一些 IDE 警告级别的代码瑕疵（如未使用的 `throws` 声明、应标记为 `final` 的字段等）。

本提交（PR #10543）是一次纯代码清理，目标是：
1. 为计划在 2.0.0 移除的若干公共 API 添加 `@Deprecated` 注解并补充 `since 1.7.0, will be removed in 2.0.0` 的 Javadoc，正式开启弃用周期，提醒下游尽早迁移；
2. 移除不再需要的 `throws` 声明以消除 IDE 警告；
3. 为后续进一步移除这些未使用方法做准备（"Prepare for removal unused DynFields, DynMethods methods"）。

这是 Iceberg 在迈向 2.0.0 过程中对内部反射工具类 API 收敛的常规清理步骤。

## 如何达成设计目的

实现思路分三部分（与提交说明中的三个子提交一一对应）：
- **移除未使用的 `throws` 声明**：在 `DynMethods` 的 `NOOP` 匿名子类中，`invokeChecked` 重写不再抛出任何受检异常，因此去掉方法签名上的 `throws Exception`，消除 IDE 提示的"未使用 throws 声明"警告；
- **为待弃用 API 加注解**：对 `DynFields.buildStaticChecked()`、`DynMethods.UnboundMethod.invokeChecked()`、`DynMethods.Builder.ctorImpl(Class, Class...)`、`DynMethods.Builder.ctorImpl(String, Class...)` 四个公共方法添加 `@Deprecated` 注解与 Javadoc 弃用说明，标注自 1.7.0 起弃用、2.0.0 移除，其中 `invokeChecked` 还附注"will become private"（即将改为私有）；
- 上述改动不改变任何运行时行为，仅为后续删除做铺垫。

## 修改详情

### `common/src/main/java/org/apache/iceberg/common/DynFields.java`

**修改目的**：将 `Builder.buildStaticChecked()` 标记为弃用，告知调用方该方法自 1.7.0 起弃用、2.0.0 移除。

**工作逻辑**：在 `buildStaticChecked()` 方法上新增 `@Deprecated` 注解，并在 Javadoc 中追加 `@deprecated since 1.7.0, will be removed in 2.0.0` 一行。方法体不变。

```diff
      * @throws NoSuchFieldException if no implementation was found
+     * @deprecated since 1.7.0, will be removed in 2.0.0
      */
+    @Deprecated
     public <T> StaticField<T> buildStaticChecked() throws NoSuchFieldException {
```

### `common/src/main/java/org/apache/iceberg/common/DynMethods.java`

**修改目的**：对 `UnboundMethod.invokeChecked()` 与 `Builder.ctorImpl(...)` 两个重载添加弃用标记，并移除 `NOOP` 匿名类中 `invokeChecked` 重写签名上未使用的 `throws Exception`。

**工作逻辑**：
- 在 `invokeChecked(Object, Object...)` 上添加 `@Deprecated // will become private` 与 Javadoc `@deprecated since 1.7.0, will be removed in 2.0.0`，表明该方法将先变为私有再被移除；
- 在 `Builder.ctorImpl(Class<?>, Class<?>...)` 与 `Builder.ctorImpl(String, Class<?>...)` 两个重载上分别添加同样的 `@Deprecated` 注解与 Javadoc；
- `NOOP` 静态字段的匿名 `UnboundMethod` 子类重写 `invokeChecked` 时不再 `throws Exception`（因为其实现只 `return null;`，确实不会抛出受检异常），故移除签名中的 `throws Exception` 以消除 IDE 警告：

```diff
         new UnboundMethod(null, "NOOP") {
           @Override
-          public <R> R invokeChecked(Object target, Object... args) throws Exception {
+          public <R> R invokeChecked(Object target, Object... args) {
             return null;
           }
```

这些改动不影响现有调用语义，只是为后续版本移除这些公共 API 提供迁移窗口。

## 小结

- **成效**：完成 `DynFields`/`DynMethods` 中四个待移除公共方法的弃用标注，并清除了一个 IDE 警告，为 2.0.0 的 API 收敛做准备。
- **影响范围**：仅 `common` 模块的 `DynFields.java`、`DynMethods.java` 两个文件，共 9 行新增、1 行删除，无功能行为变更。
- **回迁到 1.4.x 的注意事项**：本提交是为 1.7.0/2.0.0 路线图做准备的弃用标记。**1.4.x 分支不应回迁此弃用注解**——因为 1.4.x 早于 1.7.0，标注"since 1.7.0"会与 1.4.x 的版本时间线不符，反而误导下游。若 1.4.x 仅需消除 IDE 警告（`NOOP.invokeChecked` 的 `throws` 移除），可单独回迁那一小处；但 `@Deprecated` 注解部分不建议回迁。
