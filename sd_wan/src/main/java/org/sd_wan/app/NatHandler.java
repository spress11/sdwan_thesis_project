package org.sd_wan.app;


import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.*;

public class NatHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    final private DeviceId gateway;

    //List of interfaces
    private Map<PortNumber, InterfaceTable> Interfaces;

    private ApplicationId appId;

    public NatHandler(ApplicationId appId, DeviceId mainSwitch) {
        this.appId = appId;
        this.gateway = mainSwitch;
        Interfaces = new TreeMap<>(new Comparator<PortNumber>() {
            @Override
            public int compare(PortNumber o1, PortNumber o2) {
                return o1.toString().compareTo(o2.toString());
            }
        });
    }

    /**
     * Not yet implemented method for removing expired IP-TpPort combinations from the list, so that the port becomes
     * available again
     * @param flowRule The flowrule to remove
     */
    public void removeRule(FlowRule flowRule) {
        for (PortNumber port : Interfaces.keySet()) {
            Interfaces.get(port).removeFlowEntry(flowRule.id());
        }
    }

    /**
     * Makes the NAT rules to translate from host to edge router and back
     *
     * @param srcPortNumber The physical PortNumber of the source host
     * @param protocol      The protocol of the connection
     * @param outgoingPortNumber   The physical PortNumber of the interface
     * @return An array of two flow rules to apply, 0th is for edge to host (incoming) NAT,
     *          1st is for host to edge(outgoing) NAT
     */
    public FlowRule[] makeNATRules(PortNumber srcPortNumber, Ethernet packet, byte protocol, PortNumber outgoingPortNumber) {

        MacAddress srcMacAddr = MacAddress.valueOf(packet.getSourceMACAddress());

        IPv4 ipPacket = (IPv4)packet.getPayload();
        IpAddress srcIP = IpAddress.valueOf(ipPacket.getSourceAddress());
        IpAddress destIP = IpAddress.valueOf(ipPacket.getDestinationAddress());
        int outTpPort;

        int srcPort = 0;
        int destPort = 0;
        if (protocol == IPv4.PROTOCOL_TCP) {
            TCP tcpPacket = (TCP) ipPacket.getPayload();
            srcPort = tcpPacket.getSourcePort();
            destPort = tcpPacket.getDestinationPort();
            outTpPort = Interfaces.get(outgoingPortNumber).getNextTCPPort();
        } else if (protocol == IPv4.PROTOCOL_UDP) {
            UDP udpPacket = (UDP) ipPacket.getPayload();
            srcPort = udpPacket.getSourcePort();
            destPort = udpPacket.getDestinationPort();
            outTpPort = Interfaces.get(outgoingPortNumber).getNextUDPPort();
        } else {
            return null;
        }

        //Satisfies NAT endpoint-independant mapping requirement
        for (PortNumber portNumber : Interfaces.keySet()) {
            NatSessionTuple tuple;
            tuple = Interfaces.get(portNumber).hasExistingNatSessionSource(srcIP, srcPort);
            if (tuple != null) {
                outgoingPortNumber = portNumber;
                outTpPort = tuple.translatedSrcSessionIdentifier;
            }
        }

        InterfaceTable outInterface = Interfaces.get(outgoingPortNumber);
        Ip4Address interfaceIPAddr = outInterface.getIpAddress().getIp4Address();
        MacAddress interfaceMacAddr = outInterface.getMacAddress();

        //Selector and treatment for translating outgoing traffic from the host
        TrafficSelector.Builder outSelector = DefaultTrafficSelector.builder();
        TrafficTreatment.Builder outTreatment = DefaultTrafficTreatment.builder();

        //Selector and treatment for translating incoming traffic from the Destination
        TrafficSelector.Builder inSelector = DefaultTrafficSelector.builder();
        TrafficTreatment.Builder inTreatment = DefaultTrafficTreatment.builder();

        outSelector.matchEthType(Ethernet.TYPE_IPV4);
        inSelector.matchEthType(Ethernet.TYPE_IPV4);

        if (protocol == IPv4.PROTOCOL_TCP) {
            outSelector.matchIPProtocol(IPv4.PROTOCOL_TCP)
                    .matchTcpSrc(TpPort.tpPort(srcPort))
                    .matchIPSrc(IpPrefix.valueOf(srcIP, 32))
                    .matchTcpDst(TpPort.tpPort(destPort))
                    .matchIPDst(IpPrefix.valueOf(destIP, 32));

            outTreatment.setIpSrc(interfaceIPAddr)
                    .setEthSrc(interfaceMacAddr)
                    .setTcpSrc(TpPort.tpPort(outTpPort))
                    .setOutput(outgoingPortNumber);


            inSelector.matchIPProtocol(IPv4.PROTOCOL_TCP)
                    .matchTcpDst(TpPort.tpPort(outTpPort))
                    .matchIPDst(IpPrefix.valueOf(interfaceIPAddr, 32))
                    .matchTcpSrc(TpPort.tpPort(destPort))
                    .matchIPSrc(IpPrefix.valueOf(destIP, 32));

            inTreatment.setIpDst(srcIP)
                    .setEthDst(srcMacAddr)
                    .setTcpDst(TpPort.tpPort(srcPort))
                    .setOutput(srcPortNumber);

            TCP tcpPacket = (TCP) ipPacket.getPayload();
            tcpPacket.setSourcePort(outTpPort);

            ipPacket.setSourceAddress(interfaceIPAddr.toInt());
            ipPacket.setPayload(tcpPacket);
            packet.setPayload(ipPacket);
            packet.setSourceMACAddress(interfaceMacAddr);

        } else {
            outSelector.matchIPProtocol(IPv4.PROTOCOL_UDP)
                    .matchUdpSrc(TpPort.tpPort(srcPort))
                    .matchIPSrc(IpPrefix.valueOf(srcIP, 32))
                    .matchUdpDst(TpPort.tpPort(destPort))
                    .matchIPDst(IpPrefix.valueOf(destIP, 32));

            outTreatment.setIpSrc(interfaceIPAddr)
                    .setEthSrc(interfaceMacAddr)
                    .setUdpSrc(TpPort.tpPort(outTpPort))
                    .setOutput(outgoingPortNumber);

            inSelector.matchIPProtocol(IPv4.PROTOCOL_UDP)
                    .matchUdpDst(TpPort.tpPort(outTpPort))
                    .matchIPDst(IpPrefix.valueOf(interfaceIPAddr, 32))
                    .matchUdpSrc(TpPort.tpPort(destPort))
                    .matchIPSrc(IpPrefix.valueOf(destIP, 32));

            inTreatment.setIpDst(srcIP)
                    .setEthDst(srcMacAddr)
                    .setUdpDst(TpPort.tpPort(srcPort))
                    .setOutput(srcPortNumber);

            UDP udpPacket = (UDP) ipPacket.getPayload();
            udpPacket.setSourcePort(outTpPort);

            ipPacket.setSourceAddress(interfaceIPAddr.toInt());
            ipPacket.setPayload(udpPacket);
            packet.setPayload(ipPacket);
            packet.setSourceMACAddress(interfaceMacAddr);

        }

        FlowRule inFlowRule = DefaultFlowRule.builder()
                .forDevice(gateway)
                .fromApp(appId)
                .withSelector(inSelector.build())
                .withTreatment(inTreatment.build())
                //10 Sec?
                .withIdleTimeout(20)
                .withPriority(50000)
                .build();

        FlowRule outFlowRule = DefaultFlowRule.builder()
                .forDevice(gateway)
                .fromApp(appId)
                .withSelector(outSelector.build())
                .withTreatment(outTreatment.build())
                //10 Sec?
                .withIdleTimeout(20)
                .withPriority(50000)
                .build();

        InterfaceTable interfaceTable = Interfaces.get(outgoingPortNumber);


        NatSessionTuple sessionTuple = new NatSessionTuple(srcIP, interfaceIPAddr, destIP, srcPort, outTpPort, destPort, protocol);

        interfaceTable.addFlowEntry(sessionTuple, inFlowRule, outFlowRule, protocol);

        FlowRule[] result = {inFlowRule, outFlowRule};
        return result;


    }

    public IPv4 handleICMPOut(int destIp, int srcIp, IPv4 ipPktIn, PortNumber outgoingPortNumber, int hostIP) {

        ICMP icmpPktIn = (ICMP)ipPktIn.getPayload();
        if (icmpPktIn.getIcmpType() == 3 || icmpPktIn.getIcmpType() == 12) {
             log.info("ICMP Packet: " + icmpPktIn.toString() +"\nICMP encapsulated packet: " + icmpPktIn.getPayload().toString());

        }
            ICMPEcho echo = (ICMPEcho)icmpPktIn.getPayload();

            int queryID = Interfaces.get(outgoingPortNumber).getNextQueryId();
            int oldQueryId = echo.getIdentifier();

            Interfaces.get(outgoingPortNumber).addPingEntry(new NatSessionTuple(
                    IpAddress.valueOf(hostIP),
                    IpAddress.valueOf(srcIp),
                    IpAddress.valueOf(destIp),
                    oldQueryId,
                    queryID,
                    -1,
                    IPv4.PROTOCOL_ICMP));

            //log.info("Added ping entry to interface table, IP: " + hostIP + ", oldID: " + oldQueryId + ", newID: " + queryID);
            echo.setIdentifier((short)queryID);

            ipPktIn.setDestinationAddress(destIp);
            ipPktIn.setSourceAddress(srcIp);
            icmpPktIn.setPayload(echo);
            ipPktIn.setPayload(icmpPktIn);

            return ipPktIn;

    }

    public IPv4 handleICMPIn(int srcIPAddress, IPv4 ipPktIn, PortNumber ingressPort) {
        ICMP icmpPktIn = (ICMP)ipPktIn.getPayload();
        ICMPEcho echo = (ICMPEcho)icmpPktIn.getPayload();

        int queryID = (int)echo.getIdentifier();

        IpAddress hostIP = Interfaces.get(ingressPort).getHostIP(IpAddress.valueOf(srcIPAddress), queryID, IPv4.PROTOCOL_ICMP);
        int oldQueryID = Interfaces.get(ingressPort).getQueryID(IpAddress.valueOf(srcIPAddress), queryID, IPv4.PROTOCOL_ICMP);

        echo.setIdentifier((short)oldQueryID);

        ipPktIn.setDestinationAddress(hostIP.getIp4Address().toInt());
        ipPktIn.setSourceAddress(srcIPAddress);
        icmpPktIn.setPayload(echo);
        ipPktIn.setPayload(icmpPktIn);

        return ipPktIn;
    }

    /**
     *  method used for adding interfaces to the NatHandler
     * @param intf
     */
    public void addInterfaceTable(PortNumber port, WanInterface intf) {
        Interfaces.put(port, new InterfaceTable(intf));
    }

    public void removeInterfaceTable(PortNumber port) {
        Interfaces.remove(port);
    }

    public int getNumberFlows(PortNumber portNumber) {
        return Interfaces.get(portNumber).flowRules.size();
    }

    /**
     * Class used for storing information about an interface
     */
    private class InterfaceTable {

        final private WanInterface intf;

        // Maps the host's IP Address/TpPort tuple to a FlowRule for outgoing NAT
        Map<FlowId, NatSessionTuple> flowRules;

        //List of assigned TpPorts
        private List<TpPort> AssignedTCPPorts;
        private List<TpPort> AssignedUDPPorts;
        private List<NatSessionTuple> AssignedQueryIDs;

        private int nextQueryId;
        private int nextUDPPort;
        private int nextTCPPort;

        public InterfaceTable(WanInterface intf) {
            this.intf = intf;

            AssignedTCPPorts = new ArrayList<>();
            AssignedUDPPorts = new ArrayList<>();
            AssignedQueryIDs = new ArrayList<>();

            nextQueryId = 1;
            nextUDPPort = 1;
            nextTCPPort = 1;

            flowRules = new HashMap<>();
        }

        public NatSessionTuple hasExistingNatSessionSource(IpAddress srcAddr, int port) {
            for (NatSessionTuple session : flowRules.values()) {
                if (session.hostSessionIdentifier == port && session.hostIpAddr == srcAddr) {
                    return session;
                }
            }
            return null;
        }

        public IpAddress getHostIP (IpAddress endpointIP, int queryID, byte protocol) {
            for (NatSessionTuple session : AssignedQueryIDs) {
                if (session.destIpAddr.equals(endpointIP) && session.destSessionIdentifier == queryID &&
                        session.protocol == protocol) {
                    return session.hostIpAddr;
                }
            }
            return null;
        }

        public int getQueryID (IpAddress endpointIP, int queryID, byte protocol) {
            for (NatSessionTuple session : AssignedQueryIDs) {
                if (session.destIpAddr.equals(endpointIP) && session.destSessionIdentifier == queryID &&
                        session.protocol == protocol) {
                    return session.hostSessionIdentifier;
                }
            }
            return 0;
        }

        public void addFlowEntry(NatSessionTuple tuple, FlowRule outgoingFlowRule, FlowRule incomingFlowRule, byte protocol) {
            flowRules.put(outgoingFlowRule.id(), tuple);
            if (protocol == IPv4.PROTOCOL_TCP) {
                AssignedTCPPorts.add(TpPort.tpPort(tuple.translatedSrcSessionIdentifier));
            } else if (protocol == IPv4.PROTOCOL_UDP) {
                AssignedUDPPorts.add(TpPort.tpPort(tuple.translatedSrcSessionIdentifier));
            }
        }

        public void addPingEntry(NatSessionTuple sessionTuple) {
            AssignedQueryIDs.add(sessionTuple);
            //TODO: Schedule timer to remove session in 60 sec
        }

        public void removePingEntry(NatSessionTuple sessionTuple) {
            AssignedQueryIDs.remove(sessionTuple);
        }

        public void removeFlowEntry(FlowId flowId) {
            if (flowRules.get(flowId) != null) {
                NatSessionTuple session = flowRules.get(flowId);
                int sessionId = session.destSessionIdentifier;
                if (session.protocol == IPv4.PROTOCOL_TCP) {
                    AssignedTCPPorts.remove(TpPort.tpPort(sessionId));
                } else if (session.protocol == IPv4.PROTOCOL_UDP) {
                    AssignedUDPPorts.remove(TpPort.tpPort(sessionId));
                } else if (session.protocol == IPv4.PROTOCOL_ICMP) {
                    AssignedQueryIDs.remove(flowRules.get(flowId));
                }
                flowRules.remove(flowId);

            }
        }

        public int getNextTCPPort() {
            //Copy the nextPort to be assigned
            int returnPort = nextTCPPort;

            //Find the new nextPort value
            boolean nextPortFound = false;
            while (!nextPortFound) {
                if (!AssignedTCPPorts.contains(TpPort.tpPort(++nextTCPPort))) {
                    nextPortFound = true;
                }
            }

            //Return previous nextPort value
            return returnPort;
        }

        public int getNextUDPPort() {
            //Copy the nextPort to be assigned
            int returnPort = nextUDPPort;

            //Find the new nextPort value
            boolean nextPortFound = false;
            while (!nextPortFound) {
                if (!AssignedUDPPorts.contains(TpPort.tpPort(++nextUDPPort))) {
                    nextPortFound = true;
                }
            }

            //Return previous nextPort value
            return returnPort;
        }

        public int getNextQueryId() {
            //Copy the nextPort to be assigned
            int returnId = nextQueryId;

            //Find the new nextPort value
            boolean nextIdFound = false;
            while (!nextIdFound) {
                if (!AssignedQueryIDs.contains(++nextQueryId)) {
                    nextIdFound = true;
                }
            }

            //Return previous nextQueryId value
            return returnId;
        }

        public IpAddress getIpAddress() {
            return intf.getIp4Address();
        }

        public MacAddress getMacAddress() {
            return intf.getMacAddress();
        }
    }

    /**
     * Class used for storing a combination of IP Address and TpPort to keep track of NAT connections
     */
    private class NatSessionTuple {
        IpAddress hostIpAddr;
        IpAddress translatedSrcIpAddr;
        IpAddress destIpAddr;
        int hostSessionIdentifier;
        int translatedSrcSessionIdentifier;
        int destSessionIdentifier;
        byte protocol;

        /*@Override
        public String toString() {
            return "NatSessionTuple{" +
                    "hostIpAddr=" + hostIpAddr +
                    ", destIpAddr=" + destIpAddr +
                    ", srcSessionIdentifier=" + hostSessionIdentifier +
                    ", destSessionIdentifier=" + destSessionIdentifier +
                    ", protocol=" + protocol +
                    '}';
        }*/

        public NatSessionTuple(IpAddress hostIpAddr, IpAddress translatedSrcIpAddr, IpAddress destIpAddr,
                               int hostSessionIdentifier, int translatedSrcSessionIdentifier,
                               int destSessionIdentifier, byte protocol) {
            this.hostIpAddr = hostIpAddr;
            this.translatedSrcIpAddr = translatedSrcIpAddr;
            this.destIpAddr = destIpAddr;
            this.hostSessionIdentifier = hostSessionIdentifier;
            this.translatedSrcSessionIdentifier = translatedSrcSessionIdentifier;
            this.destSessionIdentifier = destSessionIdentifier;
            this.protocol = protocol;
        }

        @Override
        public int hashCode() {
            int result = hostIpAddr.hashCode() + destIpAddr.hashCode() + translatedSrcIpAddr.hashCode();
            result = 31 * result + hostSessionIdentifier + destSessionIdentifier +
                    translatedSrcSessionIdentifier + protocol;
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            NatSessionTuple that = (NatSessionTuple) o;

            if (hostSessionIdentifier != that.hostSessionIdentifier) return false;
            if (translatedSrcSessionIdentifier != that.translatedSrcSessionIdentifier) return false;
            if (destSessionIdentifier != that.destSessionIdentifier) return false;
            if (protocol != that.protocol) return false;
            if (!hostIpAddr.equals(that.hostIpAddr)) return false;
            if (!translatedSrcIpAddr.equals(that.translatedSrcIpAddr)) return false;
            return destIpAddr.equals(that.destIpAddr);
        }
    }
}


