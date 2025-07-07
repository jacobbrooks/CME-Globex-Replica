package com.cme;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

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

}
