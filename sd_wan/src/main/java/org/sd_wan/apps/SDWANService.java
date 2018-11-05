/*
 *
 */
  
package org.sd_wan.apps;
 
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.intf.Interface;
import org.sd_wan.app.CurrentPortStatistics;
import org.sd_wan.app.WanGateway;
import org.sd_wan.app.WanInterface;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A demonstrative service for the intent reactive forwarding application to
 * export.
 */
public interface SDWANService {

    public Map<DeviceId, WanGateway> getGateways();

    public void addGateway(DeviceId deviceId);

    public void addInterface(DeviceId deviceId, PortNumber port, WanInterface intf);

    public WanGateway getGateway(DeviceId deviceId);

    public void removeInterface(DeviceId deviceId, PortNumber port, String name);

    //public Map<PortNumber, CurrentPortStatistics> getPortStatistics(DeviceId deviceId);
}
