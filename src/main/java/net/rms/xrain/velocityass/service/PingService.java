package net.rms.xrain.velocityass.service;

import net.rms.xrain.velocityass.config.RouteInfo;
import net.rms.xrain.velocityass.config.ServerConfig;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PingService {
    private final RouteManager routeManager;
    private final Logger logger;
    private final ScheduledExecutorService executorService;
    private boolean running;
    
    public PingService(RouteManager routeManager, Logger logger) {
        this.routeManager = routeManager;
        this.logger = logger;
        this.executorService = Executors.newScheduledThreadPool(4);
        this.running = false;
    }
    
    public void startPingTask() {
        if (running) {
            return;
        }
        
        running = true;
        logger.info("启动延迟检测服务");
        
        schedulePingTasks();
        
        executorService.scheduleWithFixedDelay(this::schedulePingTasks, 30, 30, TimeUnit.SECONDS);
    }
    
    public void stopPingTask() {
        if (!running) {
            return;
        }
        
        running = false;
        logger.info("停止延迟检测服务");
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private void schedulePingTasks() {
        for (ServerConfig serverConfig : routeManager.getAllServerConfigs().values()) {
            for (RouteInfo route : serverConfig.getRoutes()) {
                if (route.isEnabled()) {
                    CompletableFuture.supplyAsync(() -> pingRoute(route, serverConfig.getPingTimeout()), executorService)
                            .thenAccept(result -> {
                                routeManager.updateRouteStatus(
                                        serverConfig.getServerName(),
                                        route.getAddress(),
                                        result.available,
                                        result.ping
                                );
                                
                                if (result.available) {
                                    logger.debug("Ping {} -> {}ms", route.getAddress(), result.ping);
                                } else {
                                    logger.debug("Ping {} -> 失败", route.getAddress());
                                }
                            })
                            .exceptionally(throwable -> {
                                logger.debug("Ping {} 异常: {}", route.getAddress(), throwable.getMessage());
                                routeManager.updateRouteStatus(
                                        serverConfig.getServerName(),
                                        route.getAddress(),
                                        false,
                                        -1
                                );
                                return null;
                            });
                }
            }
        }
    }
    
    private PingResult pingRoute(RouteInfo route, int timeoutMs) {
        String host = route.getHost();
        int port = route.getPort();
        
        long startTime = System.currentTimeMillis();
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            long ping = System.currentTimeMillis() - startTime;
            return new PingResult(true, ping);
        } catch (IOException e) {
            return new PingResult(false, -1);
        }
    }
    
    public CompletableFuture<PingResult> pingRouteAsync(RouteInfo route, int timeoutMs) {
        return CompletableFuture.supplyAsync(() -> pingRoute(route, timeoutMs), executorService);
    }
    
    public static class PingResult {
        public final boolean available;
        public final long ping;
        
        public PingResult(boolean available, long ping) {
            this.available = available;
            this.ping = ping;
        }
        
        @Override
        public String toString() {
            return available ? ping + "ms" : "不可用";
        }
    }
}