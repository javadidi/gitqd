package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 绑定住院号（T09）。
 *
 * <p><b>没有 userId 字段</b>：归属一律由 controller 从 token 取（附录 B 第 806 条），
 * 请求体里带 userId 就等于把「替谁绑住院人」的决定权交给客户端。
 *
 * <p><b>住院号是用户手输的，不做格式校验</b>，只校验非空与长度（V1__init.sql:45 是
 * {@code VARCHAR(64) NOT NULL}）。种子里的住院号长成 {@code ZY20260001}，但没有任何规格
 * 定义过它的格式，写一个 {@code ^ZY\d{8}$} 就是自造规则（宁少勿假）。
 * 这里的"验证"因此只有两件事：参数合法、住院号全局未被占用。
 *
 * <p>科室与床号是<b>选填</b>：两列在 V1 里都是 {@code DEFAULT NULL}，seed.sql 的 5 行住院人
 * 也有 2 行是 NULL（第 4、5 行），说明"还没分配床位"是这张表的正常状态而不是异常。
 * PRD 数据模型（需求文档 577 行）把科室、床位列为住院人字段，但没写由谁填——
 * 本卡按"用户自报"处理，与住院号同源。
 */
public class InpatientBindRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名过长")
    private String name;

    @NotBlank(message = "住院号不能为空")
    @Size(max = 64, message = "住院号过长")
    private String inpatientNo;

    @Size(max = 128, message = "科室过长")
    private String department;

    @Size(max = 32, message = "床号过长")
    private String bedNo;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInpatientNo() { return inpatientNo; }
    public void setInpatientNo(String inpatientNo) { this.inpatientNo = inpatientNo; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getBedNo() { return bedNo; }
    public void setBedNo(String bedNo) { this.bedNo = bedNo; }
}
