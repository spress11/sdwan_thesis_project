package org.sd_wan.app;

import org.onlab.packet.*;
import org.onosproject.net.Link;

public class WanInterface {
    private String name;
    private MacAddress macAddress;
    private Ip4Address ip4Address;
    private int bandwidth;
    private boolean state;


    public WanInterface(String name, MacAddress mac, Ip4Address ip, int bandwidth) {
        this.name = name;
        this.macAddress = mac;
        this.ip4Address = ip;
        this.state = false;
        this.bandwidth = bandwidth;
    }

    public MacAddress getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(MacAddress macAddress) {
        this.macAddress = macAddress;
    }

    public Ip4Address getIp4Address() {
        return ip4Address;
    }

    public void setIp4Address(Ip4Address ip4Address) {
        this.ip4Address = ip4Address;
    }

    public int getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(int bandwidth) {
        this.bandwidth = bandwidth;
    }

    public boolean getState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
