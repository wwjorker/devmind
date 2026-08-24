package com.devmind.module.ai.agent;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public record AgentToolChoice(Mode mode, String functionName) {

    public AgentToolChoice {
        if (mode == null) {
            throw new IllegalArgumentException("tool choice mode must not be null");
        }
        if (mode == Mode.FUNCTION && !StringUtils.hasText(functionName)) {
            throw new IllegalArgumentException("named tool choice requires a function name");
        }
        if (mode != Mode.FUNCTION && functionName != null) {
            throw new IllegalArgumentException("function name is only valid for named tool choice");
        }
    }

    public static AgentToolChoice auto() {
        return new AgentToolChoice(Mode.AUTO, null);
    }

    public static AgentToolChoice none() {
        return new AgentToolChoice(Mode.NONE, null);
    }

    public static AgentToolChoice required() {
        return new AgentToolChoice(Mode.REQUIRED, null);
    }

    public static AgentToolChoice function(String functionName) {
        return new AgentToolChoice(Mode.FUNCTION, functionName);
    }

    public boolean isNone() {
        return mode == Mode.NONE;
    }

    Object toWireValue() {
        if (mode != Mode.FUNCTION) {
            return mode.wireValue;
        }
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", functionName);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("type", "function");
        choice.put("function", function);
        return choice;
    }

    public enum Mode {
        AUTO("auto"),
        NONE("none"),
        REQUIRED("required"),
        FUNCTION("function");

        private final String wireValue;

        Mode(String wireValue) {
            this.wireValue = wireValue;
        }
    }
}
