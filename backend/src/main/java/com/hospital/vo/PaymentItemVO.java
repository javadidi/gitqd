package com.hospital.vo;

public class PaymentItemVO {

    private Long id;
    private String name;
    private String category;
    private Integer quantity;
    private Long priceFen;
    private Long subtotalFen;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getPriceFen() { return priceFen; }
    public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    public Long getSubtotalFen() { return subtotalFen; }
    public void setSubtotalFen(Long subtotalFen) { this.subtotalFen = subtotalFen; }
}
