# 提交 0587：Flink: Bump minor versions

## 提交信息

- **序号**：0587 / 4088
- **哈希**：3891b48733fd7f98138c0e8988fdccb36af06ce9
- **短哈希**：3891b4873
- **日期**：2024-03-12 12:30:22 +0530
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Flink: Bump minor versions (#9875)
- **PR/Issue**：#9875

## 总体目的

Apache Iceberg 的 Flink 集成按 Flink 大版本分别维护独立子模块（`flink/v1.15`、`flink/v1.16`、`flink/v1.17`、`flink/v1.18`），每个子模块绑定一个具体的 Flink 依赖版本。本提交的目的是把当前仍在维护范围内的两个 Flink 版本的补丁号（patch 号）升级到最新发布的补丁版本：

- Flink 1.16 系列：`1.16.2` → `1.16.3`
- Flink 1.17 系列：`1.17.1` → `1.17.2`

Flink 的 patch 版本发布通常包含 bug 修复、安全补丁与小幅兼容性改进，不引入 API 破坏性变更。Iceberg 的 Flink 连接器高度依赖 Flink 的稳定 API（`DataStream`、`Table API`、`flink-table-planner` 等），因此跟随上游补丁版本升级可以让用户在不变更 Iceberg 集成代码的前提下，获得 Flink 上游已修复的问题修正。这是依赖维护中"跟随上游 patch 发布"的常规动作，也是 1.5.0 发版前后依赖对齐工作的一部分。

需要特别说明的是，本提交未触及 `flink118`（保持在 `1.18.1`）与 `flink115`（在 1.5.0 发布说明中已标记 "Remove Flink 1.15"，由其他提交移除）——仅升级 1.16/1.17 这两个仍活跃的中间版本。

## 如何达成设计目的

改动分为两条互相关联的路径，二者必须同步修改才能保持测试通过：

1. **依赖版本声明**：在 `gradle/libs.versions.toml` 的 `[versions]` 区块修改 `flink116`、`flink117` 两个版本变量的值。这两个变量在 `[libraries]` 区块被多个 `flink116-*`、`flink117-*` 库别名通过 `version.ref` 引用，是整个 Flink 1.16/1.17 模块依赖版本的"单一来源"。改一处即可让所有引用该 ref 的依赖（`flink-avro`、`flink-streaming-java`、`flink-table-api-java-bridge`、`flink-runtime`、`flink-test-utils`、`flink-connector-base`、`flink-connector-files`、`flink-connector-test-utils`、`flink-core`、`flink-metrics-dropwizard`、`flink-test-utils-junit`，以及通过字符串拼接构造的 `flink-table-planner_${scalaVersion}`）一并升级到新版本。

2. **版本断言测试**：在 `flink/v1.16/flink/.../TestFlinkPackage.java` 与 `flink/v1.17/flink/.../TestFlinkPackage.java` 中同步修改 `testVersion()` 的期望字符串。这两个测试用于校验运行时实际加载的 Flink 版本与构建声明一致，依赖升级后必须同步更新期望值，否则测试会失败。

由于使用 Gradle 的"rich version"机制（`strictly = "<version>"`），版本被强约束为单一精确值，任何传递依赖若要求不同版本都会导致构建失败，从而保证 Flink 各子构件版本严格一致（Flink 的运行时与编译期 API 必须版本对齐，否则会出现 `NoSuchMethodError` 等运行时故障）。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Flink 1.16/1.17 的版本变量从旧 patch 号升级到新 patch 号，作为依赖版本的单一来源。

**工作逻辑**：

文件顶部注释解释了使用 rich version（`strictly`）的动机：避免 Dependabot / Renovate 等自动化依赖工具把不同版本的同一库错误地对齐。`strictly = "<version>"` 是 Gradle 的强版本约束，表示"必须严格使用此版本，禁止任何其它版本（包括传递依赖引入的）"。

```toml
flink116 = { strictly = "1.16.2"}   # 旧
flink117 = { strictly = "1.17.1"}   # 旧
flink118 =  { strictly = "1.18.1"}  # 未改动
```

改为：

```toml
flink116 = { strictly = "1.16.3"}   # 新
flink117 = { strictly = "1.17.2"}   # 新
flink118 =  { strictly = "1.18.1"}  # 未改动
```

这两个变量是 `[libraries]` 区块中所有 `flink116-*` / `flink117-*` 别名的 `version.ref`。引用链路为：`build.gradle`（如 `flink/v1.16/build.gradle`）中的 `compileOnly libs.flink116.streaming.java` → 解析为 `org.apache.flink:flink-streaming-java:1.16.3`。改一处变量即可让该模块下全部 Flink 依赖（编译期 `compileOnly`、测试期 `testImplementation`、集成测试 `integrationImplementation`、运行时 `implementation`）整体升级到新版本。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java`

**修改目的**：同步更新 `testVersion()` 中的期望版本字符串，使其与升级后的 Flink 1.16.3 匹配。

**工作逻辑**：

`TestFlinkPackage.testVersion()` 通过 `Assert.assertEquals("1.16.2", FlinkPackage.version())` 校验运行时实际加载的 Flink 版本。`FlinkPackage.version()` 的实现位于同包的 `FlinkPackage.java`，核心是：

```java
private static final String VERSION = DataStream.class.getPackage().getImplementationVersion();
```

即从 Flink 核心 API 类 `org.apache.flink.streaming.api.datastream.DataStream` 所在 JAR 的 manifest 中读取 `Implementation-Version` 属性。该属性由 Flink 发布流程写入 JAR 元数据，值等于 Flink 的发布版本号。

当 `libs.versions.toml` 把 `flink116` 升到 `1.16.3` 后，测试运行时 classpath 上加载的 `flink-streaming-java-1.16.3.jar` 的 manifest 中 `Implementation-Version` 即为 `1.16.3`，因此 `FlinkPackage.version()` 返回 `"1.16.3"`。期望字符串必须从 `"1.16.2"` 改为 `"1.16.3"`，否则断言失败。

测试类上的注释明确写到："This unit test would need to be adjusted as new Flink version is supported."——这正是该测试的设计意图：作为版本升级的"刹车点"，强迫升级者显式确认版本号变更，避免版本被静默修改。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java`

**修改目的**：与 v1.16 同理，把 v1.17 模块的 `testVersion()` 期望值从 `"1.17.1"` 改为 `"1.17.2"`。

**工作逻辑**：与 v1.16 完全对称。`FlinkPackage.version()` 从 `DataStream` JAR manifest 读取版本，依赖升级到 `1.17.2` 后，期望字符串必须同步改为 `"1.17.2"`。

## 小结

本提交把 Iceberg Flink 集成的两个维护版本（1.16、1.17）跟随上游升级到最新 patch 发布（1.16.3、1.17.2），改动极小（4 行 / 4 处），但跨越 3 个文件，必须整体合入才能保证构建与测试一致。`strictly` rich version 约束确保所有 Flink 子构件版本严格对齐，避免传递依赖引入版本漂移；`TestFlinkPackage` 测试则作为版本升级的显式确认机制，把"版本号变更"从隐式的依赖解析提升为必须人为更新的断言点。

回迁到 1.4.x 分支的注意事项：当前 1.4.x 分支的 `gradle/libs.versions.toml` 使用了**与 main 不同的 rich version 风格**——不是单一精确 `strictly = "1.16.2"`，而是版本区间加偏好：

```toml
flink116 = { strictly = "[1.16, 1.17[", prefer = "1.16.2"}
flink117 = { strictly = "[1.17, 1.18[", prefer = "1.17.1"}
```

即 1.4.x 允许 1.16.x 区间内任意 patch（`[1.16, 1.17[`），但偏好 `1.16.2`。若要把本提交的版本升级效果回迁到 1.4.x，正确的做法是修改 `prefer` 字段（`prefer = "1.16.3"`、`prefer = "1.17.2"`），保持 `strictly` 的区间语义不变，而不是把 `strictly` 改成 main 上的单一精确值——后者会改变 1.4.x 的版本策略。同时 `TestFlinkPackage.java` 的期望字符串也必须同步更新（1.4.x 上对应测试同样断言 `"1.16.2"` / `"1.17.1"`）。考虑到 1.16/1.17 的 patch 升级通常包含安全修复，**建议回迁**，但需按 1.4.x 的版本风格正确落地。
