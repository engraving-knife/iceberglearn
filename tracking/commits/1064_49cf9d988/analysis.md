# 提交 1064：Core: V3 Metadata Upgrade Validation and Testing (#10861)

## 提交信息

- **序号**：1064 / 4088
- **哈希**：49cf9d98818b7d40c9fb7602274b4ba0fd83d82a
- **短哈希**：49cf9d988
- **日期**：2024-08-16 18:08:25 -0500
- **作者**：Jonathan Leang <leangjonathan@gmail.com>
- **提交说明**：Core: V3 Metadata Upgrade Validation and Testing (#10861)
- **PR/Issue**：#10861

## 总体目的

Iceberg 表格式版本（format version）目前支持 v1、v2，社区正在推进 v3 规范与实现。表元数据升级（upgrade）是核心操作之一，`TableMetadata.upgradeToFormatVersion(...)`、`buildReplacement(...)`、`replaceProperties(...)` 等路径都会触发格式版本变更，并产生 `MetadataUpdate.UpgradeFormatVersion` 这种 changes 条目；`UpdateRequirements` 也会对此类变更做校验。

之前针对格式版本升级的测试大多写死 v1 → v2 这一条路径，参数化集合只包含 `Arrays.asList(1)`，断言中硬编码 "Cannot downgrade v2 table to v1"、"Cannot upgrade table to unsupported format version: v4 (supported: v3)" 等字符串，无法覆盖 v1 → v3、v2 → v3 等新的升级路径，也难以在将来支持 v3 后继续复用。

本提交的目的是为即将到来的 v3 表格式版本做测试准备：把所有"v1 升 v2"相关的测试改造成基于参数的版本无关测试，让其能自动覆盖从任一支持的版本升级到任一更高版本（包括 v3）的所有组合，并验证 upgrade 产生的 changes、断言消息、forUpdateTable 校验等行为在 v3 路径下同样成立。这属于 V3 元数据升级能力的"测试先行"工作，确保后续真正放开 `SUPPORTED_TABLE_FORMAT_VERSION = 3` 时不会因测试盲区而引入回归。

## 如何达成设计目的

整体思路是把"写死 v1/v2"的测试抽象成"遍历所有合法升级路径"的参数化测试：

1. 在 `TestFormatVersions` 中把 `parameters()` 由 `Arrays.asList(1)` 改为 `Arrays.asList(1, 2)`，让该参数化测试类同时覆盖 v1 和 v2 作为基础版本；断言中把硬编码的"升到 v2"改成 `formatVersion + 1`，让"升级一格"的行为对任意基础版本都成立。
2. 新增 `testFormatVersionUpgradeToLatest`：直接升级到当前支持的最高版本（`TableMetadata.SUPPORTED_TABLE_FORMAT_VERSION`），验证 changes 中只包含一个 `UpgradeFormatVersion` 且目标版本正确，commit 后表的当前版本也同步。
3. 对降级与不支持版本的测试用 `String.format` 动态构造期望异常消息，使其对任意 `formatVersion` 都能匹配，而不写死 "v2"/"v1" 字面量。
4. 在 `TestTableMetadata` 中引入 `upgradeFormatVersionProvider`：用 `IntStream` 生成所有"低 → 高"合法升级组合 `(baseFormatVersion, newFormatVersion)`，把原先两个写死 v1→v2 的 `@Test` 改为 `@ParameterizedTest + @MethodSource`，自动覆盖 v1→v2、v1→v3、v2→v3 等所有组合。
5. 在 `TestUpdateRequirements` 中把 `upgradeFormatVersion` 测试改为 `@ParameterizedTest + @ValueSource(ints = {2, 3})`，验证 `UpdateRequirements.forUpdateTable(...)` 在升级到 v2 或 v3 时均能正确生成并校验 `UpgradeFormatVersion` requirement。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestFormatVersions.java`

**修改目的**：让原仅覆盖 v1 的参数化测试同时覆盖 v1 和 v2，并把"升级一格""降级""不支持版本"等用例改造为对任意 `formatVersion` 都成立的写法，同时新增"升级到最新版本"用例。

**工作逻辑**：

- `parameters()` 改为 `Arrays.asList(1, 2)`，使该类所有 `@TestTemplate` 用例分别在 v1、v2 表上执行一次；
- `testDefaultFormatVersion` 断言由 `isEqualTo(1)` 改为 `isEqualTo(formatVersion)`；
- `testFormatVersionUpgrade`：把目标版本写成 `newFormatVersion = formatVersion + 1`，构造 `newTableMetadata` 后通过流式过滤断言其 `changes()` 中恰好包含一个 `MetadataUpdate.UpgradeFormatVersion` 且其 `formatVersion()` 等于 `newFormatVersion`，再 commit 并断言当前版本已升级；这强化了对 changes 内容的验证而不仅仅看最终版本号；
- 新增 `testFormatVersionUpgradeToLatest`：把当前表升级到 `TableMetadata.SUPPORTED_TABLE_FORMAT_VERSION`，断言 changes 中仅有一个 UpgradeFormatVersion 且目标版本等于该常量，commit 后表的当前版本也等于该常量；
- `testFormatVersionDowngrade`：先把表升级到 `formatVersion + 1`，再尝试降级回 `formatVersion`，断言抛出 `IllegalArgumentException` 且消息为 `String.format("Cannot downgrade v%d table to v%d", newFormatVersion, formatVersion)`，让消息验证对任意版本生效；
- `testFormatVersionUpgradeNotSupported`：尝试升级到 `TableMetadata.SUPPORTED_TABLE_FORMAT_VERSION + 1`，断言抛出 `IllegalArgumentException` 且消息为 `String.format("Cannot upgrade table to unsupported format version: v%d (supported: v%d)", unsupportedFormatVersion, TableMetadata.SUPPORTED_TABLE_FORMAT_VERSION)`；最终表的当前版本仍为 `formatVersion`。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`

**修改目的**：把原先只测 v1→v2 的两个 `@Test`（`testReplaceV1MetadataToV2ThroughTableProperty`、`testUpgradeV1MetadataToV2ThroughTableProperty`）改造为遍历所有合法升级路径的参数化测试，自动覆盖 v3 路径。

**工作逻辑**：

- 新增 import：`org.junit.jupiter.params.provider.Arguments.arguments`、`java.util.stream.IntStream`、`java.util.stream.Stream`、`org.junit.jupiter.params.ParameterizedTest`、`org.junit.jupiter.params.provider.Arguments`、`org.junit.jupiter.params.provider.MethodSource`；
- 新增静态工厂方法 `upgradeFormatVersionProvider()`：用 `IntStream.range(1, SUPPORTED_TABLE_FORMAT_VERSION)` 枚举所有 `baseFormatVersion`，再对每个基础版本用 `IntStream.rangeClosed(baseFormatVersion + 1, SUPPORTED_TABLE_FORMAT_VERSION)` 枚举所有更高的 `newFormatVersion`，组合成 `arguments(baseFormatVersion, newFormatVersion)` 流；这样在 `SUPPORTED_TABLE_FORMAT_VERSION = 3` 时会自动产生 (1,2)、(1,3)、(2,3) 三条参数；
- 把 `testReplaceV1MetadataToV2ThroughTableProperty` 重命名为 `testReplaceMetadataThroughTableProperty(int baseFormatVersion, int newFormatVersion)`，加 `@ParameterizedTest + @MethodSource("upgradeFormatVersionProvider")`：构造一个 `format-version = baseFormatVersion` 的元数据，再通过 `buildReplacement(...)` 用 `format-version = newFormatVersion` 替换，断言最终 `meta.formatVersion()` 等于 `newFormatVersion`，且 properties 中 `format-version` 键被消费掉、其他新旧 key 都保留；
- 把 `testUpgradeV1MetadataToV2ThroughTableProperty` 重命名为 `testUpgradeMetadataThroughTableProperty(int baseFormatVersion, int newFormatVersion)`，同样改为参数化：用 `replaceProperties(...)` 把 `format-version` 从 `baseFormatVersion` 改为 `newFormatVersion`，断言 `meta.formatVersion()` 等于 `newFormatVersion`，且 properties 中不再包含 `format-version` 键，只包含其他新 property。

### `core/src/test/java/org/apache/iceberg/TestUpdateRequirements.java`

**修改目的**：把 `upgradeFormatVersion` 测试由只测升到 v2 改为参数化测试，覆盖升到 v2 和 v3 两种情况，确保 `UpdateRequirements.forUpdateTable(...)` 在 v3 升级路径下也能正确生成并校验 requirement。

**工作逻辑**：

- 新增 import：`org.junit.jupiter.params.ParameterizedTest`、`org.junit.jupiter.params.provider.ValueSource`；
- 把 `@Test` 改为 `@ParameterizedTest`，加 `@ValueSource(ints = {2, 3})`，方法签名增加参数 `int formatVersion`；
- 把 `new MetadataUpdate.UpgradeFormatVersion(2)` 改为 `new MetadataUpdate.UpgradeFormatVersion(formatVersion)`，对每条 requirement 调用 `req.validate(metadata)` 进行校验；
- 后续断言部分（断言 requirement 列表结构与内容）保持原样，但因为输入参数化，断言会自动作用于 v2 与 v3 两种升级请求。

## 小结

- **成效**：把原先写死 v1→v2 的格式版本升级测试全部改造为参数化测试，自动覆盖所有"低版本 → 高版本"的合法升级组合，新增"升级到最新版本"用例，强化了对 `changes()` 内容、异常消息、`UpdateRequirements` 校验的覆盖。一旦后续把 `SUPPORTED_TABLE_FORMAT_VERSION` 提升到 3，这些测试会自动覆盖 v1→v3、v2→v3 等新路径，无需再改测试代码。
- **影响范围**：仅修改 3 个测试文件（`TestFormatVersions.java`、`TestTableMetadata.java`、`TestUpdateRequirements.java`），全部位于 `core/src/test/java`，不影响生产代码；但因为部分用例现在会基于 `TableMetadata.SUPPORTED_TABLE_FORMAT_VERSION` 自动展开，当该常量由 2 提升到 3 时，测试用例数量会自动增加，对未来 V3 落地具有保护作用。
- **回迁到 1.4.x 的注意事项**：本提交属于 V3 测试先行工作，本质是测试增强，不引入新功能，回迁到 1.4.x 风险较低。但需注意：1.4.x 上 `TableMetadata.SUPPORTED_TABLE_FORMAT_VERSION` 仍为 2，回迁后参数化集合实际只展开 v1→v2 一条路径，新增的"升级到最新"用例相当于升级到 v2，不会带来新的覆盖增量；同时 1.4.x 上的 `TestFormatVersions.parameters()` 是否已是 `Arrays.asList(1)`、`TestTableMetadata` 是否已有这两个被改造的方法需要核对，若 1.4.x 上方法名或上下文有差异，cherry-pick 时需手工调整。整体来看，本提交对 1.4.x 不是必需（1.4.x 不打算支持 v3），可选择不回迁。
