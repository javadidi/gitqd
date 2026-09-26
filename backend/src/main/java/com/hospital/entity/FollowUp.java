package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("follow_up")
public class FollowUp extends BaseEntity {

    private Long patientId;
    private Long departmentId;
    private Long doctorId;
    private String disease;
    private String status;

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public String getDisease() { return disease; }
    public void setDisease(String disease) { this.disease = disease; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
