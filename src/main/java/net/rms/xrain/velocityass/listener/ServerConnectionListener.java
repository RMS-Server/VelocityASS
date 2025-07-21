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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ServerConnectionListener {
    private final RouteManager routeManager;
    private final Logger logger;
    private final ProxyServer proxyServer;
    private final Object plugin;
    
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
        
        // 检查是否有多路由配置
        if (!routeManager.hasRoutes(serverName)) {
            logger.debug("服务器 {} 未配置多路由，使用默认连接", serverName);
            return;
        }
        
        // 使用带宽感知的路由选择（传入玩家ID）
        RouteInfo bestRoute = routeManager.selectBestRoute(serverName, event.getPlayer().getUniqueId());
        if (bestRoute == null) {
            logger.warn("服务器 {} 没有可用路由，玩家 {} 连接可能失败", 
                    serverName, event.getPlayer().getUsername());
            return;
        }
        
        // 检查是否需要替换目标服务器
        String originalAddress = originalServer.getServerInfo().getAddress().getHostString() + ":" + 
                                originalServer.getServerInfo().getAddress().getPort();
        
        if (!originalAddress.equals(bestRoute.getAddress())) {
            // 拒绝原始连接，我们将手动处理连接
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            
            // 异步执行路由连接
            proxyServer.getScheduler().buildTask(plugin, () -> {
                connectPlayerToRoute(event.getPlayer(), serverName, bestRoute, originalAddress);
            }).schedule();
        } else {
            logger.debug("玩家 {} 连接服务器 {} 使用原始地址 {}", 
                    event.getPlayer().getUsername(), serverName, originalAddress);
        }
    }
    
    private void connectPlayerToRoute(com.velocitypowered.api.proxy.Player player, String serverName, RouteInfo route, String originalAddress) {
        try {
            // 创建临时服务器信息用于连接
            InetSocketAddress routeAddress = new InetSocketAddress(route.getHost(), route.getPort());
            ServerInfo routeServerInfo = new ServerInfo(serverName + "_temp", routeAddress);
            
            // 临时注册服务器
            RegisteredServer tempServer = proxyServer.registerServer(routeServerInfo);
            
            // 连接玩家到路由服务器
            player.createConnectionRequest(tempServer).connect().thenAccept(result -> {
                if (result.isSuccessful()) {
                    logger.info("玩家 {} 成功通过路由连接到 {}: {} -> {} (延迟: {}ms)", 
                            player.getUsername(), 
                            serverName,
                            originalAddress, 
                            route.getAddress(),
                            route.getLastPing() > 0 ? route.getLastPing() : "未知");
                } else {
                    logger.error("玩家 {} 通过路由连接到 {} 失败: {}", 
                            player.getUsername(), 
                            serverName,
                            result.getReasonComponent().orElse(net.kyori.adventure.text.Component.text("未知错误")));
                    
                    // 标记该路由不可用
                    routeManager.markRouteUnavailable(serverName, route.getAddress());
                }
                
                // 连接完成后清理临时服务器注册
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
            
            // 标记该路由不可用
            routeManager.markRouteUnavailable(serverName, route.getAddress());
        }
    }
    
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String newServerName = event.getServer().getServerInfo().getName();
        
        // 如果玩家已经有之前的路由映射，先清理旧的映射
        String oldRouteAddress = routeManager.getPlayerRouteMapping().get(player.getUniqueId());
        if (oldRouteAddress != null) {
            // 从旧路由中移除玩家
            routeManager.removePlayerFromAllRoutes(player.getUniqueId());
            logger.debug("玩家 {} 切换服务器，已从旧路由 {} 移除", player.getUsername(), oldRouteAddress);
        }
        
        // 启动定期带宽更新任务
        proxyServer.getScheduler()
                .buildTask(plugin, () -> {
                    if (player.isActive()) {
                        routeManager.updatePlayerBandwidthUsage(player);
                    }
                })
                .repeat(5, TimeUnit.SECONDS) // 每5秒更新一次
                .schedule();
        
        logger.debug("玩家 {} 连接到服务器 {}，已启动带宽监控", 
                player.getUsername(), newServerName);
    }
    
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        
        // 玩家断开连接时清理路由映射
        routeManager.onPlayerDisconnect(player.getUniqueId());
        
        logger.debug("玩家 {} 断开连接，已清理路由映射", player.getUsername());
    }
}