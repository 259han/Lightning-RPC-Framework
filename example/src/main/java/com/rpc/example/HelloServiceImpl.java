package com.rpc.example;

import lombok.extern.slf4j.Slf4j;

/**
 * 示例服务实现
 */
@Slf4j
public class HelloServiceImpl implements HelloService {
    
    @Override
    public String hello(String name) {
        log.info("收到hello请求，参数: {}", name);
        return "Hello, " + name + "!";
    }
    
    @Override
    public int add(int a, int b) {
        log.info("收到add请求，参数: a={}, b={}", a, b);
        return a + b;
    }
}
