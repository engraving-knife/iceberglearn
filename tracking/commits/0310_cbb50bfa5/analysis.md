# 提交 0310：Core: Remove unused sourceTransform in private method (#9379)

## 提交信息

- **序号**：0310 / 4088
- **哈希**：cbb50bfa5ad8cd490e991ff4e1d7bf7c025e3d5d
- **短哈希**：cbb50bfa5
- **日期**：2023-12-25 08:26:52 -0800
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Remove unused sourceTransform in private method (#9379)
- **PR/Issue**：#9379

## 总体目的

本提交是一次小范围的代码清理，移除 `BaseUpdatePartitionSpec` 中私有方法 `rewriteDeleteAndAddField` 上一个未使用的形参 `sourceTransform`（类型 `Pair<Integer, Transform<?, ?>>`），同时同步更新唯一一处调用点不再传入该参数。这是一次纯粹的无行为变化的死参数删除——既不改变方法语义，也不影响任何外部 API（因为该方法是 `private`）。

`BaseUpdatePartitionSpec` 是 Iceberg `UpdatePartitionSpec` API 的核心实现，负责表分区 spec 的增/删/改字段操作。`rewriteDeleteAndAddField` 这个私有方法处理的是"恢复一个之前被标记删除的分区字段、并允许同时给它改名"的路径：用户先调 `removeField` 把某分区字段加入 `deletes` 集合，随后又调 `addField` 想重新加回相同 `(sourceId, transform)` 组合的字段（可指定新名字），此时 `addField(String name, Term term)` 主流程会检查到已存在的 `existing` 字段位于 `deletes` 集合中、且 transform 一致，于是走 `rewriteDeleteAndAddField` —— 该方法从 `deletes` 移除该字段 id（即"恢复"该字段，不再删除），若 `name` 为 null 或与原字段名相同则直接返回 `this`，否则委托 `renameField(existing.name(), name)` 记录改名。

原方法签名是 `rewriteDeleteAndAddField(PartitionField existing, String name, Pair<Integer, Transform<?, ?>> sourceTransform)`，但 `sourceTransform` 这个参数从未在方法体内被读取——方法只用到 `existing`（取 `fieldId()` 和 `name()`）和 `name`。在调用点 `rewriteDeleteAndAddField(existing, name, sourceTransform)` 中传入的 `sourceTransform` 也没有任何用途，只是机械地向上传递。这类"看起来需要、实际没用"的参数往往是在重构过程中遗留下来没清理干净的——例如这个方法过去可能确实用过 `sourceTransform` 来做某种校验或日志，后来逻辑简化后忘了清理形参。本提交就是补做这次清理，让方法签名与实际使用的参数对齐，降低阅读者对"这个 Pair 是用来干嘛的？"的认知负担。

## 如何达成设计目的

改动只涉及一个文件、两处相邻代码：把 `rewriteDeleteAndAddField` 的签名从三参改为两参（去掉尾部的 `Pair<Integer, Transform<?, ?>> sourceTransform`），同时把唯一调用点的 `rewriteDeleteAndAddField(existing, name, sourceTransform)` 改为 `rewriteDeleteAndAddField(existing, name)`。由于该方法是 `private`，无任何外部调用方，无需考虑兼容性；又因为 `sourceTransform` 在方法体内从未被读取，删除它对方法行为没有任何影响。改动后方法签名更精炼、调用点也不再机械传递一个无用的 `Pair` 对象（虽然 `Pair` 是轻量对象，但省去一次构造与传递也有助于让代码意图更清晰）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseUpdatePartitionSpec.java`

**修改目的**：移除私有方法 `rewriteDeleteAndAddField` 的未使用形参 `sourceTransform`，并同步更新调用点。

**工作逻辑**：

1. **方法签名简化**（行 159-160）：

   ```java
   // 旧
   private BaseUpdatePartitionSpec rewriteDeleteAndAddField(
       PartitionField existing, String name, Pair<Integer, Transform<?, ?>> sourceTransform) {
   // 新
   private BaseUpdatePartitionSpec rewriteDeleteAndAddField(PartitionField existing, String name) {
   ```

   方法体本身完全不变：

   ```java
   deletes.remove(existing.fieldId());
   if (name == null || existing.name().equals(name)) {
     return this;
   } else {
     return renameField(existing.name(), name);
   }
   ```

   证实 `sourceTransform` 在方法体内确实从未被使用——它既没有参与任何判断、也没有传给下游方法。

2. **调用点更新**（行 183）：

   ```java
   // 旧
   if (existing != null
       && deletes.contains(existing.fieldId())
       && existing.transform().equals(sourceTransform.second())) {
     return rewriteDeleteAndAddField(existing, name, sourceTransform);
   }
   // 新
   if (existing != null
       && deletes.contains(existing.fieldId())
       && existing.transform().equals(sourceTransform.second())) {
     return rewriteDeleteAndAddField(existing, name);
   }
   ```

   注意调用点 `if` 条件里仍正常使用 `sourceTransform.second()`（在调用方 `addField` 局部变量 `sourceTransform` 仍存在并用于判断 transform 是否一致），只是不再把它作为参数传给 `rewriteDeleteAndAddField`——因为后者不需要它。本次改动不删除 `addField` 中的 `sourceTransform` 局部变量（它在多处仍被使用，例如构建 `validationKey`、做 `Preconditions.checkArgument` 等），只是去掉了它向 `rewriteDeleteAndAddField` 的"无意义传递"。

## 小结

本次提交是一次精准的死参数清理：把 `BaseUpdatePartitionSpec.rewriteDeleteAndAddField` 私有方法签名上的 `Pair<Integer, Transform<?, ?>> sourceTransform` 形参（在方法体内从未被读取）删除，并同步更新唯一调用点。改动只涉及 1 个文件、净减 1 行（3 删 2 增），无任何行为变化、不影响外部 API，纯粹是为了消除"看起来需要、实际没用"的形参，让方法签名与实际使用的参数对齐，降低代码阅读与维护时的认知负担。这是典型的代码卫生（code hygiene）式提交，与同期合并的 #9365/#9368 等功能性改造形成对比——属于 Iceberg 主分支日常维护中清理技术债的一类常规工作。
