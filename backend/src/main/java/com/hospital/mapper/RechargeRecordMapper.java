package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.RechargeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RechargeRecordMapper extends BaseMapper<RechargeRecord> {

    /**
     * 把充值单从待支付推进到成功，并写入第三方流水号。返回 1 = 本次推进；0 = 已推进过或状态不对。
     *
     * <p>{@code WHERE status = 'PENDING'} 是幂等守卫，与 T12 的 {@code confirmIfPending}、
     * T13 的 {@code cancelIfActive} 同一族写法：判定和写入在一次数据库操作里完成，
     * 所以"同一张充值单被置两次成功"在数据库层面不可能发生。
     *
     * <p>现在它只被同一个事务内部调用，看起来是多余的；留着是因为真实微信支付落地后
     * 这张单会被<b>回调</b>推进，而回调天然会重推——那时这条 WHERE 就是唯一的防线，
     * 到时无需回来补。
     *
     * <p>{@code updated_at} 不在 SET 里：自定义 SQL 不走 MP 的自动填充，
     * 交给 DDL 的 {@code ON UPDATE CURRENT_TIMESTAMP(3)}（V1:150）。
     */
    @Update("UPDATE recharge_record SET status = 'SUCCESS', trade_no = #{tradeNo} "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markSuccess(@Param("id") Long id, @Param("tradeNo") String tradeNo);
}
