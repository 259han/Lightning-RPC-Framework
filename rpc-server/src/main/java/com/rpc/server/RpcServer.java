package com.rpc.server;

import com.rpc.common.registry.ServiceRegistry;
import com.rpc.protocol.codec.RpcMessageDecoder;
import com.rpc.protocol.codec.RpcMessageEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC服务器
 */
@Slf4j
public class RpcServer {
    private final String host;
    private final int port;
    private final ServiceRegistry serviceRegistry;
    private final Map<String, Object> serviceMap = new ConcurrentHashMap<>();
    
    public RpcServer(String host, int port, ServiceRegistry serviceRegistry) {
        this.host = host;
        this.port = port;
        this.serviceRegistry = serviceRegistry;
    }
    
    /**
     * 注册服务
     *
     * @param service      服务实现
     * @param serviceName  服务名称
     */
    public void registerService(Object service, String serviceName) {
        if (serviceMap.containsKey(serviceName)) {
            return;
        }
        serviceMap.put(serviceName, service);
        log.info("服务注册成功: {}", serviceName);
    }
    
    /**
     * 启动服务器
     */
    public void start() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new RpcMessageDecoder());
                            ch.pipeline().addLast(new RpcMessageEncoder());
                            ch.pipeline().addLast(new RpcServerHandler(serviceMap));
                        }
                    });
            
            // 绑定端口
            ChannelFuture future = bootstrap.bind(host, port).sync();
            log.info("RPC服务器启动成功，监听端口: {}", port);
            
            // 注册所有服务
            InetSocketAddress address = new InetSocketAddress(host, port);
            for (String serviceName : serviceMap.keySet()) {
                serviceRegistry.registerService(serviceName, address);
                log.info("服务 [{}] 注册到注册中心", serviceName);
            }
            
            // 等待服务器关闭
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("RPC服务器启动异常", e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            
            // 注销所有服务
            InetSocketAddress address = new InetSocketAddress(host, port);
            for (String serviceName : serviceMap.keySet()) {
                serviceRegistry.unregisterService(serviceName, address);
            }
        }
    }
}
