port = 5001
results = []

#Directory containing the server log files to be parsed into useful data
resultsDir = "2-interface-test2/"

#Directory where final data should be stored
dataDir = "2-interface-tests-data/"

#fill results with the 60 log files gathered in the test
for i in range(0, 60):
    results.append(open(resultsDir+str(port)+".txt", "r"))
    port += 1

#data will last for 70 seconds, so create arrays to store the per/second traffic
#for each interface
bandwidth1 = [0] * 70
bandwidth2 = [0] * 70
bandwidths = [bandwidth1, bandwidth2]

#Iterate over the 60 log files
for i in range(0, 60):
    #result is the entire log file
    result = results[i].read()

    #Split the log file into lines and trim the
    #first 5 lines that do not contain useful data
    lines = result.split("\n")[5:]

    #startLine represents the line where the connections is made
    #sometimes the iperf client sends multiple messages in a session before
    #the session is made, so the startLine can vary
    startLine = 0

    #Start line will always start with "[ ID]"
    for j in range(0, len(lines)):
        if lines[j][0:5] == "[ ID]":
            startLine = j
            break

    #if no startLine has been found, this iperf session was never
    #properly established
    if startLine == 0:
        continue

    #need to find which interface the iperf session was sent over
    #to store the results in the correct array
    intf = "none"

    #log files identify the session using an ID number,
    #num is the session ID
    num = int(lines[startLine+1][1:4].strip())

    #using the session ID, find which interface the session was sent over
    #because the client can send multiple messages before the session is
    #created it is important to ensure the ID matches
    for j in range(0, startLine):
        if int(lines[j][1:4].strip()) == num:
            #Store the interface IP address
            intf = lines[j][46:56].strip()

    #increment startLine so that it indexes the first data point
    #in the log file
    startLine += 1
    intfIndex = -1
    #interface 1 and 2 must have values stored in their respective arrays
    if intf == "10.0.0.10":
        intfIndex = 0
    elif intf == "10.0.0.11":
        intfIndex = 1
    else:
        print("Err: Other interface found for file: " + str(i + 5001) + ".txt")
        continue

    #Total counts the total data sent in this session
    total = 0

    #transfer value is the number of Kbits transmitted in the first second of the session
    transfer = lines[startLine][19:26]
    #increase the bandwidth at second "i" value by the amount transferred
    bandwidths[intfIndex][i] += float(transfer.strip())
    #increase the total traffic sent in this session
    total += float(transfer.strip())

    #now parse lines next 9 lines in log file
    for j in range(1, 9):
        #if line is blank, log file is complete
        if lines[startLine + j].strip() == "":
            break

        #ignore out of order warnings
        if "datagrams received out-of-order" in lines[startLine + j]:
            continue

        #if line covers the total bandwidth of the session, it will store a value
        #in MBits, so add the total session traffic (in Kbits) - "total"
        if "0.0-" in lines[startLine + j]:
            transfer = lines[startLine + 9][19:26]
            bandwidths[intfIndex][i+j] = (float(transfer.strip()) * 1000) - total
            break
        #otherwise, read and store transfer value as normal
        else:
            transfer = lines[startLine + j][19:26]
            bandwidths[intfIndex][i+j] += float(transfer.strip())
            total += float(transfer.strip())   
#create interface 1 bandwidth string, values per second separated by newlines
bw1String = '\n'.join(str(e) for e in bandwidths[0])
#print("bw1String: \n" + bw1String)

#create interface 2 bandwidth string, values per second separated by newlines
bw2String = '\n'.join(str(e) for e in bandwidths[1])
#print("bw2String: \n" + bw2String)

#store results in files
#NOTE: file name should be changed per-test
bw1data = open(dataDir+"test2-intf1-data.txt", "w+")
bw1data.writelines(bw1String)
bw1data.close()

bw2data = open(dataDir+"test2-intf2-data.txt", "w+")
bw2data.writelines(bw2String)
bw2data.close()

#close all log files
for i in range(0, 60):
    results[i].close()
    
    
