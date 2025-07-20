package net.rms.xrain.velocityass.service;

import com.velocitypowered.api.proxy.ProxyServer;
import net.rms.xrain.velocityass.config.ConfigManager;
import net.rms.xrain.velocityass.config.RouteInfo;
import net.rms.xrain.velocityass.config.ServerConfig;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RouteManager {
    private final ConfigManager configManager;
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Map<String, Long> lastConnectionAttempt;
    
    public RouteManager(ConfigManager configManager, ProxyServer proxyServer, Logger logger) {
        this.configManager = configManager;
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.lastConnectionAttempt = new ConcurrentHashMap<>();
    }
    
    public RouteInfo selectBestRoute(String serverName) {
        ServerConfig serverConfig = configManager.getServerConfig(serverName);
        if (serverConfig == null) {
            logger.debug("未找到服务器 {} 的配置", serverName);
            return null;
        }
        
        RouteInfo bestRoute = serverConfig.getBestRoute();
        if (bestRoute == null) {
            logger.warn("服务器 {} 没有可用的路由", serverName);
            return null;
        }
        
        // 记录连接尝试时间
        String key = serverName + ":" + bestRoute.getAddress();
        lastConnectionAttempt.put(key, System.currentTimeMillis());
        
        logger.debug("为服务器 {} 选择路由: {} (延迟: {}ms)", 
                serverName, bestRoute.getAddress(), bestRoute.getLastPing());
        
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
}