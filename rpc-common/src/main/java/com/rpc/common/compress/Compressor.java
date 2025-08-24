package com.rpc.common.compress;

/**
 * 压缩接口
 */
public interface Compressor {
    /**
     * 压缩
     *
     * @param bytes 原始字节数组
     * @return 压缩后的字节数组
     */
    byte[] compress(byte[] bytes);

    /**
     * 解压缩
     *
     * @param bytes 压缩后的字节数组
     * @return 原始字节数组
     */
    byte[] decompress(byte[] bytes);

    /**
     * 获取压缩类型
     *
     * @return 压缩类型
     */
    byte getType();
}
