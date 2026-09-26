package com.hospital.dto;

/**
 * 派单请求。这四个字段同时也是幂等键，任一为空都会让幂等判定失效，
 * 所以 TaskService 在派单前逐一校验。
 */
public record TaskDispatchRequest(String type, String relatedType, Long relatedId, Long assigneeId) {
}
