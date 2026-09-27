package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增就诊人（T08）。
 *
 * <p><b>没有 userId 字段</b>：归属一律由 controller 从 token 取（附录 B 第 806 条），
 * 请求体里带 userId 就等于把「替谁建就诊人」的决定权交给客户端。
 *
 * <p>就诊卡号是用户手输的必填项，不是系统生成：V1 的 {@code card_no} 是 NOT NULL + uk_card_no，
 * 而卡片 R1 要求「命中返回友好提示」——只有用户输入才谈得上友好提示。
 */
public class PatientCreateRequest {

    /** 关系取值来自 V1__init.sql:31 的列注释，seed.sql 的 10 行就诊人也正好覆盖这 5 个 */
    public static final String RELATION_PATTERN = "^(SELF|CHILD|PARENT|SPOUSE|OTHER)$";

    /** 身份证只收 18 位（末位可为 X）。15 位老证没有任何规格提到，按宁少勿假不支持 */
    public static final String ID_CARD_PATTERN = "^\\d{17}[\\dXx]$";

    public static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名过长")
    private String name;

    @NotBlank(message = "就诊卡号不能为空")
    @Size(max = 64, message = "就诊卡号过长")
    private String cardNo;

    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = ID_CARD_PATTERN, message = "身份证号格式不正确")
    private String idCard;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = PHONE_PATTERN, message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "与本人关系不能为空")
    @Pattern(regexp = RELATION_PATTERN, message = "与本人关系取值不正确")
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
