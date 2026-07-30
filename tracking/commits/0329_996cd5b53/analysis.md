# 提交 0329：AWS: Add S3 Access Grants Integration (#9385)

## 提交信息

- **序号**：0329 / 4088
- **哈希**：996cd5b53a667fd0a5e6f58b94beae531fad038e
- **短哈希**：996cd5b53
- **日期**：2024-01-04 11:38:38 -0800
- **作者**：Adnan Hemani
- **提交说明**：AWS: Add S3 Access Grants Integration (#9385)
- **PR/Issue**：#9385

## 总体目的

AWS S3 Access Grants 是 AWS 推出的一项目标为"在 S3 上做细粒度访问控制"的服务：管理员可以在 S3 Access Grants 中为 IAM 主体（用户/角色）按 bucket、前缀、甚至对象粒度授予读/写权限，应用程序访问 S3 时由 S3 Access Grants 插件代为向 Access Grants 服务请求临时凭证，拿到凭证后再发起实际的 S3 请求。这相比直接给执行作业的 IAM 角色授予整个 bucket 的访问权限要细粒度得多，也便于审计与权限回收，在数据湖场景下尤其有价值——不同团队/作业访问同一 bucket 下不同前缀的数据可以走不同授权路径。

本提交的目标是让 Iceberg 的 `S3FileIO` 能够可选地启用 S3 Access Grants：用户在 catalog 配置中设置 `s3.access-grants.enabled=true`，Iceberg 构造 S3 客户端时就会把 AWS 官方提供的 `S3AccessGrantsPlugin` 挂到 `S3ClientBuilder` 上，后续所有 S3 读写都会经过该插件走 Access Grants 授权。同时提供一个 `s3.access-grants.fallback-to-iam` 选项，控制当 Access Grants 返回"拒绝"时是否回退到执行作业 IAM 角色自身的 S3 权限（fallback 行为由 AWS 插件本身支持，Iceberg 只是透传该开关）。

实现这一特性时需要特别注意依赖管理：`S3AccessGrantsPlugin` 由 AWS 单独发布在 `software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin` 坐标下，并不是 AWS SDK BOM 默认引入的模块。如果直接在 `S3FileIOProperties` 中静态 import 该插件的类，会让所有使用 `iceberg-aws` 的用户都被迫引入这个 jar（即便他们没用 Access Grants）。为避免这一点，本提交把对插件类的直接引用收敛到一个独立的内部类 `S3AccessGrantsPluginConfigurations`，并通过 Iceberg 既有的 `DynMethods` 反射机制按需加载——只有当用户显式开启 `s3.access-grants.enabled` 时才会触发加载与实例化，否则代码路径完全不触碰插件类。依赖本身在 `build.gradle` 中以 `compileOnly` 引入（运行时由用户自行声明），保证默认 classpath 不被污染。

## 如何达成设计目的

整体设计分四层：
1. 在 `S3FileIOProperties` 中新增两个配置项 `S3_ACCESS_GRANTS_ENABLED`（默认 false）与 `S3_ACCESS_GRANTS_FALLBACK_TO_IAM_ENABLED`（默认 false），并在构造时解析为布尔字段。提供 `applyS3AccessGrantsConfigurations(S3ClientBuilder)` 方法：仅当 enabled 时才通过 `DynMethods` 反射调用 `S3AccessGrantsPluginConfigurations.create(properties)` 拿到配置对象，再调用其 `configureS3ClientBuilder(builder)` 把 `S3AccessGrantsPlugin` 加到 builder 上。
2. 新增 `S3AccessGrantsPluginConfigurations` 类作为对 AWS 插件的"包装/桥接"：它直接 import AWS 插件类，负责构造 `S3AccessGrantsPlugin` 实例（按 fallback 标志）并 `builder.addPlugin(...)`。该类只会在用户开启特性时通过反射被加载，从而把对 AWS 插件类的静态依赖隔离在此类内部。
3. 在两个 S3 客户端构造入口（`AwsClientFactories.createS3ClientBuilder` 与 `DefaultS3FileIOAwsClientFactory.createS3ClientBuilder`）的 builder 链上各加一行 `.applyMutation(s3FileIOProperties::applyS3AccessGrantsConfigurations)`，让所有 S3 客户端构造路径都过一遍该方法（在 disabled 时是 no-op）。
4. 在 `build.gradle` 与 `gradle/libs.versions.toml` 中以 `compileOnly` 引入 `aws-s3-accessgrants-java-plugin` 依赖（版本 1.0.1），并在 test scope 中以 `testImplementation` 引入相同 artifact 以便测试能加载该类。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3AccessGrantsPluginConfigurations.java`（新增）

**修改目的**：把对 AWS `S3AccessGrantsPlugin` 的直接依赖收敛到一个独立类中，避免污染 `S3FileIOProperties` 的 classpath；同时封装"构造插件并加到 builder"的逻辑。

**工作逻辑**：
- 包级可见（`class` 无 public 修饰符），仅 Iceberg 内部使用。
- 字段 `private boolean isS3AccessGrantsFallbackToIamEnabled;`。
- `configureS3ClientBuilder(T builder)`：用 fallback 标志构造 `S3AccessGrantsPlugin`（`S3AccessGrantsPlugin.builder().enableFallback(isS3AccessGrantsFallbackToIamEnabled).build()`），再 `builder.addPlugin(s3AccessGrantsPlugin)` 把插件注册到 S3 client。一旦注册，AWS SDK 在每次 S3 请求前会先调用插件——插件代为向 Access Grants 服务请求凭证，拿到临时凭证后由 SDK 用它签名实际请求。
- `initialize(Map<String, String> properties)`：用 `PropertyUtil.propertyAsBoolean` 读 `S3FileIOProperties.S3_ACCESS_GRANTS_FALLBACK_TO_IAM_ENABLED`，默认 `S3_ACCESS_GRANTS_FALLBACK_TO_IAM_ENABLED_DEFAULT`（false）。
- `static S3AccessGrantsPluginConfigurations create(Map<String, String> properties)`：工厂方法——`new` 一个实例、`initialize`、返回。该方法名 `create` 与 `Map` 参数签名是给 `S3FileIOProperties.loadSdkPluginConfigurations` 通过 `DynMethods.builder("create").hiddenImpl(impl, Map.class)` 反射调用的契约。
- import `software.amazon.awssdk.s3accessgrants.plugin.S3AccessGrantsPlugin`——这是本类（也是整个 Iceberg）唯一直接 import AWS 插件类的地方。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java`

**修改目的**：(1) 新增两个配置项与字段；(2) 提供 `applyS3AccessGrantsConfigurations` 入口；(3) 通过反射按需加载插件配置类，避免静态依赖。

**工作逻辑**：
- 新增 `import org.apache.iceberg.common.DynMethods;`。
- 新增两个公开常量（带 Javadoc 说明含义与官方文档链接）：
  - `S3_ACCESS_GRANTS_ENABLED = "s3.access-grants.enabled"`，默认 `S3_ACCESS_GRANTS_ENABLED_DEFAULT = false`。
  - `S3_ACCESS_GRANTS_FALLBACK_TO_IAM_ENABLED = "s3.access-grants.fallback-to-iam"`，默认 `S3_ACCESS_GRANTS_FALLBACK_TO_IAM_ENABLED_DEFAULT = false`，Javadoc 指向 AWS 插件 GitHub。
- 新增两个私有字段 `isS3AccessGrantsEnabled`、`isS3AccessGrantsFallbackToIamEnabled`，并在两处初始化：构造函数默认值区（`this.isS3AccessGrantsEnabled = S3_ACCESS_GRANTS_ENABLED_DEFAULT;` 等）与 `initialize(Map)` 方法中通过 `PropertyUtil.propertyAsBoolean` 从 properties 解析。
- 新增 4 个 getter/setter：`isS3AccessGrantsEnabled`/`setS3AccessGrantsEnabled`、`isS3AccessGrantsFallbackToIamEnabled`/`setS3AccessGrantsFallbackToIamEnabled`。
- 新增核心方法 `applyS3AccessGrantsConfigurations(T builder)`：仅当 `isS3AccessGrantsEnabled` 为 true 时，调用 `loadSdkPluginConfigurations(S3AccessGrantsPluginConfigurations.class.getName(), allProperties)` 反射拿到配置对象，再调用 `s3AccessGrantsPluginConfigurations.configureS3ClientBuilder(builder)`。disabled 时是 no-op，不会触发对 AWS 插件类的加载——这是依赖隔离的关键。
- 新增私有方法 `loadSdkPluginConfigurations(String impl, Map<String, String> properties)`：用 `DynMethods.builder("create").hiddenImpl(impl, Map.class).buildStaticChecked().invoke(properties)` 反射调用目标类的 `create(Map)` 静态工厂。`DynMethods` 是 Iceberg 内部的反射工具，`hiddenImpl` 表示可以访问非 public 类与方法。捕获 `NoSuchMethodException` 转 `IllegalArgumentException`，提示"无法创建 SDK Plugin 配置对象"。Javadoc 说明"动态加载以避免可选 SDK 插件的运行时依赖"——正是这一反射加载让 `compileOnly` 依赖成立。

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientFactories.java`

**修改目的**：在 S3 客户端构造的 builder 链上接入 `applyS3AccessGrantsConfigurations`。

**工作逻辑**：在 `createS3ClientBuilder(...)` 方法的 builder 链中，紧跟 `s3FileIOProperties::applySignerConfiguration` 之后新增一行 `.applyMutation(s3FileIOProperties::applyS3AccessGrantsConfigurations)`。`applyMutation` 是 AWS SDK `SdkBuilder` 的链式方法，会把 builder 传给传入的 consumer 并返回 builder，便于在链上叠加配置。这一行让所有走 `AwsClientFactories` 工厂路径的 S3 客户端都会经过 Access Grants 配置（disabled 时为 no-op）。

### `aws/src/main/java/org/apache/iceberg/aws/s3/DefaultS3FileIOAwsClientFactory.java`

**修改目的**：在另一条 S3 客户端构造路径（默认 factory）上同样接入 `applyS3AccessGrantsConfigurations`。

**工作逻辑**：在 `createS3ClientBuilder()` 的 builder 链中，紧跟 `s3FileIOProperties::applySignerConfiguration` 之后新增 `.applyMutation(s3FileIOProperties::applyS3AccessGrantsConfigurations)`，与 `AwsClientFactories` 完全对称。Iceberg 有两条 S3 客户端构造入口（工厂类与默认 factory 类），两条路径都要改才能保证所有用户配置都能启用 Access Grants。

### `aws/src/test/java/org/apache/iceberg/aws/TestS3FileIOProperties.java`

**修改目的**：为 Access Grants 开关编写单元测试，验证启用/禁用时的 builder 行为。

**工作逻辑**：
- `testS3AccessGrantsEnabled()`：构造 `ImmutableMap.of(S3_ACCESS_GRANTS_ENABLED, "true")` 的 properties，创建 `S3FileIOProperties`，调用 `applyS3AccessGrantsConfigurations(S3Client.builder())`，断言 `builder.plugins().size() == 1`（即 Access Grants 插件被加入）。
- `testS3AccessGrantsDisabled()`：分两种场景：(a) 显式 `"false"` 与 (b) 空 map（隐式默认）。两种都断言 `builder.plugins().size() == 0`，验证 disabled 时不会注入插件。测试依赖 test scope 中的 `aws-s3-accessgrants-java-plugin` 才能加载 `S3AccessGrantsPluginConfigurations` 类。

### `build.gradle`

**修改目的**：在 `iceberg-aws` 模块中引入 `aws-s3-accessgrants-java-plugin` 依赖。

**工作逻辑**：
- 在 `project(':iceberg-aws')` 的 `dependencies` 块的 `compileOnly` 区，新增 `compileOnly(libs.awssdk.s3accessgrants)`——运行时由用户自行引入，避免默认增加 jar 体积。这与 `S3FileIOProperties` 通过反射按需加载的策略配合，使未启用 Access Grants 的用户完全不需要这个 jar。
- 在 `testImplementation` 区，新增 `testImplementation("software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin")`——注意这里是直接写坐标而非用 libs 引用，可能是为了与既有写法一致（同一区已有 `software.amazon.awssdk:iam`、`software.amazon.awssdk:s3control` 等直接坐标）。

### `gradle/libs.versions.toml`

**修改目的**：在版本目录中登记 S3 Access Grants 插件的版本与坐标，供 `build.gradle` 引用。

**工作逻辑**：
- 在 `[versions]` 区新增 `awssdk-s3accessgrants = "1.0.1"`。
- 在 `[libraries]` 区新增 `awssdk-s3accessgrants = { module = "software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin", version.ref = "awssdk-s3accessgrants" }`，供 `compileOnly(libs.awssdk.s3accessgrants)` 使用。

## 小结

这个提交为 Iceberg `S3FileIO` 增加了可选的 S3 Access Grants 集成能力，让用户通过 `s3.access-grants.enabled=true` 即可让所有 S3 读写走 AWS Access Grants 细粒度授权路径，并支持 `s3.access-grants.fallback-to-iam` 控制 IAM 回退。设计上的亮点在于依赖隔离：通过 `DynMethods` 反射 + `compileOnly` 依赖 + 独立包装类 `S3AccessGrantsPluginConfigurations`，让未启用该特性的用户完全不感知 AWS 插件 jar 的存在，避免了"为了一个可选特性拖累整个 iceberg-aws classpath"的问题。改动小而聚焦，但补齐了 Iceberg 在 AWS 生态下细粒度访问控制的关键能力。
