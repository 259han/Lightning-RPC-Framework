package com.rpc.example;

/**
 * 示例服务接口
 */
public interface HelloService {
    /**
     * 问候方法
     *
     * @param name 姓名
     * @return 问候语
     */
    String hello(String name);
    
    /**
     * 计算方法
     *
     * @param a 数字a
     * @param b 数字b
     * @return 计算结果
     */
    int add(int a, int b);
}
