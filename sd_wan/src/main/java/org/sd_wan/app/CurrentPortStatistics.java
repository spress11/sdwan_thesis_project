package org.sd_wan.app;

import org.onosproject.net.PortNumber;

public class CurrentPortStatistics {
    public long totalBytes;
    public int bitsPerSecond;
    public long packetsDropped;
    public long packetErrors;

    public CurrentPortStatistics() {
        this.totalBytes = 0;
        this.bitsPerSecond = 0;
        this.packetsDropped = 0;
        this.packetErrors = 0;
    }

    public String toString() {
        return "Bytes: " + totalBytes + ", bits/sec: " +
                bitsPerSecond + ", packetsDropped: " + packetsDropped +
                ", packetErrors: " + packetErrors;
    }
}

