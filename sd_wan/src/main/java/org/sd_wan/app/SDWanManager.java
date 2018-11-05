/*
 * Copyright 2017-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sd_wan.app;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;

import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.flow.*;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.net.host.InterfaceIpAddress;
import org.sd_wan.apps.SDWANService;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkListener;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.packet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.Null;
import java.nio.ByteBuffer;
import java.util.*;

@Component(immediate = true)
@Service
public class SDWanManager implements SDWANService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private FlowRuleService flowRuleService;

    //  PacketProcessor pktprocess; // To process incoming packets and use methods
    //  of PacketProcess such as addProcessor and removeProcessor
    private PacketProcessor pktprocessor = new WanProcessor();

    final private IpPrefix localIP = IpPrefix.valueOf("10.0.0.0/24");

    private Map<DeviceId, WanGateway> gateways = new TreeMap<>(new Comparator<DeviceId>() {
        @Override
        public int compare(DeviceId o1, DeviceId o2) {
            return o1.toString().compareTo(o2.toString());
        }
    });

    private Map<Ip4Address, MacAddress> WanInterfaceArpAddresses = new TreeMap<>(new Comparator<Ip4Address>() {
        @Override
        public int compare(Ip4Address o1, Ip4Address o2) {
            return o1.toString().compareTo(o2.toString());
        }
    });

    //Nat
    private NatFlowListener natFlowListener = new NatFlowListener();
    private NetworkDeviceListener deviceListener = new NetworkDeviceListener();
    private NetworkHostListener hostListener = new NetworkHostListener();

    private ApplicationId appId;

    //List of hosts and host details gathered through HostService
    private HashMap<HostId, Host> hosts = new HashMap<>();

    /**
     * Initializes the application, as well as stores hard-coded IP, MAC and Pyhsical PortNumbers in lists used for processing.
     * Also initializes flow rules in the combiner switch to forward all outgoing traffic to a single link,
     * as well as split incoming traffic based on their original interface.
     */
    @Activate
    protected void activate() {
        //Regsiter the app with ONOS Core service
        appId = coreService.registerApplication("org.sd_wan.app");

        packetService.addProcessor(pktprocessor, PacketProcessor.director(50000));

        //Register Hosts discovered by Host Service
        for (Host h : hostService.getHosts()) {
            hosts.put(h.id(), h);
        }

        //Add network listeners to respective services
        flowRuleService.addListener(natFlowListener);
        deviceService.addListener(deviceListener);
        hostService.addListener(hostListener);

        log.info("Started");

    }

    @Deactivate
    protected void deactivate() {

        packetService.removeProcessor(pktprocessor);
        flowRuleService.removeFlowRulesById(appId);
        flowRuleService.removeListener(natFlowListener);
        hostService.removeListener(hostListener);
        deviceService.removeListener(deviceListener);
        log.info("SD Wan App Stopped");
    }

    @Override
    public Map<DeviceId, WanGateway> getGateways() {
        return gateways;
    }

    @Override
    public void addGateway(DeviceId deviceId) {
        gateways.put(deviceId, new WanGateway(new NatHandler(appId, deviceId)));

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.CONTROL, appId, Optional.of(deviceId));

        log.info("New gateway added: " + deviceId.toString());
    }

    @Override
    public void addInterface(DeviceId deviceId, PortNumber port, WanInterface intf) {

        WanGateway gateway = gateways.get(deviceId);

        Port intfPort = deviceService.getPort(deviceId, port);
        if (intfPort != null) {
            gateway.addInterface(port, intf);
            gateway.updateInterfaceState(port, intfPort.isEnabled());
            gateways.replace(deviceId, gateway);
            log.info("Added interface: " + intf.getName() + " to gateway: " + deviceId.toString());
            WanInterfaceArpAddresses.put(intf.getIp4Address(), intf.getMacAddress());
            return;
        }

        //If we get to here, the link does not exist, so alert user of failure
        log.info("Unable to add interface: " + intf.getName() + " to gateway: " + deviceId.toString());
    }

    @Override
    public WanGateway getGateway(DeviceId deviceId) {
        return gateways.get(deviceId);
    }

    @Override
    public void removeInterface(DeviceId deviceId, PortNumber port, String name) {
        WanGateway gateway = gateways.get(deviceId);
        WanInterface intf = gateway.removeInterface(port);
        if (intf != null) {
            WanInterfaceArpAddresses.remove(intf.getIp4Address());
            log.info("Removing interface: " + name + " from gateway: " + deviceId.toString());
        } else {
            log.info("Interface: " + name + " could not be found on gateway: " + deviceId.toString());
        }
        gateways.replace(deviceId, gateway);
    }

    /**
     * WanProcessor is the main part of the SD-WAN application.
     * Processes NAT and Load Balancing for icmp, tcp and udp traffic.
     * Also manages proxy ARP for interfaces from the gateway switch.
     */
    private class WanProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext pktIn) {

            DeviceId deviceId = pktIn.inPacket().receivedFrom().deviceId();
            PortNumber ingressPortNumber = pktIn.inPacket().receivedFrom().port();
            Ethernet pkt = pktIn.inPacket().parsed();


            if (pkt.getEtherType() == Ethernet.TYPE_ARP){

                ARP arpPacket = (ARP) pkt.getPayload();

                Ip4Address targetAddr = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());

                MacAddress macAddress = WanInterfaceArpAddresses.get(targetAddr);

                //Run a proxy ARP, replying to arp requests for the network interfaces
                if (macAddress != null) {
                    Ethernet arpReply = ARP.buildArpReply(targetAddr.getIp4Address(),
                            macAddress, pkt);

                    packetService.emit(
                            new DefaultOutboundPacket(
                                    deviceId,
                                    DefaultTrafficTreatment.builder().setOutput(PortNumber.FLOOD).build(),
                                    ByteBuffer.wrap(arpReply.serialize())));
                }

            } else if(pkt.getEtherType() == Ethernet.TYPE_IPV4) {

                //Only handle IP traffic at the gateways
                if (gateways.get(deviceId) == null) {
                    return;
                }

                IPv4 ipPacket = (IPv4) pkt.getPayload();

                //Handle ICMP packet individually
                if (ipPacket.getProtocol() == IPv4.PROTOCOL_ICMP) {
                    handleICMP(deviceId, ingressPortNumber, pkt);
                    return;
                }

                //For remaining IP Traffic. check packet source IP address is local
                if (localIP.contains(IpAddress.valueOf(ipPacket.getSourceAddress()))) {

                    if (localIP.contains(IpAddress.valueOf(ipPacket.getDestinationAddress()))) {
                        //TODO: Handle packet meant for inside LAN
                        log.info("Unhandled intraLAN message: " + ipPacket.toString());

                    } else {
                        FlowRule[] natRules;
                        PortNumber outPort = nextOutPort(deviceId);

                        if (ipPacket.getProtocol() == IPv4.PROTOCOL_TCP || ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) {

                            natRules = gateways.get(deviceId).makeNatRules(ingressPortNumber, pkt,
                                    ipPacket.getProtocol(), outPort);

                        } else {
                            return;
                        }

                        FlowRule inFlowRule = natRules[0];
                        FlowRule outFlowRule = natRules[1];

                        flowRuleService.applyFlowRules(outFlowRule);
                        flowRuleService.applyFlowRules(inFlowRule);

                        TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(outPort).build();
                        packetService.emit(
                                new DefaultOutboundPacket(
                                        deviceId,
                                        treatment,
                                        ByteBuffer.wrap(pkt.serialize())));
                    }
                }
            }
        }

        private PortNumber nextOutPort(DeviceId deviceId) {
            WanGateway gateway = gateways.get(deviceId);
            Map<PortNumber, Integer> weights = gateway.getInterfaceWeights();
            int totalUnusedCapacity = gateway.getTotalUnusedCapacity();
            if (totalUnusedCapacity <= 0) {
                log.info("Total unused capacity = " +totalUnusedCapacity);
                totalUnusedCapacity = 1;
            }
            Random rand = new Random();

            int randomNumber = rand.nextInt((int) Math.round(totalUnusedCapacity));
            for (PortNumber port : weights.keySet()) {
                if (weights.get(port) > randomNumber) {
                    if (gateway.getInterface(port).getState()) {
                        return port;
                    } else {
                        randomNumber -= weights.get(port);
                    }
                } else {
                    randomNumber -= weights.get(port);
                }
            }
            //Should not reach here
            return null;
        }

        private void handleICMP(DeviceId deviceId, PortNumber ingressPortNumber, Ethernet pktIn) {
            IPv4 ipPacketIn = (IPv4) pktIn.getPayload();
            IPv4 ipPacketOut;
            ICMP icmpPacketIn = (ICMP) ipPacketIn.getPayload();

            if (icmpPacketIn.getIcmpCode() == 0 && icmpPacketIn.getIcmpType() == 8) {
                PortNumber outgoingPortNumber = nextOutPort(deviceId);
                WanGateway gateway = gateways.get(deviceId);
                WanInterface intf = gateway.getInterface(outgoingPortNumber);

                //log.info("outgoing ICMP");
                ipPacketOut = gateway.handleICMPOut(ipPacketIn, outgoingPortNumber);

                ipPacketOut.setTtl(ipPacketIn.getTtl());
                ipPacketOut.setChecksum((short) 0);

                pktIn.setSourceMACAddress(intf.getMacAddress());
                pktIn.setPayload(ipPacketOut);

                packetService.emit(new DefaultOutboundPacket(deviceId,
                        DefaultTrafficTreatment.builder().setOutput(outgoingPortNumber).build(),
                        ByteBuffer.wrap(pktIn.serialize())));

            } else if (icmpPacketIn.getIcmpCode() == 0 && icmpPacketIn.getIcmpType() == 0) {
                //log.info("Incoming ICMP");
                ipPacketOut = gateways.get(deviceId).handleICMPIn(ipPacketIn, ingressPortNumber);

                ipPacketOut.setTtl(ipPacketIn.getTtl());
                ipPacketOut.setChecksum((short) 0);

                MacAddress hostMacAddress = MacAddress.ZERO;
                PortNumber hostPortNumber = PortNumber.FLOOD;

                for (Host h : hosts.values()) {
                    if (h.ipAddresses().contains(IpAddress.valueOf(ipPacketOut.getDestinationAddress()))) {
                        hostMacAddress = h.mac();
                        hostPortNumber = h.location().port();
                    }
                }

                Ethernet pktOut = pktIn;
                pktOut.setDestinationMACAddress(hostMacAddress);
                pktOut.setPayload(ipPacketOut);

                packetService.emit(new DefaultOutboundPacket(deviceId,
                        DefaultTrafficTreatment.builder().setOutput(hostPortNumber).build(),
                        ByteBuffer.wrap(pktOut.serialize())));
            } else {
                log.info("Unhandled ICMP packet: " + icmpPacketIn.toString());
            }
        }
    }

    /**
     * Flow Rule Event listener, intended to remove expired flow rules from the NatHandler,
     * so that expired TCP ports can be reused
     */
    private class NatFlowListener implements FlowRuleListener {
        @Override
        public void event(FlowRuleEvent e) {
            FlowRule flowRule = ((FlowRuleEvent)e).subject();
            DeviceId deviceId = flowRule.deviceId();

            if (e.type() == FlowRuleEvent.Type.RULE_REMOVED && flowRule.appId() == appId.id()) {
               gateways.get(deviceId).removeRule(flowRule);
            }
        }
    }

    /**
     * Host listener manages an up to date list of hosts in the network
     */
    private class NetworkHostListener implements HostListener {
        @Override
        public void event(HostEvent e) {

            if (e.type() == HostEvent.Type.HOST_ADDED) {
                //Add new host to list
                hosts.put(e.subject().id(), e.subject());

            } else if (e.type() == HostEvent.Type.HOST_UPDATED) {
                //Update Host in list
                hosts.remove(e.prevSubject().id());
                hosts.put(e.subject().id(), e.subject());

            } else if (e.type() == HostEvent.Type.HOST_REMOVED) {
                //Remove host from list
                hosts.remove(e.subject().id());

            } else if (e.type() == HostEvent.Type.HOST_MOVED) {
                //TODO: handle Host Moved event
                //log.info("HOST MOVED EVENT, CURRENTLY NOT HANDLED BY CONTROLLER\n");
            }
        }
    }

    /**
     * Device listener manages an up to date list of devices and statistics in the network
     */
    private class NetworkDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent e) {

            //Gathers the new port stats for gateway switches
            if (e.type() == DeviceEvent.Type.PORT_STATS_UPDATED) {
                Device device = e.subject();

                //If the device is a Gateway device
                if (gateways.keySet().contains(device.id())) {

                    WanGateway gateway = gateways.get(device.id());
                    for (PortNumber port : gateway.getInterfaces().keySet()) {
                        CurrentPortStatistics currentPortStatistics = new CurrentPortStatistics();
                        if (!gateway.getInterface(port).getState()) {
                            currentPortStatistics.bitsPerSecond = 0;
                            currentPortStatistics.totalBytes = 0;
                            currentPortStatistics.packetErrors = 0;
                            currentPortStatistics.packetsDropped = 0;
                        } else {
                            PortStatistics deltaPortStats = deviceService.getDeltaStatisticsForPort(device.id(), port);
                            PortStatistics portStats = deviceService.getStatisticsForPort(device.id(), port);

                            int newTraffic = (int) (deltaPortStats.bytesReceived() + deltaPortStats.bytesSent());

                            currentPortStatistics.bitsPerSecond = ((newTraffic / 5) * 8);
                            currentPortStatistics.totalBytes = portStats.bytesReceived() + portStats.bytesSent();
                            currentPortStatistics.packetErrors = portStats.packetsRxErrors() + portStats.packetsTxErrors();
                            currentPortStatistics.packetsDropped = portStats.packetsRxDropped() + portStats.packetsTxDropped();

                            //TODO: Check the delay of the link
                            //int delay = generate_ping(i);

                        }
                        gateway.updateInterfaceStatistics(port, currentPortStatistics);
                    }

                    gateways.replace(device.id(), gateway);
                }

            } else if (e.type() == DeviceEvent.Type.PORT_UPDATED || e.type() == DeviceEvent.Type.PORT_ADDED  ||
                    e.type() == DeviceEvent.Type.PORT_REMOVED ) {
                WanGateway gateway = gateways.get(e.subject().id());
                Port intfPort = e.port();
                WanInterface intf = gateway.getInterface(intfPort.number());
                if (intf != null) {
                    intf.setState(intfPort.isEnabled());
                }
            }
        }
    }
}



