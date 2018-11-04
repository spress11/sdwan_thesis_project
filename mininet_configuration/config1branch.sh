#!/bin/bash

#s2 and s3 are simple bridges, forwarding all traffic between ports 1 and 2
sudo ovs-ofctl add-flow s2 in_port=1,actions=output:2
sudo ovs-ofctl add-flow s2 in_port=2,actions=output:1

sudo ovs-ofctl add-flow s3 in_port=1,actions=output:2
sudo ovs-ofctl add-flow s3 in_port=2,actions=output:1

#s4 must combine traffic from each interface and output to h1 through eth3
sudo ovs-ofctl add-flow s4 in_port=1,eth_src=00:00:00:00:00:10,actions=output:3
sudo ovs-ofctl add-flow s4 in_port=2,eth_src=00:00:00:00:00:11,actions=output:3

#s4 must then separate traffic from h1 to the respective interface
sudo ovs-ofctl add-flow s4 in_port=3,eth_dst=00:00:00:00:00:10,actions=output:1
sudo ovs-ofctl add-flow s4 in_port=3,eth_dst=00:00:00:00:00:11,actions=output:2

#s4 should also flood ARP packets to allow the Proxy-arp for network interfaces
sudo ovs-ofctl add-flow s4 arp,actions=flood
