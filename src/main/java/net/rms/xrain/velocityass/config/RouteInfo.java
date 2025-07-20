package net.rms.xrain.velocityass.config;

public class RouteInfo {
    private String address;
    private int priority;
    private boolean enabled;
    private long lastPing;
    private boolean available;
    
    public RouteInfo(String address, int priority, boolean enabled) {
        this.address = address;
        this.priority = priority;
        this.enabled = enabled;
        this.lastPing = -1;
        this.available = true;
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
    
    @Override
    public String toString() {
        return String.format("RouteInfo{address='%s', priority=%d, enabled=%s, lastPing=%dms, available=%s}", 
                address, priority, enabled, lastPing, available);
    }
}