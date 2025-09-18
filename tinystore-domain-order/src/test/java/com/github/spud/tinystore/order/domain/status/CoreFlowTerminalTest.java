package com.github.spud.tinystore.order.domain.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CoreFlowTerminalTest {

    @Test
    void testTerminalStatesMatchEnumFlag(){
        for (CoreFlowStatus s : CoreFlowStatus.values()) {
            Assertions.assertEquals(s.isTerminal(), TerminalStateChecker.isTerminal(s), "终态判定不一致: "+s);
        }
    }
}

