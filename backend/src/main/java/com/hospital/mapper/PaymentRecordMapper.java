package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    /**
     * 余额缴费：把一张待缴费单推进成已缴（T15）。返回 1 = 推进成功；0 = 这张单本来就不是 PENDING。
     *
     * <p>与 {@code RechargeRecordMapper.markSuccess} 同一条纪律：判据不靠"先查一遍状态再改"，
     * 而是靠 {@code WHERE status = 'PENDING'} 影响的行数。患者连点两次、或两个设备同时点，
     * 输家拿到 0 → 3004，不会重复扣余额。
     *
     * <p><b>为什么不写 {@code trade_no}</b>：这一列（V1:164）注释是「第三方交易号」，
     * 而就诊卡余额支付<b>没有第三方</b>——钱从本院的 {@code patient.balance_fen} 搬到本院的
     * {@code payment_record}，全程不出系统。给它填一个自造的流水号（像 T14 充值那样
     * {@code MOCK_TXN_RC_*}）会让财务对账时误以为存在一笔可查的通道交易，
     * 留 NULL 才是这句话的真话。
     */
    @Update("UPDATE payment_record SET status = 'SUCCESS', pay_method = #{payMethod} "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markPaidByBalance(@Param("id") Long id, @Param("payMethod") String payMethod);
}
