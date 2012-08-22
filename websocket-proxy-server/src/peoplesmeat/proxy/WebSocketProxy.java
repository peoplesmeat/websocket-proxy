/*
 * Copyright 2012 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package peoplesmeat.proxy;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.atmosphere.config.service.WebSocketHandlerService;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketHandler;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.logging.LogManager;

@WebSocketHandlerService
public class WebSocketProxy extends WebSocketHandler {
	static final Logger logger = LoggerFactory.getLogger(WebSocketProxy.class);
	
	static class ProxyConnection {
		WebSocket webSocket;
		Connection connection;
		MemoryManager memoryManager;

		public ProxyConnection(WebSocket webSocket, Connection connection,
				MemoryManager memoryManager2) {
			super();
			this.webSocket = webSocket;
			this.connection = connection;
			this.memoryManager = memoryManager2;
		}

	}
	
	ProxyConnection proxyConnection; 
	
    @Override
	public void onOpen(final WebSocket webSocket) {
    	logger.info("Connected " + webSocket);
    	
    	TCPNIOTransport transport2 = TCPNIOTransportBuilder.newInstance().build();
 
		transport2.setProcessor(FilterChainBuilder.stateless()
				.add(new TransportFilter()).add(new BaseFilter() {
					public NextAction handleRead(FilterChainContext ctx) {											
						Buffer buffer = (Buffer) ctx.getMessage();
						byte[] bytes = new byte[buffer.limit()];
						buffer.get(bytes);
						
						try {
							String encoded = Hex.encodeHexString(bytes);
							while (encoded.length() > 4000) {
								String toSend = encoded.substring(0,4000); 
								webSocket.write(toSend); 
								encoded = encoded.substring(4000); 
							}
							webSocket.write(encoded); 

						} catch (IOException e) {
							logger.error("Error Writing",e); 
						} 
						return ctx.getStopAction();
					}
				}).build());
		try {
			transport2.start();
			Connection connection2 = transport2.connect("localhost", 22).get();
			proxyConnection = new ProxyConnection(webSocket, connection2, transport2.getMemoryManager()); 
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    public void onTextMessage(WebSocket webSocket, String message) {

    	byte[] data= null;
		try {
			data = Hex.decodeHex(message.toCharArray());
		} catch (DecoderException e1) {			
			e1.printStackTrace();
		} 
    	Buffer send_buffer = proxyConnection.memoryManager
				.allocate(data.length).put(data);
    	
		send_buffer.position(0);
		try {
			proxyConnection.connection.write(send_buffer).get();
		} catch (Exception e) { 
			logger.error("error sending", e.getMessage()); 
		}
    }
}
