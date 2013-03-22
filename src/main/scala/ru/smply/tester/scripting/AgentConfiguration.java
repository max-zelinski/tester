package ru.smply.tester.scripting;

public class AgentConfiguration {
    public AgentConfiguration() {}

    public AgentConfiguration(String controller, String workerProcessJvmOptions) {
        this.controller = controller;
        this.workerProcessJvmOptions = workerProcessJvmOptions;
    }

    public String getController() {
        return controller;
    }

    public void setController(String controller) {
        this.controller = controller;
    }

    private String controller;

    public String getWorkerProcessJvmOptions() {
        return workerProcessJvmOptions;
    }

    public void setWorkerProcessJvmOptions(String workerProcessJvmOptions) {
        this.workerProcessJvmOptions = workerProcessJvmOptions;
    }
    private String workerProcessJvmOptions;
}
