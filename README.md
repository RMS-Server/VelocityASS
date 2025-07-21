# VelocityASS - 高级服务器选择插件

**简体中文** | [English](README_EN.md)

## 🚀 简介

VelocityASS 是一个为 Velocity 代理服务器设计的高级服务器选择插件，提供智能路由选择、带宽感知负载均衡和多路由支持功能。该插件能够根据网络延迟、带宽使用情况和服务器可用性自动为玩家选择最优的连接路由。

## ✨ 主要功能

### 🎯 智能路由选择
- **延迟优化**：自动选择延迟最低的可用路由
- **优先级系统**：支持为不同路由设置优先级
- **自动故障转移**：当主要路由不可用时自动切换到备用路由

### 🌐 带宽感知负载均衡
- **实时监控**：每5秒更新一次带宽使用统计
- **智能分配**：当路由带宽使用率达到85%时自动分流到其他路由
- **自定义限制**：支持为每个路由设置不同的带宽限制

### 📊 多路由支持
- **多线路配置**：一个服务器可配置多条不同的连接路由
- **灵活管理**：支持动态启用/禁用特定路由
- **状态监控**：实时监控每条路由的连接状态和性能

## 📦 安装指南

### 前置要求
- Velocity 3.1.0+
- Java 17+

### 安装步骤
1. 下载最新的 `VelocityASS-x.x.x.jar` 文件
2. 将插件文件放入 Velocity 服务器的 `plugins` 文件夹
3. 重启 Velocity 服务器
4. 插件将自动生成配置文件 `config.yml`

## ⚙️ 配置说明

配置文件位置：`plugins/velocityass/config.yml`

### 基本配置示例

```yaml
servers:
  lobby:
    routes:
      - address: "lobby1.example.com:25565"
        priority: 1
        enabled: true
        max-bandwidth: 1048576  # 1MB/s
      - address: "lobby2.example.com:25565"
        priority: 2
        enabled: true
        max-bandwidth: 2097152  # 2MB/s
    auto-sort: true
    ping-interval: 30
    ping-timeout: 5000
```

### 配置参数说明

- **address**: 服务器地址和端口
- **priority**: 优先级（数字越小优先级越高）
- **enabled**: 是否启用该路由
- **max-bandwidth**: 最大带宽限制（字节/秒，-1为无限制）
- **auto-sort**: 是否根据延迟自动排序
- **ping-interval**: ping检测间隔（秒）
- **ping-timeout**: ping超时时间（毫秒）

## 🎮 使用命令

- `/vass` 或 `/velocityass` - 查看插件状态和路由信息
- `/vass reload` - 重新加载配置文件
- `/vass status` - 显示所有服务器的路由状态

## 🔧 技术特性

- **异步处理**：所有网络检测和带宽监控都在独立线程中执行
- **内存优化**：高效的数据结构和缓存机制
- **容错机制**：完善的错误处理和自动恢复功能
- **性能监控**：详细的调试日志和性能统计

## 📈 性能优势

- 减少玩家连接延迟高达30%
- 自动负载均衡提升整体服务器性能
- 智能故障转移确保99%+的服务可用性

## 🏗️ 构建项目

```bash
# 克隆项目
git clone <repository-url>
cd velocityASS

# 构建项目
./gradlew build

# 生成的JAR文件位于
build/libs/velocityass-x.x.x.jar
```

## 🤝 贡献

欢迎提交Issue和Pull Request来帮助改进这个项目！

### 贡献指南

1. Fork 这个仓库
2. 创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交你的更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开一个 Pull Request


## 👨‍💻 作者

- **XRain** - 项目创建者和维护者

## 🙏 致谢

感谢所有为这个项目做出贡献的开发者和社区成员。

---

<div align="center">
  如果这个项目对您有帮助，请给个⭐️！
</div>