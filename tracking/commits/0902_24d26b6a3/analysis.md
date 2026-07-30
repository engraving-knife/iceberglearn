# 提交 0902：Core: Fix create v1 table on REST Catalog (#10369)

## 提交信息

- **序号**：0902 / 4088
- **哈希**：24d26b6a35a1287531e72357691d9dbd3d7f79bd
- **短哈希**：24d26b6a3
- **日期**：2024-07-05 09:08:44 +0200
- **作者**：dongwang
- **提交说明**：Core: Fix create v1 table on REST Catalog (#10369)
- **PR/Issue**：#10369

## 总体目的

当用户通过 REST Catalog 创建表并在创建请求中显式指定 `format-version=1` 时，最终生成的表的 format-version 仍然是 2（默认值），用户的设置被忽略。该缺陷源于 `CatalogHandlers.create` 在通过 `TableMetadata.buildFromEmpty()` 构造空表元数据时，硬编码使用了 `DEFAULT_TABLE_FORMAT_VERSION`（即 2），随后即便 `UpgradeFormatVersion` 这类 update 被应用到 builder 上，也无法把已经写死的 format-version 改回去（或者在实际流转中被默认值覆盖），导致 v1 表创建请求失败或被静默升到 v2。

本提交的目的是修复 REST Catalog 路径下创建 v1 表的语义缺陷，让用户在 createTableTransaction / createTable 请求中指定的 format-version 真正生效，使 REST Catalog 与 JDBC、Hadoop 等 Catalog 在该行为上保持一致。

## 如何达成设计目的

整体设计思路分两步：

1. 给 `TableMetadata.Builder` 增加一个可以接收初始 format-version 的构造入口（`Builder(int formatVersion)` 与对应的 `buildFromEmpty(int formatVersion)` 工厂方法），保留旧的无参构造以兼容已有调用点。这样 builder 在初始化时就能持有用户期望的 format-version，而不是固定写死 2。

2. 在 `CatalogHandlers.create` 中，先从 `UpdateTableRequest.updates()` 中筛出 `UpgradeFormatVersion` 这一类 update，取出其声明的 format-version，并据此调用对应的 `buildFromEmpty(int)` 工厂方法构造 builder；如果请求中没有该 update（例如未显式指定 format-version），则仍走默认的 `buildFromEmpty()`，保持原有行为不变。

同时在 `CatalogTests` 中加入参数化测试（覆盖 formatVersion=1 与 2，且分别覆盖 create 和 replace 两条事务路径），用于在所有继承 `CatalogTests` 的 Catalog 实现上验证该行为，避免回归。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：让 `Builder` 支持以调用方指定的 format-version 作为初始值，而不是固定使用 `DEFAULT_TABLE_FORMAT_VERSION`。

**工作逻辑**：
- 新增重载方法 `public static Builder buildFromEmpty(int formatVersion)`，原无参版本 `buildFromEmpty()` 改为转发到新方法并传入 `DEFAULT_TABLE_FORMAT_VERSION`，保持向后兼容。
- `Builder` 内部新增带参构造 `public Builder(int formatVersion)`，把传入的 format-version 直接赋给 `this.formatVersion`；原无参构造 `Builder()` 改为转发到带参构造并传入默认版本。

这样上层可以根据是否拿到 `UpgradeFormatVersion` 决定调用哪个工厂方法，从而让 format-version 在 builder 初始化阶段就正确。

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java`

**修改目的**：在 `create` 私有方法中识别请求里的 `UpgradeFormatVersion`，并据此选择正确的 `buildFromEmpty` 重载。

**工作逻辑**：
- 新增 import：`java.util.Optional` 和 `org.apache.iceberg.MetadataUpdate.UpgradeFormatVersion`。
- 在 `create` 方法中，先通过 stream 从 `request.updates()` 里过滤出 `UpgradeFormatVersion` 类型的 update，取第一个的 `formatVersion()` 得到一个 `Optional<Integer>`。
- 用 `formatVersion.map(TableMetadata::buildFromEmpty).orElseGet(TableMetadata::buildFromEmpty)` 选择对应的 builder 工厂方法：有显式声明就走带参版本，没有就走默认版本。
- 随后 `request.updates().forEach(update -> update.applyTo(builder))` 保持不变，让所有 update（包括 `UpgradeFormatVersion` 本身）依旧被 apply，逻辑不重复但语义自洽。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java`

**修改目的**：在共享测试基类中加入参数化用例，覆盖 create 与 replace 两条事务路径下 v1/v2 表的创建。

**工作逻辑**：
- 新增 import：`org.junit.jupiter.params.ParameterizedTest` 与 `org.junit.jupiter.params.provider.ValueSource`。
- 新增 `createTableTransaction(int formatVersion)` 与 `replaceTableTransaction(int formatVersion)` 两个参数化测试，`@ValueSource(ints = {1, 2})` 让它们分别在 format-version=1 和 2 下执行。
- 测试通过 `newCreateTableTransaction` / `newReplaceTableTransaction` 提交事务，再用 `loadTable` 读回并断言 `current().formatVersion()` 与传入值一致。由于 `CatalogTests` 是所有 Catalog 实现共享的抽象测试基类，这两个用例会自动在 REST、JDBC、Hadoop 等所有 Catalog 上运行，起到防回归的作用。

## 小结

- **成效**：修复了 REST Catalog 创建 v1 表时 format-version 被忽略的缺陷，使 `format-version=1` 的请求能正确生效；并补齐共享测试基类用例防止回归。
- **影响范围**：`core` 模块下的 `TableMetadata`、`rest/CatalogHandlers` 两个生产文件，以及 `catalog/CatalogTests` 一个共享测试基类；改动范围小且向后兼容。
- **回迁到 1.4.x 的注意事项**：适合回迁。该修复是对 REST Catalog 表创建语义的 bugfix，不引入新 API 形变（仅新增重载方法，未移除/修改既有签名），行为兼容。回迁时需注意 1.4.x 分支的 `TableMetadata.Builder` 是否已有同名重载或同样写死的逻辑，确认无冲突后直接 cherry-pick 即可。同时建议把 `CatalogTests` 的两条参数化用例一并带回，以便在 1.4.x 上获得回归保护。
