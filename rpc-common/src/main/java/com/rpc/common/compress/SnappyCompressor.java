package com.rpc.common.compress;

import com.rpc.common.constants.RpcConstants;
import lombok.extern.slf4j.Slf4j;
import org.xerial.snappy.Snappy;

import java.io.IOException;

/**
 * Snappy压缩实现
 * 
 * 特点：
 * - 压缩速度极快
 * - 压缩比适中
 * - 适合对延迟敏感的场景
 * - CPU消耗低
 */
@Slf4j
public class SnappyCompressor implements Compressor {
    
    /**
     * 压缩阈值：小于512字节的数据不压缩（避免负优化）
     * Snappy对小数据的压缩效果有限，设置较小的阈值
     */
    private static final int COMPRESSION_THRESHOLD = 512;
    
    @Override
    public byte[] compress(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return bytes;
        }
        
        // 小数据不压缩
        if (bytes.length < COMPRESSION_THRESHOLD) {
            return bytes;
        }
        
        try {
            byte[] compressed = Snappy.compress(bytes);
            
            // 如果压缩后反而更大，返回原数据
            if (compressed.length >= bytes.length) {
                log.debug("Snappy压缩后数据更大，返回原数据。原始大小: {}, 压缩后大小: {}", 
                         bytes.length, compressed.length);
                return bytes;
            }
            
            // 只在DEBUG级别且压缩效果显著时才输出日志
            if (log.isDebugEnabled() && compressed.length < bytes.length * 0.9) {
                double compressionRatio = (1.0 - (double) compressed.length / bytes.length) * 100;
                log.debug("Snappy压缩完成。原始大小: {}, 压缩后大小: {}, 压缩比: {:.2f}%", 
                         bytes.length, compressed.length, compressionRatio);
            }
            
            return compressed;
        } catch (IOException e) {
            log.error("Snappy压缩失败", e);
            return bytes; // 压缩失败时返回原数据
        }
    }
    
    @Override
    public byte[] decompress(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return bytes;
        }
        
        try {
            return Snappy.uncompress(bytes);
        } catch (IOException e) {
            log.error("Snappy解压缩失败", e);
            throw new RuntimeException("Snappy解压缩失败", e);
        }
    }
    
    @Override
    public byte getType() {
        return RpcConstants.COMPRESS_TYPE_SNAPPY;
    }
}
