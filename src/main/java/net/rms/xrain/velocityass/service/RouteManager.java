package net.rms.xrain.velocityass.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.BandwidthManager;
import com.velocitypowered.api.proxy.player.PlayerBandwidthStats;
import net.rms.xrain.velocityass.config.ConfigManager;
import net.rms.xrain.velocityass.config.RouteInfo;
import net.rms.xrain.velocityass.config.ServerConfig;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RouteManager {
    private final ConfigManager configManager;
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Map<String, Long> lastConnectionAttempt;
    private final Map<UUID, String> playerRouteMapping; // 玩家到路由地址的映射
    private final BandwidthAwareRouteSelector bandwidthSelector;
    private final BandwidthManager bandwidthManager;
    
    public RouteManager(ConfigManager configManager, ProxyServer proxyServer, Logger logger) {
        this.configManager = configManager;
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.lastConnectionAttempt = new ConcurrentHashMap<>();
        this.playerRouteMapping = new ConcurrentHashMap<>();
        this.bandwidthSelector = new BandwidthAwareRouteSelector(proxyServer, logger);
        this.bandwidthManager = proxyServer.getBandwidthManager();
        
        // 启用带宽追踪
        bandwidthManager.setBandwidthTrackingEnabled(true);
        logger.info("RouteManager已初始化，带宽感知路由选择已启用");
    }
    
    public RouteInfo selectBestRoute(String serverName) {
        return selectBestRoute(serverName, null);
    }
    
    public RouteInfo selectBestRoute(String serverName, UUID playerId) {
        ServerConfig serverConfig = configManager.getServerConfig(serverName);
        if (serverConfig == null) {
            logger.debug("未找到服务器 {} 的配置", serverName);
            return null;
        }
        
        RouteInfo bestRoute;
        if (playerId != null) {
            // 使用带宽感知选择器
            bestRoute = bandwidthSelector.selectBestRouteWithFallback(serverConfig, playerId);
        } else {
            // 传统选择逻辑（向后兼容）
            bestRoute = serverConfig.getBestRoute();
        }
        
        if (bestRoute == null) {
            logger.warn("服务器 {} 没有可用的路由", serverName);
            return null;
        }
        
        // 记录连接尝试时间和玩家路由映射
        String key = serverName + ":" + bestRoute.getAddress();
        lastConnectionAttempt.put(key, System.currentTimeMillis());
        
        if (playerId != null) {
            // 先清理该玩家的所有旧路由映射
            removePlayerFromAllRoutes(playerId);
            // 然后添加到新路由
            playerRouteMapping.put(playerId, bestRoute.getAddress());
        }
        
        logger.debug("为服务器 {} 选择路由: {} (延迟: {}ms, 带宽使用率: {:.1f}%)", 
                serverName, bestRoute.getAddress(), bestRoute.getLastPing(), 
                bestRoute.getBandwidthUtilization());
        
        return bestRoute;
    }
    
    public void updateRouteStatus(String serverName, String address, boolean available, long ping) {
        ServerConfig serverConfig = configManager.getServerConfig(serverName);
        if (serverConfig == null) {
            return;
        }
        
        for (RouteInfo route : serverConfig.getRoutes()) {
            if (route.getAddress().equals(address)) {
                route.setAvailable(available);
                if (available && ping > 0) {
                    route.setLastPing(ping);
                }
                break;
            }
        }
    }
    
    public void markRouteUnavailable(String serverName, String address) {
        updateRouteStatus(serverName, address, false, -1);
        logger.warn("标记路由不可用: {} -> {}", serverName, address);
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public Map<String, ServerConfig> getAllServerConfigs() {
        return configManager.getAllServerConfigs();
    }
    
    public boolean hasRoutes(String serverName) {
        ServerConfig config = configManager.getServerConfig(serverName);
        return config != null && !config.getRoutes().isEmpty();
    }
    
    public int getAvailableRouteCount(String serverName) {
        ServerConfig config = configManager.getServerConfig(serverName);
        if (config == null) {
            return 0;
        }
        
        return (int) config.getRoutes().stream()
                .filter(route -> route.isEnabled() && route.isAvailable())
                .count();
    }
    
    // 新增的带宽相关方法
    public void updatePlayerBandwidthUsage(Player player) {
        // 使用BandwidthManager获取玩家统计信息
        Optional<PlayerBandwidthStats> statsOpt = bandwidthManager.getPlayerBandwidthStats(player);
        if (!statsOpt.isPresent()) {
            logger.debug("无法获取玩家 {} 的带宽统计信息", player.getUsername());
            return;
        }
        
        PlayerBandwidthStats stats = statsOpt.get();
        String routeAddress = playerRouteMapping.get(player.getUniqueId());
        
        if (routeAddress == null) {
            logger.debug("玩家 {} 没有路由映射", player.getUsername());
            return;
        }
        
        // 查找对应的路由并更新带宽使用情况
        if (player.getCurrentServer().isPresent()) {
            String serverName = player.getCurrentServer().get().getServerInfo().getName();
            ServerConfig serverConfig = configManager.getServerConfig(serverName);
            
            if (serverConfig != null) {
                for (RouteInfo route : serverConfig.getRoutes()) {
                    if (route.getAddress().equals(routeAddress)) {
                        // 重新计算该路由的总带宽使用
                        double totalBandwidth = calculateRouteBandwidthUsage(route);
                        route.setCurrentBandwidthUsage(totalBandwidth);
                        
                        logger.debug("更新路由 {} 带宽使用: {:.2f} KB/s", 
                                routeAddress, totalBandwidth / 1024.0);
                        break;
                    }
                }
            }
        }
    }
    
    private double calculateRouteBandwidthUsage(RouteInfo route) {
        double totalBandwidth = 0.0;
        
        for (UUID playerId : route.getConnectedPlayers()) {
            Player player = proxyServer.getPlayer(playerId).orElse(null);
            if (player != null) {
                Optional<PlayerBandwidthStats> statsOpt = bandwidthManager.getPlayerBandwidthStats(playerId);
                if (statsOpt.isPresent()) {
                    PlayerBandwidthStats stats = statsOpt.get();
                    // 计算总带宽：下载速度 + 上传速度
                    double playerBandwidth = stats.getDownloadSpeed() + stats.getUploadSpeed();
                    totalBandwidth += playerBandwidth;
                    
                    logger.debug("玩家 {} 带宽: 下载 {:.2f} KB/s, 上传 {:.2f} KB/s, 总计 {:.2f} KB/s", 
                            player.getUsername(),
                            stats.getDownloadSpeed() / 1024.0,
                            stats.getUploadSpeed() / 1024.0,
                            playerBandwidth / 1024.0);
                }
            }
        }
        
        logger.debug("路由 {} 总带宽使用: {:.2f} KB/s ({} 玩家)", 
                route.getAddress(), totalBandwidth / 1024.0, route.getConnectedPlayerCount());
        
        return totalBandwidth;
    }
    
    public void onPlayerDisconnect(UUID playerId) {
        removePlayerFromAllRoutes(playerId);
        logger.debug("玩家 {} 断开连接，已清理所有路由映射", playerId);
    }
    
    public void removePlayerFromAllRoutes(UUID playerId) {
        String routeAddress = playerRouteMapping.remove(playerId);
        if (routeAddress != null) {
            // 从路由中移除玩家
            getAllServerConfigs().values().forEach(serverConfig -> {
                serverConfig.getRoutes().stream()
                        .filter(route -> route.getAddress().equals(routeAddress))
                        .findFirst()
                        .ifPresent(route -> {
                            bandwidthSelector.removePlayerFromRoute(playerId, route);
                            // 重新计算该路由的带宽使用
                            double totalBandwidth = calculateRouteBandwidthUsage(route);
                            route.setCurrentBandwidthUsage(totalBandwidth);
                        });
            });
            
            logger.debug("从路由 {} 移除玩家 {}", routeAddress, playerId);
        }
        
        // 额外保险：遍历所有路由，确保彻底清理
        getAllServerConfigs().values().forEach(serverConfig -> {
            serverConfig.getRoutes().forEach(route -> {
                if (route.getConnectedPlayers().remove(playerId)) {
                    logger.debug("从路由 {} 额外清理玩家 {}", route.getAddress(), playerId);
                    // 重新计算该路由的带宽使用
                    double totalBandwidth = calculateRouteBandwidthUsage(route);
                    route.setCurrentBandwidthUsage(totalBandwidth);
                }
            });
        });
    }
    
    public void updateAllRoutesBandwidth() {
        getAllServerConfigs().values().forEach(serverConfig -> {
            serverConfig.getRoutes().forEach(route -> {
                double totalBandwidth = calculateRouteBandwidthUsage(route);
                route.setCurrentBandwidthUsage(totalBandwidth);
            });
        });
    }
    
    public BandwidthAwareRouteSelector getBandwidthSelector() {
        return bandwidthSelector;
    }
    
    public Map<UUID, String> getPlayerRouteMapping() {
        return new ConcurrentHashMap<>(playerRouteMapping);
    }
    
    public void shutdown() {
        if (bandwidthSelector != null) {
            bandwidthSelector.shutdown();
        }
        logger.info("RouteManager已关闭");
    }
}