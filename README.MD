# 网络应用端口代理和消息转发
==========

## 支持
Netty4 



## 注意 
* CFB模式分为 `CFB default`例如 AES算法时 CFB就是 CFB128bits， `CFB1`是 AES-CFB1bit， `CBF8`则是 AES-CFB8bits
* java算法时如果设置为 CFB8 （流加密模式）可以和现存的客户端进行通信，但是如果设置成 CFB模式，则会出现因为块数据不全而没有结果的情况