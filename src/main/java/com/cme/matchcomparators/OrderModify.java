package com.cme.matchcomparators;

import com.cme.OrderType;
import com.cme.TimeInForce;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Builder
@Getter
public class OrderModify {
    private Integer orderId;
    private String clientOrderId;
    private Integer quantity;
    private OrderType orderType;
    private Long price;
    private TimeInForce timeInForce;
    private Long triggerPrice;
    private Integer minQuantity;
    private Integer displayQuantity;
    private LocalDate expiration;
}
