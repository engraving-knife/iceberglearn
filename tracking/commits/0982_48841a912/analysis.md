# 提交 0982：Docs: Make compatibility example consistent (#10781)

## 提交信息

- **序号**：0982 / 4088
- **哈希**：48841a91209552d761b54a0c6f6ed4a200d97d73
- **短哈希**：48841a912
- **日期**：2024-07-25 19:49:06 -0600
- **作者**：emkornfield
- **提交说明**：Docs: Make compatibility example consistent (#10781)
- **PR/Issue**：#10781

## 总体目的

Iceberg 的贡献者文档 `site/docs/contribute.md` 中包含一段关于 API 兼容性的示例代码，用于向开发者解释什么情况属于 API 破坏性变更（API-breaking change）。该示例通过 `ManageSnapshots` 接口新增一个 `createBranch(String name)` 方法来演示：接口新增方法后，已有实现类因未实现该方法而无法编译。

然而，原示例存在两处不一致的地方，导致示例本身的"说明文字"和"代码片段"相互矛盾，对读者造成误导。本提交的目的就是修正这两处不一致，让兼容性示例在文字描述和代码实现上保持统一。

## 如何达成设计目的

实现思路很直接：通过修改文档中的示例代码块，让两段示例（接口与实现类）展示的内容与"新增方法会导致破坏性变更"这一解释完全对应。具体包括：

1. 在接口示例的注释中补一句说明，强调因为已存在的实现类未实现新方法，导致无法通过编译；
2. 将实现类示例中的 `createBranch` 方法签名从两个参数版本 `createBranch(String name, long snapshotId)` 改成与新接口签名一致的单参数版本 `createBranch(String name)`，使其与上方的接口定义对应。

## 修改详情

### `site/docs/contribute.md`

**修改目的**：修复兼容性示例文档中描述与代码不一致的问题，使示例本身能够正确演示"接口新增方法会导致已有实现类编译失败"这一概念。

**工作逻辑**：改动局限于一处代码块，主要分为两处小修改：

1. 接口示例补注释：在 `ManageSnapshots` 接口的示例注释中追加两行，明确指出因为已存在的实现该接口的类没有实现新方法 `createBranch(String name)`，所以会无法编译。这让读者一眼就能理解"为什么这是 API 破坏性变更"。
2. 实现类示例签名同步：原示例中 `SnapshotManager` 的 `createBranch` 方法签名仍然保留旧的 `createBranch(String name, long snapshotId)`，与上方接口新增的 `createBranch(String name)` 不一致。此处将其改为单参数版本，使实现类签名与新接口保持一致。

修改前后对照如下：

```diff
 public interface ManageSnapshots extends PendingUpdate<Snapshot> {
   // existing code...

   // adding this method introduces an API-breaking change
+  // since existing classes implementing ManageSnapshots
+  // will no longer compile.
   ManageSnapshots createBranch(String name);
 }
```

```diff
 public class SnapshotManager implements ManageSnapshots {
   // existing code...

   @Override
-  public ManageSnapshots createBranch(String name, long snapshotId) {
-    updateSnapshotReferencesOperation().createBranch(name, snapshotId);
+  public ManageSnapshots createBranch(String name) {
+    updateSnapshotReferencesOperation().createBranch(name);
     return this;
   }
 }
```

## 小结

- **成效**：修复了贡献者文档中兼容性示例的不一致问题，使示例的描述文字与代码片段保持对应，读者更容易理解"接口新增方法导致实现类无法编译"这一破坏性变更的含义。
- **影响范围**：仅修改 `site/docs/contribute.md` 一个文件中的示例代码块，无任何代码或构建逻辑变化。
- **回迁到 1.4.x 的注意事项**：属于纯文档修订，回迁无风险。如果 1.4.x 分支中存在同样的不一致示例，建议回迁以保持文档质量；若 1.4.x 文档版本与 main 差异较大，也可以选择性忽略。
