package net.rms.xrain.velocityass.config;

import java.util.List;
import java.util.ArrayList;

public class ServerConfig {
    private String serverName;
    private List<RouteInfo> routes;
    private boolean autoSort;
    private int pingInterval;
    private int pingTimeout;
    
    public ServerConfig(String serverName) {
        this.serverName = serverName;
        this.routes = new ArrayList<>();
        this.autoSort = true;
        this.pingInterval = 30;
        this.pingTimeout = 5000;
    }
    
    public String getServerName() {
        return serverName;
    }
    
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
    
    public List<RouteInfo> getRoutes() {
        return routes;
    }
    
    public void setRoutes(List<RouteInfo> routes) {
        this.routes = routes;
    }
    
    public void addRoute(RouteInfo route) {
        this.routes.add(route);
    }
    
    public boolean isAutoSort() {
        return autoSort;
    }
    
    public void setAutoSort(boolean autoSort) {
        this.autoSort = autoSort;
    }
    
    public int getPingInterval() {
        return pingInterval;
    }
    
    public void setPingInterval(int pingInterval) {
        this.pingInterval = pingInterval;
    }
    
    public int getPingTimeout() {
        return pingTimeout;
    }
    
    public void setPingTimeout(int pingTimeout) {
        this.pingTimeout = pingTimeout;
    }
    
    public RouteInfo getBestRoute() {
        return routes.stream()
                .filter(route -> route.isEnabled() && route.isAvailable())
                .min((r1, r2) -> {
                    if (autoSort && r1.getLastPing() > 0 && r2.getLastPing() > 0) {
                        return Long.compare(r1.getLastPing(), r2.getLastPing());
                    }
                    return Integer.compare(r1.getPriority(), r2.getPriority());
                })
                .orElse(null);
    }
    
    @Override
    public String toString() {
        return String.format("ServerConfig{serverName='%s', routes=%d, autoSort=%s, pingInterval=%ds, pingTimeout=%dms}",
                serverName, routes.size(), autoSort, pingInterval, pingTimeout);
    }
}