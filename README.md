websocket-proxy
===============

A java implementation of websocket proxy connection

mvn exec:java -Dexec.mainClass="peoplesmeat.websocket.grizzly.Grizzly" -Dexec.args="-proxy localhost:3128 -host wss://localhost:8443/websocket/proxy"