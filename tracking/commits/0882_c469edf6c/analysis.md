# 提交 0882：Common: DynConstructors cleanup (#10542)

## 提交信息

- **序号**：0882 / 4088
- **哈希**：c469edf6cd241e848d3926a07d978cc4b5994cc5
- **短哈希**：c469edf6c
- **日期**：2024-06-28（Fri Jun 28 11:32:05 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Common: DynConstructors cleanup (#10542)
- **PR/Issue**：#10542

## 总体目的

`DynConstructors` 是 Iceberg `common` 模块中用于通过反射动态调用构造器的工具类，承担底层实现类的动态加载职责（例如根据类名反射构造对象、绕过访问控制调用私有构造器等）。该类历史上引入了若干技术债，本提交对其进行一次集中清理，目标是消除已废弃的 Guava API 使用、修正泛型与可变参数的潜在陷阱，并为后续版本（1.6.0 / 1.7.0）移除旧 API 铺路。

具体来说，本提交主要解决三类问题：

1. **Guava 已废弃 API 的替换**。`Throwables.propagateIfInstanceOf` 与 `Throwables.propagate` 自 Guava 20 起已被标记废弃，建议使用 `Throwables.throwIfInstanceOf` 并显式抛出 `new RuntimeException(...)`。原代码继续使用废弃 API 会在新版本 Guava 中带来兼容性风险。
2. **varargs 重载歧义**。`DynConstructors.Builder.hiddenImpl(Class<?>...)` 与 `hiddenImpl(Class<T>, Class<?>...)` 同时存在，前者作为可变参数方法实际上永远会被后者在调用时抢占（编译器优先匹配更具体的重载），导致 varargs 版本成为死代码。由于仓库内并无任何调用，作者选择将其标记 `@Deprecated`（计划 1.7.0 移除）而非"修复"，避免引入 API 不兼容。
3. **泛型与不可变性的清理**。例如 `ctor` 字段泛型化为 `Ctor<?>` 后在 `build()`/`buildChecked()` 处显式强转；`problems`、`hidden` 等可变字段加上 `final`；裸类型 `Class` 改为 `Class<?>`；`Ctor<T>` 钻石运算符简化等。这些修改不影响行为，但提升类型安全与可读性。

此外，本次还首次为该类补充了单元测试 `TestDynConstructors`，覆盖 `impl`、`newInstance`、错误实现触发 `ClassCastException` 等关键路径。

## 如何达成设计目的

实现方式为纯重构式清理：

- 将 Guava `Throwables.propagateIfInstanceOf` / `Throwables.propagate` 调用点逐个替换为推荐写法（`throwIfInstanceOf` + 显式 `throw new RuntimeException(cause)`），保持原有异常传播语义不变；
- 对两处 `@Deprecated`（`getConstructedClass` 与 varargs 版 `hiddenImpl`）添加 Javadoc，标注 `since 1.6.0, will be removed in 1.7.0`，给下游明确的弃用预期；
- 借助 IDE 重构能力，统一泛型、`final`、钻石运算符等代码风格，不改逻辑；
- 新增 `TestDynConstructors`，使用 AssertJ + JUnit 5，覆盖正常构造与类型不匹配场景，为清理后的行为提供回归保护。

## 修改详情

### `common/src/main/java/org/apache/iceberg/common/DynConstructors.java`

**修改目的**：替换 Guava 废弃 API、标记死代码为 `@Deprecated`、统一泛型与不可变字段，完成代码清理。

**工作逻辑**：

1. **异常处理改写**：
   - `Ctor.newInstanceChecked` 中 `InvocationTargetException` 的处理：将 `Throwables.propagateIfInstanceOf(e.getCause(), Exception.class)`、`Throwables.propagateIfInstanceOf(e.getCause(), RuntimeException.class)`、`throw Throwables.propagate(e.getCause())` 三行依次替换为 `Throwables.throwIfInstanceOf(...)` 两次调用 + `throw new RuntimeException(e.getCause())`。`throwIfInstanceOf` 会当 cause 是指定类型时直接抛出，否则返回，最终用 `RuntimeException` 兜底包裹，与原 `propagate` 语义等价。
   - `Ctor.newInstance` 中同样把 `propagateIfInstanceOf(e, RuntimeException.class)` 改为 `throwIfInstanceOf`，`throw Throwables.propagate(e)` 改为 `throw new RuntimeException(e.getCause())`。注意此处原 `propagate(e)` 包裹的是 `e` 本身，新代码改用 `e.getCause()`，这是清理中实际上对语义做了细微统一（与 `newInstanceChecked` 中对 cause 的处理保持一致）。

2. **API 标记弃用**：
   - `Ctor.getConstructedClass()` 加 `@Deprecated` + Javadoc，计划 1.7.0 移除。
   - `Builder.hiddenImpl(Class<?>... types)`（varargs 版本）加 `@Deprecated` + Javadoc，说明它与 `hiddenImpl(Class, Class...)` 冲突，建议改用 `builder(Class)`。注释明确因 varargs 无法实际调用、且无使用方，故弃用而非修复。

3. **泛型与 final 清理**：
   - `Builder.ctor` 由 `Ctor` 裸类型改为 `Ctor<?>`，对应 `build()` / `buildChecked()` 返回处改用显式 `(Ctor<C>) ctor` 强转（同时保留方法级 `@SuppressWarnings("unchecked")`）。
   - `Builder.problems` 加 `final`（构造时初始化，不再被重新赋值）。
   - `MakeAccessible.hidden` 字段加 `final`。
   - `hiddenImpl(String, Class<?>...)` 内的裸 `Class targetClass = Class.forName(...)` 改为 `Class<?> targetClass`。
   - `new Ctor<T>(...)` 简化为 `new Ctor<>(...)`，移除多余的 `@SuppressWarnings("unchecked")`。

### `common/src/test/java/org/apache/iceberg/common/TestDynConstructors.java`

**修改目的**：新建测试类，为 `DynConstructors` 的核心行为提供回归测试，覆盖清理后的 API 路径。

**工作逻辑**：包含 4 个用例，使用 AssertJ + JUnit 5：

- `testImplNewInstance`：通过 `DynConstructors.builder().impl(MyClass.class).buildChecked()` 直接以实现类构造 `Ctor`，断言 `newInstance()` 返回 `MyClass` 实例。
- `testInterfaceImplNewInstance`：通过 `builder(MyInterface.class).impl("...$MyClass")` 以类名方式加载实现，验证按接口类型构造的反射路径可用。
- `testInterfaceWrongImplString`：故意传入不实现 `MyInterface` 的 `MyUnrelatedClass` 类名，断言 `newInstance()` 抛出 `ClassCastException`，并用 `hasMessage` 校验消息内容。注释中留下 TODO 指出该错误本应在 `buildChecked` 阶段就抛出。
- `testInterfaceWrongImplClass`：同上但通过 `impl(MyUnrelatedClass.class)` 传入，同样期望 `ClassCastException`。

测试类内定义了 `MyInterface` 接口、`MyClass`（实现接口）与 `MyUnrelatedClass`（不实现接口）三个嵌套类型作为测试夹具。

## 小结

- **成效**：完成 `DynConstructors` 的代码清理，移除对 Guava 废弃 API 的依赖、标记两处不再推荐使用的 API 为 `@Deprecated`（计划 1.7.0 删除）、统一泛型与不可变字段风格，并补充首个针对该类的单元测试，提升可维护性与类型安全。
- **影响范围**：仅 `common` 模块的 `DynConstructors.java` 与新增测试 `TestDynConstructors.java`，无对外 API 行为变更（`@Deprecated` 仅是标记，编译期不报错），不影响运行时语义。
- **回迁到 1.4.x 的注意事项**：可作为可选的代码质量改进回迁，风险较低。需注意 1.4.x 分支 Guava 版本若较旧，`Throwables.throwIfInstanceOf` 是否可用（该方法自 Guava 1.0 起即存在，应无问题）。新增的 `@Deprecated` 标记不破坏二进制兼容性。若 1.4.x 维护策略保守，亦可仅回迁测试、不回迁生产代码改动。
