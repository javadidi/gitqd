package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 编辑就诊人（T08）。
 *
 * <p>与 {@link PatientCreateRequest} 的差别只有一处，但很重要：
 * <b>cardNo / idCard / phone 传空串（或不传）= 保持原值不变</b>。
 *
 * <p>为什么不是"必填"：响应里这三个字段只有打码值，前端拿不到明文，
 * 没法把原值预填回输入框。若强制必填，用户改个昵称就得重打一遍身份证和手机号。
 * 于是约定「留空即不改」——明文因此始终不出后端，编辑又可用。
 * name / relation 不敏感，前端能预填，所以仍然必填。
 */
public class PatientUpdateRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名过长")
    private String name;

    /** 空串 = 不改。改了就照样过 R1 的唯一性校验（排除自己）。卡号无格式规格，只限长度 */
    @Size(max = 64, message = "就诊卡号过长")
    private String cardNo;

    /** 空串 = 不改 */
    @Pattern(regexp = "^$|" + PatientCreateRequest.ID_CARD_PATTERN, message = "身份证号格式不正确")
    private String idCard;

    /** 空串 = 不改 */
    @Pattern(regexp = "^$|" + PatientCreateRequest.PHONE_PATTERN, message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "与本人关系不能为空")
    @Pattern(regexp = PatientCreateRequest.RELATION_PATTERN, message = "与本人关系取值不正确")
    private String relation;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
}
