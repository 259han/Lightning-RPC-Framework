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
     * 获取服务唯一标识
     */
    public String getRpcServiceName() {
        return this.getInterfaceName() + "#" + this.getGroup() + "#" + this.getVersion();
    }
}
