# 提交 0525：API: Fix EncryptingFileIO factory method

## 提交信息

- **序号**：0525 / 4088
- **哈希**：53ab0e2099fb11350e8f76b039955a597d6bbad7
- **短哈希**：53ab0e209
- **日期**：2024-02-21 08:07:27 +0100
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：API: Fix EncryptingFileIO factory method (#9757)
- **PR/Issue**：#9757

## 总体目的

修复 `EncryptingFileIO` 的工厂方法在"传入的 `FileIO` 已是 `EncryptingFileIO` 包装但携带不同的 `EncryptionManager`"场景下的错误行为。原工厂方法 `create(FileIO io, EncryptionManager em)` 在检测到 `io instanceof EncryptingFileIO` 时直接返回该包装对象，完全忽略调用方传入的 `em` 参数——这会导致调用方期望使用某个加密管理器解密，实际却用了包装层内嵌的、可能不同的加密管理器，造成解密失败或用错密钥。本提交将方法重命名为 `combine` 并修正为：遍历包装链，要么找到 em 匹配的层直接复用，要么剥离到最内层的非加密 FileIO 再用目标 `em` 包装。

## 如何达成设计目的

设计核心是把"创建/复用加密 FileIO"的语义从"创建（可能忽略入参）"改为"合并（combine，保证最终用指定的 em）"。具体路径：

1. **方法重命名 `create` → `combine`**：语义更准确——该方法不是无脑新建，而是把一个 FileIO 与一个 EncryptionManager 合并为一个可用 `EncryptingFileIO`，复用已有匹配包装或新建。重命名同时强制所有调用方更新调用点，避免遗漏语义变化。

2. **包装链遍历**：当传入的 `io` 已是 `EncryptingFileIO` 时，不再无条件返回它，而是比较其内部 `em` 与入参 `em`：
   - 若相等（`encryptingIO.em == em`），说明已有一层匹配的加密包装，直接返回该层，避免重复包装。
   - 若不等，说明该层是用别的 em 包装的，对当前需求无意义，于是递归调用 `combine(encryptingIO.io, em)` 剥离这一层、继续向内层查找，直到找到匹配层或到达非 `EncryptingFileIO` 的底层，再用 `new EncryptingFileIO(io, em)` 包装。

3. **更新调用方**：`BaseReader.inputFiles()` 中 `EncryptingFileIO.create(table().io(), table().encryption())` 同步改为 `combine`。

## 修改详情

### `api/src/main/java/org/apache/iceberg/encryption/EncryptingFileIO.java`

**修改目的**：修正工厂方法的复用逻辑，确保返回的 `EncryptingFileIO` 使用的 `EncryptionManager` 与调用方传入的 `em` 一致。

**工作逻辑**：

修改前：
```java
public static EncryptingFileIO create(FileIO io, EncryptionManager em) {
  if (io instanceof EncryptingFileIO) {
    return (EncryptingFileIO) io;   // 忽略入参 em，可能用错 em
  }
  return new EncryptingFileIO(io, em);
}
```

修改后：
```java
public static EncryptingFileIO combine(FileIO io, EncryptionManager em) {
  if (io instanceof EncryptingFileIO) {
    EncryptingFileIO encryptingIO = (EncryptingFileIO) io;
    if (encryptingIO.em == em) {
      return encryptingIO;          // em 匹配，复用该层
    }
    return combine(encryptingIO.io, em);  // em 不匹配，剥层后递归
  }
  return new EncryptingFileIO(io, em);    // 底层，新建包装
}
```

要点：
- 用 `==` 而非 `equals` 比较 `em`。`EncryptionManager` 实例通常由表元数据单例持有，调用方传入的 `table().encryption()` 与包装层持有的应是同一实例，故引用相等即可；这也避免依赖 `EncryptionManager` 是否正确实现 `equals`。
- 递归调用 `combine` 处理多层嵌套（理论上 `EncryptingFileIO.io` 仍可能是 `EncryptingFileIO` 的极端情况），保证最终落到匹配层或底层。
- `EncryptingFileIO` 内部 `em` 字段需为包级/同类可见才能在静态方法中访问（原代码已如此设计）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java`

**修改目的**：跟随工厂方法重命名，更新唯一调用点。

**工作逻辑**：`inputFiles()` 方法在惰性初始化 `lazyInputFiles` 时，把 `EncryptingFileIO.create(table().io(), table().encryption())` 改为 `EncryptingFileIO.combine(...)`。该方法对 `taskGroup.tasks()` 引用的所有文件通过 `combine` 得到的 `EncryptingFileIO` 调用 `bulkDecrypt` 批量解密。修正后，即使 `table().io()` 已经是被另一个 em 包装过的 `EncryptingFileIO`，`combine` 也会剥离错配层并用 `table().encryption()` 正确包装，确保 Spark 读取时使用正确的解密密钥。

## 小结

**成效**：修复了一个潜在的解密错配 bug——当底层 FileIO 已被不同 `EncryptionManager` 包装时，读取路径会使用错误的加密管理器。这对支持表级加密密钥轮换、或同一会话中访问多个加密表的场景尤为重要。方法重命名为 `combine` 使语义自解释。

**影响范围**：`api` 模块的 `EncryptingFileIO`（生产代码）与 Spark v3.5 的 `BaseReader` 调用点。其他模块（Flink、Hive 等）若有同名调用点应在同 PR 或后续 PR 一并更新（本提交仅改 Spark v3.5 一处，因 `combine` 是新名，其他模块若仍用 `create` 会编译失败——回迁时需排查全仓库调用点）。

**回迁到 1.4.x 的注意事项**：

1. 由于方法是 `public static` 且重命名，回迁会破坏二进制兼容性（调用 `EncryptingFileIO.create` 的外部代码需改 `combine`）。1.4.x 作为已发布分支，需评估是否有外部依赖此方法。保守做法可保留旧 `create` 作为 deprecated 转发到 `combine`。
2. 必须同步检查 1.4.x 下所有模块对 `EncryptingFileIO.create` 的调用（`rg "EncryptingFileIO.create"`），全部改为 `combine`，否则编译失败。本提交只展示了 Spark v3.5 的调用点，需确认 1.4.x 是否还存在 Spark 3.3/3.4、Flink、Hive 等其他调用点。
3. `em == em` 引用比较依赖 `table().encryption()` 返回稳定单例；若 1.4.x 的加密管理器实现每次新建实例，则该比较会失效而总是走到递归剥层 + 新建，虽结果正确但可能产生重复包装层。需验证 1.4.x 的 `table().encryption()` 语义。
