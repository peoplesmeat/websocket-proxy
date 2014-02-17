websocket-proxy
===============

A java implementation of websocket proxy connection

mvn exec:java -Dexec.mainClass="peoplesmeat.websocket.grizzly.Grizzly" -Dexec.args="-proxy localhost:3128 -host wss://localhost:8443/websocket/proxy"

AJP Connector does not work with WebSockets

Cert Issues

cat www_yourdomain_de.crt PositiveSSLCA2.crt AddTrustExternalCARoot.crt > cert-chain.txt

openssl pkcs12 -export -inkey jetty.key -in cert-chain.txt -out jetty.pkcs12

keytool -importkeystore -srckeystore dev.maraudertech.com.pkcs12 -srcstoretype PKCS12 -deststoretype JKS -destkeystore keystore.jks
