package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.Patient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PatientMapper extends BaseMapper<Patient> {

    /**
     * 把「本人已软删、且卡号相同」的那一行复活，并用传入的姓名/关系/身份证/手机号覆盖。
     * 返回 1 = 复活成功，0 = 没有这样的行（调用方据此走正常 insert）。
     *
     * <p><b>为什么必须手写 SQL</b>：{@code BaseEntity.deleted} 上有 {@code @TableLogic}，
     * MyBatis-Plus 会给它自己生成的每一条 SELECT/UPDATE 追加 {@code deleted = 0}，
     * 所以 {@code updateById} 永远碰不到软删行。而逻辑删是 MP 的 SQL 注入器直接写进语句的，
     * 不是拦截器，{@code @InterceptorIgnore} 那套开关管不到它——3.5.5 没有「本次查询忽略逻辑删」的口子。
     * 手写 SQL 是唯一出路，也是本仓库第一条自定义 SQL。
     *
     * <p><b>为什么用一条 UPDATE 而不是「先查软删行再改」</b>：单条语句天然原子，
     * 不存在「查的时候还没有、改的时候已被别人复活」的窗口。{@code uk_card_no} 建在 card_no 单列上，
     * 全库同一卡号最多一行，所以这里最多影响 1 行，不用担心批量误伤。
     *
     * <p>{@code updated_at} 不在 SET 里：走自定义 SQL 时 MP 的 {@code MetaObjectHandler} 自动填充不生效，
     * 交给 DDL 的 {@code ON UPDATE CURRENT_TIMESTAMP(3)}。
     */
    @Update("UPDATE patient SET deleted = 0, name = #{name}, relation = #{relation}, "
            + "id_card = #{idCard}, phone = #{phone} "
            + "WHERE card_no = #{cardNo} AND user_id = #{userId} AND deleted = 1")
    int reviveSoftDeletedByCardNo(Patient patient);
}
