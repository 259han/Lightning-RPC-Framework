package com.rpc.protocol.codec;

import com.rpc.common.compress.Compressor;
import com.rpc.common.constants.RpcConstants;
import com.rpc.extension.ExtensionLoader;
import com.rpc.protocol.RpcMessage;
import com.rpc.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC消息编码器
 * <pre>
 * 协议格式：
 * +-------+----------+----------+----------+----------+----------+----------+----------+
 * | Magic | Version  | FullLen  | MsgType  | Codec    | Compress | ReqId    | Payload  |
 * | 4byte | 1byte    | 4byte    | 1byte    | 1byte    | 1byte    | 8byte    | Variable |
 * +-------+----------+----------+----------+----------+----------+----------+----------+
 * </pre>
 */
@Slf4j
public class RpcMessageEncoder extends MessageToByteEncoder<RpcMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) {
        try {
            // 写入魔数
            out.writeBytes(RpcConstants.MAGIC_NUMBER);
            // 写入版本号
            out.writeByte(RpcConstants.VERSION);
            // 先占位，等待后面计算总长度
            int fullLengthIndex = out.writerIndex();
            out.writerIndex(fullLengthIndex + 4);
            // 写入消息类型
            out.writeByte(msg.getMessageType());
            // 写入序列化类型
            out.writeByte(msg.getCodecType());
            // 写入压缩类型
            out.writeByte(msg.getCompressType());
            // 写入请求ID
            out.writeLong(msg.getRequestId());
            
            // 序列化消息体
            byte[] data = null;
            if (msg.getData() != null) {
                Serializer serializer = getSerializer(msg.getCodecType());
                data = serializer.serialize(msg.getData());
                
                // 压缩数据
                if (msg.getCompressType() != RpcConstants.COMPRESS_TYPE_NONE && data != null) {
                    Compressor compressor = getCompressor(msg.getCompressType());
                    data = compressor.compress(data);
                }
            }
            
            // 写入负载
            if (data != null) {
                out.writeBytes(data);
            }
            
            // 计算总长度并回写
            int writerIndex = out.writerIndex();
            out.writerIndex(fullLengthIndex);
            out.writeInt(RpcConstants.HEADER_LENGTH + (data == null ? 0 : data.length));
            out.writerIndex(writerIndex);
        } catch (Exception e) {
            log.error("编码异常", e);
        }
    }
    
    /**
     * 根据序列化类型获取序列化器
     */
    private Serializer getSerializer(byte codecType) {
        ExtensionLoader<Serializer> loader = ExtensionLoader.getExtensionLoader(Serializer.class);
        
        // 根据类型选择对应的序列化器
        String serializerName = getSerializerName(codecType);
        Serializer serializer = loader.getExtension(serializerName);
        
        if (serializer == null) {
            // 如果找不到指定的序列化器，使用默认的JSON序列化器
            serializer = new com.rpc.serialize.JsonSerializer();
        }
        return serializer;
    }
    
    /**
     * 根据压缩类型获取压缩器
     */
    private Compressor getCompressor(byte compressType) {
        ExtensionLoader<Compressor> loader = ExtensionLoader.getExtensionLoader(Compressor.class);
        
        // 根据类型选择对应的压缩器
        String compressorName = getCompressorName(compressType);
        Compressor compressor = loader.getExtension(compressorName);
        
        if (compressor == null) {
            // 如果找不到指定的压缩器，使用无压缩
            compressor = new com.rpc.common.compress.NoneCompressor();
        }
        return compressor;
    }
    
    /**
     * 根据序列化类型码获取序列化器名称
     */
    private String getSerializerName(byte codecType) {
        switch (codecType) {
            case RpcConstants.SERIALIZATION_JSON:
                return "json";
            case RpcConstants.SERIALIZATION_HESSIAN:
                return "hessian";
            case RpcConstants.SERIALIZATION_PROTOBUF:
                return "protobuf";
            default:
                return "json";
        }
    }
    
    /**
     * 根据压缩类型码获取压缩器名称
     */
    private String getCompressorName(byte compressType) {
        switch (compressType) {
            case RpcConstants.COMPRESS_TYPE_NONE:
                return "none";
            case RpcConstants.COMPRESS_TYPE_GZIP:
                return "gzip";
            case RpcConstants.COMPRESS_TYPE_SNAPPY:
                return "snappy";
            case RpcConstants.COMPRESS_TYPE_LZ4:
                return "lz4";
            default:
                return "none";
        }
    }
}
