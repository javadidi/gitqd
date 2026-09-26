package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.enums.Capability;
import com.hospital.annotation.RequireCap;
import com.hospital.vo.PaymentDetailVO;
import com.hospital.vo.PaymentItemVO;
import com.hospital.vo.PaymentSummaryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    @GetMapping("/{id}")
    public Result<PaymentDetailVO> detail(@PathVariable Long id) {
        return Result.success(buildMockPayment(id));
    }

    @RequireCap(Capability.APPROVE_REFUND)
    @GetMapping("/{id}/refund-approve")
    public Result<String> approveRefund(@PathVariable Long id) {
        return Result.success("退款已审批");
    }

    private PaymentDetailVO buildMockPayment(Long id) {
        PaymentItemVO item1 = new PaymentItemVO();
        item1.setId(11L);
        item1.setName("血常规");
        item1.setCategory("LAB");
        item1.setQuantity(2);
        item1.setPriceFen(2500L);
        item1.setSubtotalFen(5000L);

        PaymentItemVO item2 = new PaymentItemVO();
        item2.setId(12L);
        item2.setName("胸部CT");
        item2.setCategory("IMAGING");
        item2.setQuantity(1);
        item2.setPriceFen(38000L);
        item2.setSubtotalFen(38000L);

        List<PaymentItemVO> items = Arrays.asList(item1, item2);

        PaymentSummaryVO summary = new PaymentSummaryVO();
        summary.setGrossFen(43000L);
        summary.setDiscountFen(3000L);
        summary.setReceivedFen(40000L);
        summary.setOutstandingFen(0L);
        summary.setDiffFen(0L);
        summary.setNote("医保结算");
        summary.setItemCount(2);

        PaymentDetailVO vo = new PaymentDetailVO();
        vo.setId(id);
        vo.setOrderNo("JF20260925-0001");
        vo.setPatientName("张三");
        vo.setStatus("PAID");
        vo.setAmountFen(43000L);
        vo.setDiscountFen(3000L);
        vo.setReceivableFen(40000L);
        vo.setReceivedFen(40000L);
        vo.setItems(items);
        vo.setSummary(summary);
        return vo;
    }
}
