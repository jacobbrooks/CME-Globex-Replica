package com.cme;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Builder
@Getter
public class Security {
    private final int id;
    private final int topMin;
    private final int topMax;
    private final int proRataMin;
    private final int splitPercentage;
    private final int protectionPoints;
    private final MatchingAlgorithm matchingAlgorithm;
    private final LocalDate expiration;

}
