# 提交 1276：AWS: Use testcontainers-minio instead of S3Mock (#11349)

## 提交信息

- **序号**：1276 / 4088
- **哈希**：6ba1a1f578b2445f9bf3a2721fae538275bc9948
- **短哈希**：6ba1a1f57
- **日期**：2024-10-24（Thu Oct 24 08:37:33 2024 -0700）
- **作者**：sullis <github@seansullivan.com>
- **提交说明**：AWS: Use testcontainers-minio instead of S3Mock (#11349)
- **PR/Issue**：#11349

## 总体目的

Iceberg 的 `aws` 模块在测试 S3 相关的 `S3FileIO`/`S3InputStream`/`S3OutputStream`/`S3RestSigner` 等组件时，长期依赖 Adobe 提供的 `s3mock-junit5`（一个内嵌的 S3 协议 mock 服务）。但 S3Mock 对 S3 协议的覆盖并不完整，最关键的缺陷是**不支持条件写入（conditional writes，即 `IfNoneMatch` 语义）**——而这正是 Iceberg 在 S3 上做表元数据乐观提交（commit）的核心机制。S3 在 2024 年正式 GA 了条件写入能力（PUT Object 时通过 `IfNoneMatch: *` 实现原子创建），Iceberg 也已依赖该能力实现"仅当目标对象不存在时才写入"的提交语义。S3Mock 不能正确模拟该语义，使得相关代码路径无法被真实测试覆盖。

本提交将 `aws` 模块测试基础设施从 Adobe S3Mock 整体迁移到 testcontainers 官方提供的 `MinIOContainer`（MinIO 是一个高度兼容 S3 协议的对象存储，原生支持条件写入），同时顺手把测试代码中残留的 `javax.servlet` API 升级为 `jakarta.servlet`（移除 S3Mock 自带的 Spring Boot Starter 后，改用 jetty-servlet 提供 jakarta 命名空间的 servlet API）。新增 `TestMinioUtil` 显式验证 MinIO 容器对条件写入（412 Precondition Failed）的支持，确保后续 `S3FileIO` 测试建立在真实的 S3 语义之上。

## 如何达成设计目的

整体迁移思路：

1. **删除自实现容器** `MinioContainer.java`（基于 `GenericContainer` 手写 MinIO 启动/健康检查/URL 获取），改用 testcontainers 官方 `MinIOContainer`，减少自维护代码；
2. **新增工具类** `MinioUtil`：封装容器创建（`createContainer`）与 `S3Client` 构造（`createS3Client`）两个静态方法，统一所有测试的容器/客户端获取入口，避免每个测试类重复样板；
3. **新增冒烟测试** `TestMinioUtil`：通过 `ifNoneMatch("*")` 重复 `putObject` 验证 MinIO 正确返回 412，确认条件写入语义可用，给后续 S3FileIO 测试提供信心；
4. **改造三个核心 S3 测试**（`TestS3FileIO`/`TestS3InputStream`/`TestS3OutputStream`）：把 `@ExtendWith(S3MockExtension.class)` + `@RegisterExtension S3MockExtension` 换成 `@Testcontainers` + `@Container MinIOContainer`，并把 `S3_MOCK::createS3ClientV2` 换成 `MinioUtil.createS3Client(MINIO)`；
5. **改造 `TestS3RestSigner`**：原直接 `new MinioContainer(credentials)` 改为 `MinioUtil.createContainer(credentials)`，并用 `getS3URL()`（官方容器方法）替换原 `getURI()`；
6. **servlet API 升级** `S3SignerServlet`：`javax.servlet.*` → `jakarta.servlet.*`，因为 S3Mock 移除后不再传递 `spring-boot-starter-logging` 等带 `javax.servlet` 的依赖，改用 `jetty-servlet`（jakarta 命名空间）；
7. **依赖配置**：`build.gradle` 与 `libs.versions.toml` 移除 `s3mock-junit5`，新增 `testcontainers-junit-jupiter` 与 `testcontainers-minio`，新增 `jetty-servlet`。

## 修改详情

### `aws/src/test/java/org/apache/iceberg/aws/s3/MinioContainer.java`（删除，-68 行）

**修改目的**：移除自维护的 MinIO 容器封装，改用官方 `MinIOContainer`。

**原工作逻辑**：继承 `GenericContainer<MinioContainer>`，固定使用 `minio/minio:edge` 镜像，通过 `withCommand("server", "/data")` 启动 MinIO；用环境变量 `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` 配置凭证；设置 `MINIO_DOMAIN=localhost` 启用 virtual-host 风格请求；用 `HttpWaitStrategy` 探测 `/minio/health/ready` 等待就绪；`getURI()` 返回 `http://host:mappedPort`。

### `aws/src/test/java/org/apache/iceberg/aws/s3/MinioUtil.java`（新增，65 行）

**修改目的**：提供统一的 MinIO 容器与 S3Client 创建入口。

**工作逻辑**：

- `createContainer()` / `createContainer(AwsCredentials)`：用 `DockerImageName.parse("minio/minio:latest")` 构造官方 `MinIOContainer`，保留 `MINIO_DOMAIN=localhost` 环境变量（virtual-host 风格请求所需）；若提供凭证则通过 `withUserName`/`withPassword` 设置（官方容器 API）；
- `createS3Client(MinIOContainer)`：从容器 `getS3URL()` 取 endpoint，构造 `S3Client`：`StaticCredentialsProvider` 用容器 `getUserName()`/`getPassword()`，`endpointOverride(uri)` 指向 MinIO，`region(US_EAST_1)` 占位，`forcePathStyle(true)`（注释说明 OSX 无法解析子域名）。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestMinioUtil.java`（新增，75 行）

**修改目的**：验证 MinIO 容器对条件写入的支持，作为后续 S3FileIO 测试的语义基线。

**工作逻辑**：`@Container MinIOContainer MINIO = MinioUtil.createContainer()`；测试 `validateS3ConditionalWrites`：
1. 创建随机 bucket；
2. 循环 5 次 `putObject` 同一 key，请求带 `ifNoneMatch("*")`；
3. 第 0 次（首次）应成功；
4. 第 1-4 次应抛 `S3Exception`，断言 `Status Code: 412` 与 "At least one of the pre-conditions you specified did not hold"；
5. 最后 `getObject` 验证内容为首次写入的 `test-payload-0`，证明后续写入被正确拒绝。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIO.java`（修改）

**修改目的**：把 S3FileIO 的测试后端从 S3Mock 切到 MinIO。

- 删除 `com.adobe.testing.s3mock.junit5.S3MockExtension`、`ExtendWith`、`RegisterExtension` 导入；
- 新增 `MinIOContainer`/`@Container`/`@Testcontainers` 导入；
- 类注解 `@ExtendWith(S3MockExtension.class)` → `@Testcontainers`；
- 字段 `S3_MOCK = S3MockExtension.builder().silent().build()` → `MINIO = MinioUtil.createContainer()`；
- `s3` 供应商 `S3_MOCK::createS3ClientV2` → `() -> MinioUtil.createS3Client(MINIO)`，访问修饰符由 `public` 收窄为 `private`。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3InputStream.java`（修改）

**修改目的**：同上，输入流测试切换到 MinIO。

- 类注解换 `@Testcontainers`；`S3_MOCK` 字段换为 `MINIO = MinioUtil.createContainer()`；
- `s3 = S3_MOCK.createS3ClientV2()` → `s3 = MinioUtil.createS3Client(MINIO)`。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3OutputStream.java`（修改）

**修改目的**：同上，输出流测试切换到 MinIO。

- 同样把 `S3MockExtension` 换为 `MinIOContainer` + `MinioUtil`；`@ExtendWith` 换 `@Testcontainers`。

### `aws/src/test/java/org/apache/iceberg/aws/s3/signer/S3SignerServlet.java`（修改）

**修改目的**：跟随依赖切换，把 servlet API 从 `javax.servlet` 升级为 `jakarta.servlet`。

- 三个导入 `javax.servlet.http.HttpServlet`/`HttpServletRequest`/`HttpServletResponse` → `jakarta.servlet.http.*`；
- 这是因为移除 S3Mock 后不再有传递 `javax.servlet` 的依赖，新增的 `jetty-servlet` 提供 `jakarta.servlet` 命名空间。

### `aws/src/test/java/org/apache/iceberg/aws/s3/signer/TestS3RestSigner.java`（修改）

**修改目的**：把 S3 REST 签名测试的 MinIO 容器切换为官方实现，并迁移到 testcontainers JUnit 集成。

- 新增 `java.net.URI` 导入，把 `MinioContainer` 导入换为 `MinioUtil`；
- 新增 `@Testcontainers`/`@Container`/`MinIOContainer` 导入；
- 类上加 `@Testcontainers`；`MINIO_CONTAINER` 从 `new MinioContainer(...)` 改为 `MinioUtil.createContainer(...)`，并加 `@Container` 注解让 testcontainers JUnit 自动管理生命周期；
- `beforeClass` 增加断言 `assertThat(MINIO_CONTAINER.isRunning()).isTrue()`，确保容器在 HTTP 服务器初始化前已就绪；
- `endpointOverride(MINIO_CONTAINER.getURI())` → `endpointOverride(URI.create(MINIO_CONTAINER.getS3URL()))`（官方容器方法名变化）。

### `build.gradle`（修改）

**修改目的**：调整 `:iceberg-aws` 测试依赖。

- 移除 `testImplementation(libs.s3mock.junit5)` 及其三个 `exclude`（`spring-boot-starter-logging`/`logback-classic`/`junit`）——这些 exclude 正是 S3Mock 引入的传递依赖冲突；
- 新增 `testImplementation libs.testcontainers.junit.jupiter`、`libs.testcontainers.minio`、`libs.jetty.servlet`（后者提供 jakarta servlet API 给 `S3SignerServlet`）。

### `gradle/libs.versions.toml`（修改）

**修改目的**：版本目录同步。

- 移除 `s3mock-junit5 = "2.17.0"` 版本与 `s3mock-junit5` 库定义；
- 新增 `testcontainers-junit-jupiter` 与 `testcontainers-minio` 库定义（复用既有 `testcontainers` 版本号）。

## 小结

- **成效**：aws 模块测试后端从功能受限的 S3Mock 迁移到高度 S3 兼容的 MinIO，使条件写入（`IfNoneMatch`/412）等关键 S3 语义能被真实测试覆盖；同时减少了自维护的 `MinioContainer` 代码，转用官方 testcontainers 模块，降低维护成本；顺手完成 `javax.servlet` → `jakarta.servlet` 的命名空间升级。
- **影响范围**：仅测试代码与测试依赖，不影响生产运行时；所有 S3 相关测试类（FileIO/InputStream/OutputStream/RestSigner）统一走 `MinioUtil` 入口。
- **回迁到 1.4.x 的注意事项**：迁移涉及测试依赖增删与 servlet 命名空间切换，回迁时需确保 1.4.x 上 `jetty-servlet` 与 `testcontainers-minio`/`testcontainers-junit-jupiter` 的版本可解析（共用 `testcontainers` 版本号即可）。若 1.4.x 仍有其它测试类依赖 `javax.servlet`，需一并迁移到 `jakarta.servlet`，否则编译会因命名空间不一致而失败。另外 MinIO 容器需要本地 Docker 环境，CI 环境需确认已具备 testcontainers 运行条件。
