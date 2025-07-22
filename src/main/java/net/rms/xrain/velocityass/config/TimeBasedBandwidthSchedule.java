package net.rms.xrain.velocityass.config;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TimeBasedBandwidthSchedule {
    private long defaultBandwidth;
    private List<BandwidthTimeSlot> timeSlots;
    
    public TimeBasedBandwidthSchedule() {
        this.defaultBandwidth = -1;
        this.timeSlots = new ArrayList<>();
    }
    
    public TimeBasedBandwidthSchedule(long defaultBandwidth) {
        this.defaultBandwidth = defaultBandwidth;
        this.timeSlots = new ArrayList<>();
    }
    
    public long getDefaultBandwidth() {
        return defaultBandwidth;
    }
    
    public void setDefaultBandwidth(long defaultBandwidth) {
        this.defaultBandwidth = defaultBandwidth;
    }
    
    public List<BandwidthTimeSlot> getTimeSlots() {
        return timeSlots;
    }
    
    public void setTimeSlots(List<BandwidthTimeSlot> timeSlots) {
        this.timeSlots = timeSlots != null ? timeSlots : new ArrayList<>();
    }
    
    public void addTimeSlot(BandwidthTimeSlot timeSlot) {
        if (timeSlot != null && timeSlot.isValid()) {
            this.timeSlots.add(timeSlot);
            sortTimeSlotsByPriority();
        }
    }
    
    public void removeTimeSlot(BandwidthTimeSlot timeSlot) {
        this.timeSlots.remove(timeSlot);
    }
    
    public void clearTimeSlots() {
        this.timeSlots.clear();
    }
    
    public long getCurrentBandwidthLimit() {
        return getCurrentBandwidthLimit(LocalTime.now());
    }
    
    public long getCurrentBandwidthLimit(LocalTime currentTime) {
        for (BandwidthTimeSlot slot : timeSlots) {
            if (slot.isTimeInSlot(currentTime)) {
                return slot.getBandwidth();
            }
        }
        return defaultBandwidth;
    }
    
    public BandwidthTimeSlot getCurrentTimeSlot() {
        return getCurrentTimeSlot(LocalTime.now());
    }
    
    public BandwidthTimeSlot getCurrentTimeSlot(LocalTime currentTime) {
        for (BandwidthTimeSlot slot : timeSlots) {
            if (slot.isTimeInSlot(currentTime)) {
                return slot;
            }
        }
        return null;
    }
    
    public boolean hasTimeSlots() {
        return !timeSlots.isEmpty();
    }
    
    public boolean isScheduleEnabled() {
        return hasTimeSlots() || defaultBandwidth > 0;
    }
    
    private void sortTimeSlotsByPriority() {
        timeSlots.sort(Comparator.comparingInt(BandwidthTimeSlot::getPriority));
    }
    
    public List<BandwidthTimeSlot> getValidTimeSlots() {
        return timeSlots.stream()
                .filter(BandwidthTimeSlot::isValid)
                .toList();
    }
    
    public boolean validateTimeSlots() {
        for (BandwidthTimeSlot slot : timeSlots) {
            if (!slot.isValid()) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TimeBasedBandwidthSchedule{");
        sb.append("defaultBandwidth=").append(defaultBandwidth);
        sb.append(", timeSlots=[");
        
        for (int i = 0; i < timeSlots.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(timeSlots.get(i));
        }
        
        sb.append("]}");
        return sb.toString();
    }
    
    public String getScheduleInfo() {
        if (!hasTimeSlots()) {
            return String.format("固定带宽: %s", 
                    defaultBandwidth > 0 ? (defaultBandwidth / 1024) + " KB/s" : "无限制");
        }
        
        StringBuilder info = new StringBuilder();
        info.append(String.format("默认带宽: %s, ", 
                defaultBandwidth > 0 ? (defaultBandwidth / 1024) + " KB/s" : "无限制"));
        info.append(timeSlots.size()).append("个时间段规则");
        
        return info.toString();
    }
    
    public boolean isEmpty() {
        return !hasTimeSlots() && defaultBandwidth <= 0;
    }
}