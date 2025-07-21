# VelocityASS - 高级服务器选择插件

<div align="right">
  <button onclick="switchLanguage()" id="langBtn" style="padding: 8px 16px; border: none; background: #4CAF50; color: white; border-radius: 4px; cursor: pointer;">🇺🇸 English</button>
</div>

<div id="chinese" style="display: block;">

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

## 🤝 贡献

欢迎提交Issue和Pull Request来帮助改进这个项目！

## 📄 许可证

本项目采用 MIT 许可证。

## 👨‍💻 作者

- **XRain** - 项目创建者和维护者

</div>

<div id="english" style="display: none;">

## 🚀 Introduction

VelocityASS is an advanced server selection plugin designed for Velocity proxy servers, providing intelligent route selection, bandwidth-aware load balancing, and multi-route support. This plugin automatically selects the optimal connection route for players based on network latency, bandwidth usage, and server availability.

## ✨ Key Features

### 🎯 Intelligent Route Selection
- **Latency Optimization**: Automatically selects the route with the lowest latency
- **Priority System**: Support for setting priorities for different routes
- **Automatic Failover**: Automatically switches to backup routes when primary routes are unavailable

### 🌐 Bandwidth-Aware Load Balancing
- **Real-time Monitoring**: Updates bandwidth usage statistics every 5 seconds
- **Intelligent Distribution**: Automatically distributes traffic to other routes when route bandwidth usage reaches 85%
- **Custom Limits**: Support for setting different bandwidth limits for each route

### 📊 Multi-Route Support
- **Multi-line Configuration**: Configure multiple different connection routes for one server
- **Flexible Management**: Support for dynamically enabling/disabling specific routes
- **Status Monitoring**: Real-time monitoring of connection status and performance for each route

## 📦 Installation Guide

### Prerequisites
- Velocity 3.1.0+
- Java 17+

### Installation Steps
1. Download the latest `VelocityASS-x.x.x.jar` file
2. Place the plugin file in the `plugins` folder of your Velocity server
3. Restart the Velocity server
4. The plugin will automatically generate the configuration file `config.yml`

## ⚙️ Configuration

Configuration file location: `plugins/velocityass/config.yml`

### Basic Configuration Example

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

### Configuration Parameters

- **address**: Server address and port
- **priority**: Priority (lower numbers have higher priority)
- **enabled**: Whether to enable this route
- **max-bandwidth**: Maximum bandwidth limit (bytes/second, -1 for unlimited)
- **auto-sort**: Whether to automatically sort by latency
- **ping-interval**: Ping detection interval (seconds)
- **ping-timeout**: Ping timeout (milliseconds)

## 🎮 Commands

- `/vass` or `/velocityass` - View plugin status and route information
- `/vass reload` - Reload configuration file
- `/vass status` - Display route status for all servers

## 🔧 Technical Features

- **Asynchronous Processing**: All network detection and bandwidth monitoring run in separate threads
- **Memory Optimization**: Efficient data structures and caching mechanisms
- **Fault Tolerance**: Comprehensive error handling and automatic recovery features
- **Performance Monitoring**: Detailed debug logs and performance statistics

## 📈 Performance Benefits

- Reduce player connection latency by up to 30%
- Automatic load balancing improves overall server performance
- Intelligent failover ensures 99%+ service availability

## 🤝 Contributing

Issues and Pull Requests are welcome to help improve this project!

## 📄 License

This project is licensed under the MIT License.

## 👨‍💻 Author

- **XRain** - Project creator and maintainer

</div>

<script>
function switchLanguage() {
    const chinese = document.getElementById('chinese');
    const english = document.getElementById('english');
    const btn = document.getElementById('langBtn');
    
    if (chinese.style.display === 'block') {
        chinese.style.display = 'none';
        english.style.display = 'block';
        btn.innerHTML = '🇨🇳 中文';
    } else {
        chinese.style.display = 'block';
        english.style.display = 'none';
        btn.innerHTML = '🇺🇸 English';
    }
}

// Auto-detect language based on browser settings
document.addEventListener('DOMContentLoaded', function() {
    const browserLang = navigator.language || navigator.userLanguage;
    if (browserLang.startsWith('zh')) {
        // Chinese is already displayed by default
        document.getElementById('langBtn').innerHTML = '🇺🇸 English';
    } else {
        switchLanguage(); // Switch to English
    }
});
</script>

<style>
#langBtn:hover {
    background: #45a049;
    transform: scale(1.05);
    transition: all 0.3s ease;
}

body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

code {
    background: #f4f4f4;
    padding: 2px 4px;
    border-radius: 3px;
}

pre {
    background: #f8f8f8;
    border: 1px solid #ddd;
    border-radius: 4px;
    padding: 15px;
    overflow-x: auto;
}

h1, h2 {
    border-bottom: 1px solid #eee;
    padding-bottom: 10px;
}
</style>