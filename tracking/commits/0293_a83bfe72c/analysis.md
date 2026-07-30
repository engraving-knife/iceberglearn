# 提交 0293：Spec: Clarify file length handling for AES GCM streams (#9136)

## 提交信息

- **序号**：0293 / 4088
- **哈希**：a83bfe72ccc18ec6f22621630b6dbabaf4648064
- **短哈希**：a83bfe72c
- **日期**：2023-12-19 12:59:32 -0800
- **作者**：ggershinsky
- **提交说明**：Spec: Clarify file length handling for AES GCM streams (#9136)
- **PR/Issue**：#9136

## 总体目的

Iceberg 的 `AES GCM Stream` 是一种文件格式扩展，用于对元数据文件（manifest、manifest list、snapshot、stats 等）以及 Avro 数据文件进行加密和防篡改保护。其工作原理是把明文流切成等长大小的块（最后一块可以更短），每块用 AES GCM 独立加密，输出 `[nonce(12B) | 密文 | GCM tag(16B)]` 形式的密文块。GCM 自带的认证 tag 防止块内字节被替换；通过 AAD（由文件 AAD 前缀 + 块序号 suffix 拼接）防止块在文件内/文件间被互换，也能防止整文件被旧版本替换。

但是这套机制存在一个被忽视的攻击面：**末尾块删除攻击（truncation attack）**。由于 GCM Stream 没有把"文件应有的总长度"作为认证的一部分写入流头（流头只有 magic `AGS1` 和 `BlockLength`，即单块明文大小，并不是文件总长），如果攻击者直接删除文件末尾的若干密文块，剩下的块仍然能逐块通过 GCM tag 验证——因为每个块的 tag 只覆盖该块自身的 nonce/密文/AAD，与"后面还有没有块"无关。读者读到末尾就停下来，会误以为读到的是完整文件，从而得到一份被悄悄裁剪过的"合法"内容。这对于元数据文件尤其危险：例如删掉 manifest 末尾几条记录，可能让读者看不到某些数据文件的存在，进而绕过访问控制或破坏一致性。

本提交的目的就是在规范文档 `format/gcm-stream-spec.md` 中明确补充这一安全要求：读者实现必须使用来自可信来源（例如已签名的文件元数据）的文件长度值，而不是直接采用文件系统报告的长度。这样读者在打开流时就可以用可信长度去比对实际长度，一旦发现被截断就能立即报错，从而关闭这一攻击面。这是一处纯规范（spec）层面的澄清，不修改任何代码实现，但为后续实现者给出了明确的安全契约。

## 如何达成设计目的

设计方式非常轻量：在 `gcm-stream-spec.md` 文档末尾新增一个小节 `### File length`，用一段话指出末尾块删除攻击的存在，并规定读者实现必须从可信来源取文件长度。规范本身不强制规定"可信来源"的具体形式（Iceberg 中通常是已签名的元数据中记录的文件长度字段），只给出方向性约束，把实现细节留给各语言的 reader。

## 修改详情

### `format/gcm-stream-spec.md`

**修改目的**：在 AES GCM Stream 规范中补充"文件长度"小节，澄清对末尾块删除攻击的防护要求。

**工作逻辑**：

新增内容（追加在文档末尾，紧接"Additional Authenticated Data"小节之后）：

```markdown
### File length

An attacker can delete a few last blocks in an encrypted file. To detect the attack, the reader implementations of the AES GCM Stream must use the file length value taken from a trusted source (such as a signed file metadata), and not from the file system.
```

这段规范文字包含两个关键点：

1. **威胁模型**：明确指出攻击者可以删除加密文件的最后几块。这是 GCM Stream 原有 AAD 机制无法覆盖的场景——AAD 只能防止块被替换/交换，不能防止块被裁掉。因为每块的 GCM tag 是独立认证的，缺少"文件总块数"或"文件总长度"这样的全局不变量，删除末尾块不会破坏任何已有块的 tag 验证。

2. **实现要求**：规定 reader 实现必须使用来自可信源（如已签名的文件元数据）的文件长度，而不能使用文件系统报告的长度。这与 Iceberg 现有实现（`AesGcmInputFile.getLength()` 调用 `sourceFile.getLength()` 取底层文件长度，再用 `AesGcmInputStream.calculatePlaintextLength` 反推明文长度并据此计算块数）形成对照：规范要求调用方应确保传入的 `sourceFile` 所报告的长度本身来自可信元数据，否则就存在被截断而不自知的风险。规范没有改代码，但明确了实现者的责任边界。

## 小结

这是一个安全规范层面的澄清提交：在 AES GCM Stream 规范中新增"File length"小节，明确指出"末尾块删除"这一原有 AAD 机制无法覆盖的攻击面，并要求 reader 实现从可信来源（如已签名元数据）获取文件长度而非直接信任文件系统报告的长度。改动仅 4 行文档，但填补了一个重要的安全语义缺口，为后续 reader 实现的硬化提供了规范依据。
