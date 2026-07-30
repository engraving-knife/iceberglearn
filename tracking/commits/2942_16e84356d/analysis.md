# 提交 2942：Core: Add UUIDv7 generator (#14700)

## 提交信息

- **序号**：2942 / 4088
- **哈希**：16e84356dae1975fa04d8c3ecce30a90df18ca9f
- **短哈希**：16e84356d
- **日期**：2025-12-02
- **作者**：Huaxin Gao
- **提交说明**：Core: Add UUIDv7 generator
- **PR/Issue**：#14700

## 总体目的

本提交为 Iceberg 的 `UUIDUtil` 工具类新增了一个生成 RFC 9562 UUIDv7 的方法 `generateUuidV7()`。

UUIDv7 是 2024 年正式标准化的 RFC 9562 中引入的一种时间排序 UUID 变体，与传统的 UUIDv4（纯随机）相比，它把 48 位 Unix 毫秒级时间戳放在最高有效位，使得同一毫秒内生成的 UUID 在字典序上接近、整体随时间单调递增。这种"时间有序 + 随机"的混合结构带来几个重要好处：

1. **可排序性**：UUID 本身即可作为按时间排序的键，无需额外维护创建时间字段；
2. **数据库索引友好**：B-Tree 索引中插入位置连续，页分裂少，写入吞吐与存储碎片表现都显著优于纯随机的 v4；
3. **冲突概率可控**：62 位随机 + 同毫秒内时间戳，碰撞概率极低且不依赖中心化协调。

Iceberg 在多处需要稳定、唯一的标识符（如元数据文件、表标识、操作标识等）。在对象存储与元数据存储场景下，使用时间有序的 UUID 可以减少 listing 时的随机 I/O、提升 manifest/metadata 文件的命名局部性。`UUIDUtil` 已经在 Iceberg 内部被广泛使用（Avro 的 `ValueReaders`/`ValueWriters`、`UUIDConversion`、`Conversions` 等用于 UUID 类型与字节/字节数组之间的转换），因此在此处补齐一个标准化的 v7 生成器是自然的扩展点。

本实现严格遵循 RFC 9562 的位布局，使用 `SecureRandom` 提供密码学级别的随机性，并对时间戳做 48 位边界校验，避免时间戳超过 48 位范围时静默截断导致数据错乱（当前 Unix 时间约 1.7e12，远小于 2^48 ≈ 2.8e14，但加上时间漂移或未来时间仍需保护）。

## 如何达成设计目的

整体设计是把生成逻辑收敛到 `api` 模块的 `UUIDUtil` 中作为静态方法 `generateUuidV7()`，复用一个进程级 `SecureRandom` 实例（避免每次调用都新建开销），按 RFC 9562 位布局组装 MSB 与 LSB 两个 64 位 long，最终返回 `java.util.UUID`。同时在 `api` 测试模块新增 `TestUUIDUtil` 验证生成的 UUID 版本号与变体位正确。由于放在 `api` 模块，该能力可被 `core` 及下游各引擎模块直接调用。

## 修改详情

### `api/src/main/java/org/apache/iceberg/util/UUIDUtil.java` (+31/-0 lines)

**修改目的**：在既有 UUID 转换工具类中新增符合 RFC 9562 的 UUIDv7 生成方法。

**工作逻辑**：
改动分两部分：

1. **共享 `SecureRandom`**：在类顶部新增静态字段
   ```java
   private static final SecureRandom SECURE_RANDOM = new SecureRandom();
   ```
   并新增 `import java.security.SecureRandom;`。使用静态共享实例避免每次生成时重新创建 `SecureRandom`（其初始化涉及熵收集，开销较大），同时保持线程安全（`SecureRandom.nextBytes` 是线程安全的）。

2. **`generateUuidV7()` 方法**：按 RFC 9562 位布局组装 UUID。位布局为：
   - 48-bit Unix epoch milliseconds
   - 4-bit version（0b0111 = 7）
   - 12-bit random（rand_a）
   - 2-bit variant（RFC 4122，0b10）
   - 62-bit random（rand_b）

   关键实现逻辑：
   - **时间戳获取与校验**：`long epochMs = System.currentTimeMillis();`，再用 `Preconditions.checkState((epochMs >>> 48) == 0, ...)` 确保时间戳在 48 位之内。这是防御性编程——若系统时钟异常或未来时间超过 2^48 ms（约公元 10889 年），会抛 `IllegalStateException` 而非静默截断。
   - **一次抽取 10 字节随机数**：`byte[] randomBytes = new byte[10]; SECURE_RANDOM.nextBytes(randomBytes);`。10 字节正好覆盖 rand_a 的 12 位与 rand_b 的 62 位所需的总位数（12 + 62 = 74 位 ≈ 10 字节多一点，但通过掩码裁剪到精确位数）。用 `ByteBuffer.wrap(randomBytes).order(ByteOrder.BIG_ENDIAN)` 包装以便按大端序读取。
   - **rand_a 提取**：`long randMSB = ((long) rb.getShort()) & 0x0FFFL;` 读 2 字节 short 并掩码到低 12 位（`0x0FFF` = 0000 1111 1111 1111）。
   - **rand_b 提取**：`long randLSB = rb.getLong() & 0x3FFFFFFFFFFFFFFFL;` 读 8 字节 long 并掩码到低 62 位（`0x3FFF...F` 顶部 2 位为 0，保留低 62 位），为变体位预留空间。
   - **组装 MSB**：
     ```java
     long msb = (epochMs << 16);  // 时间戳放最高 48 位
     msb |= 0x7000L;              // 版本 7 放在 bits 15-12
     msb |= randMSB;              // rand_a 放在 bits 11-0
     ```
     `epochMs << 16` 把 48 位时间戳移到 long 的 bits 63-16；`0x7000L` 在 bits 15-12 写入 0111（版本 7），与 `java.util.UUID.version()`（返回 `(msb >>> 12) & 0xF`）对应；低 12 位填 rand_a。
   - **组装 LSB**：
     ```java
     long lsb = 0x8000000000000000L;  // RFC 4122 变体 '10' 在最高 2 位
     lsb |= randLSB;                  // 其余 62 位填随机
     ```
     `0x8000...0` 把 bit 63 置 1、bit 62 置 0，对应 `java.util.UUID.variant()` 返回 2（RFC 4122）；OR 上低 62 位随机数。注意 randLSB 的高 2 位已被掩码清零，不会覆盖变体位。

   最终 `return new UUID(msb, lsb);`。整体逻辑严格符合 RFC 9562，且对时间戳与随机数的位边界都有显式约束，鲁棒性良好。

### `api/src/test/java/org/apache/iceberg/util/TestUUIDUtil.java` (+34/-0 lines, new file)

**修改目的**：为新增的 UUIDv7 生成器建立最小回归测试。

**工作逻辑**：
新增测试类 `TestUUIDUtil`，包含一个测试方法 `uuidV7HasVersionAndVariant()`：
```java
UUID uuid = UUIDUtil.generateUuidV7();
assertThat(uuid.version()).isEqualTo(7);
assertThat(uuid.variant()).isEqualTo(2);
```
通过 `java.util.UUID` 内置的 `version()` 与 `variant()` 解析方法，断言生成结果的版本字段为 7、变体字段为 2（RFC 4122 变体）。这是验证 UUIDv7 布局正确性的最直接手段——若位掩码或移位错误，版本号或变体号会偏离预期。该测试覆盖了实现中最关键的合规性断言，但未对唯一性、时间戳单调性、随机性分布做更复杂的统计验证（这通常依赖 RFC 一致性套件，超出 Iceberg 单元测试范围）。

## 总结

本次提交为 Iceberg 提供了一个标准化的 RFC 9562 UUIDv7 生成器，把时间戳嵌入最高有效位实现可排序性，同时保留 74 位密码学随机性。该能力落在被广泛复用的 `UUIDUtil` 中，可直接用于元数据文件命名、操作标识等需要时间局部性 + 全局唯一性的场景，有助于改善对象存储 listing 性能与索引友好度。实现严格遵循 RFC 位布局、带时间戳边界校验、复用共享 `SecureRandom`，并配有版本/变体合规性测试。
