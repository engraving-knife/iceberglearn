# 提交 0501：Upgrade Nessie to 0.77.1 (#9726)

## 提交信息

| 字段 | 内容 |
| --- | --- |
| 序号 | 0501 |
| 完整哈希 | 9bac5c490c368cbdf70e4e5ec1883e98d8ca903a |
| 短哈希 | 9bac5c490 |
| 日期 | 2024-02-14（Wed Feb 14 21:12:22 2024 +0100） |
| 作者 | Alexandre Dutra <adutra@users.noreply.github.com> |
| 说明 | Upgrade Nessie to 0.77.1 (#9726) |
| PR | #9726 |

提交统计：5 个文件修改，6 行新增，6 行删除。

涉及文件：`build.gradle`、`gradle/libs.versions.toml`、`nessie/src/test/java/org/apache/iceberg/nessie/BaseTestIceberg.java`、`nessie/src/test/java/org/apache/iceberg/nessie/TestNessieCatalog.java`、`nessie/src/test/java/org/apache/iceberg/nessie/TestNessieViewCatalog.java`

## 总体目的

本提交将 Iceberg 集成的 Nessie 版本从 `0.76.3` 升级到 `0.77.1`，并同步适配 Nessie 0.77.x 在测试支撑构件（test artifact）上的一次制品重组。Nessie 是 Iceberg 支持的可插拔目录实现之一，`iceberg-nessie` 模块通过 `nessie-client` 在生产代码侧与 Nessie 服务交互，而在测试侧借助 Nessie 提供的 JAX-RS 测试扩展（`nessie-jaxrs-testextension`）和版本化存储测试扩展（`nessie-versioned-storage-testextension`）在进程内启动一个内嵌的 Nessie 服务，以内存存储后端（`nessie-versioned-storage-inmemory`）作为底层持久化，从而无需外部服务即可端到端验证 `NessieCatalog`、`NessieViewCatalog` 的行为。

与同批次由 Dependabot 触发的纯版本号 bumps（如 0502 assertj、0503 tez、0504 awssdk、0505 arrow，均只改一行版本字符串）不同，本提交由人工（Alexandre Dutra，Nessie 核心贡献者）提交，原因在于 Nessie 0.77.x 对内存存储测试工厂的制品坐标与 Java 包路径做了不兼容调整：原先承载 `InmemoryBackendTestFactory` 的构件 `nessie-versioned-storage-inmemory` 及包 `org.projectnessie.versioned.storage.inmemory`，被拆分/重命名为测试专用构件 `nessie-versioned-storage-inmemory-tests` 及包 `org.projectnessie.versioned.storage.inmemorytests`。这一调整把"生产用内存存储实现"与"测试用内存存储工厂"分离，使生产构件不再被迫携带测试基础类。因此升级必须同时修改版本号、构件坐标别名、以及三处测试源文件中的 import 与库别名引用，否则编译会因找不到类而失败。

升级到 0.77.1 还跟进上游的缺陷修复与稳定性改进。由于 `iceberg-nessie` 仅在生产代码侧依赖 `nessie-client`（一个相对稳定的 API 构件），而所有受影响的存储测试扩展仅用于测试类路径，本次升级对 Iceberg 公共 API 与运行时产物零影响，回迁 1.4.x 的风险主要在于需完整移植全部 5 个文件的改动（漏改任一 import 都会导致 `:iceberg-nessie` 测试编译失败）。

## 如何达成设计目的

实现路径分两步：第一步在 `gradle/libs.versions.toml` 中把 `nessie` 版本引用从 `0.76.3` 改为 `0.77.1`，并把库别名 `nessie-versioned-storage-inmemory` 的构件坐标由 `nessie-versioned-storage-inmemory` 改为 `nessie-versioned-storage-inmemory-tests`（别名本身也同步重命名为 `nessie-versioned-storage-inmemory-tests` 以保持一致）；第二步把所有引用旧别名与旧包路径的代码——`build.gradle` 中 `:iceberg-nessie` 的依赖声明，以及三个测试类中的 `import` 语句——统一切换到新别名/新包，使编译与测试在新版 Nessie 下恢复正常。

## 修改详情

### `gradle/libs.versions.toml`

修改目的：提升 Nessie 版本并重命名内存存储测试构件的库别名与坐标。

工作逻辑：

- 版本声明区第 70 行附近：`nessie = "0.76.3"` → `nessie = "0.77.1"`。该版本变量被 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-testextension` 以及被重命名的内存存储构件共同引用，一处改动即把全部 Nessie 构件拉到 0.77.1。
- 库定义区第 188 行附近：
  - 旧：`nessie-versioned-storage-inmemory = { module = "org.projectnessie.nessie:nessie-versioned-storage-inmemory", version.ref = "nessie" }`
  - 新：`nessie-versioned-storage-inmemory-tests = { module = "org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests", version.ref = "nessie" }`
  - 即把别名 key 从 `nessie-versioned-storage-inmemory` 改为 `nessie-versioned-storage-inmemory-tests`，对应 Gradle 访问器由 `libs.nessie.versioned.storage.inmemory` 变为 `libs.nessie.versioned.storage.inmemory.tests`；同时 Maven 坐标 `module` 值也由 `...:nessie-versioned-storage-inmemory` 改为 `...:nessie-versioned-storage-inmemory-tests`。其余两个 Nessie 库别名（`nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-testextension`）坐标不变，仅随版本变量升到 0.77.1。

### `build.gradle`

修改目的：将 `:iceberg-nessie` 项目中对旧别名的引用切换为新别名。

工作逻辑：

- 第 919 行附近（`:iceberg-nessie` 的 `dependencies` 块内，受 `if (JavaVersion.current().isJava11Compatible())` 守卫的测试依赖区）：
  - 旧：`testImplementation libs.nessie.versioned.storage.inmemory`
  - 新：`testImplementation libs.nessie.versioned.storage.inmemory.tests`
  - 该依赖为测试类路径提供 `InmemoryBackendTestFactory`，是 `@NessieBackend(InmemoryBackendTestFactory.class)` 注解所需的后端工厂实现。Nessie 的进程内测试要求 Java 11+，因此此处置于 Java 11 兼容判断内；Java 8 下 `:iceberg-nessie` 测试整体被禁用（见同块第 868-877 行）。同块中 `nessie-jaxrs-testextension` 与 `nessie-versioned-storage-testextension` 的引用名不变，仅随版本升级。

### `nessie/src/test/java/org/apache/iceberg/nessie/BaseTestIceberg.java`

修改目的：把测试基类中对旧包路径的 import 切换到新包。

工作逻辑：

- 第 66 行：
  - 旧：`import org.projectnessie.versioned.storage.inmemory.InmemoryBackendTestFactory;`
  - 新：`import org.projectnessie.versioned.storage.inmemorytests.InmemoryBackendTestFactory;`
  - 包名由 `inmemory` 变为 `inmemorytests`，类名 `InmemoryBackendTestFactory` 不变。该类在第 74 行被 `@NessieBackend(InmemoryBackendTestFactory.class)` 注解引用，告诉 `PersistExtension`（JUnit 5 扩展，第 73 行 `@ExtendWith(PersistExtension.class)`）使用内存后端工厂创建测试用持久化实例。`BaseTestIceberg` 是 `TestNessieCatalog` 与 `TestNessieViewCatalog` 的公共基类，提供 Nessie API 客户端、目录构造与清理逻辑，因此该 import 修正覆盖了核心测试链路。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieCatalog.java`

修改目的：同上，把目录测试类中对旧包路径的 import 切换到新包。

工作逻辑：

- 第 47 行：`import org.projectnessie.versioned.storage.inmemory.InmemoryBackendTestFactory;` → `import org.projectnessie.versioned.storage.inmemorytests.InmemoryBackendTestFactory;`
  - 该类第 53 行同样标注 `@NessieBackend(InmemoryBackendTestFactory.class)`，用于独立声明测试后端（即便继承自 `BaseTestIceberg`，注解仍显式存在以保清晰）。`TestNessieCatalog` 验证 `NessieCatalog` 的表操作（建表/提交/加载/删除/分支与标签语义等）。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieViewCatalog.java`

修改目的：同上，把视图目录测试类中对旧包路径的 import 切换到新包。

工作逻辑：

- 第 50 行：`import org.projectnessie.versioned.storage.inmemory.InmemoryBackendTestFactory;` → `import org.projectnessie.versioned.storage.inmemorytests.InmemoryBackendTestFactory;`
  - `TestNessieViewCatalog` 验证 `NessieCatalog` 对 Iceberg Views（SQL 视图规范）的支持，复用同样的 `@NessieBackend` + `PersistExtension` 测试基础设施，因此同样依赖新构件提供的 `InmemoryBackendTestFactory`。

## 小结

本提交将 `iceberg-nessie` 依赖的 Nessie 从 0.76.3 升至 0.77.1，并适配上游对内存存储测试制品的不兼容重组：把构件坐标 `nessie-versioned-storage-inmemory` 重命名为 `nessie-versioned-storage-inmemory-tests`，对应 Java 包由 `...inmemory` 改为 `...inmemorytests`。改动覆盖版本目录（版本号+库别名+坐标）、根构建脚本（`:iceberg-nessie` 测试依赖别名）以及三个测试类（import 语句）。升级仅影响测试类路径，生产代码仅依赖稳定的 `nessie-client`，对 Iceberg 公共 API 无影响。回迁 1.4.x 时必须 5 个文件整体移植，漏改任一 import 或别名都会导致 `:iceberg-nessie` 测试编译失败。
