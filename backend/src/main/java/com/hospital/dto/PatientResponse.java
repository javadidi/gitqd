package com.hospital.dto;

/**
 * 就诊人对外视图（T08）。
 *
 * <p>{@code idCard} / {@code phone} 是 {@link com.hospital.util.MaskUtil} 的打码值，
 * 明文永远不出后端；{@code cardNo} 是明文，因为卡片 372 行明确要求列表展示就诊卡号，
 * 它本身也不是加密字段（V1 里只有 id_card / phone 标了「AES加密」）。
 *
 * <p>relation 回的是 SELF/CHILD/... 原值，中文标签由前端映射——
 * 和管理后台 StatusBadge 回状态码而不回中文是同一个理由：文案改了不用动后端。
 */
public class PatientResponse {

    private Long id;
    private String name;
    private String relation;
    private String cardNo;
    /** 打码身份证；库里是 SEED_ENC 占位值时为 null */
    private String idCard;
    /** 打码手机号；未填写或占位值时为 null */
    private String phone;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
