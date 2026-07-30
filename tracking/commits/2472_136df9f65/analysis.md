# 提交 2472：Core: Use ResourcePaths instead of hard-coded resource paths (#13759)

## 提交信息

- **序号**：2472 / 4088
- **哈希**：136df9f65b5cdc6ec26c07b5b92072daf7ade0e5
- **短哈希**：136df9f65
- **日期**：2025-08-07 15:15:15 -0600
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Use ResourcePaths instead of hard-coded resource paths (#13759)
- **PR/Issue**：#13759

## 总体目的

该提交将 REST Catalog 测试中硬编码的资源路径字符串替换为 `ResourcePaths` 工具类的方法调用，提升测试代码的可维护性和一致性。

在 `TestRESTCatalog` 测试类中，存在大量硬编码的路径字符串，如 `"v1/config"`、`"v1/oauth/tokens"`、`"v1/namespaces/ns/tables"` 等。这些路径字符串与 `ResourcePaths` 类中定义的方法（如 `config()`、`tokens()`、`tables(namespace)`）生成的路径重复。使用硬编码字符串存在两个风险：一是当 REST API 路径前缀或格式发生变化时，需要同时修改生产代码和测试代码中的字符串，容易遗漏；二是硬编码字符串容易因拼写错误而产生隐蔽的 bug。通过统一使用 `ResourcePaths` 工具类，测试中引用的路径与生产代码保持单一来源，降低维护成本。

## 如何达成设计目的

设计思路简单直接：将测试中所有硬编码的路径字符串逐一替换为对应的 `ResourcePaths` 方法调用。具体替换对应关系：
- `"v1/config"` → `ResourcePaths.config()`
- `"v1/oauth/tokens"` → `ResourcePaths.tokens()`
- `"v1/namespaces/ns/tables"` → `RESOURCE_PATHS.tables(namespace)`（同时引入 `Namespace` 变量）

涉及 42 处替换，均为等价替换，不改变测试逻辑。

## 修改详情

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+42/-42 lines)

**修改目的**：用 `ResourcePaths` 方法替换硬编码路径字符串。

**工作逻辑**：

1. **config 路径**：将多处 `reqMatcher(HTTPMethod.GET, "v1/config", ...)` 替换为 `reqMatcher(HTTPMethod.GET, ResourcePaths.config(), ...)`，以及在请求路径判断处的 `"v1/config".equals(request.path())` 替换为 `ResourcePaths.config().equals(request.path())`。

2. **tokens 路径**：将 `"v1/oauth/tokens".equals(request.path())` 和 `reqMatcher(HTTPMethod.POST, "v1/oauth/tokens", ...)` 替换为 `ResourcePaths.tokens()` 调用。

3. **tables 路径**：引入 `Namespace namespace = Namespace.of("ns")` 变量，将 `reqMatcher(HTTPMethod.POST, "v1/namespaces/ns/tables", ...)` 替换为 `reqMatcher(HTTPMethod.POST, RESOURCE_PATHS.tables(namespace), ...)`。

整个文件共 42 处替换，每处都是将硬编码字符串替换为等价的 `ResourcePaths` 方法调用。

## 总结

这是一个测试代码重构提交，将 `TestRESTCatalog` 中硬编码的 REST 资源路径字符串统一替换为 `ResourcePaths` 工具类方法调用。该提交不改变任何测试逻辑和行为，仅提升代码可维护性，确保测试中的路径与生产代码使用同一来源，避免路径变更时的不一致风险。
