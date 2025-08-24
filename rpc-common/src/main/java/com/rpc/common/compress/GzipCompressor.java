package com.rpc.common.compress;

import com.rpc.common.constants.RpcConstants;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GZIP压缩实现
 * 
 * 特点：
 * - 压缩比高，适合文本数据
 * - CPU消耗相对较高
 * - 广泛支持，兼容性好
 */
@Slf4j
public class GzipCompressor implements Compressor {
    
    /**
     * 压缩阈值：小于1KB的数据不压缩（避免负优化）
     */
    private static final int COMPRESSION_THRESHOLD = 1024;
    
    @Override
    public byte[] compress(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return bytes;
        }
        
        // 小数据不压缩
        if (bytes.length < COMPRESSION_THRESHOLD) {
            return bytes;
        }
        
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(bos)) {
            
            gzipOut.write(bytes);
            gzipOut.finish();
            
            byte[] compressed = bos.toByteArray();
            
            // 如果压缩后反而更大，返回原数据
            if (compressed.length >= bytes.length) {
                log.debug("GZIP压缩后数据更大，返回原数据。原始大小: {}, 压缩后大小: {}", 
                         bytes.length, compressed.length);
                return bytes;
            }
            
            // 只在DEBUG级别且压缩效果显著时才输出日志
            if (log.isDebugEnabled() && compressed.length < bytes.length * 0.9) {
                log.debug("GZIP压缩完成。原始大小: {}, 压缩后大小: {}, 压缩比: {}%", 
                         bytes.length, compressed.length, 
                         String.format("%.2f", (1.0 - (double) compressed.length / bytes.length) * 100));
            }
            
            return compressed;
        } catch (IOException e) {
            log.error("GZIP压缩失败", e);
            return bytes; // 压缩失败时返回原数据
        }
    }
    
    @Override
    public byte[] decompress(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return bytes;
        }
        
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             GZIPInputStream gzipIn = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("GZIP解压缩失败", e);
            throw new RuntimeException("GZIP解压缩失败", e);
        }
    }
    
    @Override
    public byte getType() {
        return RpcConstants.COMPRESS_TYPE_GZIP;
    }
}
