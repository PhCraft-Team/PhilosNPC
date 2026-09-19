# Paper 26.3 适配预研

- 分支：`compat/paper-26.3`
- 基线：`master` / `55fef3c616f75a8d29aeb0e35309772993b1882e`
- 固定目标：Paper `26.3.build.19-alpha`（2026-09-19 核对），Java 25；本地 Oracle 25.0.4.1、Gradle 9.1.0。
- 插件 `api-version: 26.3`，候选 JAR 不用于旧版服务器。维持现有插件版本号；不发布 Release、不合并默认分支。

## 改动

固定 Paper 26.3 编译依赖和插件 API 声明，业务代码保持现状。

## 实际验证

- `gradle --no-daemon clean build`（有 Wrapper 的仓库使用 gradlew；PhCraftAPI 使用同版本本地 Gradle）：构建成功。
- test 为 NO-SOURCE：仓库没有插件单元测试，只确认构建成功。
- 未配置独立 Python 脚本测试。
- JAR 检查：plugin.yml 唯一、版本变量已展开、入口类存在、api-version 为 26.3。
- Paper 26.3 #19 / Java 25 的 localhost 新世界中，10 个组织插件共同启用；VaultUnlocked 2.20.1、EssentialsX 2.22.0、LuckPerms 5.5.85 启用，经济服务注册存在。正常停止并再次启动后复验。
- 验证限于加载、启用和正常停止；没有执行全部玩家功能。
- 候选 `PhilosNPC-1.2.0.jar` SHA-256：`b76f23133f2efcdc7ca42c80655d714c6c4475411a2ae74890751799bab1197a`。

## 已知限制

EssentialsX 2.22.0 虽然启用并注册经济服务，仍报告不支持该服务端版本；本次不据此认定经济功能可用于生产。尚未执行真人登录、GUI、完整交易/奖励/抽奖流程、正式服数据副本迁移或 Linux 实服测试。

Paper 目标仍是 ALPHA；不宣称支持 Spigot、Folia 或所有 26.3 后续构建。Windows 性能计数器、第三方插件旧 API 与线程池提示已记录，未修改系统设置或第三方源码。

## 数据与升级

配置结构、权限、命令、公开 API、数据库格式均不变。

只在隔离副本中替换候选 JAR 测试。正式升级时另行备份插件、配置、世界、玩家和经济数据；不能以降级 JAR 代替世界/数据回滚。当前正式服务器未部署或升级。

## 后续维护

main/master 的通用修复先正常维护，再按需 merge 到本预研分支；Paper 稳定构建发布后更新固定依赖并复验。正式升级前补齐真人操作、经济失败路径与真实数据副本验收，再决定合并和正式版本号。
