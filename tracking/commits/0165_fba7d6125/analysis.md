# 提交 0165：GCP: Use correct Guava imports (#9067)

## 提交信息

- **序号**：0165 / 4088
- **哈希**：fba7d612539fe978e0281e89bb351e821fd02a05
- **短哈希**：fba7d6125
- **日期**：2023-11-14 18:58:25 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：GCP: Use correct Guava imports (#9067)
- **PR/Issue**：#9067

## 总体目的

这个提交修复了 Iceberg GCP 模块（`iceberg-gcp`）中 GCS 输入/输出流使用的 `Lists` 工具类来源错误的问题。在修复前，`GCSInputStream` 与 `GCSOutputStream` 两个类在创建选项列表（`List<BlobSourceOption>` / `List<BlobWriteOption>`）时，导入并使用了 `com.google.api.client.util.Lists`，这是 Google HTTP/API 客户端库（`google-http-client` / `google-api-client`）中的一个内部工具类，而非 Iceberg 项目约定使用的 Guava `Lists`。

这个选择存在多重问题：

第一，Iceberg 项目有一项明确的依赖管理约定——使用"重定位（relocated/shaded）的 Guava"，即把 Guava 的包从 `com.google.common.*` 重定位到 `org.apache.iceberg.relocated.com.google.common.*` 并打包进 `iceberg-bundled-jars`。这样做是为了避免与下游用户应用自身的 Guava 版本产生冲突（Guava 在 Java 生态中被广泛使用且版本碎片化严重，直接依赖往往会引发 `NoSuchMethodError` 等运行时冲突）。Iceberg 各模块统一通过 `org.apache.iceberg.relocated.com.google.common.collect.Lists` 等重定位类来使用 Guava 集合工具。这两个被修复的文件本身就已经导入了重定位的 `Joiner` 和 `Preconditions`（`org.apache.iceberg.relocated.com.google.common.base.Joiner` / `Preconditions`），却唯独 `Lists` 误用了 Google HTTP 客户端的工具类，属于一致性缺陷。

第二，`com.google.api.client.util.Lists` 并非 Guava 的 `Lists`，它只是 Google HTTP 客户端库内部为了减少对 Guava 的硬依赖而写的一个轻量替代品（仅提供 `newArrayList()` 等少量方法）。它是一个"实现细节"性质的工具类，不是稳定的公开 API，上游随时可能重构、改名或移除。继续依赖它会将 Iceberg GCP 模块的稳定性绑定到一个本不该直接依赖的传递依赖上。

第三，从依赖 hygiene 角度看，使用正确的重定位 Guava 能让 `Lists` 的实现来源与 `Joiner`、`Preconditions` 等保持一致（都来自 Iceberg 控制并 shade 过的 Guava 版本），避免同一文件内出现"一半是重定位 Guava、一半是 Google HTTP 客户端内部工具"的混乱局面，也避免未来 `google-http-client` 升级移除该类时引发编译失败或运行时 `NoClassDefFoundError`。

对 Iceberg 演进的意义在于：这是一次虽小但实质性的正确性/健壮性修复，使 GCP 模块的集合工具使用回归项目约定的重定位 Guava 路径，消除对不稳定传递依赖的耦合，与全项目统一的依赖管理策略保持一致。

## 如何达成设计目的

整体设计思路是"用对的 import 替换错的 import"：在 `GCSInputStream` 与 `GCSOutputStream` 两个文件中，分别移除对 `com.google.api.client.util.Lists` 的导入，改为导入 Iceberg 重定位的 Guava `Lists`（`org.apache.iceberg.relocated.com.google.common.collect.Lists`）。由于两个类对 `Lists` 的使用方式（`Lists.newArrayList()`）在两套实现中行为等价，本次修改不改变任何运行时逻辑，只修正依赖来源，属于纯导入级修复，风险极低。改动结构上是对称的两文件各一行删除 + 一行新增。

## 修改详情

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSInputStream.java`

**修改目的**：将 GCS 输入流中创建 `BlobSourceOption` 列表所用的 `Lists` 工具类来源从 Google HTTP 客户端内部工具切换为 Iceberg 重定位的 Guava `Lists`，与同文件已有的 `Joiner`、`Preconditions` 等重定位 Guava 导入保持一致。

**工作逻辑**：

- 移除导入 `import com.google.api.client.util.Lists;`（位于文件第 21 行附近，属于 `com.google.api.client.util` 包，来自 `google-http-client` 传递依赖）。
- 新增导入 `import org.apache.iceberg.relocated.com.google.common.collect.Lists;`（与已有的 `org.apache.iceberg.relocated.com.google.common.base.Joiner`、`Preconditions` 同属重定位 Guava，包结构对齐）。

该类中 `Lists` 的唯一使用点在 [`openChannel()` 方法](gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSInputStream.java#L138)：`List<BlobSourceOption> sourceOptions = Lists.newArrayList();`，用于构建 GCS 读取通道的选项列表（解密 Key、userProject 等）。`Lists.newArrayList()` 在两套 `Lists` 实现中行为一致（都返回一个新的 `ArrayList`），因此切换后逻辑无变化，但实现来源被校正为受 Iceberg shade 控制的 Guava 版本。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSOutputStream.java`

**修改目的**：与输入流对称，将 GCS 输出流中创建 `BlobWriteOption` 列表所用的 `Lists` 工具类来源从 Google HTTP 客户端内部工具切换为 Iceberg 重定位的 Guava `Lists`。

**工作逻辑**：

- 移除导入 `import com.google.api.client.util.Lists;`。
- 新增导入 `import org.apache.iceberg.relocated.com.google.common.collect.Lists;`（与已有的 `org.apache.iceberg.relocated.com.google.common.base.Joiner` 同属重定位 Guava）。

该类中 `Lists` 的唯一使用点在 [`openStream()` 方法](gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSOutputStream.java#L172)：`List<BlobWriteOption> writeOptions = Lists.newArrayList();`，用于构建 GCS 写入通道的选项列表（加密 Key、userProject 等）。同样，`Lists.newArrayList()` 行为不变，仅依赖来源被校正。该文件原本只导入了重定位的 `Joiner`（未导入 `Preconditions`），本次新增的重定位 `Lists` 与之形成一致的重定位 Guava 使用风格。

## 小结

本次提交通过修正 GCP 模块两个 GCS 流类中 `Lists` 工具类的导入来源，从 Google HTTP 客户端内部工具 `com.google.api.client.util.Lists` 切换为 Iceberg 重定位的 Guava `Lists`，使 GCP 模块回归项目统一的依赖管理约定，消除对不稳定传递依赖的耦合，提升 GCP 集成模块的健壮性与一致性。
