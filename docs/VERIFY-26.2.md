# Alien Client 26.2 迁移验证文档

> 本文件回答两个问题：**「1.21.1 的功能都迁过来了吗？」** 和 **「怎么证明能用？」**
> 逐模块的操作步骤在 `docs/test/` 下的 10 个分册；本文件是入口 + 迁移专项清单。
>
> 构建状态：`BUILD SUCCESSFUL` → `build/libs/alien-5.0.jar`（约 1.3 MB / 580 个 class）
> 源码规模：410 个 `.java` / 41,175 行 ｜ 模块 134 个 ｜ mixin 97 个（+ Sodium 3 个占位）

---

## 一、迁移完整性总账

### 1.1 数字

| 指标 | 迁移初期（2026-10-23） | 现在（2026-11-03） |
|---|---:|---:|
| javac 唯一错误 | 2598 | **0** |
| 报错文件 | 212 | **0** |
| `TODO(26.2)` 遗留 | 0（还没开始标）→ 66 | **4**（全部是等上游，见 1.3） |
| 后处理着色器 | 全部降级为「不生效」 | **26.2 `PostChain` 全链路可用**（7 特效 + 区域模糊） |
| 实机冒烟 | 未做 | ✅ 进世界 + ClickGui（7 列）正常渲染 |

### 1.2 本轮（2026-11-03 晚）实际补齐的功能

下列功能此前是「编译过了但运行时是空壳」，本轮**全部换成 26.2 的真实实现**：

| 功能 | 之前 | 现在 |
|---|---|---|
| 后处理特效（`Shader` 模块 7 种模式） | `ShaderManager#renderShaders()` 只执行回调，画面毫无变化 | `mc.getShaderManager().getPostChain()` 加载 `alien:outline/pulse/smoke/gradient/snow/flow/rainbow`，`PostChain#addToFrame` 真跑，`customUniforms` 每帧写 std140 buffer 驱动动画 |
| ClickGui 背景模糊 | `BlurManager` 空实现 | `alien:blurarea` 区域模糊（最多 8 个归一化矩形，shader 内 scissor），失败自动回退原版 `minecraft:blur` |
| 聊天淡入 / 位移 / 指示器 | 注入点消失，功能没了 | 挂到 `ChatComponent$1`（`LineConsumer#accept`）+ `Drawing*GraphicsAccess#handleMessage`，用 `ChatLineRenderState` 跨两个内部类传递状态 |
| 准星（Freecam / ghostHand / noEntityTrace） | 注入点 `findCrosshairTarget` 消失 | 迁到 `LocalPlayer#raycastHitResult` + `Entity#pick`，并拦掉 26.2 新增的 `AttackRange` 捷径 |
| 名牌 `NameTags` | 世界层画不出 | 改 GUI 层：实体内头位置 → `GameRenderer#projectPointToScreen` → `GuiGraphicsExtractor` 绘制（名字/血量/延迟/装备/图腾/朋友色） |
| 雾 / 天空 / 云 / 维度色 / 光照 | 4 个注入点全消失 | 收敛到 `FogRenderer#setupFog` 返回的 `FogData`、`ClientLevel#addEnvironmentAttributeLayers` 的属性层、`LightmapRenderState` 字段 |
| `Chams` 穿墙 + 手持染色 | 只有色，`throughWall` 无消费方（假开关） | `@WrapOperation` 包 `LevelRenderer#submitEntities` 再投一份到 `alwaysOnTop` 阶段；`ModelPlayer` 不再硬编码 `setAlwaysOnTop()`；手持物经 `submitCustomGeometry` 整件染色 |
| `PickFromInventoryC2SPacket` 相关（`Offhand` / `AutoArmor` / `PacketLogger`） | 无等价 API 的注释 | 统一走 `ContainerInput.SWAP`（`AbstractContainerMenu#doClick`），`InventoryUtil` 提供 `swapWithHotbarSlot` / `swapToOffhand` / `pickFromInventory` |
| 攻击包（`Criticals` / `FakePlayer`） | 旧包名编译不过/降级 | 26.2 独立的 `ServerboundAttackPacket`，暴击逻辑按服务端判定重写 |
| 击退（`Velocity`） | `require = 0`，静默失效风险 | `@Redirect(handleSetEntityMotion → Entity#lerpMotion)` 改 `require = 1`（已确认唯一调用点） |
| `AutoTool` | `doItemUse` 被删，功能停摆 | `@Invoker("startUseItem")`，走原版完整右键流程（含 `rightClickDelay = 4`） |
| `InteractTweaks` | 冷却变「假计数」 | `@Accessor("rightClickDelay")` 读写真实字段 |
| `HoleSnap` 段数 | `CircleGizmo` 固定 20 段，`Segments` 无效 | 自写 `SegmentedCircleGizmo`，`Segments` 真正生效 |
| 传送门恶心（`BlockTickNausea`） | 注入点 `tickNausea` 消失 | 26.2 只是改名：`LocalPlayer#handlePortalTransitionEffect` |
| `ChunkESP` 加载期 | `DownloadingTerrainScreen`「无等价」 | 已并入 `LevelLoadingScreen`，判断修正 |
| 火花粒子（`NoRender`） | 粒子类 private，拦不到 | 拦 `FireworkParticles.Starter`（唯一生成者） |
| 末影水晶 Glint / 自转 | 两项都丢 | Glint 用 `RenderTypes.entityGlint()` 再提交一遍；`SpinSpeed` 缩放 `ageInTicks` |
| 假人（`FakePlayer`） | 启用即崩（见 1.4） | `EntityUtil#addClientEntity` 先分配负 id 再加 |

### 1.3 仍然遗留的 4 处（**都是等上游，不是我的取舍**）

| 位置 | 数量 | 原因 |
|---|---:|---|
| `asm/mixins/sodium/Sodium*Mixin.java` | 3 | Sodium 未发布 26.2 版本，`build.gradle` 依赖已停用。三个类目前是**空壳**，钉在语义最接近的原版类上，仅为让 config 可解析；Xray 的原版路径已由 `MixinBlockModelRenderer` / `MixinAbstractBlockRenderContext` 覆盖 |
| `mod/modules/impl/client/BaritoneModule.java` | 1 | Baritone 未发布 26.2 版本。模块只在探测到 `baritone.api` 包时才注册，标准包里**没有这一项**（所以「客户端」列实际 9 个模块、总数 133） |

> 另有 `satin`（27 个文件，备份在 `.tools/satin-original/`）**不再恢复**：26.2 官方 `PostChain` 已能完成 satin 的全部职责（加载 `post_effect`、传 uniform、换离屏 target），恢复 satin 属于倒退。

### 1.4 本轮实机发现并修掉的崩溃

在启动冒烟时点了 GUI 里的「假人」，客户端报：

```
java.lang.IllegalStateException: Tried to access entity ID before ID assignment
  at net.minecraft.world.entity.Entity.getId(Entity.java:438)
  at net.minecraft.client.multiplayer.ClientLevel.addEntity(ClientLevel.java:534)
  at dev.luminous.mod.modules.impl.misc.FakePlayer.onEnable(FakePlayer.java:70)
```

**根因**：26.2 的 `ClientLevel#addEntity` 第一句就是 `removeEntity(entity.getId(), ...)`，
而 `Entity#getId()` 在 id 未分配（`id == 0`）时**直接抛异常**。1.21.1 的 `addEntity` 不读 id，所以旧写法能跑。

**修法**：新增 `EntityUtil#addClientEntity(Entity)`，先分配一个**负 id**（服务端分配的 id 恒为正，永不冲突）
再 `addEntity`；`FakePlayer` / `Blink` / `KillEffect`（闪电）三处客户端自建实体全部改走它。

> 这类「客户端自己 new 实体」的写法在 26.2 必须全部过一遍 —— 已确认项目内只有这 3 处。

---

## 二、验证怎么走

### 第一层：迁移专项（冒烟就能看出来）

| # | 检查 | 判据 |
|---|---|---|
| 1 | 启动 | 日志 `Loading Minecraft 26.2 with Fabric Loader 0.19.5` |
| 2 | Mixin 全注入 | 无 `InvalidMixinException` / `Injection failure` / `@Redirect conflict` |
| 3 | ClickGui 布局 | 7 列（Combat/Misc/Render/Movement/Player/Exploit/Client），单排、可拖动、列头右键折叠 |
| 4 | 后处理链加载 | 日志无 `Failed to compile post processing pipeline` |
| 5 | 配置持久化 | 开关模块后重启，状态仍在 |

### 第二层：迁移专项（必须专门看，冒烟看不出来）

见 **`docs/VERIFY-26.2-CHECKLIST.md`**（可打印的勾选清单，约 40 项，覆盖后处理、渲染管线、环境效果、网络包、GUI 五块）。

### 第三层：逐模块（134 个）

按功能分 10 册，全部在 `docs/test/`：

| 分册 | 模块数 | 环境 |
|---|---:|---|
| [00-环境与工具](test/00-环境与工具.md) | — | 先读这个（含**战斗专用服搭建**） |
| [01-战斗](test/01-战斗.md) | 23 | **C 专用服** |
| [02-移动](test/02-移动.md) | 22 | S |
| [03-玩家](test/03-玩家.md) | 17 | S |
| [04-渲染](test/04-渲染.md) | 13 | L/S |
| [05-视觉](test/05-视觉.md) | 11 | L |
| [06-世界](test/06-世界.md) | 6 | S |
| [07-漏洞](test/07-漏洞.md) | 18 | S（慎用） |
| [08-杂项](test/08-杂项.md) | 14 | S |
| [09-客户端](test/09-客户端.md) | 10 | L |

> ⚠️ **测试分册的分组 ≠ ClickGui 的列**。分册是按功能拆的（便于一次只测一类），
> GUI 是 1.21.1 的 7 列原始布局。对照表在 [TEST-PLAN-26.2.md](TEST-PLAN-26.2.md)。

---

## 三、这一版**没有**的东西（避免误报）

| 现象 | 说明 |
|---|---|
| 没有「视觉 / 世界」两列 | 曾拆分过 9 列，按用户要求**已回退**到 7 列 |
| 列内不能滚动 | 同上，回退到 1.21.1 的「整屏平移」交互 |
| 玩家自己发的聊天消息不再被染成主题色 | 1.21.1 里有一条「同步消息染色」，按用户要求**已移除**；`§` 格式码照常生效 |
| `ClickGui` 的淡入淡出只有文字生效 | 26.2 无全局染色，填充色的 alpha 未做（GUI 布局那一版曾给 `Render2DUtil` 加过全局倍率，已随回退移除） |
| 「客户端」列少 `Baritone` | 上游未发布 26.2 版，见 1.3 |
| `Xray` 装 Sodium 后可能失效 | Sodium mixin 是空壳，等上游；不装 Sodium 时走原版路径正常 |
| 实体描边不是实心 body | 26.2 只能拿到原版 sobel+blur 的轮廓 alpha，属实现差异 |

---

## 四、失败上报模板

```
模块/功能：Chams 穿墙
环境：L 单机创造，平坦世界 + 一面 3 格厚石墙
结果：⚠️ 部分可用
现象：开 ThroughWall 能透过墙看到玩家；关掉后仍能看到（没被遮挡）
日志：latest.log 无异常
复现：站在墙后 5 格，开关 ThroughWall 各截一张图
期望：关掉后玩家被墙挡住
```

- 崩溃 → `run/crash-reports/` 或 `.minecraft/crash-reports/` 整个文件
- 只报错不崩 → `latest.log` 里 `[Alien]` 附近的行 + 栈
