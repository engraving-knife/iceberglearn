# 提交 0483：Azure: Bump Azurite container (#9668)

## 提交信息

- **序号**：0483 / 4088
- **哈希**：3c703ccce8fb083a8c4c0084c059a60fd1635094
- **短哈希**：3c703ccce
- **日期**：2024-02-07 08:17:53 +0100
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Azure: Bump Azurite container (#9668)
- **PR/Issue**：#9668

## 总体目的

这个提交把 `iceberg-azure` 测试模块使用的 Azurite 容器镜像版本从 `3.26.0` 升级到 `3.29.0`。Azurite 是微软开源的 Azure Storage 本地模拟器，提供与 Azure Blob、Data Lake（ADLS Gen2）、Queue、Table 服务兼容的本地 HTTP 端点。Iceberg 的 `iceberg-azure` 模块在测试套件里通过 Testcontainers 拉起一个 Azurite Docker 容器，作为 `ADLSFileIO`、`ADLSInputStream`、`ADLSOutputStream` 等组件的集成测试目标，避免依赖真实的 Azure 云端存储。

提交说明中明确写了升级动机："To check if it works against the latest version of the Azure SDK"，并指向 PR #9571（即下一个要分析的 0485 号提交：把 `com.azure:azure-sdk-bom` 从 1.2.18 升到 1.2.20）。也就是说，这两次升级是配套的：先把 Azure SDK BOM 升级到新版（携带新版 DataLake 客户端 API），再把 Azurite 模拟器升到与之匹配的较新版本，以便在最新版客户端 + 最新版服务端模拟器组合下验证 Iceberg 的 Azure 集成仍能正确工作。先升 SDK、再升 Azurite 是合理的次序——服务端模拟器滞后反而容易让 SDK 的新行为（如新版 REST API、新的错误响应）在测试中观察不到。

从 3.26.0 到 3.29.0 跨越三个 minor 版本，Azurite 在这段时间内通常会修复若干与 Azure 服务端行为不一致的 bug（如请求头解析、SAS 鉴权、DataLake 路径 API、错误响应码等），并跟进新加入的 REST API 表面。Iceberg 在 `ADLSFileIO` 等组件里调用的 DataLake Service Client 是直接走 REST 协议的，因此模拟器与服务端行为的对齐程度直接影响集成测试的可信度。把 Azurite 升到较新版本可以让集成测试更接近真实 Azure 的行为，同时降低"测试通过但生产失败"或"模拟器 bug 误报"的概率。

## 如何达成设计目的

实现路径非常简洁：修改 `AzuriteContainer` 类中的 `DEFAULT_TAG` 字符串常量从 `"3.26.0"` 改为 `"3.29.0"`。该常量被两个构造器使用——无参构造器拼接 `DEFAULT_IMAGE + ":" + DEFAULT_TAG` 形成 `mcr.microsoft.com/azure-storage/azurite:3.29.0`，作为默认镜像；接收 `image` 参数的构造器在传 `null` 时也回退到同一默认。`BaseAzuriteTest` 通过 `new AzuriteContainer()`（即 `AZURITE_CONTAINER` 静态字段）使用默认构造器，因此所有继承自 `BaseAzuriteTest` 的集成测试（`ADLSFileIOTest`、`ADLSInputStreamTest`、`ADLSOutputStreamTest` 等）都会自动用上新版镜像，无需任何业务侧改动。Testcontainers 在拉镜像时会自动下载 `3.29.0` tag，并以 `LogMessageWaitStrategy` 等待 `Azurite Blob service is successfully listening at .*` 正则匹配的日志出现再放行测试，保证测试在容器就绪后才开始执行。

## 修改详情

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/AzuriteContainer.java`

**修改目的**：把测试用的 Azurite Docker 镜像默认 tag 从 `3.26.0` 升级到 `3.29.0`，配合 Azure SDK BOM 升级（PR #9571）做端到端集成测试验证。

**工作逻辑**：

该文件是 `iceberg-azure` 模块测试侧的 Testcontainers 包装类，继承自 `GenericContainer<AzuriteContainer>`，并暴露若干对外的便捷方法（`createStorageContainer`、`createFile`、`serviceClient`、`fileClient`、`location`、`endpoint`、`credential`）。关键常量布局如下：

```java
private static final int DEFAULT_PORT = 10000; // default blob service port
private static final String DEFAULT_IMAGE = "mcr.microsoft.com/azure-storage/azurite";
private static final String DEFAULT_TAG = "3.29.0";  // 原 "3.26.0"
private static final String LOG_WAIT_REGEX =
    "Azurite Blob service is successfully listening at .*";
```

`DEFAULT_TAG` 通过两个构造器被使用：

```java
public AzuriteContainer() {
  this(DEFAULT_IMAGE + ":" + DEFAULT_TAG);
}

public AzuriteContainer(String image) {
  super(image == null ? DEFAULT_IMAGE + ":" + DEFAULT_TAG : image);
  this.addExposedPort(DEFAULT_PORT);
  this.addEnv("AZURITE_ACCOUNTS", ACCOUNT + ":" + KEY);
  this.setWaitStrategy(new LogMessageWaitStrategy().withRegEx(LOG_WAIT_REGEX));
}
```

- 默认无参构造器把镜像拼成 `mcr.microsoft.com/azure-storage/azurite:3.29.0`。
- 第二个构造器允许覆盖镜像（如 CI 想跑特定版本时），传入 `null` 时也回退到默认。

环境变量 `AZURITE_ACCOUNTS` 用 `account:key` 这种"账户名:密钥"对的形式向 Azurite 注册本地模拟账户；账户名 `account`、密钥 `key` 是 Azurite 文档中约定的占位凭据，配合 `endpoint()` 方法生成的 `http://<host>:<mapped-port>/account` 与 `credential()` 构造的 `StorageSharedKeyCredential("account", "key")` 即可完成与 DataLake 客户端的鉴权握手。就绪策略使用 `LogMessageWaitStrategy` 等待容器日志匹配 `LOG_WAIT_REGEX`——这是 Azurite 内部三个服务（Blob、Queue、Table）启动完成时打印的固定文本，能可靠地表示 Blob 服务已经监听就绪。

`BaseAzuriteTest` 持有 `protected static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();`，由 JUnit 自动在测试类启动时拉起容器。本次改动让所有依赖 `BaseAzuriteTest` 的测试自动获得新版 Azurite 的行为，无需修改业务代码。

## 小结

这是一个最小化的测试基础设施升级，仅修改 `AzuriteContainer.DEFAULT_TAG` 常量从 `3.26.0` 升至 `3.29.0`，让 `iceberg-azure` 模块所有集成测试在新版 Azurite 模拟器上运行。提交动机明确——配合 PR #9571 把 `com.azure:azure-sdk-bom` 升级到 1.2.20 后，用较新的服务端模拟器端到端验证新版 Azure SDK 与 Iceberg `ADLSFileIO` 之间的兼容性。改动一行，影响范围限于测试时拉取的 Docker 镜像版本，业务代码与测试逻辑不变。
