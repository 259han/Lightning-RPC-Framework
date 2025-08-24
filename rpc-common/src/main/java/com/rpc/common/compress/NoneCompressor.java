package com.rpc.common.compress;

import com.rpc.common.constants.RpcConstants;

/**
 * 无压缩实现（默认）
 */
public class NoneCompressor implements Compressor {
    
    @Override
    public byte[] compress(byte[] bytes) {
        return bytes;
    }
    
    @Override
    public byte[] decompress(byte[] bytes) {
        return bytes;
    }
    
    @Override
    public byte getType() {
        return RpcConstants.COMPRESS_TYPE_NONE;
    }
}
