package org.sd_wan.app;

import org.onlab.packet.*;
import org.onosproject.net.Link;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.FlowRule;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class WanGateway {
    private NatHandler natHandler;
    private Map<PortNumber, WanInterface> interfaceMap;
    private int totalUnusedCapacity;
    private Map<PortNumber, Integer> interfaceWeights;
    private Map<PortNumber, CurrentPortStatistics> interfaceStatistics;
    private Map<Ip4Address, MacAddress> interfaceMacAddresses;


    public WanGateway(NatHandler natHandler) {
        this.natHandler = natHandler;
        this.interfaceMap = new TreeMap<>(new Comparator<PortNumber>() {
            @Override
            public int compare(PortNumber o1, PortNumber o2) {
                return o1.toString().compareTo(o2.toString());
            }
        });

        this.interfaceWeights = new TreeMap<>(new Comparator<PortNumber>() {
            @Override
            public int compare(PortNumber o1, PortNumber o2) {
                return o1.toString().compareTo(o2.toString());
            }
        });

        this.interfaceStatistics = new TreeMap<>(new Comparator<PortNumber>() {
            @Override
            public int compare(PortNumber o1, PortNumber o2) {
                return o1.toString().compareTo(o2.toString());
            }
        });

        this.interfaceMacAddresses = new TreeMap<>(new Comparator<Ip4Address>() {
            @Override
            public int compare(Ip4Address o1, Ip4Address o2) {
                return o1.toString().compareTo(o2.toString());
            }
        });
        this.totalUnusedCapacity = 0;
    }

    public WanInterface getInterface(PortNumber port) {
        return interfaceMap.get(port);
    }

    public Map<PortNumber, WanInterface> getInterfaces() {
        return interfaceMap;
    }

    public WanInterface removeInterface(PortNumber port) {
        WanInterface intf = interfaceMap.remove(port);
        if (intf != null) {
            interfaceMacAddresses.remove(intf.getIp4Address());
            totalUnusedCapacity -= interfaceWeights.get(port);
            interfaceWeights.remove(port);
            interfaceStatistics.remove(port);
            natHandler.removeInterfaceTable(port);
            return intf;
        } else {
            return null;
        }
    }

    public void addInterface(PortNumber port, WanInterface wanInterface) {
        interfaceMap.put(port, wanInterface);
        interfaceWeights.put(port, 0);
        interfaceStatistics.put(port, new CurrentPortStatistics());
        natHandler.addInterfaceTable(port, wanInterface);
    }

    public FlowRule[] makeNatRules (PortNumber ingressPortNumber, Ethernet pkt, byte ipProtocol, PortNumber interfacePort) {
        return natHandler.makeNATRules(ingressPortNumber, pkt, ipProtocol, interfacePort);
    }

    public Map<PortNumber, Integer> getInterfaceWeights() {
        return interfaceWeights;
    }

    public int getTotalUnusedCapacity() {
        return totalUnusedCapacity;
    }

    public IPv4 handleICMPOut(IPv4 ipPacketIn, PortNumber outgoingPortNumber) {
        return natHandler.handleICMPOut(ipPacketIn.getDestinationAddress(),
                interfaceMap.get(outgoingPortNumber).getIp4Address().toInt(), ipPacketIn,
                outgoingPortNumber, ipPacketIn.getSourceAddress());
    }

    public IPv4 handleICMPIn(IPv4 ipPacketIn, PortNumber ingressPortNumber) {
        return natHandler.handleICMPIn(ipPacketIn.getSourceAddress(), ipPacketIn, ingressPortNumber);
    }

    public void removeRule(FlowRule flowRule) {
        natHandler.removeRule(flowRule);
    }

    public void updateInterfaceStatistics(PortNumber port, CurrentPortStatistics stats) {
        totalUnusedCapacity -= interfaceWeights.get(port);

        interfaceStatistics.replace(port, stats);
        Integer newWeight = interfaceMap.get(port).getBandwidth() * 1000000 - stats.bitsPerSecond;
        if (newWeight < 0) {
            newWeight = 0;
        }
        interfaceWeights.replace(port,  newWeight);
        totalUnusedCapacity += newWeight;
    }

    public Map<PortNumber, CurrentPortStatistics> getInterfaceStatistics() {
        return interfaceStatistics;
    }

    public void updateInterfaceState(PortNumber port, boolean state) {

        WanInterface intf = interfaceMap.get(port);
        intf.setState(state);
        interfaceMap.replace(port, intf);
    }

    public Map<Ip4Address, MacAddress> getInterfaceMacAddresses() {
        return interfaceMacAddresses;
    }
}
