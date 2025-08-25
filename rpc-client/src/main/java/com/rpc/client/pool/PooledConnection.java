package com.rpc.client.pool;

import io.netty.channel.Channel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 池化连接封装
 * 
 * 封装了Netty Channel，添加了连接池管理所需的元数据和状态管理
 */
@Slf4j
@Getter
public class PooledConnection {
    
    /**
     * 连接状态枚举
     */
    public enum State {
        AVAILABLE,  // 可用状态
        IN_USE,     // 使用中
        CLOSED      // 已关闭
    }
    
    private final String id;
    private final Channel channel;
    private final ConnectionPool pool;
    private final long createTime;
    
    private final AtomicReference<State> state = new AtomicReference<>(State.AVAILABLE);
    private final AtomicLong lastUsedTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong usageCount = new AtomicLong(0);
    
    public PooledConnection(String id, Channel channel, ConnectionPool pool, long createTime) {
        this.id = id;
        this.channel = channel;
        this.pool = pool;
        this.createTime = createTime;
    }
    
    /**
     * 标记连接为使用中
     */
    public boolean markInUse() {
        if (state.compareAndSet(State.AVAILABLE, State.IN_USE)) {
            lastUsedTime.set(System.currentTimeMillis());
            usageCount.incrementAndGet();
            log.debug("连接标记为使用中: {}", id);
            return true;
        }
        return false;
    }
    
    /**
     * 标记连接为可用
     */
    public boolean markAvailable() {
        if (state.compareAndSet(State.IN_USE, State.AVAILABLE)) {
            lastUsedTime.set(System.currentTimeMillis());
            log.debug("连接标记为可用: {}", id);
            return true;
        }
        return false;
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        if (state.getAndSet(State.CLOSED) != State.CLOSED) {
            if (channel.isOpen()) {
                channel.close();
            }
            log.debug("连接已关闭: {}", id);
        }
    }
    
    /**
     * 归还连接到池中
     */
    public void returnToPool() {
        if (isHealthy()) {
            pool.returnConnection(this);
        } else {
            log.warn("连接不健康，无法归还到池中: {}", id);
            close();
        }
    }
    
    /**
     * 检查连接是否健康
     */
    public boolean isHealthy() {
        return state.get() != State.CLOSED && 
               channel.isActive() && 
               channel.isOpen();
    }
    
    /**
     * 检查连接是否可用
     */
    public boolean isAvailable() {
        return state.get() == State.AVAILABLE && isHealthy();
    }
    
    /**
     * 检查连接是否在使用中
     */
    public boolean isInUse() {
        return state.get() == State.IN_USE;
    }
    
    /**
     * 获取连接状态
     */
    public State getState() {
        return state.get();
    }
    
    /**
     * 获取最后使用时间
     */
    public long getLastUsedTime() {
        return lastUsedTime.get();
    }
    
    /**
     * 获取使用次数
     */
    public long getUsageCount() {
        return usageCount.get();
    }
    
    /**
     * 获取连接存活时间
     */
    public long getAliveTime() {
        return System.currentTimeMillis() - createTime;
    }
    
    /**
     * 获取连接空闲时间
     */
    public long getIdleTime() {
        return isAvailable() ? System.currentTimeMillis() - lastUsedTime.get() : 0;
    }
    
    @Override
    public String toString() {
        return String.format("PooledConnection{id='%s', state=%s, channel=%s, usageCount=%d, aliveTime=%dms}", 
                           id, state.get(), channel, usageCount.get(), getAliveTime());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PooledConnection that = (PooledConnection) obj;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
