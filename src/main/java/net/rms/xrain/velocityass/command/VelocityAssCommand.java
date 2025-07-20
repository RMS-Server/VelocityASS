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
            return CompletableFuture.completedFuture(List.of("status", "routes", "reload"));
        }
        
        if (args[0].equalsIgnoreCase("routes") && args.length == 2) {
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