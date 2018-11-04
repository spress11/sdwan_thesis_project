import subprocess
import time

#count number of seconds
count = 0

#port value increments with each new iperf session
port = 5001

while (count < 60):
    #Command creates 10 second UDP iperf session to 10.0.1.1 (h1) connecting to new port with bandwidth 1Mbit/s. Output is stored in log file: "client-$port.txt"
    cmd = "iperf -u -c 10.0.1.1 -b 1M -t 10 -p " + str(port) + " > testResults/client-" + str(port) + ".txt"
    subprocess.Popen(cmd, shell=True)
    
    #increment counters and wait for 1 second
    port += 1
    count += 1
    time.sleep(1)

