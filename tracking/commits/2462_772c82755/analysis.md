# 提交 2462：Build: Bump com.azure:azure-sdk-bom from 1.2.36 to 1.2.37 (#13743)

## 提交信息

- **序号**：2462 / 4088
- **哈希**：772c8275598e43d2c5ef029bfe83aeaa6c713e8a
- **短哈希**：772c82755
- **日期**：2025-08-06 07:59:02 +0200
- **作者**：suhwan
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.36 to 1.2.37 (#13743)
- **PR/Issue**：#13743

## 总体目的

该提交将 Azure SDK BOM（Bill of Materials）从 1.2.36 升级到 1.2.37，同时将集成测试中使用的 Azurite 模拟器容器镜像从 3.34.0 升级到 3.35.0。

Azure SDK BOM 是 Iceberg Azure 模块使用的 Azure SDK 依赖版本管理文件，通过 BOM 可以统一管理所有 Azure SDK 组件的版本。Azurite 是一个本地开发的 Azure Storage 模拟器，用于 Iceberg Azure 模块的集成测试。

## 如何达成设计目的

通过修改两个文件完成升级：

1. 在 Gradle 版本目录中更新 `azuresdk-bom` 版本号
2. 在集成测试的 Azurite 容器配置中更新镜像标签

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 Azure SDK BOM 版本声明。

**工作逻辑**：
```toml
# 修改前
azuresdk-bom = "1.2.36"
# 修改后
azuresdk-bom = "1.2.37"
```

### `azure/src/integration/java/org/apache/iceberg/azure/adlsv2/AzuriteContainer.java` (+1/-1 lines)

**修改目的**：更新集成测试中 Azurite 容器镜像版本。

**工作逻辑**：
```java
// 修改前
private static final String DEFAULT_TAG = "3.34.0";
// 修改后
private static final String DEFAULT_TAG = "3.35.0";
```

Azurite 容器镜像版本需要与 Azure SDK 版本保持兼容，因此随 SDK 升级一并更新。

## 总结

这是一个常规的依赖升级提交，将 Azure SDK BOM 从 1.2.36 升级到 1.2.37，同时将 Azurite 测试容器从 3.34.0 升级到 3.35.0 以保持兼容性。该修改仅涉及版本声明和测试容器配置，不涉及任何业务代码逻辑变更。
