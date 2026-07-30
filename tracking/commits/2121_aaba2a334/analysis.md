# 提交分析：Nessie: Throw a NoSuchNamespaceException when listing a non-existing namespace

## 提交信息

| 字段 | 值 |
|------|-----|
| 提交号 | 2121 |
| 短哈希 | `aaba2a334` |
| 完整哈希 | `aaba2a33476e3638d05b16aee040a8f5add41148` |
| 作者 | B Vadlamani |
| 邮箱 | bhargava333@gmail.com |
| 日期 | 2025-05-13 23:35:56 2025 -0700 |
| 提交信息 | Nessie: Throw a NoSuchNamespaceException when listing a non-existing namespace (#12901) |

## 总体目的

本提交修复了 Nessie Catalog 在列出不存在的命名空间时的行为。此前，当用户尝试列出一个不存在的命名空间时，Nessie Catalog 会返回空列表而非抛出异常，这与 Iceberg Catalog 的接口规范不一致（规范要求抛出 `NoSuchNamespaceException`）。本提交通过在列出命名空间前检查命名空间是否存在来修复此问题。

## 设计目的的实现方式

1. **在 `listNamespaces` 中添加存在性检查**：在构建命名空间过滤器之前，先通过 Nessie API 检查命名空间对应的 content key 是否存在，如果不存在则抛出 `NoSuchNamespaceException`。

2. **更新测试**：将原来期望返回空列表的测试改为期望抛出 `NoSuchNamespaceException`，并移除之前标记为 `@Disabled` 的 `testListNonExistingNamespace` 测试覆盖。

3. **修复相关测试中的变量重赋值问题**：在 `TestBranchVisibility` 中将重新赋值的 `nessieCatalog` 变量改为新变量 `nessieCatalog2`，避免变量遮蔽问题。

## 修改详情

### 1. 修改 `NessieIcebergClient.java`

**文件**：`nessie/src/main/java/org/apache/iceberg/nessie/NessieIcebergClient.java`

**修改内容**：在 `listNamespaces` 方法中，当处理非根命名空间时，新增命名空间存在性检查：

```java
Content existing =
    api.getContent()
        .reference(getReference())
        .key(root.toContentKey())
        .get()
        .get(root.toContentKey());
if (existing == null) {
  throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);
}
```

**目的和工作逻辑**：在构建命名空间过滤条件之前，通过 Nessie API 查询该命名空间的 content key。如果返回 null，说明命名空间不存在，抛出 `NoSuchNamespaceException`。这确保了 Nessie Catalog 的行为与 Iceberg Catalog 接口规范一致。

### 2. 修改 `TestBranchVisibility.java`

**文件**：`nessie/src/test/java/org/apache/iceberg/nessie/TestBranchVisibility.java`

**修改内容**：
- 将两处 `assertThat(nessieCatalog.listNamespaces(namespaceAB)).isEmpty()` 替换为 `assertThatThrownBy(() -> ...).isInstanceOf(NoSuchNamespaceException.class).hasMessage(...)`
- 将重新赋值的 `nessieCatalog = initCatalog(testBranch)` 改为 `NessieCatalog nessieCatalog2 = initCatalog(testBranch)`，并更新后续引用

**目的**：适配新的异常抛出行为；修复变量遮蔽问题（原来 `nessieCatalog` 被重新赋值会导致外部引用指向新实例，改为新变量名更清晰）。

### 3. 修改 `TestMultipleClients.java`

**文件**：`nessie/src/test/java/org/apache/iceberg/nessie/TestMultipleClients.java`

**修改内容**：将列出不存在命名空间的测试从期望返回空列表改为期望抛出 `NoSuchNamespaceException`。

**目的**：适配新的异常抛出行为。

### 4. 修改 `TestNessieCatalog.java`

**文件**：`nessie/src/test/java/org/apache/iceberg/nessie/TestNessieCatalog.java`

**修改内容**：移除 `testListNonExistingNamespace` 测试方法上的 `@Disabled` 注解和方法覆盖。

**目的**：此前该测试被禁用并注释说明"Nessie 当前返回空列表而非抛出 NoSuchNamespaceException"。现在此问题已修复，可以移除禁用注解，让继承自 `CatalogTests` 的标准测试正常运行。

### 5. 修改 `TestNessieIcebergClient.java`

**文件**：`nessie/src/test/java/org/apache/iceberg/nessie/TestNessieIcebergClient.java`

**修改内容**：
- 将 `assertThat(client.listNamespaces(namespace)).isNotNull()` 改为 `assertThat(client.listNamespaces(namespace)).isEmpty()`
- 添加 `assertThat(client.listNamespaces(Namespace.empty())).containsOnly(Namespace.of("a"))` 验证

**目的**：改进测试断言的精确性——验证返回空列表而非仅验证非 null。

## 总结

本提交修复了 Nessie Catalog 在列出不存在命名空间时的行为不一致问题。核心变更包括：

1. **添加存在性检查**：在 `listNamespaces` 中通过 Nessie API 检查命名空间是否存在，不存在则抛出 `NoSuchNamespaceException`
2. **更新测试**：将期望返回空列表的测试改为期望抛出异常
3. **启用标准测试**：移除 `testListNonExistingNamespace` 的 `@Disabled` 注解
4. **修复变量遮蔽**：在 `TestBranchVisibility` 中避免变量重赋值

共修改 5 个文件，使 Nessie Catalog 的行为与 Iceberg Catalog 接口规范保持一致。
