/*
 * Copyright 2018-present Open Networking Foundation
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.host.InterfaceIpAddress;
import org.onosproject.net.intf.Interface;
import org.onosproject.ui.RequestHandler;
import org.onosproject.ui.UiMessageHandler;
import org.sd_wan.apps.SDWANService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * ONOS SDWAN UI Custom-View message handler.
 */
public class AppUiMessageHandler extends UiMessageHandler {

    private static final String SDWAN_INTERFACE_DATA_REQ = "sdwanInterfaceDataRequest";
    private static final String SDWAN_INTERFACE_DATA_RESP = "sdwanInterfaceDataResponse";

    private final Logger log = LoggerFactory.getLogger(getClass());

    SDWANService sdwanService;
    DeviceService deviceService;


    @Override
    protected Collection<RequestHandler> createRequestHandlers() {
        return ImmutableSet.of(
                new SDWANInterfaceDataRequestHandler()
        );
    }

    // handler for sample data requests
    private final class SDWANInterfaceDataRequestHandler extends RequestHandler {

        private SDWANInterfaceDataRequestHandler() {
            super(SDWAN_INTERFACE_DATA_REQ);
        }

        @Override
        public void process(ObjectNode payload) {

            String command;

            if (payload.size() > 0) {
                Iterator<String> fieldsIterator = payload.fieldNames();
                command = fieldsIterator.next();
            } else {
                command = "refresh";
            }

            sdwanService = get(SDWANService.class);
            deviceService = get(DeviceService.class);

            switch (command) {
                case "refresh": refresh();
                    break;
                case "addGateway": addGateway(payload.get(command));
                    break;
                case "requestInterfaces": requestInterfaces(payload.get(command).textValue());
                    break;
                case "addInterface": addInterface(payload.get(command).textValue(), payload.get("interface"));
                    break;
                case "removeInterface": removeInterface(payload.get(command).textValue(), payload.get("interface"));
                    break;
                case "requestStatistics": requestStatistics(payload.get(command).textValue());
                    break;
                default: log.info("Error processing command: " + command);
                    break;
            }
        }

        private void requestStatistics(String device) {
            DeviceId deviceId = DeviceId.deviceId(device);
            WanGateway gateway = sdwanService.getGateway(deviceId);
            Map<PortNumber, WanInterface> intfs = gateway.getInterfaces();
            ObjectNode result = objectNode();
            result.put("interfacesSize", intfs.size());
            result.put("statisticsResponse", true);

            Map<PortNumber, CurrentPortStatistics> stats = gateway.getInterfaceStatistics();

            int i = 0;
            for (PortNumber port : stats.keySet()) {
                ObjectNode intfStats = objectNode();

                intfStats.put("totalBytes", stats.get(port).totalBytes);
                intfStats.put("mbps", stats.get(port).bitsPerSecond / 1000000.0);
                intfStats.put("portNumber", port.toString());
                intfStats.put("name", intfs.get(port).getName());
                intfStats.put("errors", stats.get(port).packetErrors);
                intfStats.put("dropped", stats.get(port).packetsDropped);
                intfStats.put("enabled", intfs.get(port).getState());

                result.set("interface" + i, intfStats);
                i++;
            }
            sendMessage(SDWAN_INTERFACE_DATA_RESP, result);
        }

        private void removeInterface(String device, JsonNode intf) {
            DeviceId deviceId = DeviceId.deviceId(device);
            String errorSourceFlag = "No Error";
            try {
                errorSourceFlag = "Port Number";
                PortNumber port = PortNumber.portNumber(intf.get("intfPort").textValue());

                errorSourceFlag = "Interface Name";
                String name = intf.get("intfName").textValue();

                errorSourceFlag = "removeInterface() in SDWANService";
                sdwanService.removeInterface(deviceId, port, name);
            } catch (Exception ex) {
                log.info("Error removing interface from device: " + device + " due to error processing: " + errorSourceFlag);
            }

            //Refresh table of interfaces
            requestInterfaces(device);
        }

        private void addInterface(String device, JsonNode intf) {
            DeviceId deviceId = DeviceId.deviceId(device);
            String errorSourceFlag = "No Error";
            try {
                errorSourceFlag = "MacAddress";
                MacAddress mac = MacAddress.valueOf(intf.get("intfMac").textValue());

                errorSourceFlag = "Port Number";
                PortNumber port = PortNumber.portNumber(intf.get("intfPort").textValue());

                errorSourceFlag = "IP Address";
                Ip4Address ip = Ip4Address.valueOf(intf.get("intfIp").textValue());

                errorSourceFlag = "Interface Name";
                String name = intf.get("intfName").textValue();

                errorSourceFlag = "Bandwidth";
                int bandwidth = Integer.valueOf(intf.get("intfBandwidth").textValue());

                errorSourceFlag = "Invalid Interface";
                WanInterface newIntf = new WanInterface(name, mac, ip, bandwidth);

                sdwanService.addInterface(deviceId, port, newIntf);

            } catch (Exception ex) {
                log.info("Error adding new interface to device: " + device + " due to error processing: " + errorSourceFlag);
            }

            //Refresh table of interfaces
            requestInterfaces(device);
        }

        private void refresh() {
            Map<DeviceId, WanGateway> gateways = sdwanService.getGateways();

            if (gateways != null) {
                Set<DeviceId> deviceIds = gateways.keySet();
                ObjectNode result = objectNode();
                result.put("refresh", true);

                result.put("devicesSize", deviceIds.size());
                int i = 0;
                for (DeviceId deviceId : deviceIds) {
                    result.put("device" + i, deviceId.toString());
                    i++;
                }

                sendMessage(SDWAN_INTERFACE_DATA_RESP, result);
            }
        }

        private void requestInterfaces(String device) {
            DeviceId deviceId = DeviceId.deviceId(device);
            WanGateway gateway = sdwanService.getGateway(deviceId);
            Map<PortNumber, WanInterface> intfs = gateway.getInterfaces();
            ObjectNode result = objectNode();
            result.put("interfacesResponse", true);
            result.put("interfacesSize", intfs.size());

            int i = 0;
            for (PortNumber port : intfs.keySet()) {
                ObjectNode intfNode = objectNode();
                WanInterface intf = intfs.get(port);
                intfNode.put("name", intf.getName());
                intfNode.put("portNumber", port.toString());

                int bandwidth = intf.getBandwidth();

                intfNode.put("ip", intf.getIp4Address().toString());
                intfNode.put("mac", intf.getMacAddress().toString());
                intfNode.put("bandwidth", bandwidth);
                result.set("interface" + i, intfNode);
                i++;
            }
            sendMessage(SDWAN_INTERFACE_DATA_RESP, result);

        }

        private void addGateway(JsonNode val) {
            DeviceId deviceId = DeviceId.deviceId(val.textValue());
            if (deviceService.getDevice(deviceId) != null) {
                sdwanService.addGateway(deviceId);
                refresh();
            } else {
                log.info("New Gateway: " + val.textValue() + " is not found by DeviceService");
            }


        }
    }
}
