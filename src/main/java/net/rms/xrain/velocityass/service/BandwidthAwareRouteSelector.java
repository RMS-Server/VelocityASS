package net.rms.xrain.velocityass.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.BandwidthManager;
import com.velocitypowered.api.proxy.player.BandwidthSnapshot;
import com.velocitypowered.api.proxy.player.PlayerBandwidthStats;
import net.rms.xrain.velocityass.config.RouteInfo;
import net.rms.xrain.velocityass.config.ServerConfig;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BandwidthAwareRouteSelector {
    
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final BandwidthManager bandwidthManager;
    private final ScheduledExecutorService scheduler;
    
    private static final long BANDWIDTH_UPDATE_INTERVAL = 5; // 5秒更新一次带宽数据
    private static final double BANDWIDTH_THRESHOLD = 0.85; // 85%带宽使用率阈值
    private static final long BANDWIDTH_DATA_MAX_AGE = 10_000; // 带宽数据最大年龄（10秒）
    
    public BandwidthAwareRouteSelector(ProxyServer proxyServer, Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.bandwidthManager = proxyServer.getBandwidthManager();
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // 启动带宽监控任务
        startBandwidthMonitoring();
    }
    
    public RouteInfo selectBestRoute(ServerConfig serverConfig, UUID playerId) {
        if (serverConfig == null || serverConfig.getRoutes().isEmpty()) {
            return null;
        }
        
        // 确保带宽数据是最新的 - 如果数据太旧或未初始化，立即更新
        ensureFreshBandwidthData(serverConfig);
        
        List<RouteInfo> routes = serverConfig.getRoutes();
        
        // 按优先级排序的可用路由
        RouteInfo selectedRoute = routes.stream()
                .filter(route -> route.isEnabled() && route.isAvailable())
                .sorted((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()))
                .filter(this::isBandwidthAvailableForNewConnection)
                .findFirst()
                .orElse(null);
        
        if (selectedRoute != null) {
            // 先确保玩家不在其他路由中
            serverConfig.getRoutes().forEach(route -> route.removeConnectedPlayer(playerId));
            // 然后记录玩家连接到此路由
            selectedRoute.addConnectedPlayer(playerId);
            logger.debug("为玩家 {} 选择路由: {} (优先级: {}, 带宽使用率: {:.1f}%)", 
                    playerId, selectedRoute.getAddress(), 
                    selectedRoute.getPriority(), selectedRoute.getBandwidthUtilization());
        } else {
            logger.warn("服务器 {} 没有可用的路由（所有路由都已达到带宽限制）", serverConfig.getServerName());
        }
        
        return selectedRoute;
    }
    
    public RouteInfo selectBestRouteWithFallback(ServerConfig serverConfig, UUID playerId) {
        RouteInfo route = selectBestRoute(serverConfig, playerId);
        
        // 如果没有找到可用路由，尝试选择带宽使用率最低的路由作为fallback
        if (route == null) {
            route = serverConfig.getRoutes().stream()
                    .filter(r -> r.isEnabled() && r.isAvailable())
                    .sorted((r1, r2) -> {
                        // 首先按优先级排序
                        int priorityCompare = Integer.compare(r1.getPriority(), r2.getPriority());
                        if (priorityCompare != 0) {
                            return priorityCompare;
                        }
                        // 然后按带宽使用率排序
                        return Double.compare(r1.getBandwidthUtilization(), r2.getBandwidthUtilization());
                    })
                    .findFirst()
                    .orElse(null);
                    
            if (route != null) {
                // 先确保玩家不在其他路由中
                serverConfig.getRoutes().forEach(r -> r.removeConnectedPlayer(playerId));
                // 然后添加到选定的路由
                route.addConnectedPlayer(playerId);
                logger.warn("使用fallback路由为玩家 {} 选择: {} (带宽使用率: {:.1f}%)", 
                        playerId, route.getAddress(), route.getBandwidthUtilization());
            }
        }
        
        return route;
    }
    
    private boolean isBandwidthAvailableForNewConnection(RouteInfo route) {
        if (!route.isBandwidthLimited()) {
            return true; // 无带宽限制
        }
        
        // 检查当前带宽使用率是否超过阈值
        double utilizationRate = route.getBandwidthUtilization() / 100.0;
        return utilizationRate < BANDWIDTH_THRESHOLD;
    }
    
    private void startBandwidthMonitoring() {
        // 立即执行一次带宽数据更新
        try {
            logger.info("执行初始带宽数据更新...");
            updateAllRoutesBandwidthUsage();
        } catch (Exception e) {
            logger.warn("初始带宽数据更新失败", e);
        }
        
        // 然后启动定期更新任务
        scheduler.scheduleAtFixedRate(() -> {
            try {
                updateAllRoutesBandwidthUsage();
            } catch (Exception e) {
                logger.error("更新路由带宽使用情况时发生错误", e);
            }
        }, BANDWIDTH_UPDATE_INTERVAL, BANDWIDTH_UPDATE_INTERVAL, TimeUnit.SECONDS);
        
        logger.info("带宽监控任务已启动，更新间隔: {}秒", BANDWIDTH_UPDATE_INTERVAL);
    }
    
    private void updateAllRoutesBandwidthUsage() {
        // 获取全局带宽快照
        BandwidthSnapshot globalSnapshot = bandwidthManager.getTotalBandwidthSnapshot();
        
        logger.debug("全局带宽统计: {} 玩家, 总下载 {:.2f} KB/s, 总上传 {:.2f} KB/s", 
                globalSnapshot.getTrackedPlayerCount(),
                globalSnapshot.getTotalDownloadSpeed() / 1024.0,
                globalSnapshot.getTotalUploadSpeed() / 1024.0);
        
        // 遍历所有在线玩家，更新其路由带宽使用情况
        proxyServer.getAllPlayers().forEach(player -> {
            Optional<PlayerBandwidthStats> statsOpt = bandwidthManager.getPlayerBandwidthStats(player);
            if (statsOpt.isPresent()) {
                PlayerBandwidthStats stats = statsOpt.get();
                updatePlayerBandwidthUsage(player, stats);
            }
        });
    }
    
    private void updatePlayerBandwidthUsage(Player player, PlayerBandwidthStats stats) {
        if (player.getCurrentServer().isPresent()) {
            String serverName = player.getCurrentServer().get().getServerInfo().getName();
            
            // 记录详细的玩家带宽信息
            double totalPlayerBandwidth = stats.getDownloadSpeed() + stats.getUploadSpeed();
            if (totalPlayerBandwidth > 0) {
                logger.debug("玩家 {} (服务器: {}) 当前带宽: 下载 {:.2f} KB/s, 上传 {:.2f} KB/s, 总计 {:.2f} KB/s", 
                        player.getUsername(), 
                        serverName,
                        stats.getDownloadSpeed() / 1024.0,
                        stats.getUploadSpeed() / 1024.0,
                        totalPlayerBandwidth / 1024.0);
            }
        }
    }
    
    public void removePlayerFromRoute(UUID playerId, RouteInfo route) {
        if (route != null) {
            route.removeConnectedPlayer(playerId);
            logger.debug("从路由 {} 移除玩家 {}", route.getAddress(), playerId);
        }
    }
    
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("带宽感知路由选择器已关闭");
    }
    
    public double getBandwidthThreshold() {
        return BANDWIDTH_THRESHOLD;
    }
    
    public long getBandwidthUpdateInterval() {
        return BANDWIDTH_UPDATE_INTERVAL;
    }
    
    public BandwidthManager getBandwidthManager() {
        return bandwidthManager;
    }
    
    /**
     * 确保带宽数据是最新的，如果数据过时则立即更新
     */
    private void ensureFreshBandwidthData(ServerConfig serverConfig) {
        boolean needsUpdate = false;
        long currentTime = System.currentTimeMillis();
        
        // 检查是否有路由数据过时或未初始化
        for (RouteInfo route : serverConfig.getRoutes()) {
            if (route.isBandwidthLimited()) {
                long lastUpdate = route.getLastBandwidthUpdate();
                if (lastUpdate == 0 || (currentTime - lastUpdate) > BANDWIDTH_DATA_MAX_AGE) {
                    needsUpdate = true;
                    logger.debug("路由 {} 带宽数据过时，需要更新 (上次更新: {}ms前)", 
                            route.getAddress(), 
                            lastUpdate == 0 ? "从未" : String.valueOf(currentTime - lastUpdate));
                    break;
                }
            }
        }
        
        if (needsUpdate) {
            logger.debug("执行即时带宽数据更新");
            updateAllRoutesBandwidthUsage();
        }
    }
}