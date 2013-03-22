package ru.smply.tester.scripting;

public class ControllerConfiguration {
    public ControllerConfiguration() {}

    public ControllerConfiguration(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
    private String hostname;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
    private int port;
}
