package com.rpc.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC请求对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 接口名称
     */
    private String interfaceName;
    
    /**
     * 方法名称
     */
    private String methodName;
    
    /**
     * 参数类型列表
     */
    private Class<?>[] parameterTypes;
    
    /**
     * 参数值列表
     */
    private Object[] parameters;
    
    /**
     * 版本号，用于服务多版本支持
     */
    private String version;
    
    /**
     * 服务分组，用于服务分组管理
     */
    private String group;
    
    /**
     * 认证Token（JWT或API密钥）
     */
    private String authToken;
    
    /**
     * 客户端IP地址
     */
    private String clientIp;
    
    /**
     * 请求时间戳
     */
    private long timestamp;
    
    /**
     * 扩展属性（用于传递额外信息）
     */
    private transient java.util.Map<String, Object> attributes;
    
    /**
     * 获取服务唯一标识
     */
    public String getRpcServiceName() {
        return this.getInterfaceName() + "#" + this.getGroup() + "#" + this.getVersion();
    }
    
    /**
     * 设置属性
     */
    public void setAttribute(String key, Object value) {
        if (attributes == null) {
            attributes = new java.util.HashMap<>();
        }
        attributes.put(key, value);
    }
    
    /**
     * 获取属性
     */
    public Object getAttribute(String key) {
        return attributes != null ? attributes.get(key) : null;
    }
    
    /**
     * 移除属性
     */
    public Object removeAttribute(String key) {
        return attributes != null ? attributes.remove(key) : null;
    }
    
    /**
     * 检查是否有指定属性
     */
    public boolean hasAttribute(String key) {
        return attributes != null && attributes.containsKey(key);
    }
}
