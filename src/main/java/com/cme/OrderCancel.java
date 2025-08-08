package com.cme;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class OrderCancel {
    private int orderId;
    private boolean expired;
}
