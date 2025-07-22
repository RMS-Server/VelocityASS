package net.rms.xrain.velocityass.config;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RouteInfo {
    private String address;
    private int priority;
    private boolean enabled;
    private long lastPing;
    private boolean available;
    
    private long maxBandwidth;
    private double currentBandwidthUsage;
    private final Set<UUID> connectedPlayers; 
    private long lastBandwidthUpdate;
    
    private TimeBasedBandwidthSchedule bandwidthSchedule;
    
    // 带宽模式控制
    private boolean useScheduledBandwidth; 
    
    public RouteInfo(String address, int priority, boolean enabled) {
        this.address = address;
        this.priority = priority;
        this.enabled = enabled;
        this.lastPing = -1;
        this.available = true;
        this.maxBandwidth = -1; 
        this.currentBandwidthUsage = 0.0;
        this.connectedPlayers = ConcurrentHashMap.newKeySet();
        this.lastBandwidthUpdate = System.currentTimeMillis();
        this.bandwidthSchedule = null;
        this.useScheduledBandwidth = false;
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
    
    public TimeBasedBandwidthSchedule getBandwidthSchedule() {
        return bandwidthSchedule;
    }
    
    public void setBandwidthSchedule(TimeBasedBandwidthSchedule bandwidthSchedule) {
        this.bandwidthSchedule = bandwidthSchedule;
    }
    
    public boolean isUseScheduledBandwidth() {
        return useScheduledBandwidth;
    }
    
    public void setUseScheduledBandwidth(boolean useScheduledBandwidth) {
        this.useScheduledBandwidth = useScheduledBandwidth;
    }
    
    public boolean isBandwidthLimited() {
        if (useScheduledBandwidth && bandwidthSchedule != null && bandwidthSchedule.isScheduleEnabled()) {
            long currentLimit = bandwidthSchedule.getCurrentBandwidthLimit();
            return currentLimit > 0;
        }
        return maxBandwidth > 0;
    }
    
    public boolean isBandwidthAvailable() {
        if (!isBandwidthLimited()) {
            return true;
        }
        long currentLimit = getCurrentBandwidthLimit();
        return currentBandwidthUsage < currentLimit;
    }
    
    public long getCurrentBandwidthLimit() {
        if (useScheduledBandwidth && bandwidthSchedule != null && bandwidthSchedule.isScheduleEnabled()) {
            long scheduleLimit = bandwidthSchedule.getCurrentBandwidthLimit();
            if (scheduleLimit > 0) {
                return scheduleLimit;
            }
        }
        return maxBandwidth;
    }
    
    public double getBandwidthUtilization() {
        if (!isBandwidthLimited()) {
            return 0.0; 
        }
        long currentLimit = getCurrentBandwidthLimit();
        return (currentBandwidthUsage / currentLimit) * 100.0;
    }
    
    public long getAvailableBandwidth() {
        if (!isBandwidthLimited()) {
            return Long.MAX_VALUE; 
        }
        long currentLimit = getCurrentBandwidthLimit();
        return Math.max(0, currentLimit - (long)currentBandwidthUsage);
    }
    
    // 方法别名，用于与 BandwidthAwareRouteSelector 兼容
    public double getCurrentBandwidthUtilization() {
        return getBandwidthUtilization();
    }
    
    public boolean isCurrentlyBandwidthLimited() {
        return isBandwidthLimited();
    }
    
    public BandwidthTimeSlot getCurrentTimeSlot() {
        if (bandwidthSchedule != null && bandwidthSchedule.isScheduleEnabled()) {
            return bandwidthSchedule.getCurrentTimeSlot();
        }
        return null;
    }
    
    @Override
    public String toString() {
        long currentLimit = getCurrentBandwidthLimit();
        String scheduleInfo = "";
        if (useScheduledBandwidth && bandwidthSchedule != null && bandwidthSchedule.hasTimeSlots()) {
            scheduleInfo = " [分时段带宽]";
        } else if (!useScheduledBandwidth && maxBandwidth > 0) {
            scheduleInfo = " [固定带宽]";
        }
        
        return String.format("RouteInfo{address='%s', priority=%d, enabled=%s, lastPing=%dms, available=%s, " +
                "bandwidth=%.2f/%.2f KB/s (%.1f%%), players=%d%s}", 
                address, priority, enabled, lastPing, available,
                currentBandwidthUsage / 1024.0, 
                currentLimit > 0 ? currentLimit / 1024.0 : -1,
                getBandwidthUtilization(),
                connectedPlayers.size(),
                scheduleInfo);
    }
}