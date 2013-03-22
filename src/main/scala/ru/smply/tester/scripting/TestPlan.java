package ru.smply.tester.scripting;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TestPlan {
    public TestPlan() {}

    public TestPlan(Settings settings, Map<String, String> parameters) {
        this.settings = settings;
        this.parameters = wrapParametersMap(parameters);
    }

    public Settings getSettings() {
        return settings;
    }

    private Settings settings = new Settings();

    public Map<String, String> getParameters() {
        return parameters;
    }
    private Map<String, String> parameters = wrapParametersMap(new HashMap<String, String>());

    private Map<String, String> wrapParametersMap(Map<String, String> parameters) {
        return Collections.checkedMap(parameters, String.class, String.class);
    }
}
