package net.rms.xrain.velocityass.config;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class BandwidthTimeSlot {
    private String startTime;
    private String endTime;
    private long bandwidth;
    private int priority;
    
    public BandwidthTimeSlot(String startTime, String endTime, long bandwidth) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.bandwidth = bandwidth;
        this.priority = 0;
    }
    
    public BandwidthTimeSlot(String startTime, String endTime, long bandwidth, int priority) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.bandwidth = bandwidth;
        this.priority = priority;
    }
    
    public String getStartTime() {
        return startTime;
    }
    
    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }
    
    public String getEndTime() {
        return endTime;
    }
    
    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
    
    public long getBandwidth() {
        return bandwidth;
    }
    
    public void setBandwidth(long bandwidth) {
        this.bandwidth = bandwidth;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    public boolean isNextDay() {
        return endTime != null && endTime.startsWith("ND");
    }
    
    public String getActualEndTime() {
        if (isNextDay() && endTime.length() > 2) {
            return endTime.substring(2);
        }
        return endTime;
    }
    
    public boolean isTimeInSlot(LocalTime currentTime) {
        try {
            LocalTime start = LocalTime.parse(startTime);
            String actualEndTime = getActualEndTime();
            LocalTime end = LocalTime.parse(actualEndTime);
            
            if (isNextDay()) {
                return (currentTime.isAfter(start) || currentTime.equals(start)) ||
                       (currentTime.isBefore(end) || currentTime.equals(end));
            } else {
                return (currentTime.isAfter(start) || currentTime.equals(start)) &&
                       (currentTime.isBefore(end) || currentTime.equals(end));
            }
        } catch (DateTimeParseException e) {
            return false;
        }
    }
    
    public boolean isValid() {
        try {
            LocalTime.parse(startTime);
            String actualEndTime = getActualEndTime();
            if (actualEndTime != null && !actualEndTime.isEmpty()) {
                LocalTime.parse(actualEndTime);
            }
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
    
    @Override
    public String toString() {
        return String.format("BandwidthTimeSlot{start='%s', end='%s', bandwidth=%d bytes/s, priority=%d}", 
                startTime, endTime, bandwidth, priority);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        BandwidthTimeSlot that = (BandwidthTimeSlot) obj;
        return bandwidth == that.bandwidth &&
               priority == that.priority &&
               (startTime != null ? startTime.equals(that.startTime) : that.startTime == null) &&
               (endTime != null ? endTime.equals(that.endTime) : that.endTime == null);
    }
    
    @Override
    public int hashCode() {
        int result = startTime != null ? startTime.hashCode() : 0;
        result = 31 * result + (endTime != null ? endTime.hashCode() : 0);
        result = 31 * result + (int) (bandwidth ^ (bandwidth >>> 32));
        result = 31 * result + priority;
        return result;
    }
}