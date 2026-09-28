# PhilosNPC

![Java](https://img.shields.io/badge/Java-21-orange)
![Paper](https://img.shields.io/badge/Paper-1.21+-green)
![License](https://img.shields.io/badge/license-MIT-blue)

功能NPC插件 - 在游戏内创建可交互的玩家NPC和系统NPC，支持商店、传送、留言、礼包、跨世界转移等功能。

## 功能

### 个人NPC
- 花费500金币在当前位置生成与玩家外观相同的NPC（默认无装备，不影响已有NPC）
- GUI管理姿势（13种）、大小、功能
- 可用功能（每个NPC最多5个）：
  - **商店**：村民式交易界面，支持金币购买与以物换物，收购的物品进入共享收购背包
  - **传送**：固定5金币传送到设定坐标，支持传送后奖励命令
  - **留言板**：每个NPC独立留言，支持多行
- 主人会在其他玩家使用自己NPC功能（金币消费、以物换物、传送、查看留言）时收到通知，自己使用不打扰
- 礼包发放与跨世界转移为系统NPC专属，个人NPC界面中显示为灰色锁定项

### 系统NPC（管理员创建）
- **任意生物形态**：可设定为ZOMBIE、SKELETON、CREEPER等任意生物
- **玩家皮肤**：输入 `PLAYER:玩家名` 显示对应正版皮肤
- **无限商店**：商店物品无限售卖不需补货，支持金币购买和以物换物
- **自定义传送费用**：管理员可设置传送价格（0=免费）
- **礼包发放**：每位玩家限领一次管理员设置的礼包
- **跨世界转移**：白名单物品转移仓库，在RPG世界与主世界之间安全转移（见下）

### 通用特性
- NPC管理：创建、编辑、移动、删除、传送至NPC
- 持久化：NPC不会因区块卸载而消失；服务器异常重启后自动清理孤儿实体，防止NPC重复生成
- 头部动画：NPC头部会随机左右转动
- 世界隔离：共享商店背包与收购背包按"玩家 + 世界"隔离存储，RPG世界与主世界互不串档

## 安装

1. 下载 `PhilosNPC-1.4.0.jar`
2. 放入服务器 `plugins/` 目录
3. 重启服务器
4. 安装 Vault 经济插件（必需）

> 需要 **Java 21** 及以上运行环境，支持 Paper 1.21 及以上版本

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/pnpc create` | 创建个人NPC（500金币） | `philosnpc.create` |
| `/pnpc syscreate <类型>` | 创建系统NPC | `philosnpc.admin` |
| `/pnpc list` | 查看NPC列表 | `philosnpc.create` |
| `/pnpc edit <id>` | 编辑NPC | `philosnpc.create` |
| `/pnpc move <id>` | 移动NPC到当前位置 | `philosnpc.create` |
| `/pnpc delete <id>` | 删除NPC | `philosnpc.create` |
| `/pnpc tp <id>` | 传送到NPC（10金币） | `philosnpc.create` |
| `/pnpc reload` | 重载配置 | `philosnpc.admin` |

### 系统NPC类型示例
```
/pnpc syscreate ZOMBIE          # 僵尸NPC
/pnpc syscreate SKELETON        # 骷髅NPC
/pnpc syscreate CREEPER         # 苦力怕NPC
/pnpc syscreate PLAYER:Notch    # 显示Notch皮肤的玩家NPC
```

## 跨世界转移

系统NPC的"跨世界转移"功能提供一个转移仓库，只有白名单内的物品可以存入：

- 白名单匹配物品的 `rpgforge:item-id` 标签（RPGForge 物品ID），原版物品无法入仓
- 在 `config.yml` 的 `transfer.allowed-rpgforge-ids` 中配置放行的物品ID，默认只放行 `rpg_token`
- 修改后执行 `/pnpc reload` 生效，无需重启

## 交互

- **右键NPC**：打开功能选择界面（顾客视角）
- **Shift+右键NPC**：打开管理界面（仅限NPC主人或管理员）
- 个人NPC的商店背包与收购背包在同一玩家的所有NPC之间共享，并按世界隔离
- 不能与自己的NPC交易，防止利用收购背包刷物品

## 文档

完整中文Wiki：https://github.com/PhCraft-Team/PhilosNPC/wiki
