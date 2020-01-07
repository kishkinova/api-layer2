/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package com.ca.apiml.security.common.service;

import com.ca.mfaas.util.ClassOrDefaultProxyUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@ContextConfiguration
public class PassTicketServiceTest {

    @Autowired
    private PassTicketService passTicketService;

    private static String evaluated;

    @Test
    @Order(1)
    public void testInit() {
        assertNotNull(passTicketService);
        assertNotNull(ReflectionTestUtils.getField(passTicketService, "irrPassTicket"));
        ReflectionTestUtils.setField(passTicketService, "irrPassTicket", new IRRPassTicket() {
            @Override
            public void evaluate(String userId, String applId, String passTicket) {
                evaluated = userId + "-" + applId + "-" + passTicket;
            }

            @Override
            public String generate(String userId, String applId) {
                return userId + "-" + applId;
            }
        });
    }

    @Test
    @Order(2)
    public void testCalledMethod() throws IRRPassTicketEvaluationException, IRRPassTicketGenerationException {
        evaluated = null;
        passTicketService.evaluate("userId", "applId", "passTicket");
        assertEquals("userId-applId-passTicket", evaluated);
        passTicketService.evaluate("1", "2", "3");
        assertEquals("1-2-3", evaluated);

        assertEquals("userId-applId", passTicketService.generate("userId", "applId"));
        assertEquals("1-2", passTicketService.generate("1", "2"));
    }

    @Test
    public void testProxy() throws IRRPassTicketGenerationException {
        IRRPassTicket irrPassTicket = ClassOrDefaultProxyUtils.createProxy(
            IRRPassTicket.class,
            "notExistingClass",
            Impl::new
        );

        try {
            irrPassTicket.evaluate("user", "applId", "passTicket");
            fail();
        } catch (Exception e) {
            assertEquals("Dummy implementation of evaluate : user x applId x passTicket", e.getMessage());
        }

        assertEquals("success", irrPassTicket.generate("user", "applId"));
    }

    @Test
    public void testDefaultPassTicketImpl() throws IRRPassTicketEvaluationException, IRRPassTicketGenerationException {
        PassTicketService.DefaultPassTicketImpl dpti = new PassTicketService.DefaultPassTicketImpl();

        try {
            dpti.evaluate("user", "applId", "passticket");
            fail();
        } catch (IRRPassTicketEvaluationException e) {
            assertEquals(8, e.getSafRc());
            assertEquals(16, e.getRacfRsn());
            assertEquals(32, e.getRacfRc());
        }

        String passTicket1 = dpti.generate("user", "applId");
        String passTicket2 = dpti.generate("user", "applId");

        assertNotNull(passTicket1);
        assertNotNull(passTicket2);
        assertNotEquals(passTicket1, passTicket2);

        dpti.evaluate("user", "applId", passTicket1);
        dpti.evaluate("user", "applId", passTicket2);

        try {
            dpti.evaluate("userx", "applId", passTicket1);
            fail();
        } catch (IRRPassTicketEvaluationException e) {
            // different user, should throw exception
        }

        try {
            dpti.evaluate("user", "applIdx", passTicket1);
            fail();
        } catch (IRRPassTicketEvaluationException e) {
            // different applId, should throw exception
        }

        try {
            dpti.generate(PassTicketService.DefaultPassTicketImpl.UNKWNOWN_USER, "anyApplId");
            fail();
        } catch (IRRPassTicketGenerationException e) {
            assertEquals(8, e.getSafRc());
            assertEquals(8, e.getRacfRsn());
            assertEquals(16, e.getRacfRc());
            assertNotNull(e.getErrorCodes());
            assertEquals(1, e.getErrorCodes().size());
            assertEquals(AbstractIRRPassTicketException.ErrorCode.ERR_8_8_16, e.getErrorCodes().get(0));
            assertEquals(
                "Error on generation of PassTicket\n" +
                    "\tNot authorized to use this service.\n"
                , e.getMessage()
            );
        }

        try {
            dpti.generate("anyUser", PassTicketService.DefaultPassTicketImpl.UNKWNOWN_APPLID);
            fail();
        } catch (IRRPassTicketGenerationException e) {
            assertEquals(8, e.getSafRc());
            assertEquals(16, e.getRacfRsn());
            assertEquals(28, e.getRacfRc());
            assertNotNull(e.getErrorCodes());
            assertEquals(3, e.getErrorCodes().size());
            assertEquals(AbstractIRRPassTicketException.ErrorCode.ERR_8_16_X_1, e.getErrorCodes().get(0));
            assertEquals(AbstractIRRPassTicketException.ErrorCode.ERR_8_16_28, e.getErrorCodes().get(1));
            assertEquals(AbstractIRRPassTicketException.ErrorCode.ERR_8_16_X_2, e.getErrorCodes().get(2));
        }
    }

    public static class Impl implements IRRPassTicket {
        @Override
        public void evaluate(String userId, String applId, String passTicket) {
            throw new RuntimeException("Dummy implementation of evaluate : " + userId + " x " + applId + " x " + passTicket);
        }

        @Override
        public String generate(String userId, String applId) {
            return "success";
        }
    }

    @Configuration
    @ComponentScan(basePackageClasses = {PassTicketService.class})
    public static class SpringConfig {

    }

}
