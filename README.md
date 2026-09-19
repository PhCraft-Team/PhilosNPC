# PhilosNPC

> Paper 26.3 预研分支：编译目标固定为 `26.3.build.19-alpha`，运行需 Java 25。产物仅供隔离测试，验证结果见 `PAPER_26_3.md`；暂不作为正式服升级依据。

![Java](https://img.shields.io/badge/Java-25-orange)
![Paper](https://img.shields.io/badge/Paper-1.21+-green)
![License](https://img.shields.io/badge/license-MIT-blue)

功能NPC插件 - 在游戏内创建可交互的玩家NPC和系统NPC，支持商店、传送、点歌、留言等功能。

## 功能

### 个人NPC
- 花费500金币在当前位置生成与玩家外观相同的NPC
- GUI管理姿势、大小、功能
- 4种功能（每个NPC最多4个）：
  - **商店**：村民式交易界面，共享商店背包，支持物物交换
  - **传送**：固定5金币传送到设定坐标，支持传送后奖励命令
  - **点歌台**：放入唱片供玩家点播播放
  - **留言板**：每个NPC独立留言，支持多行

### 系统NPC（管理员创建）
- **任意生物形态**：可设定为ZOMBIE、SKELETON、CREEPER等任意生物
- **玩家皮肤**：输入 `PLAYER:玩家名` 显示对应正版皮肤
- **无限商店**：商店物品无限售卖不需补货，支持金币购买和以物换物
- **自定义传送费用**：管理员可设置传送价格（0=免费）
- **直接与系统交易**：玩家交易直接与系统发生，无需店主管理库存

### 通用特性
- NPC管理：创建、编辑、移动、删除、传送至NPC
- 持久化：NPC不会因区块卸载而消失
- 头部动画：NPC头部会随机左右转动

## 安装

1. 下载 `PhilosNPC-1.1.0.jar`
2. 放入服务器 `plugins/` 目录
3. 重启服务器
4. 安装 Vault 经济插件（必需）

> 需要 **Java 25** 运行环境，支持 Paper 1.21 及以上版本

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

## 交互

- **右键NPC**：打开功能选择界面（顾客视角）
- **Shift+右键NPC**：打开管理界面（仅限NPC主人或管理员）
- 个人NPC商店背包在同一个玩家的所有NPC之间共享

## 文档

完整中文Wiki：https://github.com/PhCraft-Team/PhilosNPC/wiki
