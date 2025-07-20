package net.rms.xrain.velocityass.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.rms.xrain.velocityass.config.RouteInfo;
import net.rms.xrain.velocityass.service.RouteManager;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Optional;

public class ServerConnectionListener {
    private final RouteManager routeManager;
    private final Logger logger;
    
    public ServerConnectionListener(RouteManager routeManager, Logger logger) {
        this.routeManager = routeManager;
        this.logger = logger;
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
        
        // 选择最佳路由
        RouteInfo bestRoute = routeManager.selectBestRoute(serverName);
        if (bestRoute == null) {
            logger.warn("服务器 {} 没有可用路由，玩家 {} 连接可能失败", 
                    serverName, event.getPlayer().getUsername());
            return;
        }
        
        // 检查是否需要替换目标服务器
        String originalAddress = originalServer.getServerInfo().getAddress().getHostString() + ":" + 
                                originalServer.getServerInfo().getAddress().getPort();
        
        if (!originalAddress.equals(bestRoute.getAddress())) {
            try {
                // 创建新的服务器信息
                InetSocketAddress newAddress = new InetSocketAddress(bestRoute.getHost(), bestRoute.getPort());
                ServerInfo newServerInfo = new ServerInfo(serverName + "_route", newAddress);
                
                // 尝试获取已注册的服务器，如果不存在则注册一个新的
                Optional<RegisteredServer> registeredServer = event.getPlayer().getCurrentServer()
                        .map(server -> server.getServer().getProxyServer())
                        .orElse(originalServer.getProxyServer())
                        .getServer(newServerInfo.getName());
                
                RegisteredServer targetServer;
                if (registeredServer.isPresent()) {
                    targetServer = registeredServer.get();
                } else {
                    // 注册新服务器
                    targetServer = originalServer.getProxyServer().registerServer(newServerInfo);
                }
                
                // 设置新的连接目标
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(targetServer));
                
                logger.info("为玩家 {} 重定向服务器连接: {} -> {} (延迟: {}ms)", 
                        event.getPlayer().getUsername(), 
                        originalAddress, 
                        bestRoute.getAddress(),
                        bestRoute.getLastPing() > 0 ? bestRoute.getLastPing() : "未知");
                        
            } catch (Exception e) {
                logger.error("重定向服务器连接失败，玩家: {}, 目标: {}, 错误: {}", 
                        event.getPlayer().getUsername(), bestRoute.getAddress(), e.getMessage());
                
                // 标记该路由不可用
                routeManager.markRouteUnavailable(serverName, bestRoute.getAddress());
            }
        } else {
            logger.debug("玩家 {} 连接服务器 {} 使用原始地址 {}", 
                    event.getPlayer().getUsername(), serverName, originalAddress);
        }
    }
}