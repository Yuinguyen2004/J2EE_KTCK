package com.billiard.tables;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PricingRuleControllerSecurityTest {

    @Test
    void listAndGetAllowStaffReadAccessWhileWritesStayAdminOnly() throws NoSuchMethodException {
        assertPreAuthorize("list", "hasAnyRole('ADMIN', 'STAFF')", String.class, String.class, String.class, Integer.class, Integer.class);
        assertPreAuthorize("get", "hasAnyRole('ADMIN', 'STAFF')", Long.class);
        assertPreAuthorize("create", "hasRole('ADMIN')", com.billiard.tables.dto.PricingRuleUpsertRequest.class);
        assertPreAuthorize("update", "hasRole('ADMIN')", Long.class, com.billiard.tables.dto.PricingRuleUpsertRequest.class);
        assertPreAuthorize("updateActive", "hasRole('ADMIN')", Long.class, com.billiard.shared.web.ToggleActiveRequest.class);
    }

    private static void assertPreAuthorize(String methodName, String expectedExpression, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = PricingRuleController.class.getDeclaredMethod(methodName, parameterTypes);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation)
                .as("Expected @PreAuthorize on %s", methodName)
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedExpression);
    }
}
