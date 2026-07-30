# 提交 3458：Core: Pass storage credentials to ioBuilder-created FileIO (#15752)

## 提交信息

- **序号**：3458 / 4088
- **哈希**：5dff6f6d778f1a944d87ac5029e56b953d24c3c5
- **短哈希**：5dff6f6d77
- **日期**：2026-03-25 07:31:29 +0100
- **作者**：rkaveti
- **提交说明**：Core: Pass storage credentials to ioBuilder-created FileIO (#15752)
- **PR/Issue**：#15752

## 总体目的

修复 `RESTSessionCatalog.newFileIO()` 中 ioBuilder 路径不传递存储凭证的 bug。`RESTSessionCatalog.newFileIO()` 有两条创建 FileIO 的路径：

1. **ioBuilder 路径**：当提供了自定义 ioBuilder 时（被 Trino 使用）
2. **反射路径**：当 ioBuilder 为 null 时（使用 `CatalogUtil.loadFileIO()`）

反射路径会正确地通过 `setCredentials()` 将存储凭证传递给实现了 `SupportsStorageCredentials` 的 FileIO。但 ioBuilder 路径完全忽略了 `storageCredentials` 参数，静默丢弃了 vended 凭证。

## 如何达成设计目的

- 在 ioBuilder 创建 FileIO 后，检查其是否实现 `SupportsStorageCredentials` 接口
- 如果实现了该接口且有存储凭证，调用 `setCredentials()` 传递凭证
- 这与 `CatalogUtil.loadFileIO()` 的行为保持一致

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+10/-1 lines)

**修改目的**：在 ioBuilder 路径中传递存储凭证。

**工作逻辑**：

修改前：
```java
if (null != ioBuilder) {
    return ioBuilder.apply(context, properties);
}
```

修改后：
```java
if (null != ioBuilder) {
    FileIO fileIO = ioBuilder.apply(context, properties);
    if (!storageCredentials.isEmpty()
        && fileIO instanceof SupportsStorageCredentials ioWithCredentials) {
        ioWithCredentials.setCredentials(
            storageCredentials.stream()
                .map(c -> StorageCredential.create(c.prefix(), c.config()))
                .collect(Collectors.toList()));
    }
    return fileIO;
}
```

关键逻辑：
1. 调用 `ioBuilder.apply()` 创建 FileIO
2. 检查 `storageCredentials` 不为空且 FileIO 实现 `SupportsStorageCredentials`
3. 将 `Credential` 列表转换为 `StorageCredential` 列表并调用 `setCredentials()`

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+71/-0 lines)

**修改目的**：添加测试验证 ioBuilder 路径正确接收存储凭证。

**工作逻辑**：
- `testIoBuilderReceivesStorageCredentials()` 测试方法：
  1. 创建一个 `Credential` 对象，包含 S3 访问密钥
  2. 通过自定义 `RESTCatalogAdapter` 在 `LOAD_TABLE` 响应中注入凭证
  3. 使用自定义 ioBuilder 创建 `TestFileIOWithStorageCredentials` 实例
  4. 加载表后验证 FileIO 收到了正确的存储凭证
  5. 验证凭证的 prefix、access-key-id 和 secret-access-key 都正确

## 总结

该提交修复了 `RESTSessionCatalog` 中 ioBuilder 路径不传递存储凭证的 bug。之前当使用自定义 ioBuilder（如 Trino 的场景）时，REST 服务器返回的 vended 凭证被静默丢弃。修复后在 ioBuilder 创建 FileIO 后，检查并调用 `setCredentials()` 传递凭证，与反射路径的行为保持一致。
