package projeto_gerador_ideias_backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.stream.Stream;

public enum Theme {
    TECNOLOGIA("tecnologia"),
    ESTUDOS("estudos"),
    VIAGEM("viagem"),
    TRABALHO("trabalho"),
    DIA_A_DIA("dia a dia");

    private final String value;

    Theme(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Theme fromValue(String value) {
        if (value == null) return null;

        return Stream.of(Theme.values())
                .filter(c -> c.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tema inválido: " + value));
    }
}