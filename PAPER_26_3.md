# Paper 26.3 适配预研

- 分支：`compat/paper-26.3`
- 最新基线：`master` / `80d27e5715c405fa1783aab91b041eebadc64ed1`（含已合并的库存迁移、支付安全和界面会话修复）
- 固定目标：Paper `26.3.build.19-alpha`，Java 25；项目构建使用 Gradle 9.4.0。
- Shadow Gradle 插件使用 `9.5.0`，该版本更新 ASM/jdependency 以支持新版 class 文件；打包仍执行 PacketEvents 重定位并排除其重复 `plugin.yml`。
- 插件版本沿用 `1.4.3`；Paper API 和 `plugin.yml` 的 `api-version` 为 `26.3`。`releaseEnabled` 保持 `false`，候选包仅通过 Actions 临时产物提供。

## 本次改动

- 将构建目标更新到 Paper 26.3 和 Java 25，保留当前主线的 PacketEvents 打包、库存迁移保护、商店编辑会话和支付安全逻辑。
- 将 Shadow 升到 `9.5.0`，使 Paper 26.3 / Java 25 生成的 class 文件可经既有 PacketEvents 重定位打包；JAR 去重规则不变。
- 将商店付款结果不明的锁定写入玩家 PDC 并立即保存；管理员核对经济账本后，可用 `/pnpc reconcileshop <在线玩家UUID>` 单独解除商店锁，或用 `/pnpc reconcileteleport <在线玩家UUID>` 单独解除传送锁。两个命令不会自动扣款或退款，也不会解除另一类锁。

## 实际测试

- `python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v`：34 项通过。
- Java `25.0.4.1`、Gradle `9.4.0` 下执行 `clean test --offline --info`：主代码和测试代码以 JDK 25 编译，39 项 JUnit 通过。因本地无网络且缓存中没有 Shadow `9.5.0`，此项只验证编译及测试；该运行使用本地缓存的 Shadow `9.0.0-beta12`，没有执行 JAR 打包。
- 最终候选的 `clean build`、JUnit 和 JAR 检查由本次 PR 的 GitHub Actions 使用配置中的 Shadow `9.5.0` 执行；结果以该工作流为准。
- 没有对本次整合后的候选包启动 Paper 26.3 服务端或进行游戏内交易验证。此前旧提交上的测试服经历不代表本候选已验证。

## 已知限制

- Paper 目标仍为 `26.3.build.19-alpha` 预研版；不据此宣称支持旧版 Paper、Spigot 或 Folia。
- Vault 不提供跨账户事务；发生不确定结果时管理员需先核对经济账本，再解除对应玩家的付款锁。
- 当前变更没有启动正式服、迁移正式数据或创建 Release。

## 升级影响

- 配置和存储：无新增配置项。本分支包含 master 的分世界库存迁移和歧义库存保护；旧格式数据仍按该迁移备份并按需人工核对，本 PR 没有另加库存转换步骤。
- 权限、命令和 API：沿用现有 `philosnpc.admin` 与 `reconcileteleport` 命令；新增 `reconcileshop`，两类付款锁需分别核账和解除。
- 玩家数据：发生不确定商店付款后，新增 `philosnpc:unresolved_shop_payment` 玩家 PDC 字段；必须经管理员核对账本后解锁。
- 插件依赖：Vault 和内置重定位的 PacketEvents 依赖保持不变。
- 破坏性变更：候选 JAR 需要 Java 25 和 Paper 26.3；不能安装在旧 Paper、Spigot 或 Folia 服务端。
- 升级及回滚：仅在隔离测试副本替换候选 JAR，并先备份插件、玩家、世界及经济数据；升级时遵循分世界库存迁移和人工核账保护。回滚时停服并恢复对应备份。
