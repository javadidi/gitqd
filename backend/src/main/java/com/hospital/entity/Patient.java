package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("patient")
public class Patient extends BaseEntity {

    private Long userId;
    private String name;
    private String idCard;
    private String phone;
    private String relation;
    private String cardNo;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
}
