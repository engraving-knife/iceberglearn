# 提交 0640：Build: Bump com.esotericsoftware:kryo from 4.0.2 to 4.0.3

## 提交信息

- **序号**：0640 / 4088
- **哈希**：bd4603529ebb32534c90de377919273a007d1ec4
- **短哈希**：bd4603529
- **日期**：2024-03-27（Wed Mar 27 20:10:50 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.esotericsoftware:kryo from 4.0.2 to 4.0.3 (#9984)
- **PR/Issue**：#9984

## 总体目的

本提交由 GitHub Dependabot 自动生成，将 `com.esotericsoftware:kryo` 从 4.0.2 升级到 4.0.3，是一次 `version-update:semver-patch` 级别的补丁升级，依赖类型为 `direct:production`。Kryo 是一个高性能的 Java 对象图序列化框架，常用于在测试中把对象快速序列化/反序列化以验证 Iceberg 数据模型在序列化往返（serialize/deserialize round-trip）后的正确性。

需要特别区分：Iceberg 的版本目录中存在**两个独立的 Kryo 构件**——本提交升级的 `com.esotericsoftware:kryo`（非 shaded，版本别名 `esotericsoftware-kryo`）与另一个 `com.esotericsoftware:kryo-shaded`（shaded 版本，版本别名 `kryo-shaded`，此时已为 `4.0.3`）。两者是不同的 Maven 制品、由不同的版本别名驱动。本提交只触碰前者（`esotericsoftware-kryo`），后者（`kryo-shaded`）不受影响。

Kryo（非 shaded）在 Iceberg 中的角色是**测试期序列化辅助**：在 `:iceberg-api`、`:iceberg-core`、`:iceberg-aws`、`:iceberg-azure`、`:iceberg-gcp` 五个模块中均以 `testImplementation libs.esotericsoftware.kryo` 引入，用于在单元测试中验证 Iceberg 的数据对象（如 `StructLike`、`Record`、各模块的模型类等）经 Kryo 序列化往返后保持语义一致。它不进入任何发布产物，只服务于测试。本次升级的目的即让测试侧 Kryo 跟进 4.0.3 patch，获取 4.0.2 之后的 bug 修复，保证测试序列化路径的稳定性。

## 如何达成设计目的

Iceberg 采用 Gradle 版本目录集中管理依赖版本。本提交升级的 Kryo 版本通过版本别名 `esotericsoftware-kryo` 声明，再被库别名 `esotericsoftware-kryo` 以 `version.ref = "esotericsoftware-kryo"` 引用（注意别名与版本同名）。因此本次升级只需在 `gradle/libs.versions.toml` 中修改一行：

```toml
- esotericsoftware-kryo = "4.0.2"
+ esotericsoftware-kryo = "4.0.3"
```

库别名被根 `build.gradle` 五个模块的依赖块以 `testImplementation libs.esotericsoftware.kryo` 消费。一处版本号变更即把五个模块测试类路径上的 Kryo（非 shaded）构件统一升到 4.0.3。由于 Kryo 仅在测试期使用，升级不改变任何发布产物的依赖，风险面极小。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把测试用的 `com.esotericsoftware:kryo`（非 shaded）锁定版本从 `4.0.2` 提升到 `4.0.3`。

**工作逻辑**：

改动位于版本声明区（第 38 行附近），原行 `esotericsoftware-kryo = "4.0.2"` 被改为 `esotericsoftware-kryo = "4.0.3"`，上下文如下：

```toml
caffeine = "2.9.3"
calcite = "1.10.0"
delta-standalone = "3.1.0"
delta-spark = "3.1.0"
esotericsoftware-kryo = "4.0.3"   # 由 4.0.2 升级
errorprone-annotations = "2.26.1"
findbugs-jsr305 = "3.0.2"
flink116 = { strictly = "1.16.3"}
```

该版本别名被库定义区库别名引用（约第 189 行）：

- `esotericsoftware-kryo = { module = "com.esotericsoftware:kryo", version.ref = "esotericsoftware-kryo" }`

该库别名又被根 `build.gradle` 五个模块的依赖块以 `testImplementation libs.esotericsoftware.kryo` 消费（约第 343、416、555、620、785 行）：

- `project(':iceberg-api')`（第 343 行）——API 模块测试。
- `project(':iceberg-core')`（第 416 行）——核心模型测试。
- `project(':iceberg-aws')`（第 555 行）——AWS 集成测试。
- `project(':iceberg-azure')`（第 620 行）——Azure 集成测试。
- `project(':iceberg-gcp')`（第 785 行）——GCP 集成测试。

注意：本提交**未触碰** `kryo-shaded = "4.0.3"`（约第 36 行，对应 `com.esotericsoftware:kryo-shaded` 制品，由 `kryo-shaded` 版本别名独立驱动）。两个 Kryo 构件各自独立版本化，本次只升非 shaded 的那个。

## 小结

本提交是 Dependabot 触发的 Kryo（非 shaded）patch 升级：仅修改 `gradle/libs.versions.toml` 一行，把 `esotericsoftware-kryo` 由 `4.0.2` 升至 `4.0.3`。Kryo 是 `:iceberg-api`、`:iceberg-core`、`:iceberg-aws`、`:iceberg-azure`、`:iceberg-gcp` 五个模块的测试期序列化辅助依赖，以 `testImplementation` 形式消费，不进入任何发布产物。本提交不影响另一个独立的 `kryo-shaded` 构件（其时已为 4.0.3，未受改动）。

- **影响范围**：仅依赖版本声明一处，无源码或测试代码改动；影响为上述五个模块测试类路径上的 Kryo（非 shaded）升至 4.0.3。对 Iceberg 公共 API 与发布产物无影响。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支当前的 `esotericsoftware-kryo` 版本正好是 `4.0.2`，与本提交的前置版本完全一致，因此 cherry-pick 可干净应用（上下文行匹配，无冲突）。1.4.x 上同样有五个模块以 `testImplementation libs.esotericsoftware.kryo` 消费该构件，回迁后测试侧 Kryo 升至 4.0.3，回归测试即可。另需注意 1.4.x 的 `kryo-shaded` 此时也已是 `4.0.3`，与本提交升后的 `esotericsoftware-kryo` 4.0.3 版本号一致但仍是两个独立制品，互不影响。本提交是五个升级中回迁 1.4.x 最为平滑的一项。
