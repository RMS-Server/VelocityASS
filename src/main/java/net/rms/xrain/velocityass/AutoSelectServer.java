package net.rms.xrain.velocityass;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.rms.xrain.velocityass.command.VelocityAssCommand;
import net.rms.xrain.velocityass.config.ConfigManager;
import net.rms.xrain.velocityass.listener.ServerConnectionListener;
import net.rms.xrain.velocityass.service.PingService;
import net.rms.xrain.velocityass.service.RouteManager;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
    id = "velocityass",
    name = "VelocityASS",
    version = "1.0.0",
    description = "Advanced Server Selection plugin for Velocity with multi-route support",
    authors = {"XRain"}
)
public class AutoSelectServer {
    
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    
    private ConfigManager configManager;
    private PingService pingService;
    private RouteManager routeManager;
    
    @Inject
    public AutoSelectServer(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }
    
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("VelocityASS 插件正在初始化...");
        
        try {
            configManager = new ConfigManager(dataDirectory, logger, server);
            configManager.loadConfig();
            
            routeManager = new RouteManager(configManager, server, logger);
            pingService = new PingService(routeManager, logger);
            
            // 注册事件监听器
            server.getEventManager().register(this, new ServerConnectionListener(routeManager, logger, server, this));
            
            // 注册管理命令
            server.getCommandManager().register("vass", new VelocityAssCommand(routeManager, logger));
            server.getCommandManager().register("velocityass", new VelocityAssCommand(routeManager, logger));
            
            pingService.startPingTask();
            
            logger.info("VelocityASS 插件初始化完成！");
        } catch (Exception e) {
            logger.error("VelocityASS 插件初始化失败: ", e);
        }
    }
    
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("VelocityASS 插件正在关闭...");
        
        if (pingService != null) {
            pingService.stopPingTask();
        }
        
        if (routeManager != null) {
            routeManager.shutdown();
        }
        
        logger.info("VelocityASS 插件已关闭");
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public RouteManager getRouteManager() {
        return routeManager;
    }
    
    public PingService getPingService() {
        return pingService;
    }
}