# 提交 0114：API, Core: Add uuid() to View (#8851)

## 提交信息

- **序号**：0114 / 4088
- **哈希**：4433aa8c62002e2f3c589c7d3d8ef61dee8284b1
- **短哈希**：4433aa8c6
- **日期**：2023-10-31 07:57:49 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：API, Core: Add uuid() to View (#8851)
- **PR/Issue**：#8851

## 总体目的

这个提交要解决的是 View（视图）API 缺少对外暴露稳定标识符的问题。在 Iceberg 的视图元数据模型里，[`ViewMetadata`](../../../../core/src/main/java/org/apache/iceberg/view/ViewMetadata.java) 早已持有一个 `String uuid()` 字段：视图元数据在构建时（[`ViewMetadata`](../../../../core/src/main/java/org/apache/iceberg/view/ViewMetadata.java) 第 607 行 `null == uuid ? UUID.randomUUID().toString() : uuid`）会分配一个 UUID 字符串，且该 UUID 一旦赋值就不可变更（Builder 中 `Preconditions.checkArgument(uuid == null || newUUID.equals(uuid), "Cannot reassign uuid")`，并通过 `MetadataUpdate.AssignUUID` 记录变更）。也就是说，视图在后端元数据层已经具备一个全局唯一、不可变的标识符，但面向用户的 [`View`](../../../../api/src/main/java/org/apache/iceberg/view/View.java) 接口却没有暴露访问它的方法。

这造成两个问题：第一，调用方无法通过公共 API 拿到视图的 UUID，只能绕道访问内部 `ViewMetadata`，破坏了 API 封装；第二，Iceberg 的视图规范（View Spec）要求视图具备稳定标识，以便跨 catalog、跨引用（如分支/标签引用视图）时能唯一识别一个视图实体，缺少公共 `uuid()` 方法使规范在 API 层面落地不全。本提交由 Eduard Tudenhoefner 在 PR #8851 中补齐这一缺口：在 [`View`](../../../../api/src/main/java/org/apache/iceberg/view/View.java) 接口增加 `default UUID uuid()`，并在 [`BaseView`](../../../../core/src/main/java/org/apache/iceberg/view/BaseView.java) 中实现为从 `ops.current().uuid()` 解析出 `java.util.UUID`。

对 Iceberg 演进的意义在于：这是 View 规范在 Java API 层面持续完善的一步，使视图与表一样具备对外可读的稳定标识，为后续基于 UUID 的视图引用、跨 catalog 视图识别、REST catalog 视图端点等能力打下 API 基础。属于 Iceberg 1.x 视图功能成熟化进程中的实质性增量。

## 如何达成设计目的

整体设计思路是"接口默认抛异常 + 实现类覆盖"。在 [`View`](../../../../api/src/main/java/org/apache/iceberg/view/View.java) 接口里新增一个 `default UUID uuid()` 方法，默认实现抛 `UnsupportedOperationException("Retrieving a view's uuid is not supported")`，与接口中已有的 `updateLocation()` 默认实现保持同样的"可选能力"风格——即不强制所有 View 实现都支持，但提供标准入口。然后在 [`BaseView`](../../../../core/src/main/java/org/apache/iceberg/view/BaseView.java) 中覆盖该方法，调用 `ops.current().uuid()` 拿到元数据里的 UUID 字符串，用 `UUID.fromString(...)` 转成 `java.util.UUID` 返回。最后在抽象测试基类 [`ViewCatalogTests`](../../../../core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java) 中加一条断言，验证新建视图的 `view.uuid()` 等于元数据里的 UUID。改动横跨 api/core 两个模块共 3 个文件、19 行新增。

## 修改详情

### `api/src/main/java/org/apache/iceberg/view/View.java`

**修改目的**：在 View 接口增加 `uuid()` 方法，对外暴露视图的稳定唯一标识。

**工作逻辑**：

1. 新增 import `java.util.UUID`。

2. 在接口末尾（`updateLocation()` 默认实现之后）新增默认方法：
   ```java
   /**
    * Returns the view's UUID
    *
    * @return the view's UUID
    */
   default UUID uuid() {
     throw new UnsupportedOperationException("Retrieving a view's uuid is not supported");
   }
   ```
   采用 `default` + 抛异常的模式，与同接口中 `updateLocation()` 的处理方式一致，保证向后兼容：既有的 `View` 实现类不强制实现此方法，但任何想暴露 UUID 的实现（如 `BaseView`）可覆盖它。返回类型选用 `java.util.UUID` 而非元数据层的 `String`，使公共 API 携带强类型标识符，避免调用方反复做字符串解析。

### `core/src/main/java/org/apache/iceberg/view/BaseView.java`

**修改目的**：为 `BaseView` 提供 `uuid()` 的具体实现，从视图元数据读取并解析 UUID。

**工作逻辑**：

1. 新增 import `java.util.UUID`。

2. 在 `updateLocation()` 实现之后新增：
   ```java
   @Override
   public UUID uuid() {
     return UUID.fromString(ops.current().uuid());
   }
   ```
   其中 `ops` 是 `BaseView` 持有的 `ViewOperations`，`ops.current()` 返回当前 [`ViewMetadata`](../../../../core/src/main/java/org/apache/iceberg/view/ViewMetadata.java)，其 `String uuid()` 即元数据中存储的 UUID 字符串（视图创建时由 `UUID.randomUUID().toString()` 生成并写入元数据）。`UUID.fromString` 把该字符串解析为 `java.util.UUID`。由于 `ViewMetadata` 保证 UUID 一旦赋值不可重赋（`Cannot reassign uuid` 校验），该方法返回值在视图生命周期内稳定。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java`

**修改目的**：在视图 catalog 抽象测试基类中验证 `view.uuid()` 与元数据中的 UUID 一致，确保所有 View catalog 实现都正确暴露 UUID。

**工作逻辑**：

1. 新增 import `java.util.UUID`。

2. 在视图创建后的"validate view settings"段（紧跟 `assertThat(view.uuid())` 之前，`assertThat(view.name())` 之前）新增断言：
   ```java
   assertThat(view.uuid())
       .isEqualTo(UUID.fromString(((BaseView) view).operations().current().uuid()));
   ```
   该断言把公共 API `view.uuid()` 的返回值，与直接从 `BaseView` 内部 `operations().current().uuid()` 取到的元数据 UUID 做相等比较，二者都经过 `UUID.fromString` 解析，从而同时验证：(a) `BaseView.uuid()` 实现正确读取了元数据；(b) 公共 API 与内部元数据一致。由于 `ViewCatalogTests` 是抽象基类，被所有 View catalog 测试（JDBC、REST、Hive、内存等）继承执行，这条断言会覆盖所有 catalog 实现，统一校验标准。

## 小结

通过在 [`View`](../../../../api/src/main/java/org/apache/iceberg/view/View.java) 接口新增 `default UUID uuid()` 并在 [`BaseView`](../../../../core/src/main/java/org/apache/iceberg/view/BaseView.java) 中实现为从元数据解析 UUID，本提交补齐了视图公共 API 缺失的稳定标识符能力，是 Iceberg View 规范在 Java API 层面持续完善的一步实质性增量。
