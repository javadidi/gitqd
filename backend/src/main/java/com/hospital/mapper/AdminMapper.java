package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.Admin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminMapper extends BaseMapper<Admin> {

    /**
     * 按用户名读一行，**含软删行**。默认读法（{@code @TableLogic} 追加 {@code deleted = 0}）
     * 在本卡会直接出错，原因与 T25 的排班、T27 的医生一模一样：
     * {@code uk_username}（V1:383）是<b>库级</b>唯一约束，软删行仍然占着这个名字，
     * 但按默认读法查就是"这个名字没人用"，于是 insert 撞唯一索引变成 500，
     * 而管理员看到的报错是"服务器内部错误"，不是"这个用户名被占过"。
     *
     * <p>它同时是 {@code AdminAccountService.create} 的复活判据：软删掉的账号可以按原名字重建
     * （与 T08-G 的"就诊人删除后本人可复活卡号"同一条路数），不必让名字永久失踪。
     */
    @Select("SELECT * FROM admin WHERE username = #{username} LIMIT 1")
    Admin selectByUsernameIncludingDeleted(@Param("username") String username);

    /**
     * 复活一个软删账号（同名重建）。返回 1 = 复活成功；0 = 这行已经活着或根本不存在。
     *
     * <p><b>为什么不能写成 {@code LambdaUpdateWrapper.set(Admin::getDeleted, 0)}</b>：
     * MyBatis-Plus 会给 wrapper 形式的 UPDATE 也追加 {@code WHERE deleted = 0}
     * （{@code @TableLogic} 是全局行为，不区分你是不是在改这个标记本身），
     * 于是这条语句永远匹配 0 行——接口照样回 200，名字却还是删不掉也建不回来。
     * 这是 T11 给排班复活踩过的同一个坑（{@code ScheduleMapper.reviveSoftDeleted}），
     * 抄的是同一个答案：手写 SQL，{@code WHERE deleted = 1}。
     *
     * <p>四个业务列一次写全：复活语义就是"当新账号用"，
     * 留着旧密码哈希或旧手机号会让新账号继承上一个账号的痕迹。
     */
    @Update("UPDATE `admin` SET deleted = 0, password_hash = #{passwordHash}, role_id = #{roleId}, "
            + "phone = #{phone} WHERE id = #{id} AND deleted = 1")
    int reviveById(@Param("id") Long id,
                   @Param("passwordHash") String passwordHash,
                   @Param("roleId") Long roleId,
                   @Param("phone") String phone);
}
