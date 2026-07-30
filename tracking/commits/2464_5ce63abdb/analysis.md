# 提交 2464：Azure: Fix concurrency issues in Azure credential refresh. (#13730)

## 提交信息

- **序号**：2464 / 4088
- **哈希**：5ce63abdb0d11e79f4ade9294e34966e9b03b0bc
- **短哈希**：5ce63abdb
- **日期**：2025-08-06 17:34:10 +0200
- **作者**：Marcus Sawyer
- **提交说明**：Azure: Fix concurrency issues in Azure credential refresh. (#13730)
- **PR/Issue**：#13730

## 总体目的

该提交修复了 Azure ADLS v2 凭证自动刷新（credential vending）中的两个并发问题：

1. **不适当的 `Mono#block` 调用**：在 `VendedAdlsCredentialProvider.credentialForAccount()` 方法中，代码在异步上下文中调用了 `Mono#block()` 来阻塞获取凭证。`block()` 调用在非阻塞上下文中是不被允许的，而该方法此前在 `VendedAzureSasCredentialPolicy` 的异步 `process()` 方法中被调用，导致阻塞异步线程。

2. **线程安全问题**：`VendedAzureSasCredentialPolicy` 作为 `HttpPipelinePolicy` 的实现，需要是线程安全的，因为管道可以同时发送多个请求。但原有的 `azureSasCredential` 字段没有 volatile 修饰，`maybeUpdateCredential()` 方法也没有同步保护，在并发请求中可能导致竞态条件和可见性问题。此外，`sasCredentialByAccount` Map 使用了非线程安全的 `HashMap`。

## 如何达成设计目的

整体修复方案包含以下几个关键设计：

1. **异步化凭证获取**：将 `credentialForAccount()` 的返回类型从 `String` 改为 `Mono<String>`，移除其中的 `.block()` 调用，使整个凭证获取链路完全异步化。调用方在需要同步结果时自行调用 `.block()`。

2. **异步化凭证更新流程**：将 `maybeUpdateCredential()` 从 `void` 改为 `Mono<Void>`，使用 `flatMap` 和 `Mono.defer` 组合异步操作。在异步 `process()` 方法中通过 `then()` 链式调用，不再阻塞；在同步 `processSync()` 方法中调用 `.block()`。

3. **双重检查锁定**：在 `maybeUpdateCredential()` 中使用双重检查锁定（double-checked locking）模式来安全地初始化 `AzureSasCredential` 和 `AzureSasCredentialPolicy`，并使用 `volatile` 修饰 `azureSasCredential` 字段保证可见性。

4. **线程安全 Map**：将 `sasCredentialByAccount` 从 `Maps.newHashMap()` 改为 `Maps.newConcurrentMap()`。

## 修改详情

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/VendedAdlsCredentialProvider.java` (+4/-5 lines)

**修改目的**：异步化凭证获取方法，修复不适当的 block() 调用，使用线程安全 Map。

**工作逻辑**：
- `credentialForAccount()` 返回类型从 `String` 改为 `Mono<String>`，移除 `.block()` 调用
- `sasCredentialByAccount()` 中的 Map 初始化从 `Maps.newHashMap()` 改为 `Maps.newConcurrentMap()`

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/VendedAzureSasCredentialPolicy.java` (+22/-8 lines)

**修改目的**：修复线程安全问题，异步化凭证更新流程。

**工作逻辑**：

1. `azureSasCredential` 字段添加 `volatile` 修饰符

2. `process()` 方法改为异步链式调用：
```java
// 修改前
maybeUpdateCredential();
return azureSasCredentialPolicy.process(...);
// 修改后
return maybeUpdateCredential()
    .then(Mono.defer(() -> azureSasCredentialPolicy.process(...)));
```

3. `processSync()` 方法中调用 `maybeUpdateCredential().block()`

4. `maybeUpdateCredential()` 返回 `Mono<Void>`，使用双重检查锁定：
```java
private Mono<Void> maybeUpdateCredential() {
    return vendedAdlsCredentialProvider.credentialForAccount(account)
        .flatMap(sasToken -> {
            if (azureSasCredential == null) {
                synchronized (this) {
                    if (azureSasCredential == null) {
                        this.azureSasCredential = new AzureSasCredential(sasToken);
                        this.azureSasCredentialPolicy = new AzureSasCredentialPolicy(azureSasCredential, false);
                        return Mono.empty();
                    }
                }
            }
            azureSasCredential.update(sasToken);
            return Mono.empty();
        });
}
```

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/TestVendedAdlsCredentialProvider.java` (+11/-10 lines)

**修改目的**：更新测试以适配 `credentialForAccount()` 返回 `Mono<String>` 的变更。

**工作逻辑**：所有 `provider.credentialForAccount(STORAGE_ACCOUNT)` 调用改为 `provider.credentialForAccount(STORAGE_ACCOUNT).block()`。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/TestVendedAzureSasCredentialPolicy.java` (+3/-2 lines)

**修改目的**：更新测试 mock 以返回 `Mono` 而非直接值。

**工作逻辑**：`when(...).thenReturn(validSasToken)` 改为 `when(...).thenReturn(Mono.just(validSasToken))`。

## 总结

该提交修复了 Azure 凭证自动刷新中的两个并发问题：一是在异步上下文中不当使用 `Mono#block()` 导致阻塞，二是 `HttpPipelinePolicy` 实现的线程安全问题。通过将凭证获取方法异步化、添加双重检查锁定和 volatile 修饰符、使用线程安全 Map，确保了在并发请求场景下凭证刷新的正确性。该修改涉及 Azure 模块的生产代码和测试代码，是一个重要的并发安全修复。
