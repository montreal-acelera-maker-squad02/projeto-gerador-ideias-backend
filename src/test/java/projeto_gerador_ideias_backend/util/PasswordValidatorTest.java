package projeto_gerador_ideias_backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordValidatorTest {
    
    @Test
    void shouldReturnTrueForValidPassword() {
        assertTrue(PasswordValidator.isValid("Senha@123"));
        assertTrue(PasswordValidator.isValid("MinhaSenha123!"));
        assertTrue(PasswordValidator.isValid("Test#2024"));
        assertTrue(PasswordValidator.isValid("P@ssw0rd"));
    }
    
    @Test
    void shouldReturnFalseForPasswordTooShort() {
        assertFalse(PasswordValidator.isValid("Senha@1")); // 7 caracteres
        assertFalse(PasswordValidator.isValid(null));
        assertFalse(PasswordValidator.isValid(""));
    }
    
    @Test
    void shouldReturnFalseWhenPasswordLacksUppercase() {
        assertFalse(PasswordValidator.isValid("senha@123"));
    }
    
    @Test
    void shouldReturnFalseWhenPasswordLacksLowercase() {
        assertFalse(PasswordValidator.isValid("SENHA@123"));
    }
    
    @Test
    void shouldReturnFalseWhenPasswordLacksDigit() {
        assertFalse(PasswordValidator.isValid("Senha@Boa"));
    }
    
    @Test
    void shouldReturnFalseWhenPasswordLacksSpecialChar() {
        assertFalse(PasswordValidator.isValid("Senha123"));
    }
    
    @Test
    void shouldReturnErrorMessage() {
        String requirements = PasswordValidator.getPasswordRequirements();
        assertNotNull(requirements);
        assertTrue(requirements.contains("8 caracteres"));
        assertTrue(requirements.contains("maiúscula"));
        assertTrue(requirements.contains("minúscula"));
        assertTrue(requirements.contains("dígito"));
        assertTrue(requirements.contains("especial"));
    }
}

