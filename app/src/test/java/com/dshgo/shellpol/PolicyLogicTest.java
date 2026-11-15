package com.dshgo.shellpol;

import org.junit.Test;
import static org.junit.Assert.*;

public class PolicyLogicTest {
    @Test public void threeModesAndInvalidMode() {
        assertTrue(PolicyLogic.shouldAsk(0, "id"));
        assertTrue(PolicyLogic.shouldAsk(0, "reboot"));
        assertFalse(PolicyLogic.shouldAsk(1, "id"));
        assertFalse(PolicyLogic.shouldAsk(1, "reboot"));
        assertFalse(PolicyLogic.shouldAsk(2, "id"));
        assertTrue(PolicyLogic.shouldAsk(2, "reboot"));
        assertTrue(PolicyLogic.shouldAsk(-1, "id"));
        assertTrue(PolicyLogic.shouldAsk(999, "id"));
    }
    @Test public void backendIdentityIsNotRelaxed() {
        assertTrue(PolicyLogic.validIdentity("adb", 2000));
        assertFalse(PolicyLogic.validIdentity("adb", 0));
        assertTrue(PolicyLogic.validIdentity("shizuku", 0));
        assertTrue(PolicyLogic.validIdentity("shizuku", 2000));
        assertFalse(PolicyLogic.validIdentity("shizuku", 12345));
        assertFalse(PolicyLogic.validIdentity("unknown", 0));
        assertFalse(PolicyLogic.validIdentity(null, -1));
    }
}
