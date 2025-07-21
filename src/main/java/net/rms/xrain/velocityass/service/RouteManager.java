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
    private final Map<UUID, String> playerRouteMapping;
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
        
        updateAllRoutesBandwidth();
        
        RouteInfo bestRoute;
        if (playerId != null) {
            bestRoute = bandwidthSelector.selectBestRouteWithFallback(serverConfig, playerId);
        } else {
            bestRoute = serverConfig.getBestRoute();
        }
        
        if (bestRoute == null) {
            logger.warn("服务器 {} 没有可用的路由", serverName);
            return null;
        }
        
        String key = serverName + ":" + bestRoute.getAddress();
        lastConnectionAttempt.put(key, System.currentTimeMillis());
        
        if (playerId != null) {
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
    
    public void updatePlayerBandwidthUsage(Player player) {
        Optional<PlayerBandwidthStats> statsOpt = bandwidthManager.getPlayerBandwidthStats(player);
        if (!statsOpt.isPresent()) {
            logger.debug("无法获取玩家 {} 的带宽统计信息", player.getUsername());
            return;
        }
        
        String routeAddress = playerRouteMapping.get(player.getUniqueId());
        
        if (routeAddress == null) {
            logger.debug("玩家 {} 没有路由映射", player.getUsername());
            return;
        }
        
        if (player.getCurrentServer().isPresent()) {
            String serverName = player.getCurrentServer().get().getServerInfo().getName();
            ServerConfig serverConfig = configManager.getServerConfig(serverName);
            
            if (serverConfig != null) {
                for (RouteInfo route : serverConfig.getRoutes()) {
                    if (route.getAddress().equals(routeAddress)) {
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
        int actualPlayers = 0;
        
        logger.debug("计算路由 {} 带宽使用, 玩家列表大小: {}", route.getAddress(), route.getConnectedPlayerCount());
        
        for (UUID playerId : route.getConnectedPlayers()) {
            Player player = proxyServer.getPlayer(playerId).orElse(null);
            if (player != null) {
                Optional<PlayerBandwidthStats> statsOpt = bandwidthManager.getPlayerBandwidthStats(player);
                if (statsOpt.isPresent()) {
                    PlayerBandwidthStats stats = statsOpt.get();
                    double playerBandwidth = stats.getDownloadSpeed() + stats.getUploadSpeed();
                    totalBandwidth += playerBandwidth;
                    actualPlayers++;
                    
                    logger.debug("玩家 {} 带宽: 下载 {:.2f} KB/s, 上传 {:.2f} KB/s, 总计 {:.2f} KB/s", 
                            player.getUsername(),
                            stats.getDownloadSpeed() / 1024.0,
                            stats.getUploadSpeed() / 1024.0,
                            playerBandwidth / 1024.0);
                } else {
                    logger.debug("无法获取玩家 {} 的带宽统计", player.getUsername());
                }
            } else {
                logger.debug("玩家 {} 已下线，将从路由中移除", playerId);
                route.removeConnectedPlayer(playerId);
            }
        }
        
        logger.debug("路由 {} 总带宽使用: {:.2f} KB/s ({} 活跃玩家)", 
                route.getAddress(), totalBandwidth / 1024.0, actualPlayers);
        
        return totalBandwidth;
    }
    
    public void onPlayerDisconnect(UUID playerId) {
        removePlayerFromAllRoutes(playerId);
        logger.debug("玩家 {} 断开连接，已清理所有路由映射", playerId);
    }
    
    public void removePlayerFromAllRoutes(UUID playerId) {
        String routeAddress = playerRouteMapping.remove(playerId);
        if (routeAddress != null) {
            getAllServerConfigs().values().forEach(serverConfig -> {
                serverConfig.getRoutes().stream()
                        .filter(route -> route.getAddress().equals(routeAddress))
                        .findFirst()
                        .ifPresent(route -> {
                            bandwidthSelector.removePlayerFromRoute(playerId, route);
                            double totalBandwidth = calculateRouteBandwidthUsage(route);
                            route.setCurrentBandwidthUsage(totalBandwidth);
                        });
            });
            
            logger.debug("从路由 {} 移除玩家 {}", routeAddress, playerId);
        }
        
        getAllServerConfigs().values().forEach(serverConfig -> {
            serverConfig.getRoutes().forEach(route -> {
                if (route.getConnectedPlayers().remove(playerId)) {
                    logger.debug("从路由 {} 额外清理玩家 {}", route.getAddress(), playerId);
                    double totalBandwidth = calculateRouteBandwidthUsage(route);
                    route.setCurrentBandwidthUsage(totalBandwidth);
                }
            });
        });
    }
    
    public void updateAllRoutesBandwidth() {
        logger.debug("开始更新所有路由的带宽信息");
        
        syncPlayerRouteMapping();
        
        getAllServerConfigs().values().forEach(serverConfig -> {
            serverConfig.getRoutes().forEach(route -> {
                double totalBandwidth = calculateRouteBandwidthUsage(route);
                route.setCurrentBandwidthUsage(totalBandwidth);
                route.setLastBandwidthUpdate(System.currentTimeMillis());
                
                if (route.isBandwidthLimited()) {
                    logger.debug("更新路由 {} 带宽: {:.2f}/{:.2f} KB/s ({:.1f}%)", 
                            route.getAddress(),
                            totalBandwidth / 1024.0,
                            route.getMaxBandwidth() / 1024.0,
                            route.getBandwidthUtilization());
                }
            });
        });
        
        logger.debug("所有路由带宽信息更新完成");
    }
    
    private void syncPlayerRouteMapping() {
        proxyServer.getAllPlayers().forEach(player -> {
            String routeAddress = playerRouteMapping.get(player.getUniqueId());
            if (routeAddress != null) {
                boolean found = false;
                for (ServerConfig serverConfig : getAllServerConfigs().values()) {
                    for (RouteInfo route : serverConfig.getRoutes()) {
                        if (route.getAddress().equals(routeAddress)) {
                            if (!route.getConnectedPlayers().contains(player.getUniqueId())) {
                                route.addConnectedPlayer(player.getUniqueId());
                                logger.debug("同步：将玩家 {} 添加到路由 {} (配置: {})", 
                                        player.getUsername(), routeAddress, serverConfig.getServerName());
                            }
                            found = true;
                            break;
                        }
                    }
                    if (found) break;
                }
                
                if (!found) {
                    logger.warn("玩家 {} 的路由 {} 在配置中未找到", player.getUsername(), routeAddress);
                }
            }
        });
    }
    
    public BandwidthAwareRouteSelector getBandwidthSelector() {
        return bandwidthSelector;
    }
    
    public Map<UUID, String> getPlayerRouteMapping() {
        return new ConcurrentHashMap<>(playerRouteMapping);
    }
    
    public ProxyServer getProxyServer() {
        return proxyServer;
    }
    
    public void shutdown() {
        if (bandwidthSelector != null) {
            bandwidthSelector.shutdown();
        }
        logger.info("RouteManager已关闭");
    }
}