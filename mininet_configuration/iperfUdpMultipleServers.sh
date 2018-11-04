#!/bin/bash
# Run multiple parallel instances of iperf servers

# Assumes the port numbers used by the servers start at 5001 and increase
# e.g. 5001, 5002, 5003, ...
port=5000

num_servers=60

# Run iperf multiple times
for i in `seq 1 $num_servers`; do

	# Set server port
	server_port=$(($base_port+$i));

	# Run iperf server, listening on $port, storing results in "$port.txt" at 1 second intervals
	sudo iperf -s -p $port -u -i 1 > testResults/$port.txt &

done
