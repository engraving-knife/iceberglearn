# 提交 0228：Core: Introduce AssertViewUUID for REST catalog views (#8831)

## 提交信息

- **序号**：0228 / 4088
- **哈希**：d69ba0568a2e07dfb5af233350ad5668d9aef134
- **短哈希**：d69ba0568
- **日期**：2023-12-06
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Introduce AssertViewUUID for REST catalog views (#8831)
- **PR/Issue**：#8831

## 总体目的

这个提交为 Iceberg 的 REST Catalog 视图（view）提交引入一个独立的、语义正确的 UUID 断言需求 `AssertViewUUID`，并修复了 `RESTViewOperations` 在提交视图变更时错误地使用 `AssertTableUUID`（一个针对表的断言）来校验 `ViewMetadata` 的问题。

背景：Iceberg 的提交模型依赖一组 `UpdateRequirement` 来做乐观并发控制——客户端在提交时附带一组"期望当前元数据满足的条件"（例如 UUID 必须匹配），服务端在应用变更前会校验这些条件。表（Table）已有 `AssertTableUUID` 来保证表元数据的 UUID 一致性。视图（View）作为后来加入的特性，其 REST 提交路径 `RESTViewOperations` 当时直接复用了 `AssertTableUUID`：

```java
ImmutableList.of(new UpdateRequirement.AssertTableUUID(base.uuid()))
```

这里 `base` 实际是 `ViewMetadata`。能跑通仅仅是因为 `AssertTableUUID` 当时额外实现了一个 `validate(ViewMetadata base)` 重载。这是一种"借用"——一个名叫 "Table" 的需求类去校验 view 元数据，语义混乱，错误信息也只是泛泛的 "UUID does not match"，而且 `UpdateRequirement` 接口对 `validate(TableMetadata)` 是抽象方法，对 `validate(ViewMetadata)` 是抛异常的 default，两者不对称，使得实现类很容易在错误的类型上触发默认异常。

本提交做了三件事：(1) 把 `validate(TableMetadata)` 也改成抛异常的 default 方法，让两种 `validate` 对称，要求子类只覆盖自己关心的那个；(2) 新增 `AssertViewUUID` 类，专门持有 view UUID 并实现 `validate(ViewMetadata)`，错误信息明确写为 "view UUID does not match"；(3) 把 `AssertTableUUID` 原本那个 `validate(ViewMetadata)` 重载剥离出去（文本上移到新类），让 `AssertTableUUID` 只负责表。配套地在 `UpdateRequirements` 里加 `forReplaceView(ViewMetadata, List<MetadataUpdate>)` 工厂方法，让 `RESTViewOperations` 改用它；并在 JSON 序列化层与 REST OpenAPI 规范里登记新的 `assert-view-uuid` 类型与 `CommitViewRequest.requirements` 字段。

这个改动对 Iceberg 视图特性的成熟度有重要意义：它把视图的提交语义和表彻底分开，让 REST 协议规范、Java 实现与序列化层都正确表达"断言视图 UUID"这一概念，为后续更多针对视图的 requirement（如断言 view 版本、schema 等）打下基础。

## 如何达成设计目的

整体设计是"接口对称化 + 新增专用类 + 工厂方法 + 协议同步"四步走：

1. 修改 `UpdateRequirement` 接口，让 `validate(TableMetadata)` 变成抛异常的 default，与已有的 `validate(ViewMetadata)` default 对称。这样任何 requirement 子类只需要覆盖自己要支持的元数据类型，调用错了类型会得到清晰的 `ValidationException`。
2. 在 `UpdateRequirement` 内新增 `AssertViewUUID` 类，把原本寄居在 `AssertTableUUID` 里的 `validate(ViewMetadata)` 实现迁过来，并改进错误信息。
3. 在 `UpdateRequirements` 工厂类里新增 `forReplaceView`，统一构造视图替换提交所需的 requirement 列表（目前就是单个 `AssertViewUUID`）。
4. 改 `RESTViewOperations` 调用新工厂方法，不再内联构造 `AssertTableUUID`。
5. 在 `UpdateRequirementParser` 注册 `assert-view-uuid` 类型的序列化/反序列化。
6. 在 REST OpenAPI 规范（yaml + py）里定义 `ViewRequirement` 判别式类型与 `AssertViewUUID`，并给 `CommitViewRequest` 加上可选的 `requirements` 字段。
7. 补充覆盖序列化、各类 view 元数据更新场景的单元测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/UpdateRequirement.java`

**修改目的**：让接口对称、新增 `AssertViewUUID`、把视图校验从表断言类中剥离。

**工作逻辑**：
- 把 `void validate(TableMetadata base);` 改为 `default void validate(TableMetadata base) { throw new ValidationException("Cannot validate %s against a table", this.getClass().getSimpleName()); }`，与已有的 `validate(ViewMetadata)` default 对称。两者现在都是"默认抛异常，子类按需覆盖"。
- 在 `AssertTableUUID` 的 `validate(TableMetadata)` 方法后插入闭合大括号，新增 `class AssertViewUUID implements UpdateRequirement`，包含 `private final String uuid` 字段、构造器（带 `Preconditions.checkArgument(uuid != null, ...)`）、`uuid()` 访问器，以及从 `AssertTableUUID` 迁移过来的 `validate(ViewMetadata base)` 实现。错误信息从 `"Requirement failed: UUID does not match: expected %s != %s"` 改为 `"Requirement failed: view UUID does not match: expected %s != %s"`，明确指向 view。
- 这样 `AssertTableUUID` 不再覆盖 `validate(ViewMetadata)`，若被误用于 view 元数据会走接口默认实现抛出 `Cannot validate AssertTableUUID against a view`，从类型层面杜绝了原来的语义混用。

### `core/src/main/java/org/apache/iceberg/UpdateRequirementParser.java`

**修改目的**：让 JSON 序列化层识别并处理 `assert-view-uuid` 类型。

**工作逻辑**：
- 新增常量 `ASSERT_VIEW_UUID = "assert-view-uuid"`。
- 在 `TYPES` 映射里注册 `UpdateRequirement.AssertViewUUID.class -> ASSERT_VIEW_UUID`。
- 在 `write(JsonGenerator, ...)` 的 switch 里加 `case ASSERT_VIEW_UUID: writeAssertViewUUID(...)`，新增 `writeAssertViewUUID` 方法写 `uuid` 字段。
- 在 `fromJson` 的 switch 里加 `case ASSERT_VIEW_UUID: return readAssertViewUUID(jsonNode)`，新增 `readAssertViewUUID` 从节点读 `uuid` 并构造 `AssertViewUUID`。

### `core/src/main/java/org/apache/iceberg/UpdateRequirements.java`

**修改目的**：为视图替换提交提供统一的 requirement 工厂方法。

**工作逻辑**：新增 `public static List<UpdateRequirement> forReplaceView(ViewMetadata base, List<MetadataUpdate> metadataUpdates)`。校验 `base` 与 `metadataUpdates` 非空，构造一个 `Builder(null, false)`（base 传 null 因为视图目前只生成 UUID 断言，不需要表元数据），`require(new UpdateRequirement.AssertViewUUID(base.uuid()))`，再把所有 updates 加入，最后 build 返回。引入了对 `org.apache.iceberg.view.ViewMetadata` 的 import。

### `core/src/main/java/org/apache/iceberg/rest/RESTViewOperations.java`

**修改目的**：把视图提交从借用 `AssertTableUUID` 改为使用新的视图专用 requirement。

**工作逻辑**：在 `commit()` 中把

```java
UpdateTableRequest request =
    UpdateTableRequest.create(
        null,
        ImmutableList.of(new UpdateRequirement.AssertTableUUID(base.uuid())),
        metadata.changes());
```

改为

```java
UpdateTableRequest request =
    UpdateTableRequest.create(
        null, UpdateRequirements.forReplaceView(base, metadata.changes()), metadata.changes());
```

相应地 import 从 `UpdateRequirement` 改为 `UpdateRequirements`，并移除不再需要的 `ImmutableList` import。这是本提交核心的行为修复点。

### `core/src/test/java/org/apache/iceberg/TestUpdateRequirementParser.java`

**修改目的**：覆盖 `AssertViewUUID` 的 JSON 双向序列化。

**工作逻辑**：新增 `testAssertViewUUIDFromJson` 与 `testAssertViewUUIDToJson`，分别断言 `{"type":"assert-view-uuid","uuid":"..."}` 与 `AssertViewUUID` 对象的双向转换；在 `assertEquals` 分发里加 `case ASSERT_VIEW_UUID` 走新的 `compareAssertViewUUID` 比较器，比较两个 `AssertViewUUID` 的 `uuid()` 是否一致。

### `core/src/test/java/org/apache/iceberg/TestUpdateRequirements.java`

**修改目的**：覆盖 `forReplaceView` 在各种 view 元数据更新场景下的行为与失败路径。

**工作逻辑**：引入 `ViewMetadata` mock（`viewMetadata` / `updatedViewMetadata`），在 `before()` 里设置其 `uuid()`。新增一组测试：
- `emptyUpdatesForReplaceView`：空 updates 列表只产生一个 `AssertViewUUID`。
- `assignUUIDToView` / `assignUUIDToViewFailure`：含 `AssignUUID` updates 时只生成单个 `AssertViewUUID`；当 view metadata UUID 不匹配时抛 `CommitFailedException`，信息为 `"Requirement failed: view UUID does not match: expected %s != %s"`。
- `upgradeFormatVersionForView`、`addSchemaForView`、`setAndRemovePropertiesForView`、`setLocationForView`、`addViewVersion`、`setCurrentViewVersion`：覆盖各类 `MetadataUpdate`（升级格式版本、加 schema、增删属性、改 location、加 view 版本、切换当前 view 版本）经过 `forReplaceView` 后都只产生单个 `AssertViewUUID`，并且能成功 validate。
- 新增 `assertViewUUID` 私有断言助手，验证 requirements 列表首元素是 `AssertViewUUID` 且 uuid 与 `viewMetadata.uuid()` 一致。
- 在已有的参数校验测试里补充 `forReplaceView(null, null)` 与 `forReplaceView(viewMetadata, null)` 抛 `IllegalArgumentException` 的断言。

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：在 REST 协议规范中正式登记 view requirement 类型与 `CommitViewRequest.requirements` 字段。

**工作逻辑**：新增 `ViewRequirement` 判别式（discriminator）对象类型，`type` 字段做映射 `assert-view-uuid: '#/components/schemas/AssertViewUUID'`；新增 `AssertViewUUID` schema，`allOf` 引用 `ViewRequirement`，必填 `type`（枚举 `assert-view-uuid`）与 `uuid`（string），描述为 "The view UUID must match the requirement's `uuid`"。在 `CommitViewRequest` 里新增可选 `requirements` 数组字段，`items` 引用 `ViewRequirement`，使视图提交请求可以携带 requirement。

### `open-api/rest-catalog-open-api.py`

**修改目的**：同步 Python 端 OpenAPI 模型。

**工作逻辑**：新增 `class ViewRequirement(BaseModel)`，`__root__: Any = Field(..., discriminator='type')`；在 `CommitViewRequest` 里加 `requirements: Optional[List[ViewRequirement]] = None`。

## 小结

本提交把视图提交的 UUID 断言从语义混乱的 `AssertTableUUID` 借用改为专用的 `AssertViewUUID`，让接口、实现、JSON 序列化与 REST OpenAPI 规范在视图 requirement 上达成一致，是 Iceberg 视图特性走向成熟的关键一步。
