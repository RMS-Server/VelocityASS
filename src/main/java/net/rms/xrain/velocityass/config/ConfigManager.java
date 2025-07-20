package net.rms.xrain.velocityass.config;

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
    
    public ConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.configFile = dataDirectory.resolve("config.yml");
        this.logger = logger;
        this.serverConfigs = new ConcurrentHashMap<>();
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
        String defaultConfig = """
                # VelocityASS 多线路服务器配置
                # 配置格式说明：
                # servers:
                #   服务器名称:
                #     routes:
                #       - address: "服务器地址:端口"
                #         priority: 优先级数字(越小越优先)
                #         enabled: true/false
                #     auto-sort: true/false (是否根据延迟自动排序)
                #     ping-interval: 30 (ping检测间隔，秒)
                #     ping-timeout: 5000 (ping超时时间，毫秒)
                
                servers:
                  survival:
                    routes:
                      - address: "survival1.example.com:25565"
                        priority: 1
                        enabled: true
                      - address: "survival2.example.com:25565"
                        priority: 2
                        enabled: true
                      - address: "survival3.example.com:25565"
                        priority: 3
                        enabled: true
                    auto-sort: true
                    ping-interval: 30
                    ping-timeout: 5000
                  
                  creative:
                    routes:
                      - address: "creative1.example.com:25565"
                        priority: 1
                        enabled: true
                      - address: "creative2.example.com:25565"
                        priority: 2
                        enabled: true
                    auto-sort: true
                    ping-interval: 30
                    ping-timeout: 5000
                """;
        
        Files.write(configFile, defaultConfig.getBytes());
        logger.info("已创建默认配置文件: {}", configFile);
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
            
            // 解析路由配置
            List<Map<String, Object>> routes = (List<Map<String, Object>>) serverData.get("routes");
            if (routes != null) {
                for (Map<String, Object> routeData : routes) {
                    String address = (String) routeData.get("address");
                    Integer priority = (Integer) routeData.get("priority");
                    Boolean enabled = (Boolean) routeData.get("enabled");
                    
                    if (address != null && priority != null && enabled != null) {
                        RouteInfo route = new RouteInfo(address, priority, enabled);
                        serverConfig.addRoute(route);
                    }
                }
            }
            
            // 解析其他配置
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
}