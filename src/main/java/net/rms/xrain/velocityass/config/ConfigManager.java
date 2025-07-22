package net.rms.xrain.velocityass.config;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {
    private final Path dataDirectory;
    private final Path configFile;
    private final Logger logger;
    private final Map<String, ServerConfig> serverConfigs;
    private final ProxyServer proxyServer;
    
    public ConfigManager(Path dataDirectory, Logger logger, ProxyServer proxyServer) {
        this.dataDirectory = dataDirectory;
        this.configFile = dataDirectory.resolve("config.yml");
        this.logger = logger;
        this.serverConfigs = new ConcurrentHashMap<>();
        this.proxyServer = proxyServer;
    }
    
    public void loadConfig() throws IOException {
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }
        
        if (!Files.exists(configFile)) {
            createDefaultConfig();
        }
        
        try (InputStream inputStream = Files.newInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(inputStream);
            parseConfig(config);
            logger.info("配置文件加载成功，共加载 {} 个服务器配置", serverConfigs.size());
        } catch (Exception e) {
            logger.error("加载配置文件失败: ", e);
            throw new IOException("Failed to load config", e);
        }
    }
    
    private void createDefaultConfig() throws IOException {
        StringBuilder configBuilder = new StringBuilder();
        configBuilder.append("# VelocityASS 多线路服务器配置\n");
        configBuilder.append("# 此配置基于Velocity现有服务器自动生成\n");
        configBuilder.append("# 配置格式说明：\n");
        configBuilder.append("# servers:\n");
        configBuilder.append("#   服务器名称:\n");
        configBuilder.append("#     routes:\n");
        configBuilder.append("#       - address: \"服务器地址:端口\"\n");
        configBuilder.append("#         priority: 优先级数字(越小越优先)\n");
        configBuilder.append("#         enabled: true/false\n");
        configBuilder.append("#         max-bandwidth: 1048576 (固定带宽限制，字节/秒，-1表示无限制)\n");
        configBuilder.append("#         use-scheduled-bandwidth: true/false (是否使用分时段带宽，默认false)\n");
        configBuilder.append("#         bandwidth-schedule: (可选，分时段带宽限制配置)\n");
        configBuilder.append("#           default: 1048576  # 默认带宽限制\n");
        configBuilder.append("#           time-slots:\n");
        configBuilder.append("#             - start: \"08:00\"    # 开始时间\n");
        configBuilder.append("#               end: \"18:00\"      # 结束时间\n");
        configBuilder.append("#               bandwidth: 2097152  # 该时间段带宽\n");
        configBuilder.append("#               priority: 1         # 优先级(可选)\n");
        configBuilder.append("#             - start: \"22:00\"    # 跨天示例\n");
        configBuilder.append("#               end: \"ND06:00\"    # ND表示次日\n");
        configBuilder.append("#               bandwidth: 512000   # 夜间低带宽\n");
        configBuilder.append("#     auto-sort: true/false (是否根据延迟自动排序)\n");
        configBuilder.append("#     ping-interval: 30 (ping检测间隔，秒)\n");
        configBuilder.append("#     ping-timeout: 5000 (ping超时时间，毫秒)\n\n");
        configBuilder.append("servers:\n");
        
        boolean hasServers = false;
        for (RegisteredServer server : proxyServer.getAllServers()) {
            String serverName = server.getServerInfo().getName();
            String address = server.getServerInfo().getAddress().getHostString() + ":" + 
                           server.getServerInfo().getAddress().getPort();
            
            configBuilder.append("  ").append(serverName).append(":\n");
            configBuilder.append("    routes:\n");
            configBuilder.append("      - address: \"").append(address).append("\"\n");
            configBuilder.append("        priority: 1\n");
            configBuilder.append("        enabled: true\n");
            configBuilder.append("        max-bandwidth: -1  # 无带宽限制\n");
            configBuilder.append("        # 分时段带宽调度示例（需要同时设置 use-scheduled-bandwidth: true）\n");
            configBuilder.append("        # use-scheduled-bandwidth: false  # 使用固定带宽\n");
            configBuilder.append("        # bandwidth-schedule:\n");
            configBuilder.append("        #   default: 1048576  # 默认1MB/s\n");
            configBuilder.append("        #   time-slots:\n");
            configBuilder.append("        #     - start: \"08:00\"\n");
            configBuilder.append("        #       end: \"18:00\"\n");
            configBuilder.append("        #       bandwidth: 2097152  # 白天2MB/s\n");
            configBuilder.append("        #     - start: \"22:00\"\n");
            configBuilder.append("        #       end: \"ND06:00\"  # 夜间到次日\n");
            configBuilder.append("        #       bandwidth: 512000   # 夜间512KB/s\n");
            configBuilder.append("      # 在这里添加更多路由，例如：\n");
            configBuilder.append("      # - address: \"").append(address.replace(":25565", "2:25565")).append("\"\n");
            configBuilder.append("      #   priority: 2\n");
            configBuilder.append("      #   enabled: true\n");
            configBuilder.append("      #   max-bandwidth: 1048576  # 固定1MB/s带宽限制\n");
            configBuilder.append("      #   use-scheduled-bandwidth: false  # 使用固定带宽\n");
            configBuilder.append("    auto-sort: true\n");
            configBuilder.append("    ping-interval: 30\n");
            configBuilder.append("    ping-timeout: 5000\n\n");
            
            hasServers = true;
        }
        
        if (!hasServers) {
            configBuilder.append("  # 没有检测到Velocity服务器，以下是示例配置\n");
            configBuilder.append("  survival:\n");
            configBuilder.append("    routes:\n");
            configBuilder.append("      - address: \"survival1.example.com:25565\"\n");
            configBuilder.append("        priority: 1\n");
            configBuilder.append("        enabled: true\n");
            configBuilder.append("        max-bandwidth: 1048576  # 固定1MB/s 带宽限制\n");
            configBuilder.append("        # 启用分时段带宽调度示例\n");
            configBuilder.append("        use-scheduled-bandwidth: true  # 启用分时段带宽\n");
            configBuilder.append("        bandwidth-schedule:\n");
            configBuilder.append("          default: 1048576  # 默认1MB/s\n");
            configBuilder.append("          time-slots:\n");
            configBuilder.append("            - start: \"08:00\"\n");
            configBuilder.append("              end: \"18:00\"\n");
            configBuilder.append("              bandwidth: 2097152  # 白天高带宽2MB/s\n");
            configBuilder.append("            - start: \"18:00\"\n");
            configBuilder.append("              end: \"ND01:00\"  # 晚18点到次日1点\n");
            configBuilder.append("              bandwidth: 512000   # 夜间低带宽512KB/s\n");
            configBuilder.append("      - address: \"survival2.example.com:25565\"\n");
            configBuilder.append("        priority: 2\n");
            configBuilder.append("        enabled: true\n");
            configBuilder.append("        max-bandwidth: 2097152  # 固定2MB/s 带宽限制\n");
            configBuilder.append("        use-scheduled-bandwidth: false  # 使用固定带宽\n");
            configBuilder.append("    auto-sort: true\n");
            configBuilder.append("    ping-interval: 30\n");
            configBuilder.append("    ping-timeout: 5000\n");
        }
        
        Files.write(configFile, configBuilder.toString().getBytes());
        logger.info("已基于Velocity现有服务器创建配置文件: {} (包含 {} 个服务器)", 
                configFile, proxyServer.getAllServers().size());
    }
    
    @SuppressWarnings("unchecked")
    private void parseConfig(Map<String, Object> config) {
        serverConfigs.clear();
        
        Map<String, Object> servers = (Map<String, Object>) config.get("servers");
        if (servers == null) {
            logger.warn("配置文件中未找到 servers 节点");
            return;
        }
        
        for (Map.Entry<String, Object> entry : servers.entrySet()) {
            String serverName = entry.getKey();
            Map<String, Object> serverData = (Map<String, Object>) entry.getValue();
            
            ServerConfig serverConfig = new ServerConfig(serverName);
            
            List<Map<String, Object>> routes = (List<Map<String, Object>>) serverData.get("routes");
            if (routes != null) {
                for (Map<String, Object> routeData : routes) {
                    String address = (String) routeData.get("address");
                    Integer priority = (Integer) routeData.get("priority");
                    Boolean enabled = (Boolean) routeData.get("enabled");
                    Object maxBandwidthObj = routeData.get("max-bandwidth");
                    
                    if (address != null && priority != null && enabled != null) {
                        RouteInfo route = new RouteInfo(address, priority, enabled);
                        
                        if (maxBandwidthObj != null) {
                            try {
                                long maxBandwidth = maxBandwidthObj instanceof Number ? 
                                    ((Number) maxBandwidthObj).longValue() : 
                                    Long.parseLong(maxBandwidthObj.toString());
                                route.setMaxBandwidth(maxBandwidth);
                            } catch (NumberFormatException e) {
                                logger.warn("路由 {} 的带宽限制配置无效: {}, 使用默认值 -1", 
                                        address, maxBandwidthObj);
                                route.setMaxBandwidth(-1);
                            }
                        }
                        
                        // 解析带宽模式开关
                        Boolean useScheduledBandwidth = (Boolean) routeData.get("use-scheduled-bandwidth");
                        if (useScheduledBandwidth != null) {
                            route.setUseScheduledBandwidth(useScheduledBandwidth);
                        }
                        
                        // 解析分时段带宽调度
                        Object bandwidthScheduleObj = routeData.get("bandwidth-schedule");
                        if (bandwidthScheduleObj instanceof Map) {
                            TimeBasedBandwidthSchedule schedule = parseBandwidthSchedule((Map<String, Object>) bandwidthScheduleObj, address);
                            if (schedule != null) {
                                route.setBandwidthSchedule(schedule);
                                if (route.isUseScheduledBandwidth()) {
                                    logger.info("路由 {} 启用分时段带宽调度: {}", address, schedule.getScheduleInfo());
                                } else {
                                    logger.info("路由 {} 配置了分时段带宽调度但未启用: {}", address, schedule.getScheduleInfo());
                                }
                            }
                        }
                        
                        serverConfig.addRoute(route);
                    }
                }
            }
            
            Boolean autoSort = (Boolean) serverData.get("auto-sort");
            if (autoSort != null) {
                serverConfig.setAutoSort(autoSort);
            }
            
            Integer pingInterval = (Integer) serverData.get("ping-interval");
            if (pingInterval != null) {
                serverConfig.setPingInterval(pingInterval);
            }
            
            Integer pingTimeout = (Integer) serverData.get("ping-timeout");
            if (pingTimeout != null) {
                serverConfig.setPingTimeout(pingTimeout);
            }
            
            serverConfigs.put(serverName, serverConfig);
            logger.info("加载服务器配置: {}", serverConfig);
        }
    }
    
    public ServerConfig getServerConfig(String serverName) {
        return serverConfigs.get(serverName);
    }
    
    public Map<String, ServerConfig> getAllServerConfigs() {
        return new HashMap<>(serverConfigs);
    }
    
    public void reloadConfig() throws IOException {
        loadConfig();
        logger.info("配置文件已重新加载");
    }
    
    @SuppressWarnings("unchecked")
    private TimeBasedBandwidthSchedule parseBandwidthSchedule(Map<String, Object> scheduleData, String routeAddress) {
        try {
            TimeBasedBandwidthSchedule schedule = new TimeBasedBandwidthSchedule();
            
            // 解析默认带宽
            Object defaultBandwidthObj = scheduleData.get("default");
            if (defaultBandwidthObj != null) {
                try {
                    long defaultBandwidth = defaultBandwidthObj instanceof Number ? 
                        ((Number) defaultBandwidthObj).longValue() : 
                        Long.parseLong(defaultBandwidthObj.toString());
                    schedule.setDefaultBandwidth(defaultBandwidth);
                } catch (NumberFormatException e) {
                    logger.warn("默认带宽配置无效: {}, 使用 -1", defaultBandwidthObj);
                    schedule.setDefaultBandwidth(-1);
                }
            }
            
            // 解析时间段
            List<Map<String, Object>> timeSlots = (List<Map<String, Object>>) scheduleData.get("time-slots");
            if (timeSlots != null) {
                for (Map<String, Object> slotData : timeSlots) {
                    BandwidthTimeSlot timeSlot = parseTimeSlot(slotData);
                    if (timeSlot != null) {
                        schedule.addTimeSlot(timeSlot);
                    }
                }
            }
            
            return schedule.isEmpty() ? null : schedule;
            
        } catch (Exception e) {
            logger.error("解析带宽调度配置时发生错误", e);
            return null;
        }
    }
    
    private BandwidthTimeSlot parseTimeSlot(Map<String, Object> slotData) {
        try {
            String startTime = (String) slotData.get("start");
            String endTime = (String) slotData.get("end");
            Object bandwidthObj = slotData.get("bandwidth");
            
            if (startTime == null || endTime == null || bandwidthObj == null) {
                logger.warn("时间段配置不完整，跳过: {}", slotData);
                return null;
            }
            
            long bandwidth;
            try {
                bandwidth = bandwidthObj instanceof Number ? 
                    ((Number) bandwidthObj).longValue() : 
                    Long.parseLong(bandwidthObj.toString());
            } catch (NumberFormatException e) {
                logger.warn("时间段带宽配置无效: {}, 跳过该时间段", bandwidthObj);
                return null;
            }
            
            int priority = 0;
            Object priorityObj = slotData.get("priority");
            if (priorityObj != null) {
                try {
                    priority = priorityObj instanceof Number ? 
                        ((Number) priorityObj).intValue() : 
                        Integer.parseInt(priorityObj.toString());
                } catch (NumberFormatException e) {
                    logger.warn("时间段优先级配置无效: {}, 使用默认值 0", priorityObj);
                }
            }
            
            BandwidthTimeSlot timeSlot = new BandwidthTimeSlot(startTime, endTime, bandwidth, priority);
            
            if (!timeSlot.isValid()) {
                logger.warn("时间段配置无效: {} - {}, 跳过", startTime, endTime);
                return null;
            }
            
            return timeSlot;
            
        } catch (Exception e) {
            logger.error("解析时间段配置时发生错误: {}", slotData, e);
            return null;
        }
    }
}