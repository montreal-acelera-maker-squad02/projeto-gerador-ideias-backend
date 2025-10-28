package projeto_gerador_ideias_backend.util;

import java.util.regex.Pattern;

public class PasswordValidator {
    
    private static final Pattern HAS_UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern HAS_LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");
    private static final Pattern HAS_SPECIAL_CHAR = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?]");
    
    public static boolean isValid(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        
        return HAS_UPPERCASE.matcher(password).find()
                && HAS_LOWERCASE.matcher(password).find()
                && HAS_DIGIT.matcher(password).find()
                && HAS_SPECIAL_CHAR.matcher(password).find();
    }
    
    public static String getPasswordRequirements() {
        return "A senha deve ter no mínimo 8 caracteres e conter: pelo menos uma letra maiúscula, " +
               "uma letra minúscula, um dígito e um caractere especial (!@#$%^&*()_+-=[]{}|;':\",./<>?)";
    }
}

