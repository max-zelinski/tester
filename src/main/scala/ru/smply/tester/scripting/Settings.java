package ru.smply.tester.scripting;

public class Settings {

    public Settings() {}

    public Settings(String timeout, int expectedAgents, int workersPerAgent, String scriptPath, String scriptMethod,
                    String resultPath) {
        this.timeout = timeout;
        this.expectedAgents = expectedAgents;
        this.workersPerAgent = workersPerAgent;
        this.scriptPath = scriptPath;
        this.scriptMethod = scriptMethod;
        this.resultPath = resultPath;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }
    private String timeout;

    public int getExpectedAgents() {
        return expectedAgents;
    }

    public void setExpectedAgents(int expectedAgents) {
        this.expectedAgents = expectedAgents;
    }
    private int expectedAgents;

    public int getWorkersPerAgent() {
        return workersPerAgent;
    }

    public void setWorkersPerAgent(int workersPerAgent) {
        this.workersPerAgent = workersPerAgent;
    }
    private int workersPerAgent;

    public String getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }
    private String scriptPath;

    public String getScriptMethod() {
        return scriptMethod;
    }

    public void setScriptMethod(String scriptMethod) {
        this.scriptMethod = scriptMethod;
    }
    private String scriptMethod;

    public String getResultPath() {
        return resultPath;
    }

    public void setResultPath(String resultPath) {
        this.resultPath = resultPath;
    }
    private String resultPath;
}
