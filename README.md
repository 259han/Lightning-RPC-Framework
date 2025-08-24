# RPC框架

这是一个基于Netty和ZooKeeper的高性能RPC框架，支持多种序列化方式、负载均衡和服务治理功能。

## 特性

- 🚀 **高性能**: 基于Netty的异步非阻塞网络通信
- 🔧 **服务治理**: 基于ZooKeeper的服务注册与发现
- ⚖️ **负载均衡**: 支持随机、轮询、一致性哈希负载均衡策略
- 📦 **多序列化**: 支持JSON、Hessian、Protobuf多种序列化方式
- 🗜️ **数据压缩**: 支持GZIP、Snappy、LZ4压缩，自动优化传输效率
- ⚡ **容错机制**: 超时重试、熔断器、配置中心集成
- 🔍 **链路追踪**: 分布式调用链路追踪和监控
- 🔌 **SPI扩展**: 支持序列化器、压缩器和负载均衡器的插件化扩展
- 🛡️ **异常处理**: 完善的异常处理机制
- 💾 **连接池**: 智能连接池管理，自动重连和清理
- 📊 **性能优化**: 协议编解码优化，支持序列化性能测试

## 架构设计

项目采用三层分离的架构模式：

- **客户端层(Consumer)**: 负责服务调用和请求发送
- **服务端层(Provider)**: 负责服务暴露和请求处理  
- **注册中心层(Registry)**: 基于ZooKeeper实现服务注册发现

### 模块划分

- `rpc-common`: 通用模块，包含协议定义、序列化接口等
- `rpc-server`: 服务端模块，实现服务注册和请求处理
- `rpc-client`: 客户端模块，实现服务发现和请求发送
- `example`: 使用示例

## 快速开始

### 1. 启动ZooKeeper

确保ZooKeeper在localhost:2181端口运行。

### 2. 定义服务接口

```java
public interface HelloService {
    String hello(String name);
    int add(int a, int b);
}
```

### 3. 实现服务

```java
@Slf4j
public class HelloServiceImpl implements HelloService {
    @Override
    public String hello(String name) {
        return "Hello, " + name + "!";
    }
    
    @Override
    public int add(int a, int b) {
        return a + b;
    }
}
```

### 4. 启动服务端

```java
public class ServerExample {
    public static void main(String[] args) {
        // 创建ZooKeeper服务注册器
        ZookeeperServiceRegistry serviceRegistry = new ZookeeperServiceRegistry("localhost:2181");
        
        // 创建RPC服务器
        RpcServer rpcServer = new RpcServer("localhost", 9999, serviceRegistry);
        
        // 注册服务
        HelloService helloService = new HelloServiceImpl();
        rpcServer.registerService(helloService, HelloService.class.getName() + "#default#1.0");
        
        // 启动服务器
        rpcServer.start();
    }
}
```

### 5. 启动客户端

```java
public class ClientExample {
    public static void main(String[] args) {
        // 创建ZooKeeper服务发现器
        ZookeeperServiceDiscovery serviceDiscovery = new ZookeeperServiceDiscovery("localhost:2181");
        
        // 创建Netty RPC客户端
        NettyRpcClient nettyRpcClient = new NettyRpcClient(serviceDiscovery);
        
        // 创建RPC客户端
        RpcClient rpcClient = new RpcClient(nettyRpcClient, 5000);
        
        // 获取服务代理
        HelloService helloService = rpcClient.getProxy(HelloService.class, "1.0", "default");
        
        // 调用远程方法
        String result = helloService.hello("World");
        System.out.println(result); // 输出: Hello, World!
        
        // 关闭资源
        nettyRpcClient.close();
        serviceDiscovery.close();
    }
}
```

## 协议设计

采用自定义的二进制协议：

```
+-------+----------+----------+----------+----------+----------+----------+----------+
| Magic | Version  | FullLen  | MsgType  | Codec    | Compress | ReqId    | Payload  |
| 4byte | 1byte    | 4byte    | 1byte    | 1byte    | 1byte    | 8byte    | Variable |
+-------+----------+----------+----------+----------+----------+----------+----------+
```

- **Magic Number**: 魔数(0xCAFEBABE)，用于快速验证数据包有效性
- **Version**: 协议版本号，支持协议演进
- **Full Length**: 包含协议头和数据体的总长度，解决TCP粘包问题
- **Message Type**: 区分请求(0x01)和响应(0x02)类型
- **Codec Type**: 序列化方式标识(JSON=1, Hessian=2, Protobuf=3)
- **Compress Type**: 压缩算法标识(None=0, Gzip=1, Snappy=2, LZ4=3)
- **Request ID**: 全局唯一请求标识符
- **Payload**: 序列化后的业务数据

## 序列化和压缩

### 支持的序列化方式

| 序列化器 | 特点 | 适用场景 |
|---------|------|----------|
| **JSON** | 可读性强，调试方便，跨语言支持 | 开发测试，对性能要求不高 |
| **Hessian** | 性能好，数据紧凑，兼容性强 | 生产环境的平衡选择 |
| **Protobuf** | 极高性能，极小数据体积 | 对性能要求极高的核心服务 |

### 数据压缩

| 压缩算法 | 特点 | 适用场景 |
|---------|------|----------|
| **GZIP** | 压缩比高，CPU消耗相对较高 | 文本数据，网络带宽有限 |
| **Snappy** | 压缩速度极快，压缩比适中 | 延迟敏感的场景 |
| **LZ4** | 解压速度最快，压缩比较低 | 大数据量传输，内存友好 |

- **自动压缩阈值**: 根据算法优化阈值，避免负优化
- **智能选择**: 压缩后更大时自动使用原数据

## 高级功能

### 负载均衡策略

| 策略 | 特点 | 适用场景 |
|-----|------|----------|
| **随机** | 简单高效，长期均匀分布 | 服务器性能相近 |
| **轮询** | 绝对均匀，CAS保证线程安全 | 无状态服务 |
| **一致性哈希** | 160个虚拟节点，节点变化影响小 | 有状态服务，会话保持 |

### 容错机制

- **超时控制**: 可配置请求超时时间
- **重试策略**: 支持固定延迟和指数退避
- **熔断器**: 失败率超阈值时快速失败
- **配置中心**: 文件配置中心，支持动态更新

### 链路追踪

- **TraceID/SpanID**: 分布式调用链路标识
- **标签和日志**: 丰富的上下文信息
- **可扩展收集器**: 支持自定义追踪数据收集

### 性能测试

运行各种测试：
```bash
# 基础功能测试
mvn exec:java@basic-test

# 综合功能测试  
mvn exec:java@comprehensive-test

# 性能基准测试
mvn exec:java@performance-test

# 高级功能测试
mvn exec:java@advanced-test

# 新增功能测试
mvn exec:java@new-features-test
```

## 扩展开发

### 添加新的序列化器

1. 实现`Serializer`接口
2. 在`META-INF/services/com.rpc.serialize.Serializer`文件中注册

### 添加新的压缩器

1. 实现`Compressor`接口
2. 在`META-INF/services/com.rpc.common.compress.Compressor`文件中注册

### 添加新的负载均衡策略

1. 实现`LoadBalancer`接口
2. 在`META-INF/services/com.rpc.common.loadbalance.LoadBalancer`文件中注册

## 编译运行

```bash
# 编译项目
mvn clean compile

# 运行示例（需要先启动ZooKeeper）
# 终端1: 启动服务端
java -cp target/classes com.rpc.example.ServerExample

# 终端2: 启动客户端
java -cp target/classes com.rpc.example.ClientExample
```

## 技术栈

- **网络通信**: Netty 4.1.94
- **服务注册发现**: Apache ZooKeeper + Curator
- **序列化**: Jackson (JSON), Hessian, Protostuff (Protobuf)
- **压缩算法**: GZIP, Snappy, LZ4
- **容错**: 重试、熔断器、超时控制
- **监控**: 分布式链路追踪
- **构建工具**: Maven
- **日志**: SLF4J + Logback
- **工具库**: Lombok

## 快速体验

### 使用测试脚本 (Windows)

```bash
cd example
.\test.bat
# 选择要运行的测试: 1-6
```

### 手动运行测试

```bash
# 编译项目
mvn clean compile

# 运行完整测试套件
cd example
mvn exec:java@basic-test        # 基础功能
mvn exec:java@comprehensive-test # 综合功能  
mvn exec:java@performance-test   # 性能基准
mvn exec:java@advanced-test      # 高级功能
mvn exec:java@new-features-test  # 新增功能
```

## 性能数据

基于测试环境的性能表现：

### 序列化性能 (1000次平均)
- **JSON**: ~2ms/次，数据体积中等
- **Hessian**: ~1.5ms/次，数据紧凑  
- **Protobuf**: ~0.8ms/次，体积最小

### 压缩性能 (1000次平均)
- **GZIP**: 压缩比最高(~70%), 耗时较长
- **Snappy**: 压缩速度最快，压缩比适中(~50%)
- **LZ4**: 解压最快，适合大数据传输

## 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件
