# 提交 1630：Core: Retain current view version during expiration (#12067)

## 提交信息

- **序号**：1630 / 4088
- **哈希**：681ff5772a6d0c7240353f7d8c7350c2e5037f3b
- **短哈希**：681ff5772
- **日期**：2025-01-24（Fri Jan 24 09:36:45 2025 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Retain current view version during expiration
- **PR/Issue**：#12067
- **共同作者**：Christian Thiel <Christian.Thiel@outlook.com>

## 总体目的

Iceberg 的 View（视图）元数据中也维护多个历史 `ViewVersion`，过期策略由表属性 `view.version-history.size`（默认 1）控制。`ViewMetadata.Builder.expireVersions` 在版本数超过保留阈值时，按 versionId 倒序保留前 N 个版本——即只保留"最近 N 个版本"。

但这个逻辑存在一个潜在缺陷：当 `currentVersionId` 指向的当前版本并不在"最近 N 个版本"中时（典型场景：用户通过 time travel 把 current 切回了一个较老的版本，或者通过 `replaceVersion` 替换了中间某个版本），原来的实现会按 ID 倒序取前 N 个，结果当前版本被过期掉，元数据进入"currentVersionId 指向一个已不存在的版本"的非法状态，后续读取直接报错。

本提交修复这个 bug：在 `expireVersions` 中无条件保留当前版本，再用剩余的 N-1 个名额按 ID 倒序回填其他较新版本，确保 current version 永远不会被过期。

## 如何达成设计目的

1. `expireVersions` 签名增加一个参数 `ViewVersion currentVersion`，调用方从 `versionsById.get(currentVersionId)` 取出当前版本传入；
2. 实现上先把 `currentVersion` 加入结果列表；
3. 然后遍历 ID 倒序的前 N 个版本，遇到 currentVersion 跳过（避免重复），并在结果数达到 `numVersionsToKeep` 时提前 break；
4. 单测覆盖：原有 `expireVersions` 单测改造为传入 currentVersion，并新增 `currentViewVersionIsNeverExpired` 集成式测试，验证在 `VERSION_HISTORY_SIZE=1` 且 currentVersionId=1（最老版本）时，重算 metadata 后 current version 1 仍被保留。

## 修改详情

### `core/src/main/java/org/apache/iceberg/view/ViewMetadata.java`（修改，+13/-2）

**修改目的**：修复 `expireVersions` 可能误删当前版本的 bug。

**工作逻辑**：

调用点（约 line 469）：
```
if (versions.size() > numVersionsToKeep) {
  retainedVersions =
      expireVersions(versionsById, numVersionsToKeep, versionsById.get(currentVersionId));
  ...
```

`expireVersions` 改造：
```
static List<ViewVersion> expireVersions(
    Map<Integer, ViewVersion> versionsById, int numVersionsToKeep, ViewVersion currentVersion) {
  // version ids are assigned sequentially. keep the latest versions by ID.
  List<Integer> ids = Lists.newArrayList(versionsById.keySet());
  ids.sort(Comparator.reverseOrder());

  List<ViewVersion> retainedVersions = Lists.newArrayList();
  // always retain the current version
  retainedVersions.add(currentVersion);

  for (int idToKeep : ids.subList(0, numVersionsToKeep)) {
    if (retainedVersions.size() == numVersionsToKeep) {
      break;
    }

    ViewVersion version = versionsById.get(idToKeep);
    if (currentVersion.versionId() != version.versionId()) {
      retainedVersions.add(version);
    }
  }

  return retainedVersions;
}
```

要点：
- 先无条件把 currentVersion 入列表，占据 1 个名额；
- 再按 ID 倒序遍历前 `numVersionsToKeep` 个版本，跳过 currentVersion（避免重复），并在 retainedVersions 达到 `numVersionsToKeep` 时 break；
- 由于 currentVersion 占了一个名额，循环最多再添加 `numVersionsToKeep - 1` 个其他版本，总数恰好等于 `numVersionsToKeep`；
- 边界情形：若 `numVersionsToKeep == 1`，循环里第一次判断 `retainedVersions.size() == 1 == numVersionsToKeep` 立即 break，结果只保留 currentVersion。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadata.java`（修改，+44/-3）

**修改目的**：更新原有 `expireVersions` 单测断言、新增针对"current version 不被过期"的集成测试。

**工作逻辑**：

原有 `expireVersions` 单测（约 line 60+）改为传入 currentVersion：
- `expireVersions(versionsById, 3, v1)` → 仍保留全部 3 个版本；
- `expireVersions(versionsById, 2, v1)` → 期望 `{v1, v3}`（v1 是 current 强制保留，再按 ID 倒序取 v3，v3 != v1 所以加进来，达到 2 个 break；v2 被过期）；
- `expireVersions(versionsById, 1, v1)` → 期望 `{v1}`（currentVersion 占满名额）。

新增 `currentViewVersionIsNeverExpired`：
- 构造 `VERSION_HISTORY_SIZE=1`、添加 3 个版本、`setCurrentVersionId(1)`（指向最老版本）的 `originalViewMetadata`；
- 第一次 build 不会过期（builder 内新加的版本不参与过期），断言 `versions().size()==3`、history 有 1 条记录指向 versionId=1；
- `ViewMetadata.buildFrom(originalViewMetadata).build()` 重新构建触发过期：断言 `versions().size()==1`、`currentVersionId()==1`、`currentVersion()==viewVersionOne`、history 仍有 1 条指向 versionId=1；
- 完整验证"当前版本不会被过期"且 history 与 current 一致。

## 小结

- **成效**：修复了 View 元数据过期逻辑可能把当前版本误删的 bug。该 bug 在 `view.version-history.size` 配置较小、且 currentVersionId 指向非最新版本时会触发，导致视图元数据损坏、读取失败。修复后 current version 永远被强制保留，符合"当前版本必须存在"的不变式。
- **影响范围**：仅 `ViewMetadata.Builder.expireVersions` 一处逻辑，签名变化但方法是 `@VisibleForTesting` 包级静态方法，外部不应直接调用；调用点同步更新。无序列化格式变化，无 API 兼容性问题。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支若 View 过期逻辑与 main 一致（同样存在该 bug），应回迁此修复；
  - 修复是纯逻辑改动，无新依赖，cherry-pick 安全；
  - 注意 `VERSION_HISTORY_SIZE` 表属性在 1.4.x 上是否已支持，若未支持则该路径不会被触发，但修复仍应回迁以避免后续引入该属性时再次踩坑；
  - 新增的集成测试依赖 `ViewMetadata.buildFrom(...).build()` 触发过期路径，1.4.x 上需确认 `buildFrom` 行为一致。
