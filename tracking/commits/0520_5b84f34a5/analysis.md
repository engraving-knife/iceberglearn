# 提交 0520：Core: Don't fail if a REST service doesn't support views (#9754)

## 提交信息

- **序号**：0520 / 4088
- **哈希**：5b84f34a5386fc61b17bfe7dc7c1cbe565550958
- **短哈希**：5b84f34a5
- **日期**：2024-02-20 00:14:52 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Don't fail if a REST service doesn't support views (#9754)
- **PR/Issue**：#9754

## 总体目的

Iceberg 的 REST Catalog 在 main 分支上已经支持视图（views），`RESTSessionCatalog` 继承自 `BaseViewSessionCatalog`，实现了 `ViewSessionCatalog` 接口。其中 `Builder.replaceTransaction()`（创建"替换表"事务的入口）在执行前会先调用 `viewExists(context, ident)` 检查是否存在同名视图——因为 Iceberg 中表和视图共享命名空间，同名冲突时应当报错而非覆盖。

问题在于：`viewExists` 的默认实现（来自 `ViewSessionCatalog` 接口）会调用 `loadView(context, identifier)`，而 `RESTSessionCatalog.loadView` 会向服务端发起 `GET /v1/{prefix}/namespaces/{namespace}/views/{view}` 请求。如果 REST 服务端**不支持视图**（两种典型场景：服务端或其后端 catalog 本身不支持视图；或者新客户端连到旧服务端），这个请求会失败，服务端可能返回：

- HTTP 400 Bad Request → 转成 `BadRequestException`
- HTTP 403 Forbidden → 转成 `ForbiddenException`
- HTTP 404 Not Found → 转成 `NoSuchViewException`（这个会被 `viewExists` 内部 catch，返回 false，没问题）

修改前，`BadRequestException` 和 `ForbiddenException` 都直接继承 `RuntimeException`，**不是 `RESTException` 的子类**。而 `replaceTransaction()` 里只有 `catch (RESTException | UnsupportedOperationException)` 能兜底——这两个异常漏网了，会直接冒泡导致整个 `replaceTransaction()` 失败。也就是说：**只要 REST 服务端不支持视图，连创建/替换表都会失败**，这是一个严重的兼容性回退。

本提交的目的就是修复这个回退：让表操作在不支持视图的服务端上仍能正常工作。

## 如何达成设计目的

设计分两步：

**第一步：把 `BadRequestException` 和 `ForbiddenException` 的父类从 `RuntimeException` 改为 `RESTException`。** 这样它们就成了 `RESTException` 的子类，能被 `catch (RESTException ...)` 捕获。这是合理的归类——这两个异常本来就只在 REST 通信场景下抛出（分别对应 HTTP 400 和 403），让它们归入 `RESTException` 异常树符合语义。同时，因为 `RESTException` 的构造器已经做了 `String.format` 格式化，子类构造器不再需要自己调 `String.format`，直接转发 `(message, args)` 和 `(cause, message, args)` 给 super 即可，消除了重复代码。

**第二步：在 `replaceTransaction()` 里把 `viewExists` 调用包进 try-catch。** 捕获 `RESTException | UnsupportedOperationException`，在 catch 块里只打一条 debug 日志，然后继续执行后续的表替换逻辑。注释解释了两种触发场景：
1. 服务端或后端 catalog 不支持视图
2. 新客户端连到旧服务端

`UnsupportedOperationException` 也被捕获，是因为某些 REST 客户端实现可能在不支持视图时直接抛这个异常而非走 HTTP 往返。catch 后不抛出，相当于把"视图检查失败"视为"无同名视图"，让表操作继续——这是合理的降级，因为视图检查只是表替换的附加保护，不应阻塞核心表操作。

## 修改详情

### `api/src/main/java/org/apache/iceberg/exceptions/BadRequestException.java`

**修改目的**：让 `BadRequestException` 成为 `RESTException` 的子类，使其能被 `catch (RESTException)` 捕获；同时把格式化逻辑委托给父类。

**工作逻辑**：

类声明从 `extends RuntimeException implements CleanableFailure` 改为 `extends RESTException implements CleanableFailure`。`CleanableFailure` 标记接口保留不变（仍标记为"可安全清理的提交失败"）。

两个构造器调整：

```java
// 修改前
public BadRequestException(String message, Object... args) {
  super(String.format(message, args));              // 调 RuntimeException(String)
}
public BadRequestException(Throwable cause, String message, Object... args) {
  super(String.format(message, args), cause);       // 调 RuntimeException(String, Throwable)
}

// 修改后
public BadRequestException(String message, Object... args) {
  super(message, args);                             // 调 RESTException(String, Object...)
}
public BadRequestException(Throwable cause, String message, Object... args) {
  super(cause, message, args);                      // 调 RESTException(Throwable, String, Object...)
}
```

`RESTException` 的两个构造器（`RESTException(String message, Object... args)` 和 `RESTException(Throwable cause, String message, Object... args)`）内部都做了 `String.format(message, args)`，所以行为等价——消息仍然会被格式化，只是格式化逻辑从子类移到了父类，消除了 `BadRequestException`、`ForbiddenException` 等多个子类各自调 `String.format` 的重复。

注意 `cause` 版本的参数顺序：旧代码是 `super(formattedMessage, cause)`（RuntimeException 的 `(String, Throwable)` 顺序），新代码是 `super(cause, message, args)`（RESTException 的 `(Throwable, String, Object...)` 顺序）。两者都正确传递了 cause 和 message，只是遵循各自父类的签名约定。

### `api/src/main/java/org/apache/iceberg/exceptions/ForbiddenException.java`

**修改目的**：与 `BadRequestException` 完全对称——改为继承 `RESTException`，构造器委托给父类格式化。

**工作逻辑**：改动模式与 `BadRequestException` 一一对应：类声明 `extends RESTException implements CleanableFailure`，两个构造器分别改为 `super(message, args)` 和 `super(cause, message, args)`。不再赘述。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：在 `Builder.replaceTransaction()` 中包裹 `viewExists` 调用，使表操作在不支持视图的 REST 服务端上不失败。

**工作逻辑**：

新增 import `org.apache.iceberg.exceptions.RESTException`。

`replaceTransaction()` 方法的开头从：

```java
@Override
public Transaction replaceTransaction() {
  if (viewExists(context, ident)) {
    throw new AlreadyExistsException("View with same name already exists: %s", ident);
  }

  LoadTableResponse response = loadInternal(context, ident, snapshotMode);
  ...
```

改为：

```java
@Override
public Transaction replaceTransaction() {
  try {
    if (viewExists(context, ident)) {
      throw new AlreadyExistsException("View with same name already exists: %s", ident);
    }
  } catch (RESTException | UnsupportedOperationException e) {
    // don't fail if the server doesn't support views, which could be due to:
    // 1. server or backing catalog doesn't support views
    // 2. newer client talks to an older server that doesn't support views
    LOG.debug("Failed to check whether view {} exists", ident, e);
  }

  LoadTableResponse response = loadInternal(context, ident, snapshotMode);
  ...
```

关键点：

1. **`AlreadyExistsException` 不在 catch 范围内**。如果 `viewExists` 返回 true（视图确实存在），抛出的 `AlreadyExistsException` 会正常冒泡——这是期望行为，因为同名视图确实冲突。try-catch 只捕获"无法判断视图是否存在"的通信类异常（`RESTException`）和"不支持该操作"的 `UnsupportedOperationException`。

2. **catch 后只打 debug 日志，不抛出**。相当于把"视图检查失败"降级为"假设无同名视图"，让表替换流程继续。这是合理的：表替换是核心功能，视图冲突检查是附加保护；在服务端不支持视图的场景下，根本不可能存在视图，自然没有冲突。

3. **`viewExists` 的调用链**：`viewExists(context, ident)` 是 `ViewSessionCatalog` 接口的 default 方法，内部调 `loadView(context, identifier)`，catch `NoSuchViewException` 返回 false。`RESTSessionCatalog.loadView` 发 `GET /v1/views/{view}` 请求，用 `ErrorHandlers.viewErrorHandler()` 处理错误——HTTP 400 抛 `BadRequestException`、403 抛 `ForbiddenException`、404 抛 `NoSuchViewException`。修改前 400/403 不被 `replaceTransaction` 捕获（因为不是 `RESTException` 子类）；修改后它们是 `RESTException` 子类，会被 catch。

4. **影响 `createOrReplaceTransaction()`**：该方法调用 `replaceTransaction()`，在 `NoSuchTableException` 时回退到 `createTransaction()`。修改后，`createOrReplaceTransaction()` 也间接受益——不再因为视图检查失败而报错。

## 小结

这个提交修复了一个 REST Catalog 的兼容性回退：支持视图的新客户端连到不支持视图的旧服务端时，表替换操作（`replaceTransaction`/`createOrReplaceTransaction`）会因为 `viewExists` 检查抛出 `BadRequestException`/`ForbiddenException` 而失败。修复手段是把这两个异常的父类从 `RuntimeException` 改为 `RESTException`（语义上更正确，也让它们能被 `catch (RESTException)` 兜住），并在 `replaceTransaction` 里把视图检查包进 try-catch 降级处理。改动量小（3 文件、+16/-8 行），但修复的兼容性问题影响面较大。

**影响范围**：

- **异常继承树变更**：`BadRequestException` 和 `ForbiddenException` 现在是 `RESTException` 的子类。任何 `catch (RESTException)` 的代码现在会额外捕获这两个异常——这通常是期望行为（它们本来就是 REST 通信异常）。但如果有代码先 `catch (BadRequestException)` 再 `catch (RESTException)`，顺序仍然正确（子类在前）；如果只 `catch (RESTException)` 而之前期望 `BadRequestException` 冒泡到别处处理，行为会变化。需要审视现有 catch 链。
- **`CleanableFailure` 标记不变**：两个异常仍实现 `CleanableFailure`，提交清理逻辑不受影响。
- **表操作兼容性恢复**：不支持视图的 REST 服务端上，`replaceTransaction` 和 `createOrReplaceTransaction` 恢复正常工作。

**回迁到 1.4.x 的注意事项**：

1. **1.4.x 可能还没有视图支持**。从当前工作树看，1.4.x 的 `RESTSessionCatalog extends BaseSessionCatalog`（不是 `BaseViewSessionCatalog`），且 `replaceTransaction()` 里没有 `viewExists` 调用。这意味着这个提交**不直接适用于 1.4.x**——1.4.x 的 `replaceTransaction` 根本不做视图检查，自然不会因视图不支持而失败。
2. **异常继承树变更可以独立回迁**。即使 1.4.x 没有视图支持，把 `BadRequestException` 和 `ForbiddenException` 改为继承 `RESTException` 仍然是合理的改进（语义更准确、减少重复代码），可以单独 cherry-pick 这部分改动，不会引入问题。
3. **如果 1.4.x 后续要回迁视图支持**，则需要把整个视图相关的提交链（包括 `BaseViewSessionCatalog`、`ViewSessionCatalog`、`RESTSessionCatalog` 的视图方法、以及本提交的 try-catch 保护）一起回迁，否则会出现"有视图检查但没兜底"的中间状态，反而引入新的兼容性问题。
4. **构造器参数顺序变化**：`BadRequestException(Throwable, String, Object...)` 的参数顺序与旧版 `BadRequestException(String, Object...)` + cause 不同。如果有外部代码直接构造这两个异常，参数顺序需要适配——但因为是 `@FormatMethod` 标注的，编译期 error-prone 能帮着检查格式化参数。
