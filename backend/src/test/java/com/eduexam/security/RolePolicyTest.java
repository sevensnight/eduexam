package com.eduexam.security;

import com.eduexam.domain.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RolePolicyTest {

    @Test
    void loginPrincipalRejectsUnknownPersistedRole() {
        User valid = new User();
        valid.setRole("student");
        assertDoesNotThrow(() -> new LoginUser(valid));

        User invalid = new User();
        invalid.setRole("superuser");
        assertThrows(IllegalArgumentException.class, () -> new LoginUser(invalid));
    }
}
