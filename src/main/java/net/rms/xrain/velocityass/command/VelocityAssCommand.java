package net.rms.xrain.velocityass.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rms.xrain.velocityass.config.RouteInfo;
import net.rms.xrain.velocityass.config.ServerConfig;
import net.rms.xrain.velocityass.service.RouteManager;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VelocityAssCommand implements SimpleCommand {
    private final RouteManager routeManager;
    private final Logger logger;
    
    public VelocityAssCommand(RouteManager routeManager, Logger logger) {
        this.routeManager = routeManager;
        this.logger = logger;
    }
    
    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        
        if (args.length == 0) {
            showHelp(invocation);
            return;
        }
        
        switch (args[0].toLowerCase()) {
            case "status":
                showStatus(invocation);
                break;
            case "routes":
                if (args.length > 1) {
                    showServerRoutes(invocation, args[1]);
                } else {
                    showAllRoutes(invocation);
                }
                break;
            case "bandwidth":
            case "bw":
                if (args.length > 1) {
                    showServerBandwidth(invocation, args[1]);
                } else {
                    showAllBandwidth(invocation);
                }
                break;
            case "debug":
                showDebugBandwidth(invocation);
                break;
            case "reload":
                reloadConfig(invocation);
                break;
            default:
                showHelp(invocation);
                break;
        }
    }
    
    private void showHelp(Invocation invocation) {
        invocation.source().sendMessage(Component.text("=== VelocityASS 命令帮助 ===", NamedTextColor.GOLD));
        invocation.source().sendMessage(Component.text("/vass status - 显示插件状态", NamedTextColor.YELLOW));
        invocation.source().sendMessage(Component.text("/vass routes [服务器名] - 显示路由信息", NamedTextColor.YELLOW));
        invocation.source().sendMessage(Component.text("/vass bandwidth|bw [服务器名] - 显示带宽使用情况", NamedTextColor.YELLOW));
        invocation.source().sendMessage(Component.text("/vass debug - 显示详细带宽调试信息", NamedTextColor.YELLOW));
        invocation.source().sendMessage(Component.text("/vass reload - 重新加载配置", NamedTextColor.YELLOW));
    }
    
    private void showStatus(Invocation invocation) {
        invocation.source().sendMessage(Component.text("=== VelocityASS 状态 ===", NamedTextColor.GOLD));
        
        int totalServers = routeManager.getAllServerConfigs().size();
        int totalRoutes = routeManager.getAllServerConfigs().values().stream()
                .mapToInt(config -> config.getRoutes().size())
                .sum();
        
        invocation.source().sendMessage(Component.text("配置的服务器数量: " + totalServers, NamedTextColor.GREEN));
        invocation.source().sendMessage(Component.text("总路由数量: " + totalRoutes, NamedTextColor.GREEN));
        
        for (ServerConfig config : routeManager.getAllServerConfigs().values()) {
            int availableRoutes = routeManager.getAvailableRouteCount(config.getServerName());
            invocation.source().sendMessage(Component.text(
                    config.getServerName() + ": " + availableRoutes + "/" + config.getRoutes().size() + " 可用",
                    availableRoutes > 0 ? NamedTextColor.GREEN : NamedTextColor.RED
            ));
        }
    }
    
    private void showAllRoutes(Invocation invocation) {
        invocation.source().sendMessage(Component.text("=== 所有服务器路由 ===", NamedTextColor.GOLD));
        
        for (ServerConfig config : routeManager.getAllServerConfigs().values()) {
            showServerRoutes(invocation, config.getServerName());
            invocation.source().sendMessage(Component.text(" "));
        }
    }
    
    private void showServerRoutes(Invocation invocation, String serverName) {
        ServerConfig config = routeManager.getConfigManager().getServerConfig(serverName);
        if (config == null) {
            invocation.source().sendMessage(Component.text("未找到服务器: " + serverName, NamedTextColor.RED));
            return;
        }
        
        invocation.source().sendMessage(Component.text("=== " + serverName + " 路由信息 ===", NamedTextColor.GOLD));
        invocation.source().sendMessage(Component.text("自动排序: " + (config.isAutoSort() ? "启用" : "禁用"), NamedTextColor.YELLOW));
        invocation.source().sendMessage(Component.text("检测间隔: " + config.getPingInterval() + "秒", NamedTextColor.YELLOW));
        
        for (int i = 0; i < config.getRoutes().size(); i++) {
            RouteInfo route = config.getRoutes().get(i);
            NamedTextColor color = route.isAvailable() && route.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED;
            
            String status = route.isEnabled() ? 
                    (route.isAvailable() ? "可用" : "不可用") : "已禁用";
            String ping = route.getLastPing() > 0 ? route.getLastPing() + "ms" : "未知";
            
            invocation.source().sendMessage(Component.text(
                    String.format("  %d. %s (优先级: %d, 状态: %s, 延迟: %s)",
                            i + 1, route.getAddress(), route.getPriority(), status, ping),
                    color
            ));
        }
        
        RouteInfo bestRoute = config.getBestRoute();
        if (bestRoute != null) {
            invocation.source().sendMessage(Component.text("当前最佳路由: " + bestRoute.getAddress(), NamedTextColor.AQUA));
        } else {
            invocation.source().sendMessage(Component.text("无可用路由", NamedTextColor.RED));
        }
    }
    
    private void reloadConfig(Invocation invocation) {
        try {
            routeManager.getConfigManager().reloadConfig();
            invocation.source().sendMessage(Component.text("配置重新加载成功！", NamedTextColor.GREEN));
            logger.info("配置被 {} 重新加载", getSourceName(invocation));
        } catch (Exception e) {
            invocation.source().sendMessage(Component.text("配置重新加载失败: " + e.getMessage(), NamedTextColor.RED));
            logger.error("配置重新加载失败", e);
        }
    }
    
    private void showAllBandwidth(Invocation invocation) {
        invocation.source().sendMessage(Component.text("=== 所有服务器带宽使用情况 ===", NamedTextColor.GOLD));
        
        // 先更新所有路由的带宽信息
        routeManager.updateAllRoutesBandwidth();
        
        for (ServerConfig config : routeManager.getAllServerConfigs().values()) {
            showServerBandwidth(invocation, config.getServerName());
            invocation.source().sendMessage(Component.text(" "));
        }
    }
    
    private void showServerBandwidth(Invocation invocation, String serverName) {
        ServerConfig config = routeManager.getConfigManager().getServerConfig(serverName);
        if (config == null) {
            invocation.source().sendMessage(Component.text("未找到服务器: " + serverName, NamedTextColor.RED));
            return;
        }
        
        // 更新带宽信息
        routeManager.updateAllRoutesBandwidth();
        
        invocation.source().sendMessage(Component.text("=== " + serverName + " 带宽使用情况 ===", NamedTextColor.GOLD));
        
        for (int i = 0; i < config.getRoutes().size(); i++) {
            RouteInfo route = config.getRoutes().get(i);
            
            // 确定颜色
            NamedTextColor color;
            if (!route.isEnabled()) {
                color = NamedTextColor.GRAY;
            } else if (!route.isAvailable()) {
                color = NamedTextColor.RED;
            } else if (route.isBandwidthLimited() && route.getBandwidthUtilization() > 85.0) {
                color = NamedTextColor.RED;
            } else if (route.isBandwidthLimited() && route.getBandwidthUtilization() > 60.0) {
                color = NamedTextColor.YELLOW;
            } else {
                color = NamedTextColor.GREEN;
            }
            
            String status = route.isEnabled() ? 
                    (route.isAvailable() ? "可用" : "不可用") : "已禁用";
            
            String bandwidthInfo;
            if (route.isBandwidthLimited()) {
                bandwidthInfo = String.format("%.2f/%.2f KB/s (%.1f%%)",
                        route.getCurrentBandwidthUsage() / 1024.0,
                        route.getMaxBandwidth() / 1024.0,
                        route.getBandwidthUtilization());
            } else {
                bandwidthInfo = String.format("%.2f KB/s (无限制)",
                        route.getCurrentBandwidthUsage() / 1024.0);
            }
            
            String playerInfo = String.format("玩家: %d", route.getConnectedPlayerCount());
            
            invocation.source().sendMessage(Component.text(
                    String.format("  %d. %s (优先级: %d)",
                            i + 1, route.getAddress(), route.getPriority()),
                    NamedTextColor.WHITE
            ));
            
            invocation.source().sendMessage(Component.text(
                    String.format("     状态: %s | 带宽: %s | %s",
                            status, bandwidthInfo, playerInfo),
                    color
            ));
            
            // 显示延迟信息
            String ping = route.getLastPing() > 0 ? route.getLastPing() + "ms" : "未知";
            invocation.source().sendMessage(Component.text(
                    String.format("     延迟: %s | 最后更新: %d秒前",
                            ping, (System.currentTimeMillis() - route.getLastBandwidthUpdate()) / 1000),
                    NamedTextColor.GRAY
            ));
        }
        
        // 显示推荐路由
        RouteInfo bestRoute = routeManager.getBandwidthSelector()
                .selectBestRoute(config, java.util.UUID.randomUUID());
        if (bestRoute != null) {
            invocation.source().sendMessage(Component.text(
                    "推荐路由: " + bestRoute.getAddress() + 
                    " (带宽使用率: " + String.format("%.1f%%", bestRoute.getBandwidthUtilization()) + ")",
                    NamedTextColor.AQUA));
        } else {
            invocation.source().sendMessage(Component.text("无可用路由", NamedTextColor.RED));
        }
        
        // 显示总体统计
        int totalPlayers = config.getRoutes().stream()
                .mapToInt(RouteInfo::getConnectedPlayerCount)
                .sum();
        double totalBandwidth = config.getRoutes().stream()
                .mapToDouble(RouteInfo::getCurrentBandwidthUsage)
                .sum();
        
        invocation.source().sendMessage(Component.text(
                String.format("总计: %d 玩家, %.2f KB/s 总带宽",
                        totalPlayers, totalBandwidth / 1024.0),
                NamedTextColor.AQUA));
    }
    
    private void showDebugBandwidth(Invocation invocation) {
        invocation.source().sendMessage(Component.text("=== 带宽调试信息 ===", NamedTextColor.GOLD));
        
        try {
            // 获取BandwidthManager
            com.velocitypowered.api.proxy.player.BandwidthManager bm = 
                    routeManager.getBandwidthSelector().getBandwidthManager();
            
            if (bm == null) {
                invocation.source().sendMessage(Component.text("无法获取BandwidthManager", NamedTextColor.RED));
                return;
            }
            
            // 显示带宽追踪状态
            boolean trackingEnabled = bm.isBandwidthTrackingEnabled();
            long updateInterval = bm.getUpdateInterval();
            invocation.source().sendMessage(Component.text(
                    String.format("带宽追踪: %s | 更新间隔: %dms", 
                            trackingEnabled ? "已启用" : "已禁用", updateInterval),
                    trackingEnabled ? NamedTextColor.GREEN : NamedTextColor.RED));
            
            // 获取全局带宽快照
            com.velocitypowered.api.proxy.player.BandwidthSnapshot snapshot = bm.getTotalBandwidthSnapshot();
            invocation.source().sendMessage(Component.text(
                    String.format("全局统计: %d 玩家", snapshot.getTrackedPlayerCount()),
                    NamedTextColor.YELLOW));
            invocation.source().sendMessage(Component.text(
                    String.format("总下载速度: %.2f KB/s | 总上传速度: %.2f KB/s",
                            snapshot.getTotalDownloadSpeed() / 1024.0,
                            snapshot.getTotalUploadSpeed() / 1024.0),
                    NamedTextColor.WHITE));
            invocation.source().sendMessage(Component.text(
                    String.format("平均每玩家: 下载 %.2f KB/s | 上传 %.2f KB/s",
                            snapshot.getAverageDownloadSpeedPerPlayer() / 1024.0,
                            snapshot.getAverageUploadSpeedPerPlayer() / 1024.0),
                    NamedTextColor.WHITE));
            invocation.source().sendMessage(Component.text(
                    String.format("峰值: 下载 %.2f KB/s | 上传 %.2f KB/s",
                            snapshot.getPeakTotalDownloadSpeed() / 1024.0,
                            snapshot.getPeakTotalUploadSpeed() / 1024.0),
                    NamedTextColor.AQUA));
            
            invocation.source().sendMessage(Component.text("=== 各玩家详细统计 ===", NamedTextColor.GOLD));
            
            // 获取所有玩家带宽统计
            java.util.Collection<com.velocitypowered.api.proxy.player.PlayerBandwidthStats> allStats = 
                    bm.getAllPlayerBandwidthStats();
            
            if (allStats.isEmpty()) {
                invocation.source().sendMessage(Component.text("暂无玩家带宽数据", NamedTextColor.GRAY));
            } else {
                for (com.velocitypowered.api.proxy.player.PlayerBandwidthStats stats : allStats) {
                    double totalBandwidth = stats.getDownloadSpeed() + stats.getUploadSpeed();
                    NamedTextColor color = totalBandwidth > 100 * 1024 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
                    
                    invocation.source().sendMessage(Component.text(
                            String.format("%s (服务器: %s)",
                                    stats.getPlayerUsername(),
                                    stats.getCurrentServerName()),
                            NamedTextColor.WHITE));
                    invocation.source().sendMessage(Component.text(
                            String.format("  带宽: 下载 %.2f KB/s | 上传 %.2f KB/s | 总计 %.2f KB/s",
                                    stats.getDownloadSpeed() / 1024.0,
                                    stats.getUploadSpeed() / 1024.0,
                                    totalBandwidth / 1024.0),
                            color));
                    invocation.source().sendMessage(Component.text(
                            String.format("  总流量: 接收 %.2f MB | 发送 %.2f MB",
                                    stats.getTotalBytesReceived() / 1024.0 / 1024.0,
                                    stats.getTotalBytesSent() / 1024.0 / 1024.0),
                            NamedTextColor.GRAY));
                }
            }
            
        } catch (Exception e) {
            invocation.source().sendMessage(Component.text("获取带宽调试信息失败: " + e.getMessage(), NamedTextColor.RED));
            logger.error("获取带宽调试信息失败", e);
        }
    }
    
    private String getSourceName(Invocation invocation) {
        if (invocation.source() instanceof Player) {
            return ((Player) invocation.source()).getUsername();
        }
        return "控制台";
    }
    
    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        
        if (args.length <= 1) {
            return CompletableFuture.completedFuture(List.of("status", "routes", "bandwidth", "bw", "debug", "reload"));
        }
        
        if ((args[0].equalsIgnoreCase("routes") || args[0].equalsIgnoreCase("bandwidth") || args[0].equalsIgnoreCase("bw")) 
                && args.length == 2) {
            return CompletableFuture.completedFuture(
                    routeManager.getAllServerConfigs().keySet().stream().toList()
            );
        }
        
        return CompletableFuture.completedFuture(List.of());
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocityass.admin");
    }
}