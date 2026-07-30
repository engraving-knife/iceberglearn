# 提交 0638：Build: Bump orc from 1.9.2 to 1.9.3

## 提交信息

- **序号**：0638 / 4088
- **哈希**：4de819e80b7031b91d4e40c742959a5cbab96a6e
- **短哈希**：4de819e80
- **日期**：2024-03-27（Wed Mar 27 17:35:38 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump orc from 1.9.2 to 1.9.3 (#10033)
- **PR/Issue**：#10033

## 总体目的

本提交由 GitHub Dependabot 自动生成，将 Apache ORC 从 1.9.2 升级到 1.9.3，是一次 `version-update:semver-patch` 级别的补丁升级。Dependabot 在元数据中声明了两个受影响制品：`org.apache.orc:orc-core` 与 `org.apache.orc:orc-tools`（均为 `direct:production`）。按 semver 规范，patch 升级只包含 bug 修复与向后兼容的改进，不引入破坏性 API 变更。

ORC 在 Iceberg 中的角色是 ORC 文件格式的读写支持底座。Iceberg 的 `:iceberg-orc` 模块依赖 `orc-core`（提供 ORC 文件的 Reader/Writer、TypeDescription、Stripe 读写等核心能力），是 Iceberg 对接 ORC 列式存储格式的实现层；`:iceberg-data` 模块也通过 `orc-core` 提供通用的 ORC 读取入口。`orc-tools` 仅在 `:iceberg-orc` 的测试中以 `testImplementation` 引入，提供 ORC 工具命令用于测试辅助。升级 ORC patch 版本可获取 1.9.2 之后累积的 bug 修复（如读写正确性、Stripe/压缩器相关问题），保障 Iceberg 对 ORC 文件处理的稳定性。

## 如何达成设计目的

Iceberg 采用 Gradle 版本目录集中管理依赖版本。ORC 的版本通过单个版本别名 `orc` 统一声明，再被 `orc-core` 与 `orc-tools` 两个库别名以 `version.ref = "orc"` 引用。因此本次升级只需在 `gradle/libs.versions.toml` 中修改一行：

```toml
- orc = "1.9.2"
+ orc = "1.9.3"
```

两个制品共享同一版本别名，一处改动即同步升级。值得注意的是，`:iceberg-orc` 与 `:iceberg-data` 在 `build.gradle` 中引用 `orc-core` 时使用了一个特殊写法：`implementation("${libs.orc.core.get().module}:${libs.versions.orc.get()}:nohive")`，即显式取 `:nohive` 分类器（classifier）的 `orc-core` 构件，并在引入时 `exclude group: 'org.apache.hadoop'`、`exclude group: 'commons-lang'`、`exclude group: 'com.google.protobuf', module: 'protobuf-java'`、`exclude group: 'org.apache.hive', module: 'hive-storage-api'`（后两者因已被 shade 进 orc-core fat jar 故排除）。这套 exclude/分类器机制是为剥离 Hive 依赖、避免 Hadoop 版本冲突而设。本提交未触碰这些规则，说明升级到 1.9.3 后 ORC 1.9.3 仍发布 `:nohive` 构件且依赖结构未变，既有协调策略仍然有效。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 ORC（核心库与工具库）的锁定版本从 `1.9.2` 提升到 `1.9.3`。

**工作逻辑**：

改动位于版本声明区（第 72 行附近），原行 `orc = "1.9.2"` 被改为 `orc = "1.9.3"`，上下文如下：

```toml
nessie = "0.79.0"
netty-buffer = "4.1.108.Final"
netty-buffer-compat = "4.1.108.Final"
object-client-bundle = "3.3.2"
orc = "1.9.3"   # 由 1.9.2 升级
parquet = "1.13.1"
pig = "0.17.0"
roaringbitmap = "1.0.5"
```

该版本别名被库定义区两个库别名引用（约第 141 行与第 190 行）：

- `orc-core = { module = "org.apache.orc:orc-core", version.ref = "orc" }`
- `orc-tools = { module = "org.apache.orc:orc-tools", version.ref = "orc" }`

二者又被根 `build.gradle` 消费：

- `project(':iceberg-data')` 依赖块（约第 444 行）：`implementation("${libs.orc.core.get().module}:${libs.versions.orc.get()}:nohive") { exclude group: 'org.apache.hadoop'; exclude group: 'commons-lang'; exclude group: 'com.google.protobuf', module: 'protobuf-java'; exclude group: 'org.apache.hive', module: 'hive-storage-api' }`——以 nohive 构件引入 ORC 核心，剥离 Hive/Hadoop 依赖。
- `project(':iceberg-orc')` 依赖块（约第 904 行）：同样以 nohive 构件 `implementation` 引入 `orc-core` 并执行相同 exclude；第 924 行 `testImplementation libs.orc.tools` 在测试期引入 `orc-tools`。

升级后，`orc-core`（nohive）与 `orc-tools` 会按 1.9.3 解析，CI 验证 `:iceberg-orc` 与 `:iceberg-data` 在 1.9.3 下编译并通过 ORC 读写相关测试。

## 小结

本提交是 Dependabot 触发的 ORC patch 升级：仅修改 `gradle/libs.versions.toml` 一行，把 `orc` 由 `1.9.2` 升至 `1.9.3`，连带把 `orc-core` 与 `orc-tools` 两个构件升版。ORC 是 `:iceberg-orc` 模块（生产 + 测试）与 `:iceberg-data` 模块对接 ORC 列式文件格式的核心依赖，以 `:nohive` 分类器 + 一组 exclude 规则剥离 Hive 依赖。

- **影响范围**：仅依赖版本声明一处，无源码或测试代码改动；运行时影响为引入 ORC 1.9.3 的 bug 修复。对 Iceberg 公共 API 无影响。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支当前的 `orc` 版本为 `1.9.1`，比本提交的前置版本 `1.9.2` 还低一个 patch。1.4.x 上 `:iceberg-orc` 模块与 `:iceberg-data` 模块均存在，且 `orc-core`/`orc-tools` 库别名与 nohive 引用方式一致，cherry-pick 上下文行 `orc = "1.9.2"` 在 1.4.x 上实际为 `orc = "1.9.1"`，会产生小幅冲突但可手工解决（直接把 1.4.x 的 `1.9.1` 改为 `1.9.3`，跨越 1.9.2 与 1.9.3 两个 patch）。因仍是同一 1.9.x minor 内的 patch 跳跃，API 兼容性风险低，回迁可行性较高；建议回迁后回归 `:iceberg-orc` 与 `:iceberg-data` 的 ORC 读写测试。
