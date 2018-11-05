// js for sdwan app custom view
(function () {
    'use strict';

    // injected refs
    var $log, $scope, wss, ks, $compile;

    // constants
    var dataReq = 'sdwanInterfaceDataRequest',
        dataResp = 'sdwanInterfaceDataResponse';

    function getData() {
        wss.sendEvent(dataReq);
    }

    function addGateway() {
        var deviceId = document.getElementById("newGateway").value;
        var payload = { "addGateway":deviceId};
        wss.sendEvent(dataReq, payload);
    }

    function addInterface() {
        var deviceId = document.getElementById("gatewayList").value;
        var intfName = document.getElementById("intfName").value;
        var intfPort = document.getElementById("intfPort").value;
        var intfMac = document.getElementById("intfMac").value;
        var intfIp = document.getElementById("intfIp").value;
        var intfBandwidth = document.getElementById("intfBandwidth").value;

        var intf = {"intfName":intfName, "intfPort":intfPort, "intfMac":intfMac,
                "intfIp":intfIp, "intfBandwidth":intfBandwidth};

        var payload = { "addInterface":deviceId, "interface":intf};

        wss.sendEvent(dataReq, payload);

    }

    function requestInterfaces() {
        var deviceId = document.getElementById("gatewayList").value;
        if (deviceId != null) {
            var payload = { "requestInterfaces":deviceId};
            wss.sendEvent(dataReq, payload);
        }
    }

    function requestStatistics() {
        var deviceId = document.getElementById("gatewayList").value;
        if (deviceId != "") {
            var payload = { "requestStatistics":deviceId};
            wss.sendEvent(dataReq, payload);
        }
    }

    function removeInterface(event) {
        var srcId = event.srcElement.id;

        //5th char is the interface number
        var index = Number(srcId.charAt(4)) + 2;
        var table = document.getElementById("gatewayTable");
        var cells = table.rows[index].cells;

        var deviceId = document.getElementById("gatewayList").value;
        var intfName = cells[0].innerText;
        var intfPort = cells[1].innerText;
        var intfMac = cells[2].innerText;
        var intfIp = cells[3].innerText;
        var intfBandwidth = cells[5].innerText;

        var intf = {"intfName":intfName, "intfPort":intfPort, "intfMac":intfMac,
                "intfIp":intfIp, "intfBandwidth":intfBandwidth};

        var payload = { "removeInterface":deviceId, "interface":intf};
        wss.sendEvent(dataReq, payload);

        table.deleteRow(index);
        document.getElementById("statisticsTable").deleteRow(index);
    }

    function showInterfaces(interfacesSize, interfaces) {
        var table = document.getElementById("gatewayTable");
        table.style.display = "table";
        document.getElementById("gatewayHeader").innerText = "Gateway: " + document.getElementById("gatewayList").value;

        while (table.rows.length > 2) {
            table.deleteRow(-1);
        }
        var statsTable = document.getElementById("statisticsTable");
        while (statsTable.rows.length > 1) {
            statsTable.deleteRow(-1);
        }
        for (var i = 0; i < interfacesSize; i++) {

            var htmlString = "<td>" + interfaces[i].name + "</td>";
            htmlString += "<td>" + interfaces[i].portNumber + "</td>";
            htmlString += "<td>" + interfaces[i].mac + "</td>";
            htmlString += "<td>" + interfaces[i].ip + "</td>";
            htmlString += "<td>" + interfaces[i].bandwidth + "</td>";
            htmlString += "<td><input type='button' id='intf" + i + "' name='remove interface' value='Remove Interface' ng-click='removeInterface($event)'></td>";

            var row;
            if (table.rows[i + 2] != null) {
                row = table.rows[i + 2];
                row.innerHTML = htmlString;
            } else {
                row = table.insertRow(i+2);
                row.innerHTML = htmlString;
            }

            $compile(row)($scope);
            $scope.$digest();
        }

        var interval = setInterval(requestStatistics, 5000);

    }

    function showStatistics(numInterfaces, interfaces) {
        var table = document.getElementById("statisticsTable");
        table.style.display = "table";
        document.getElementById("statisticsHeader").style.display = "block";
        for (var i = 0; i < numInterfaces; i++) {
            var htmlString = "<td>" + interfaces[i].name + "</td>";
            htmlString += "<td>" + interfaces[i].portNumber + "</td>";
            htmlString += "<td>" + interfaces[i].enabled + "</td>";
            htmlString += "<td>" + interfaces[i].mbps + "</td>";
            htmlString += "<td>" + interfaces[i].totalBytes + "</td>";
            htmlString += "<td>" + interfaces[i].errors + "</td>";
            htmlString += "<td>" + interfaces[i].dropped + "</td>";

            var row;
            if (table.rows[i + 1] != null) {
                row = table.rows[i + 1];
                row.innerHTML = htmlString;
            } else {
                row = table.insertRow(i+1);
                row.innerHTML = htmlString;
            }
        }
    }

    function respDataCb(data) {

        $scope.data = data;
        $scope.$apply();

        if (data.hasOwnProperty("refresh")) {
            refresh(data);

        }
        if (data.hasOwnProperty("interfacesResponse")) {
            var interfaces = [];
            for (var i = 0; i < data.interfacesSize; i++) {
                interfaces.push(data["interface" + i]);
            }
            showInterfaces(data.interfacesSize, interfaces);
        }
        if (data.hasOwnProperty("statisticsResponse")) {
            var interfaces = [];
            for (var i = 0; i < data.interfacesSize; i++) {
                interfaces.push(data["interface" + i]);
            }
            showStatistics(data.interfacesSize, interfaces)
        }

    }

    function refresh(data) {
        gatewayList = document.getElementById('gatewayList');

        var options = gatewayList.options;
        var optionsArray = [];
        var optionVals = [];
        for (var i = 0; i < options.length; i++) {
            optionVals.push(options[i].value);
            optionsArray.push(options[i]);
        }

        var devicesSize = data.devicesSize;

        for (var i = 0; i < devicesSize; i++) {
            var device = data["device" + i];

            if (!optionVals.includes(device)) {
                var dev = document.createElement("option");
                dev.text = "Device: " + device;
                dev.value = device;

                gatewayList.add(dev, null);
            }

            optionsArray.splice(optionVals.indexOf(device), 1);
            optionVals.splice(optionVals.indexOf(device), 1);
        }

        for (var i = 0; i < optionsArray.length; i++) {
            options.remove(optionsArray[i]);
        }
        document.getElementById("gatewayList").selectedIndex = -1;
    }

    angular.module('ovSdwanInterface', [])
        .controller('OvSdwanInterfaceCtrl',
        ['$log', '$compile', '$scope', 'WebSocketService', 'KeyService',

        function (_$log_, _$compile_, _$scope_, _wss_, _ks_) {
            $log = _$log_;
            $compile = _$compile_;
            $scope = _$scope_;
            wss = _wss_;
            ks = _ks_;

            var handlers = {};
            $scope.data = {};

            // data response handler
            handlers[dataResp] = respDataCb;
            wss.bindHandlers(handlers);

            // custom click handler
            $scope.getData = getData;
            $scope.addGateway = addGateway;
            $scope.requestInterfaces = requestInterfaces;
            $scope.addInterface = addInterface;
            $scope.removeInterface = removeInterface;

            // get data the first time...
            getData();

            // cleanup
            $scope.$on('$destroy', function () {
                wss.unbindHandlers(handlers);
                ks.unbindKeys();
                $log.log('OvSdwanInterfaceCtrl has been destroyed');
            });

            $log.log('OvSdwanInterfaceCtrl has been created');
        }]);

}());
