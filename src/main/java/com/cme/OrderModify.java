package com.cme;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

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
    private Boolean inFlightMitigation;

    @Setter
    private Integer restingQuantity;
}
