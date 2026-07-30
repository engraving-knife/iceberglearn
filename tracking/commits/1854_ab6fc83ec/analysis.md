# 提交 1854：Core: Don't expose InMemoryViewOperations and RESTViewBuilder outside their visibility scope (#12524)

## 提交信息

- **序号**：1854 / 4088
- **哈希**：ab6fc83ec0269736355a0a89c51e44e822264da8
- **短哈希**：ab6fc83ec
- **日期**：2025-03-14 08:32:43 -0600
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Don't expose InMemoryViewOperations and RESTViewBuilder outside their visibility scope (#12524)
- **PR/Issue**：#12524

## 总体目的

Iceberg 的 API/ABI 兼容性由 Revapi（`.palantir/revapi.yml`）监控。Revapi 检测到两处返回类型暴露了本应是包级私有或内部实现的类：

1. `InMemoryCatalog#newViewOps(TableIdentifier)` 返回类型是 `InMemoryCatalog.InMemoryViewOperations`——这是一个内部嵌套类，本不该出现在 public/protected 方法的签名里。`newViewOps` 是 `protected` 方法，但其返回类型把内部实现类暴露给了子类和外部反射调用方。
2. `RESTSessionCatalog#buildView(SessionContext, TableIdentifier)` 返回类型是 `RESTSessionCatalog.RESTViewBuilder`——同样是内部嵌套类，却通过 `public` 方法暴露。

这种"返回具体内部类"的写法有两个危害：
- 破坏了封装：调用方可以依赖具体的 `InMemoryViewOperations`/`RESTViewBuilder` 类型，使后续重构（如换实现、改内部类）变成二进制不兼容变更。
- 触发 Revapi 报警：每次内部类改动都会被 Revapi 标记为 breaking change，干扰真正的兼容性审查。

本提交把这两个方法的返回类型收窄为对应的接口/父类：`InMemoryViewOperations` → `ViewOperations`，`RESTViewBuilder` → `ViewBuilder`。实际返回的对象实例不变（仍是 `new InMemoryViewOperations(...)` 和 `new RESTViewBuilder(...)`），只是声明类型提升为接口，从而隐藏实现类。

## 如何达成设计目的

修改两个方法签名的返回类型为接口类型，方法体不变（仍返回具体实现实例，因为实例本身就是接口的实现）。同时在 `.palantir/revapi.yml` 的 `acceptedBreaks` 下登记这两处返回类型变更，并附 justification "Break is acceptable because class is exposed outside of its visibility scope"——即：原本就不该暴露该类，本次变更只是把暴露的实现类替换为接口，属于可接受的破坏性变更。

## 修改详情

### `.palantir/revapi.yml` (修改, +13 lines)

**修改目的**：把两处返回类型变更登记为 Revapi 可接受的中断，避免 CI 兼容性检查失败。

**工作逻辑**：在 `org.apache.iceberg:iceberg-core` 的 `acceptedBreaks` 列表下新增两条 `java.method.returnTypeChanged` 记录：
- `InMemoryCatalog::newViewOps` 返回类型从 `InMemoryViewOperations` 改为 `ViewOperations`；
- `RESTSessionCatalog::buildView` 返回类型从 `RESTViewBuilder` 改为 `ViewBuilder`。
每条都带 justification 说明"类暴露在其可见性范围之外"是可接受的中断。

### `core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java` (修改, +2/-1 lines)

**修改目的**：把 `newViewOps` 的返回类型从内部类 `InMemoryViewOperations` 收窄为接口 `ViewOperations`。

**工作逻辑**：`@Override protected ViewOperations newViewOps(TableIdentifier identifier) { return new InMemoryViewOperations(io, identifier); }`。方法体不变，仍返回 `InMemoryViewOperations` 实例（它是 `BaseViewOperations` → `ViewOperations` 的子类），但声明类型变为 `ViewOperations`，对外隐藏实现类。新增 `import org.apache.iceberg.view.ViewOperations;`。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (修改, +1/-1 line)

**修改目的**：把 `buildView` 的返回类型从内部类 `RESTViewBuilder` 收窄为父类 `ViewBuilder`。

**工作逻辑**：`@Override public ViewBuilder buildView(SessionContext context, TableIdentifier identifier) { return new RESTViewBuilder(context, identifier); }`。同样，方法体不变，声明类型提升为 `ViewBuilder`。

## 小结

- **成效**：`InMemoryViewOperations` 与 `RESTViewBuilder` 不再通过方法签名暴露给外部，封装性提升；后续对这两个内部类的修改不再触发 Revapi 的二进制兼容性报警。
- **影响范围**：core 模块 2 个文件（+3/-2），Revapi 配置 1 个文件（+13）。属于源码与二进制层面的"返回类型收窄"——对调用方而言，如果之前用接口类型接收返回值则完全无影响；如果用了具体实现类类型接收，则需改为接口类型。
- **回迁到 1.4.x 的注意事项**：建议回迁，前提是 1.4.x 的 `InMemoryCatalog.newViewOps` 与 `RESTSessionCatalog.buildView` 仍返回具体内部类。回迁时需同步更新 `.palantir/revapi.yml`（若 1.4.x 启用 Revapi）。变更本身是方法签名收窄，调用方一般用接口接收，兼容性风险低。需确认 1.4.x 的 `ViewOperations`/`ViewBuilder` 接口已存在。
