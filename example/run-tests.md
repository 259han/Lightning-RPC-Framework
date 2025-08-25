# RPC框架测试执行指南

## 测试分类

### 1. 基础功能测试
**目标**: 验证核心功能的正确性
**执行命令**: 
```bash
mvn exec:java@basic-test
```
**测试内容**:
- RPC请求/响应序列化
- 数据压缩功能
- 边界条件处理

### 2. 综合功能测试
**目标**: 验证各组件集成和SPI机制
**执行命令**:
```bash
mvn exec:java@comprehensive-test
```
**测试内容**:
- 序列化器SPI加载
- 压缩器功能验证
- 负载均衡器测试
- 异常处理机制

### 3. 性能测试
**目标**: 评估序列化和压缩性能
**执行命令**:
```bash
mvn exec:java@performance-test
```
**测试内容**:
- 序列化器性能对比
- 压缩效果评估
- 大数据量处理能力

### 4. 集成测试（需要ZooKeeper）
**目标**: 验证完整RPC调用流程
**前置条件**: 启动ZooKeeper服务 (localhost:2181)
**执行命令**:
```bash
mvn exec:java@integration-test
```
**测试内容**:
- 客户端-服务端通信
- 服务注册与发现
- 实际RPC调用

### 5. 高级功能测试
**目标**: 验证第四阶段高级功能
**执行命令**:
```bash
mvn exec:java@advanced-test
```
**测试内容**:
- 超时和重试功能
- 熔断器模式
- 配置中心集成
- 链路追踪功能

### 6. 新增功能测试
**目标**: 验证新增的负载均衡和压缩功能
**执行命令**:
```bash
mvn exec:java@new-features-test
```
**测试内容**:
- 一致性哈希负载均衡
- Snappy压缩算法
- LZ4压缩算法
- 压缩性能对比

### 7. 性能监控测试
**目标**: 验证连接池优化和性能指标收集功能
**执行命令**:
```bash
mvn exec:java@monitoring-test
```
**测试内容**:
- 连接池管理和优化
- 性能指标收集
- 实时监控和统计
- 连接池统计分析

## 快速测试执行顺序

1. **编译项目**
```bash
mvn clean compile
```

2. **基础功能验证**
```bash
mvn exec:java@basic-test
```

3. **综合功能验证**
```bash
mvn exec:java@comprehensive-test
```

4. **性能基准测试**
```bash
mvn exec:java@performance-test
```

5. **高级功能测试**
```bash
mvn exec:java@advanced-test
```

6. **新增功能测试**
```bash
mvn exec:java@new-features-test
```

7. **性能监控测试**
```bash
mvn exec:java@monitoring-test
```

8. **集成测试**（可选，需要ZooKeeper）
```bash
mvn exec:java@integration-test
```

## 测试输出说明

- ✅ 表示测试通过
- ❌ 表示测试失败
- 性能测试会输出详细的时间和吞吐量数据
- 集成测试会显示实际的RPC调用结果

## 故障排除

- 如果编译失败，检查Java版本是否为21+
- 如果中文显示乱码，确保终端支持UTF-8编码
- 集成测试失败通常是因为ZooKeeper未启动
- 性能测试结果可能因硬件配置而异
