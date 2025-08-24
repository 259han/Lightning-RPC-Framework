package com.rpc.common.compress;

import com.rpc.common.constants.RpcConstants;
import lombok.extern.slf4j.Slf4j;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Exception;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.nio.ByteBuffer;

/**
 * LZ4压缩实现
 * 
 * 特点：
 * - 解压速度最快
 * - 压缩比较低
 * - 适合大数据量传输
 * - 内存友好
 */
@Slf4j
public class Lz4Compressor implements Compressor {
    
    /**
     * 压缩阈值：小于256字节的数据不压缩
     * LZ4适合大数据，小数据压缩意义不大
     */
    private static final int COMPRESSION_THRESHOLD = 256;
    
    /**
     * LZ4工厂实例，使用最快的实现
     */
    private static final LZ4Factory LZ4_FACTORY = LZ4Factory.fastestInstance();
    
    /**
     * LZ4压缩器实例（线程安全）
     */
    private static final LZ4Compressor LZ4_COMPRESSOR = LZ4_FACTORY.fastCompressor();
    
    /**
     * LZ4解压器实例（线程安全）
     */
    private static final LZ4FastDecompressor LZ4_DECOMPRESSOR = LZ4_FACTORY.fastDecompressor();
    
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
            // 计算压缩后的最大长度
            int maxCompressedLength = LZ4_COMPRESSOR.maxCompressedLength(bytes.length);
            
            // 创建输出缓冲区，前4字节存储原始数据长度
            byte[] compressed = new byte[maxCompressedLength + 4];
            
            // 写入原始数据长度（用于解压时分配缓冲区）
            ByteBuffer.wrap(compressed, 0, 4).putInt(bytes.length);
            
            // 执行压缩
            int compressedLength = LZ4_COMPRESSOR.compress(bytes, 0, bytes.length, compressed, 4);
            
            // 创建最终结果数组
            byte[] result = new byte[compressedLength + 4];
            System.arraycopy(compressed, 0, result, 0, compressedLength + 4);
            
            // 如果压缩后反而更大，返回原数据
            if (result.length >= bytes.length) {
                log.debug("LZ4压缩后数据更大，返回原数据。原始大小: {}, 压缩后大小: {}", 
                         bytes.length, result.length);
                return bytes;
            }
            
            // 只在DEBUG级别且压缩效果显著时才输出日志
            if (log.isDebugEnabled() && result.length < bytes.length * 0.9) {
                double compressionRatio = (1.0 - (double) result.length / bytes.length) * 100;
                log.debug("LZ4压缩完成。原始大小: {}, 压缩后大小: {}, 压缩比: {:.2f}%", 
                         bytes.length, result.length, compressionRatio);
            }
            
            return result;
        } catch (LZ4Exception e) {
            log.error("LZ4压缩失败", e);
            return bytes; // 压缩失败时返回原数据
        }
    }
    
    @Override
    public byte[] decompress(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return bytes;
        }
        
        try {
            // 读取原始数据长度
            int originalLength = ByteBuffer.wrap(bytes, 0, 4).getInt();
            
            // 验证原始长度的合理性
            if (originalLength <= 0 || originalLength > 100 * 1024 * 1024) { // 限制100MB
                throw new IllegalArgumentException("无效的原始数据长度: " + originalLength);
            }
            
            // 创建输出缓冲区
            byte[] decompressed = new byte[originalLength];
            
            // 执行解压缩
            LZ4_DECOMPRESSOR.decompress(bytes, 4, decompressed, 0, originalLength);
            
            return decompressed;
        } catch (LZ4Exception e) {
            log.error("LZ4解压缩失败", e);
            throw new RuntimeException("LZ4解压缩失败", e);
        } catch (Exception e) {
            log.error("LZ4解压缩异常", e);
            throw new RuntimeException("LZ4解压缩异常", e);
        }
    }
    
    @Override
    public byte getType() {
        return RpcConstants.COMPRESS_TYPE_LZ4;
    }
}
