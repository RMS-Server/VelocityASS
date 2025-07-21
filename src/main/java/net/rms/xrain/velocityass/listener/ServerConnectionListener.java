package net.rms.xrain.velocityass.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.rms.xrain.velocityass.config.RouteInfo;
import net.rms.xrain.velocityass.service.RouteManager;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ServerConnectionListener {
    private final RouteManager routeManager;
    private final Logger logger;
    private final ProxyServer proxyServer;
    private final Object plugin;
    private final Map<UUID, Integer> retryAttempts = new ConcurrentHashMap<>();
    private static final int MAX_RETRY_ATTEMPTS = 2;
    
    public ServerConnectionListener(RouteManager routeManager, Logger logger, ProxyServer proxyServer, Object plugin) {
        this.routeManager = routeManager;
        this.logger = logger;
        this.proxyServer = proxyServer;
        this.plugin = plugin;
    }
    
    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        RegisteredServer originalServer = event.getOriginalServer();
        if (originalServer == null) {
            return;
        }
        
        String serverName = originalServer.getServerInfo().getName();
        
        if (!routeManager.hasRoutes(serverName)) {
            logger.debug("服务器 {} 未配置多路由，使用默认连接", serverName);
            return;
        }
        
        RouteInfo bestRoute = routeManager.selectBestRoute(serverName, event.getPlayer().getUniqueId());
        if (bestRoute == null) {
            logger.warn("服务器 {} 没有可用路由，玩家 {} 连接可能失败", 
                    serverName, event.getPlayer().getUsername());
            return;
        }
        
        String originalAddress = originalServer.getServerInfo().getAddress().getHostString() + ":" + 
                                originalServer.getServerInfo().getAddress().getPort();
        
        if (!originalAddress.equals(bestRoute.getAddress())) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            
            proxyServer.getScheduler().buildTask(plugin, () -> {
                connectPlayerToRoute(event.getPlayer(), serverName, bestRoute, originalAddress);
            }).delay(100, TimeUnit.MILLISECONDS).schedule();
        } else {
            logger.debug("玩家 {} 连接服务器 {} 使用原始地址 {}", 
                    event.getPlayer().getUsername(), serverName, originalAddress);
        }
    }
    
    private void connectPlayerToRoute(com.velocitypowered.api.proxy.Player player, String serverName, RouteInfo route, String originalAddress) {
        try {
            if (!player.isActive()) {
                logger.warn("玩家 {} 已不在线，取消路由连接", player.getUsername());
                return;
            }
            
            if (player.getCurrentServer().isPresent()) {
                String currentServerName = player.getCurrentServer().get().getServerInfo().getName();
                String currentAddress = player.getCurrentServer().get().getServerInfo().getAddress().getHostString() + ":" +
                                       player.getCurrentServer().get().getServerInfo().getAddress().getPort();
                
                if (route.getAddress().equals(currentAddress)) {
                    logger.info("玩家 {} 已经连接到路由 {}，跳过重复连接", 
                            player.getUsername(), route.getAddress());
                    return;
                }
            }
            
            InetSocketAddress routeAddress = new InetSocketAddress(route.getHost(), route.getPort());
            ServerInfo routeServerInfo = new ServerInfo(serverName + "_temp", routeAddress);
            
            RegisteredServer tempServer = proxyServer.registerServer(routeServerInfo);
            
            player.createConnectionRequest(tempServer).connect().thenAccept(result -> {
                if (result.isSuccessful()) {
                    logger.info("玩家 {} 成功通过路由连接到 {}: {} -> {} (延迟: {}ms)", 
                            player.getUsername(), 
                            serverName,
                            originalAddress, 
                            route.getAddress(),
                            route.getLastPing() > 0 ? route.getLastPing() : "未知");
                    retryAttempts.remove(player.getUniqueId());
                } else {
                    String errorMessage = result.getReasonComponent()
                            .map(component -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component))
                            .orElse("未知错误");
                    
                    logger.error("玩家 {} 通过路由连接到 {} 失败: {}", 
                            player.getUsername(), 
                            serverName,
                            errorMessage);
                    
                    if (isViaVersionRelatedError(errorMessage)) {
                        int currentRetries = retryAttempts.getOrDefault(player.getUniqueId(), 0);
                        if (currentRetries < MAX_RETRY_ATTEMPTS) {
                            logger.warn("检测到ViaVersion相关连接错误（重试 {}/{}），尝试重新连接: {}", 
                                    currentRetries + 1, MAX_RETRY_ATTEMPTS, errorMessage);
                            retryAttempts.put(player.getUniqueId(), currentRetries + 1);
                            retryConnectionAfterDelay(player, serverName, route, originalAddress, 500);
                            return;
                        } else {
                            logger.error("ViaVersion相关错误重试次数已达上限，停止重试: {}", errorMessage);
                            retryAttempts.remove(player.getUniqueId());
                        }
                    }
                    
                    if (isNetworkConnectivityIssue(errorMessage)) {
                        logger.warn("检测到网络连接问题，标记路由不可用: {} -> {}", serverName, route.getAddress());
                        routeManager.markRouteUnavailable(serverName, route.getAddress());
                    } else {
                        logger.info("连接失败原因非网络问题，保持路由可用状态: {} -> {}", serverName, route.getAddress());
                    }
                }
                
                proxyServer.getScheduler().buildTask(plugin, () -> {
                    try {
                        proxyServer.unregisterServer(routeServerInfo);
                        logger.debug("已清理临时服务器注册: {}", routeServerInfo.getName());
                    } catch (Exception e) {
                        logger.warn("清理临时服务器注册失败: {}, 错误: {}", routeServerInfo.getName(), e.getMessage());
                    }
                }).delay(2, java.util.concurrent.TimeUnit.SECONDS).schedule();
            });
            
        } catch (Exception e) {
            logger.error("创建路由连接失败，玩家: {}, 服务器: {}, 路由: {}, 错误: {}", 
                    player.getUsername(), serverName, route.getAddress(), e.getMessage());
            
            routeManager.markRouteUnavailable(serverName, route.getAddress());
        }
    }
    
    /**
     * 检测是否为ViaVersion相关错误
     */
    private boolean isViaVersionRelatedError(String errorMessage) {
        if (errorMessage == null) return false;
        
        String lowercaseError = errorMessage.toLowerCase();
        
        return lowercaseError.contains("viaversion") || 
               lowercaseError.contains("protocol") ||
               lowercaseError.contains("packet") ||
               lowercaseError.contains("login_finished") ||
               lowercaseError.contains("decoderexception") ||
               lowercaseError.contains("informativeexception");
    }
    
    /**
     * 延迟重试连接（仅用于ViaVersion相关错误）
     */
    private void retryConnectionAfterDelay(Player player, String serverName, RouteInfo route, String originalAddress, int delayMs) {
        if (!player.isActive()) {
            logger.debug("玩家 {} 已下线，取消重试连接", player.getUsername());
            return;
        }
        
        proxyServer.getScheduler().buildTask(plugin, () -> {
            logger.info("重试为玩家 {} 连接到路由 {}", player.getUsername(), route.getAddress());
            connectPlayerToRoute(player, serverName, route, originalAddress);
        }).delay(delayMs, TimeUnit.MILLISECONDS).schedule();
    }
    
    private boolean isNetworkConnectivityIssue(String errorMessage) {
        if (errorMessage == null) return true;
        
        String lowercaseError = errorMessage.toLowerCase();
        
        if (isViaVersionRelatedError(errorMessage)) {
            logger.debug("检测到ViaVersion相关错误，不标记路由不可用: {}", errorMessage);
            return false;
        }
        
        return lowercaseError.contains("connection") && 
               (lowercaseError.contains("refused") || 
                lowercaseError.contains("timeout") || 
                lowercaseError.contains("unreachable") ||
                lowercaseError.contains("reset") ||
                lowercaseError.contains("timed out"));
    }
    
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String newServerName = event.getServer().getServerInfo().getName();
        
        String oldRouteAddress = routeManager.getPlayerRouteMapping().get(player.getUniqueId());
        if (oldRouteAddress != null) {
            routeManager.getAllServerConfigs().values().forEach(serverConfig -> {
                serverConfig.getRoutes().forEach(route -> {
                    if (route.removeConnectedPlayer(player.getUniqueId())) {
                        logger.debug("玩家 {} 从路由 {} 的玩家列表中移除", player.getUsername(), route.getAddress());
                    }
                });
            });
        }
        
        proxyServer.getScheduler()
                .buildTask(plugin, () -> {
                    if (player.isActive()) {
                        routeManager.updatePlayerBandwidthUsage(player);
                    }
                })
                .schedule();
        
        logger.debug("玩家 {} 连接到服务器 {}，已启动带宽监控", 
                player.getUsername(), newServerName);
    }
    
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        
        routeManager.onPlayerDisconnect(player.getUniqueId());
        retryAttempts.remove(player.getUniqueId());
        
        logger.debug("玩家 {} 断开连接，已清理路由映射和重试计数器", player.getUsername());
    }
}