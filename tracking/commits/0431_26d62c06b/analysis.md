# 提交 0431：Flink: Added error handling and default logic for Flink version detection

## 提交信息

- **序号**：0431
- **哈希**：26d62c06bce6f50d46f51860f0014bba2f9538d2
- **短哈希**：26d62c06b
- **日期**：2024-01-31 08:11:20 -0500
- **作者**：Geoffrey Jacoby <gjacoby@apache.org>
- **提交说明**：Flink: Added error handling and default logic for Flink version detection (#9452)
- **PR/Issue**：#9452

## 总体目的

Iceberg 的 Flink 集成模块通过反射从 Flink 核心 jar 包中读取版本号，以便在不同 Flink 版本上启用/禁用相应的能力或行为。原有实现非常脆弱——`FlinkPackage` 类用一个静态 final 字段直接持有 `DataStream.class.getPackage().getImplementationVersion()` 的结果。一旦反射返回 null（例如类路径中存在多个 `DataStream` 类副本、shading/uber jar 场景、或 manifest 缺失版本属性），整个静态字段就是 null，调用方拿到的版本信息完全无法用于后续条件判断，甚至可能在调用 `version().startsWith(...)` 等逻辑时抛出 NPE，进而导致 Flink 作业或 Iceberg 集成在初始化阶段崩溃。

这个提交针对上述问题做了一层防御性的封装：将版本检测从"一次反射、永不重试、不做兜底"改造为"延迟检测 + 异常吞掉 + 显式默认值"的模式，并提供测试钩子以便能在单测中模拟反射失败的场景。设计意图是把"是否能从 jar 中拿到版本"和"业务逻辑能否继续运行"这两件事解耦——版本探测失败不应让整个 Flink 集成不可用，而是用 `FLINK-UNKNOWN-VERSION` 这个 sentinel 值显式表达"未知版本"，让上层逻辑能感知并自行决定降级策略（比如关闭依赖具体版本的特性、记录告警日志等），而不是直接挂掉。

该修复同时应用到了 `flink/v1.16`、`flink/v1.17`、`flink/v1.18` 三个版本的集成模块，三份代码完全相同，说明 Iceberg 维护多 Flink 版本分支时采用的是直接复制代码而不是抽象公共模块的策略，因此同一修复需要逐分支应用。

## 如何达成设计目的

实现路径核心是三点：将静态 final 字段改为 `AtomicReference<String>` 以支持延迟赋值和测试期注入；将真正的反射逻辑抽取到 `versionFromJar()` 方法便于在单测中 mock；在 `version()` 方法中包裹 try-catch，无论反射抛异常还是返回 null 都统一退化为 `FLINK-UNKNOWN-VERSION`，并通过 `AtomicReference` 做缓存避免重复反射。同时新增 `setVersion()` 钩子（标注 `@VisibleForTesting`）和 `FLINK_UNKNOWN_VERSION` 常量公开导出，方便上层调用方识别"未知版本"状态。

## 修改详情

### flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/util/FlinkPackage.java

**修改目的**：Flink 1.16 集成模块中版本检测的入口类，需要为反射失败和 null 结果增加兜底逻辑。

**工作逻辑**：
- 把原本 `private static final String VERSION = DataStream.class.getPackage().getImplementationVersion();` 改成 `private static final AtomicReference<String> VERSION = new AtomicReference<>();`，由"类加载即求值"变为"首次调用 `version()` 时求值"。
- 新增公共常量 `FLINK_UNKNOWN_VERSION = "FLINK-UNKNOWN-VERSION"`，作为版本探测失败的统一 sentinel 值。
- `version()` 方法采用"先看缓存、未命中则检测、检测失败兜底、最后写回缓存"的逻辑：先检查 `VERSION.get()` 是否为 null；若为 null 调用 `versionFromJar()`，捕获所有 `Exception`（注释中举例了 shading 场景下 `DataStream` 类在 classpath 出现多次会导致反射拿不到实现版本），返回 null 时也兜底成 `FLINK_UNKNOWN_VERSION`；最终通过 `VERSION.set(detectedVersion)` 缓存结果，避免重复反射开销。
- 抽取 `versionFromJar()` 为 package-private 静态方法并标注 `@VisibleForTesting`，这样 Mockito 的 `mockStatic` 可以拦截该方法注入异常或 null 返回值来测试兜底逻辑。
- 抽取 `setVersion(String version)` 为测试钩子，用于在测试间重置缓存状态，避免上一次测试缓存的版本号影响下一次测试。

### flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java

**修改目的**：为新增的兜底逻辑补充单元测试。

**工作逻辑**：
- 新增 `testDefaultVersion()` 测试方法，覆盖两种探测失败路径：一是 `versionFromJar()` 抛 `RuntimeException`，二是返回 null。两种情况都断言 `FlinkPackage.version()` 返回 `FLINK_UNKNOWN_VERSION`。
- 由于反射失败在真实环境中难以稳定复现，测试用 `Mockito.mockStatic(FlinkPackage.class)` 在类级别 mock 静态方法：mock `versionFromJar()` 的行为，同时用 `thenCallRealMethod()` 让 `version()` 走真实代码，从而验证 `version()` 的异常处理和兜底逻辑。
- 每次测试前都调用 `FlinkPackage.setVersion(null)` 清空缓存，避免前序测试残留的版本值干扰当前断言。

### flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/util/FlinkPackage.java

**修改目的**：Flink 1.17 集成模块中的同名类，与 v1.16 版本代码完全一致，应用同样的兜底修复。

**工作逻辑**：与 v1.16 中的修改完全相同——`AtomicReference` 缓存、`FLINK_UNKNOWN_VERSION` 常量、try-catch 兜底、`versionFromJar()` 抽取、`setVersion()` 测试钩子。

### flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java

**修改目的**：Flink 1.17 集成模块对应的测试，新增 `testDefaultVersion()` 测试。

**工作逻辑**：与 v1.16 测试完全相同，唯一差别是原有的 `testVersion()` 断言期望值是 `"1.17.1"`（v1.16 是 `"1.16.2"`）。

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/util/FlinkPackage.java

**修改目的**：Flink 1.18 集成模块中的同名类，与 v1.16/v1.17 完全一致，应用同样的兜底修复。

**工作逻辑**：与 v1.16、v1.17 中的修改完全相同。

### flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java

**修改目的**：Flink 1.18 集成模块对应的测试，新增 `testDefaultVersion()` 测试。

**工作逻辑**：与 v1.16、v1.17 测试完全相同，唯一差别是 `testVersion()` 期望值是 `"1.18.1"`。

## 小结

这是一个典型的"防御性编程加固"提交：把原本依赖单次反射、不做任何异常处理的脆弱初始化改造为带兜底、可测试、缓存的版本探测机制。修复模式有几点值得借鉴：用 sentinel 常量而不是 null 表达"未知"状态，让调用方能显式区分；用 `AtomicReference` 实现"惰性初始化 + 缓存"而无需额外同步；用 `@VisibleForTesting` 配合 package-private 钩子方法支持 `mockStatic` 注入故障。同时该提交也反映出 Iceberg 多 Flink 版本维护的现状——同一份代码在 v1.16/v1.17/v1.18 三处逐字复制，这种模式虽然简单但增加了未来同步修复的成本。该修复对下游的影响是：在 shading、uber jar、classpath 冲突等导致反射拿不到版本的真实生产场景下，Iceberg 的 Flink 集成不再因 NPE 或 null 比较而崩溃，而是以 `FLINK-UNKNOWN-VERSION` 继续运行，把降级决策的主动权交还给上层。
