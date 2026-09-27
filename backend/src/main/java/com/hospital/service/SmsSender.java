package com.hospital.service;

/**
 * 短信发送出口。业务代码只依赖这个接口，换通道商不用改调用方。
 */
public interface SmsSender {

    void send(String phone, String code);
}
