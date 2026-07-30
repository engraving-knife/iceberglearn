# 提交 1162：Kafka Connect: separate CI workflow (#11075)

## 提交信息

- **序号**：1162 / 4088
- **哈希**：40ffcb9ad42491c75e4c306fec1486e50849d03c
- **短哈希**：40ffcb9ad
- **日期**：2024-09-17（Tue Sep 17 23:52:16 2024 -0700）
- **作者**：Bryan Keller <bryanck@gmail.com>
- **提交说明**：Kafka Connect: separate CI workflow (#11075)
- **PR/Issue**：#11075

## 总体目的

Iceberg 仓库此前只有一个面向核心模块的 Java CI 流水线，并未给 Kafka Connect 模块单独的 CI 工作流。随着 Kafka Connect 子模块（`kafka-connect-events`、`kafka-connect`、`kafka-connect-runtime`）逐步成型，需要把它纳入独立的 CI 流水线，与其他引擎（Spark/Flink/Hive/Delta）一样：

1. 单独触发构建与测试，避免在主 Java CI 中混入 Kafka Connect 测试，造成单条流水线过长、定位失败困难。
2. 在 PR 上当仅改动无关模块（如 `spark/**`、`flink/**`、`docs/**` 等）时跳过 Kafka Connect CI，节省 CI 资源。
3. 引入与 Spark/Flink/Hive 一致的"版本矩阵"机制（`kafkaVersions`），以便后续支持多个 Kafka 版本构建，目前默认值为 `3`。
4. 在 `settings.gradle` 中把 Kafka Connect 模块的 include 改为按 `kafkaVersions` 条件加载，让无关 CI（如纯 Spark/Flink 任务）能通过 `-DkafkaVersions=` 跳过该模块的装载，加快构建并避免依赖冲突。
5. 顺手修复了 Kafka Connect 集成测试的初始化方式，使其更稳健（懒加载单例 + 启动超时），适应独立 CI 环境的运行节奏。

## 如何达成设计目的

通过 5 类改动协同完成：

1. **新增工作流文件** `.github/workflows/kafka-connect-ci.yml`：定义 `Kafka Connect CI` 作业，在 push 到 `main`/`0.*`/`1.*`/`2.*` 分支或打 `apache-iceberg-**` tag 时触发；在 PR 上则按 `paths-ignore` 仅当改动命中 Kafka Connect 相关路径时才运行。作业以矩阵方式在 JVM 11/17/21 上执行 `:iceberg-kafka-connect:iceberg-kafka-connect-events:check`、`:iceberg-kafka-connect:iceberg-kafka-connect:check`、`:iceberg-kafka-connect:iceberg-kafka-connect-runtime:check`，并显式传 `-DkafkaVersions=3`。
2. **在 `gradle.properties` 中新增 Kafka 版本配置**：`defaultKafkaVersions=3` 与 `knownKafkaVersions=3`，对齐 Spark/Flink/Hive 的版本管理风格。
3. **改造 `settings.gradle`**：移除顶部的 `include 'kafka-connect'` 与无条件子模块 include，改为当 `kafkaVersions` 包含 `3` 时才 include 整个 Kafka Connect 模块组；同时复用 `knownKafkaVersions`/`kafkaVersions` 校验机制，并在 `-DallModules` 时自动注入 `knownKafkaVersions`。
4. **更新其他 CI 工作流**（`delta-conversion-ci.yml`、`flink-ci.yml`、`hive-ci.yml`、`java-ci.yml`、`spark-ci.yml`、`publish-snapshot.yml`、`dev/stage-binaries.sh`）：在所有 `./gradlew` 调用中追加 `-DkafkaVersions=`（即不加载 Kafka Connect），并在各 `paths-ignore` 列表中加入 `.github/workflows/kafka-connect-ci.yml` 与 `kafka-connect/**`，使这些流水线既不会构建 Kafka Connect 模块，也不会因 Kafka Connect 路径变更而触发。
5. **修复 Kafka Connect 集成测试**：把 `TestContext` 从饿汉式单例（`public static final INSTANCE`）改为线程安全的懒汉式 `instance()` 工厂方法，并为 docker-compose 容器添加 2 分钟启动超时；`IntegrationTestBase` 改为在 `@BeforeAll` 中获取 `TestContext` 实例，统一字段初始化时序，并应用 JDK 11+ 的 `var`/菱形语法清理。

## 修改详情

### `.github/workflows/kafka-connect-ci.yml`（新增）

**修改目的**：为 Kafka Connect 模块提供独立 CI 流水线。

**工作逻辑**：完整 105 行新文件，包含：
- 触发条件：push 到主线/维护分支/发布 tag；PR 上仅在改动未命中 `paths-ignore` 时触发。
- `concurrency` 配置以 `${{ github.workflow }}-${{ github.ref }}` 分组，PR 上取消进行中的旧任务。
- `kafka-connect-tests` 作业在 `ubuntu-22.04` 上以 JVM `[11, 17, 21]` 矩阵运行；先 checkout、setup Java（zulu 发行版）、缓存 `~/.gradle/caches` 与 `~/.gradle/wrapper`，再修复 `/etc/hosts` 中的主机名解析（沿用 Iceberg CI 惯例），随后执行：

  ```bash
  ./gradlew -DsparkVersions= -DhiveVersions= -DflinkVersions= -DkafkaVersions=3 \
    :iceberg-kafka-connect:iceberg-kafka-connect-events:check \
    :iceberg-kafka-connect:iceberg-kafka-connect:check \
    :iceberg-kafka-connect:iceberg-kafka-connect-runtime:check \
    -Pquick=true -x javadoc
  ```

  注意通过 `-DsparkVersions= -DhiveVersions= -DflinkVersions=` 显式排除其他引擎模块，避免无关依赖加载。
- 失败时上传 `**/build/testlogs` 作为 artifact 便于排障。

### `gradle.properties`

**修改目的**：声明 Kafka 版本矩阵的默认值与已知值。

**工作逻辑**：在已有的 `defaultSparkVersions`/`knownSparkVersions` 之后追加：

```properties
systemProp.defaultKafkaVersions=3
systemProp.knownKafkaVersions=3
```

### `settings.gradle`

**修改目的**：将 Kafka Connect 模块由无条件 include 改为按 `kafkaVersions` 条件加载。

**工作逻辑**：
- 删除原顶部的 `include 'kafka-connect'` 与 `project(':kafka-connect').name = 'iceberg-kafka-connect'`。
- 在 `if (null != System.getProperty("allModules"))` 块内新增 `System.setProperty("kafkaVersions", System.getProperty("knownKafkaVersions"))`，使 `-DallModules` 一键打开全部模块时自动启用 Kafka Connect。
- 新增与 Spark 版本管理同款的 Kafka 版本解析逻辑：

  ```groovy
  List<String> knownKafkaVersions = System.getProperty("knownKafkaVersions").split(",")
  String kafkaVersionsString = System.getProperty("kafkaVersions") != null ? System.getProperty("kafkaVersions") : System.getProperty("defaultKafkaVersions")
  List<String> kafkaVersions = kafkaVersionsString != null && !kafkaVersionsString.isEmpty() ? kafkaVersionsString.split(",") : []

  if (!knownKafkaVersions.containsAll(kafkaVersions)) {
    throw new GradleException("Found unsupported Kafka versions: " + (kafkaVersions - knownKafkaVersions))
  }
  ```

- 把原先 3 个 Kafka Connect 子模块（`kafka-connect-events`、`kafka-connect`、`kafka-connect-runtime`）的无条件 include 包裹进 `if (kafkaVersions.contains("3")) { ... }` 块内，只有当 `kafkaVersions` 含 `3` 时才装载。

### `.github/workflows/delta-conversion-ci.yml`、`flink-ci.yml`、`hive-ci.yml`、`java-ci.yml`、`spark-ci.yml`

**修改目的**：让其他 CI 流水线不再构建 Kafka Connect，且不会因 Kafka Connect 路径变更而触发。

**工作逻辑**：对每个文件做两件事：
- 在 `paths-ignore` 列表中加入 `.github/workflows/kafka-connect-ci.yml` 与 `kafka-connect/**`。
- 在 `./gradlew` 调用中追加 `-DkafkaVersions=`（空字符串表示不加载 Kafka Connect），避免因 `settings.gradle` 改造后默认行为变化而误装载。

### `.github/workflows/publish-snapshot.yml`

**修改目的**：发布快照时显式启用 Kafka Connect 模块以发布其产物。

**工作逻辑**：把发布命令中的 `-DhiveVersions=` 之后追加 `-DkafkaVersions=3`：

```yaml
./gradlew -DflinkVersions= -DsparkVersions=3.3,3.4,3.5 -DscalaVersion=2.13 -DkafkaVersions=3 -DhiveVersions= publishApachePublicationToMavenRepository ...
```

### `dev/stage-binaries.sh`

**修改目的**：发布脚本同步支持 Kafka Connect 模块。

**工作逻辑**：新增 `KAFKA_VERSIONS=3` 变量并在 `./gradlew -Prelease ...` 命令中追加 `-DkafkaVersions=$KAFKA_VERSIONS`。

### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/TestContext.java`

**修改目的**：将测试上下文改为线程安全懒加载单例，并加上容器启动超时。

**工作逻辑**：
- 移除 `public static final TestContext INSTANCE = new TestContext();`，改为 `private static volatile TestContext instance;`。
- 新增 `public static synchronized TestContext instance()`，双重判空后构造实例。
- 在 `ComposeContainer` 构造时调用 `.withStartupTimeout(Duration.ofMinutes(2))`，避免容器启动缓慢时无限等待。
- 新增 `import java.time.Duration;`。

这一改动既保证了在独立 CI 中第一次调用时才启动 docker-compose 容器（更可控），也通过 `synchronized` + `volatile` 防止多线程并发场景下重复启动容器。

### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/IntegrationTestBase.java`

**修改目的**：适配 `TestContext` 单例改型，规范字段初始化时序。

**工作逻辑**：
- 把 `private final TestContext context = TestContext.INSTANCE;` 改为 `private static TestContext context;`。
- 新增 `@BeforeAll public static void baseBeforeAll()`，在其中调用 `context = TestContext.instance();`，确保所有测试方法共享同一上下文实例且在类加载阶段完成容器初始化。
- `@BeforeEach baseBefore()` 中改为通过 `this.` 显式赋值 `catalog`、`producer`、`admin`，并去掉原先方法末尾的空行。
- 将匿名内部类 `new Condition<String>() {...}` 简化为 `new Condition<>() {...}`（Java 11+ 菱形语法）。
- 新增 `import org.junit.jupiter.api.BeforeAll;`。

## 小结

- **成效**：Kafka Connect 模块从此拥有与 Spark/Flink/Hive/Delta 同级的独立 CI 流水线，矩阵覆盖 JVM 11/17/21；其他流水线通过 `-DkafkaVersions=` 与 `paths-ignore` 解耦，构建更快、定位更清晰；发布与二进制打包脚本同步覆盖 Kafka Connect 产物；集成测试的单例与启动超时修复提升了独立 CI 环境下的稳定性。
- **影响范围**：CI 配置（6 个 workflow 文件 + `stage-binaries.sh`）、Gradle 配置（`gradle.properties`、`settings.gradle`）、Kafka Connect 集成测试基础设施（2 个 Java 文件）。涉及构建系统与测试基础设施的较大重构，但不改产品运行时行为。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 是已发布的维护分支，Kafka Connect 模块在 1.4.x 发布时点尚未独立成单独 CI，且本提交依赖 `settings.gradle` 的版本矩阵机制；若 1.4.x 分支不存在 Kafka Connect 模块（或不存在该目录），**不需要也无法回迁** CI 工作流与 `settings.gradle` 改造。
  - 如果 1.4.x 分支已经包含 Kafka Connect 模块，回迁时必须**整体回迁**：`gradle.properties` 的版本声明、`settings.gradle` 的条件 include、6 个 CI workflow 的 `paths-ignore` 与 `-DkafkaVersions=` 参数、`publish-snapshot.yml`、`dev/stage-binaries.sh`，以及 `TestContext`/`IntegrationTestBase` 的测试基础设施修复，缺一会导致 CI 漏跑或构建报错。
  - 单纯的测试基础设施修复（`TestContext` 懒加载 + 启动超时）可单独回迁到 1.4.x 以提升集成测试稳定性，前提是 1.4.x 已有 Kafka Connect 集成测试代码。
  - 总体建议：作为 CI/构建基础设施改动，**默认不需要回迁到 1.4.x**，除非 1.4.x 分支明确需要继续发布新的 Kafka Connect 产物并希望复用 main 的 CI 拆分。
