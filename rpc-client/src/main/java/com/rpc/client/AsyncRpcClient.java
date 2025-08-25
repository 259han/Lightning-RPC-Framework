package com.rpc.client;

import com.rpc.common.config.RpcConfig;
import com.rpc.common.exception.RpcException;
import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 异步RPC客户端实现
 * 
 * 支持批量调用、并行调用、背压控制等高级异步特性
 */
@Slf4j
public class AsyncRpcClient implements BatchRpcClient {
    
    private final RpcRequestTransport requestTransport;
    private final RpcConfig config;
    private final ExecutorService asyncExecutor;
    private final Semaphore backpressureSemaphore;
    private final AtomicInteger pendingCalls = new AtomicInteger(0);
    private final ConcurrentMap<Long, CompletableFuture<?>> pendingFutures = new ConcurrentHashMap<>();
    private volatile boolean closed = false;
    
    public AsyncRpcClient(RpcRequestTransport requestTransport, RpcConfig config) {
        this.requestTransport = requestTransport;
        this.config = config;
        
        // 创建异步执行器
        int asyncThreads = Math.max(4, Runtime.getRuntime().availableProcessors());
        this.asyncExecutor = Executors.newFixedThreadPool(asyncThreads, r -> {
            Thread t = new Thread(r, "AsyncRpcClient-Worker");
            t.setDaemon(true);
            return t;
        });
        
        // 背压控制：限制并发请求数
        int maxConcurrent = config.getConnectionPoolConfig().getMaxPendingRequests() * 2;
        this.backpressureSemaphore = new Semaphore(maxConcurrent);
        
        log.info("AsyncRpcClient已初始化，异步线程数: {}, 最大并发数: {}", asyncThreads, maxConcurrent);
    }
    
    @Override
    public <T> CompletableFuture<RpcResponse<T>> asyncCall(RpcRequest request) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("AsyncRpcClient已关闭"));
        }
        
        CompletableFuture<RpcResponse<T>> future = new CompletableFuture<>();
        long requestId = System.nanoTime();
        pendingFutures.put(requestId, future);
        
        // 异步执行，避免阻塞调用线程
        CompletableFuture.runAsync(() -> {
            try {
                // 背压控制
                if (!backpressureSemaphore.tryAcquire(config.getRequestTimeout(), TimeUnit.MILLISECONDS)) {
                    throw new RpcException("系统繁忙，请求被拒绝（背压保护）");
                }
                
                pendingCalls.incrementAndGet();
                
                // 执行实际的RPC调用
                CompletableFuture<RpcResponse<Object>> transportFuture = requestTransport.sendRequest(request);
                
                transportFuture.whenComplete((response, throwable) -> {
                    try {
                        pendingCalls.decrementAndGet();
                        backpressureSemaphore.release();
                        pendingFutures.remove(requestId);
                        
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                        } else {
                            @SuppressWarnings("unchecked")
                            RpcResponse<T> typedResponse = (RpcResponse<T>) response;
                            future.complete(typedResponse);
                        }
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
                
            } catch (Exception e) {
                pendingCalls.decrementAndGet();
                backpressureSemaphore.release();
                pendingFutures.remove(requestId);
                future.completeExceptionally(e);
            }
        }, asyncExecutor);
        
        return future;
    }
    
    @Override
    public <T> CompletableFuture<List<RpcResponse<T>>> batchCall(List<RpcRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("AsyncRpcClient已关闭"));
        }
        
        log.debug("开始批量调用，请求数: {}", requests.size());
        
        // 创建所有异步调用的Future
        List<CompletableFuture<RpcResponse<T>>> futures = requests.stream()
                .map(this::<T>asyncCall)
                .collect(Collectors.toList());
        
        // 等待所有调用完成
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<RpcResponse<T>> results = new ArrayList<>();
                    for (CompletableFuture<RpcResponse<T>> future : futures) {
                        try {
                            results.add(future.get());
                        } catch (Exception e) {
                            // 为失败的请求创建错误响应
                            RpcResponse<T> errorResponse = new RpcResponse<>();
                            errorResponse.setCode(500);
                            errorResponse.setMessage("批量调用中的请求失败: " + e.getMessage());
                            results.add(errorResponse);
                        }
                    }
                    log.debug("批量调用完成，成功: {}, 总计: {}", 
                             results.stream().mapToInt(r -> r.getCode() == 200 ? 1 : 0).sum(),
                             results.size());
                    return results;
                });
    }
    
    @Override
    public <T> CompletableFuture<List<RpcResponse<T>>> parallelCall(List<RpcRequest> requests, int maxConcurrency) {
        if (requests == null || requests.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("AsyncRpcClient已关闭"));
        }
        
        maxConcurrency = Math.max(1, Math.min(maxConcurrency, requests.size()));
        log.debug("开始并行调用，请求数: {}, 最大并发数: {}", requests.size(), maxConcurrency);
        
        CompletableFuture<List<RpcResponse<T>>> resultFuture = new CompletableFuture<>();
        List<RpcResponse<T>> results = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            results.add(null); // 预分配空间
        }
        
        Semaphore concurrencyControl = new Semaphore(maxConcurrency);
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // 提交所有任务
        for (int i = 0; i < requests.size(); i++) {
            final int index = i;
            final RpcRequest request = requests.get(i);
            
            CompletableFuture.runAsync(() -> {
                try {
                    concurrencyControl.acquire();
                    
                    asyncCall(request).whenComplete((response, throwable) -> {
                        try {
                            if (throwable != null) {
                                errorCount.incrementAndGet();
                                // 创建错误响应
                                RpcResponse<T> errorResponse = new RpcResponse<>();
                                errorResponse.setCode(500);
                                errorResponse.setMessage("并行调用失败: " + throwable.getMessage());
                                results.set(index, errorResponse);
                            } else {
                                @SuppressWarnings("unchecked")
                                RpcResponse<T> typedResponse = (RpcResponse<T>) response;
                                results.set(index, typedResponse);
                            }
                            
                            // 检查是否所有请求都完成了
                            if (completedCount.incrementAndGet() == requests.size()) {
                                log.debug("并行调用完成，成功: {}, 失败: {}, 总计: {}", 
                                         requests.size() - errorCount.get(), errorCount.get(), requests.size());
                                resultFuture.complete(results);
                            }
                        } finally {
                            concurrencyControl.release();
                        }
                    });
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errorCount.incrementAndGet();
                    if (completedCount.incrementAndGet() == requests.size()) {
                        resultFuture.completeExceptionally(e);
                    }
                }
            }, asyncExecutor);
        }
        
        return resultFuture;
    }
    
    @Override
    public int getPendingAsyncCalls() {
        return pendingCalls.get();
    }
    
    @Override
    public void cancelAllPendingCalls() {
        log.info("取消所有等待中的异步调用，数量: {}", pendingFutures.size());
        
        pendingFutures.values().forEach(future -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        
        pendingFutures.clear();
        pendingCalls.set(0);
    }
    
    /**
     * 获取异步调用统计信息
     */
    public AsyncCallStats getStats() {
        return AsyncCallStats.builder()
                .pendingCalls(pendingCalls.get())
                .totalFutures(pendingFutures.size())
                .availablePermits(backpressureSemaphore.availablePermits())
                .build();
    }
    
    /**
     * 关闭异步客户端
     */
    public void close() {
        if (closed) return;
        
        closed = true;
        
        log.info("开始关闭AsyncRpcClient...");
        
        // 取消所有等待中的调用
        cancelAllPendingCalls();
        
        // 关闭异步执行器
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("AsyncRpcClient已关闭");
    }
    
    /**
     * 异步调用统计信息
     */
    public static class AsyncCallStats {
        public final int pendingCalls;
        public final int totalFutures;
        public final int availablePermits;
        
        private AsyncCallStats(int pendingCalls, int totalFutures, int availablePermits) {
            this.pendingCalls = pendingCalls;
            this.totalFutures = totalFutures;
            this.availablePermits = availablePermits;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private int pendingCalls;
            private int totalFutures;
            private int availablePermits;
            
            public Builder pendingCalls(int pendingCalls) {
                this.pendingCalls = pendingCalls;
                return this;
            }
            
            public Builder totalFutures(int totalFutures) {
                this.totalFutures = totalFutures;
                return this;
            }
            
            public Builder availablePermits(int availablePermits) {
                this.availablePermits = availablePermits;
                return this;
            }
            
            public AsyncCallStats build() {
                return new AsyncCallStats(pendingCalls, totalFutures, availablePermits);
            }
        }
        
        @Override
        public String toString() {
            return String.format("AsyncCallStats{pending=%d, futures=%d, permits=%d}", 
                               pendingCalls, totalFutures, availablePermits);
        }
    }
}
