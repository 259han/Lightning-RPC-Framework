package com.rpc.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC响应对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcResponse<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 响应码
     */
    private Integer code;
    
    /**
     * 错误消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 成功响应
     */
    public static <T> RpcResponse<T> success(T data) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setCode(200);
        response.setMessage("调用成功");
        response.setData(data);
        return response;
    }
    
    /**
     * 失败响应
     */
    public static <T> RpcResponse<T> fail(Integer code, String message) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
