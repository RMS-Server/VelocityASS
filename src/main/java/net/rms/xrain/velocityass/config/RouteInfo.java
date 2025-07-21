package net.rms.xrain.velocityass.config;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RouteInfo {
    private String address;
    private int priority;
    private boolean enabled;
    private long lastPing;
    private boolean available;
    
    // 带宽相关字段
    private long maxBandwidth; // 最大带宽限制 (字节/秒)
    private double currentBandwidthUsage; // 当前带宽使用 (字节/秒)
    private final Set<UUID> connectedPlayers; // 连接到此路由的玩家
    private long lastBandwidthUpdate; // 最后一次带宽更新时间
    
    public RouteInfo(String address, int priority, boolean enabled) {
        this.address = address;
        this.priority = priority;
        this.enabled = enabled;
        this.lastPing = -1;
        this.available = true;
        this.maxBandwidth = -1; // -1 表示无限制
        this.currentBandwidthUsage = 0.0;
        this.connectedPlayers = ConcurrentHashMap.newKeySet();
        this.lastBandwidthUpdate = System.currentTimeMillis();
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public long getLastPing() {
        return lastPing;
    }
    
    public void setLastPing(long lastPing) {
        this.lastPing = lastPing;
    }
    
    public boolean isAvailable() {
        return available;
    }
    
    public void setAvailable(boolean available) {
        this.available = available;
    }
    
    public String getHost() {
        return address.split(":")[0];
    }
    
    public int getPort() {
        String[] parts = address.split(":");
        return parts.length > 1 ? Integer.parseInt(parts[1]) : 25565;
    }
    
    // 带宽相关方法
    public long getMaxBandwidth() {
        return maxBandwidth;
    }
    
    public void setMaxBandwidth(long maxBandwidth) {
        this.maxBandwidth = maxBandwidth;
    }
    
    public double getCurrentBandwidthUsage() {
        return currentBandwidthUsage;
    }
    
    public void setCurrentBandwidthUsage(double currentBandwidthUsage) {
        this.currentBandwidthUsage = currentBandwidthUsage;
        this.lastBandwidthUpdate = System.currentTimeMillis();
    }
    
    public Set<UUID> getConnectedPlayers() {
        return connectedPlayers;
    }
    
    public void addConnectedPlayer(UUID playerId) {
        connectedPlayers.add(playerId);
    }
    
    public boolean removeConnectedPlayer(UUID playerId) {
        return connectedPlayers.remove(playerId);
    }
    
    public int getConnectedPlayerCount() {
        return connectedPlayers.size();
    }
    
    public long getLastBandwidthUpdate() {
        return lastBandwidthUpdate;
    }
    
    public void setLastBandwidthUpdate(long lastBandwidthUpdate) {
        this.lastBandwidthUpdate = lastBandwidthUpdate;
    }
    
    public boolean isBandwidthLimited() {
        return maxBandwidth > 0;
    }
    
    public boolean isBandwidthAvailable() {
        if (!isBandwidthLimited()) {
            return true; // 无限制
        }
        return currentBandwidthUsage < maxBandwidth;
    }
    
    public double getBandwidthUtilization() {
        if (!isBandwidthLimited()) {
            return 0.0; // 无限制时返回0%
        }
        return (currentBandwidthUsage / maxBandwidth) * 100.0;
    }
    
    public long getAvailableBandwidth() {
        if (!isBandwidthLimited()) {
            return Long.MAX_VALUE; // 无限制
        }
        return Math.max(0, maxBandwidth - (long)currentBandwidthUsage);
    }
    
    @Override
    public String toString() {
        return String.format("RouteInfo{address='%s', priority=%d, enabled=%s, lastPing=%dms, available=%s, " +
                "bandwidth=%.2f/%.2f KB/s (%.1f%%), players=%d}", 
                address, priority, enabled, lastPing, available,
                currentBandwidthUsage / 1024.0, 
                maxBandwidth > 0 ? maxBandwidth / 1024.0 : -1,
                getBandwidthUtilization(),
                connectedPlayers.size());
    }
}