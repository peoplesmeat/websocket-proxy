package peoplesmeat.websocket.grizzly;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.StringFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.ProxyServer.Protocol;
import com.ning.http.client.Response;
import com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProvider;
import com.ning.http.client.websocket.DefaultWebSocketListener;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;

public class Grizzly {
	static Logger logger = LoggerFactory.getLogger(Grizzly.class);
	static String websocketUrl;
	static String proxyServer = null;
	static int proxyPort = 3128; 

	static void doTcp() throws IOException {
		// Create TCP transport
		final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
				.build();

		// Create a FilterChain using FilterChainBuilder
		FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();

		// Add TransportFilter, which is responsible
		// for reading and writing data to the connection
		filterChainBuilder.add(new TransportFilter());
		filterChainBuilder.add(new RelayFilter());

		transport.setProcessor(filterChainBuilder.build());

		try {
			// binding transport to start listen on certain host and port
			transport.bind("localhost", 8899);

			// start the transport
			transport.start();

			logger.info("Press any key to stop the server...");
			System.in.read();
		} finally {
			logger.info("Stopping transport...");
			// stop the transport
			transport.stop();

			logger.info("Stopped transport...");
		}

	}

	static class RelayFilter extends BaseFilter {
		TCPNIOTransport transport2;
		Connection connection2;
		WebSocket websocket; 
		@Override
		public NextAction handleRead(FilterChainContext ctx) throws IOException {
			// Peer address is used for non-connected UDP Connection :)
			final Object peerAddress = ctx.getAddress();

			final Object message = ctx.getMessage();			
			Buffer buffer = (Buffer) ctx.getMessage();
			byte[] bytes = new byte[buffer.limit()];
			buffer.get(bytes);			
			
			String encoded = Hex.encodeHexString(bytes);
			while (encoded.length() > 4000) {
				String toSend = encoded.substring(0,4000); 
				websocket.sendTextMessage(toSend); 
				encoded = encoded.substring(4000); 
			}
			websocket.sendTextMessage(encoded); 
									
			return ctx.getStopAction();
		}
		
		private void connectViaWebSocket(final Connection connection, final MemoryManager memoryManager) throws InterruptedException, ExecutionException, IOException {
			
			Builder configBuilder = new AsyncHttpClientConfig.Builder();
			if (proxyServer != null) { 
				configBuilder.setProxyServer(new ProxyServer(Protocol.HTTP, proxyServer, proxyPort)); 
			}
			
			AsyncHttpClientConfig config = configBuilder.build(); 

			AsyncHttpClient c = new AsyncHttpClient(new GrizzlyAsyncHttpProvider(
					config), config);

			String wsUrl = websocketUrl;
			WebSocketListener listener = new DefaultWebSocketListener() {
				@Override
				public void onClose(WebSocket ws) {
					System.out.println(ws);
				}

				@Override
				public void onMessage(String txtmessage) {
					byte[] message = null;
					try {
						message = Hex.decodeHex(txtmessage.toCharArray());
					} catch (DecoderException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					} 					
					Buffer buffer = memoryManager.allocate(message.length).put(message);
					buffer.position(0); 
					try {
						connection.write(buffer).get();
					} catch (Exception e) { 
						logger.error("error writing to local connection", e); 
					} 
				}
			};

			WebSocketUpgradeHandler handler = new WebSocketUpgradeHandler.Builder()
					.addWebSocketListener(listener).build();
			logger.info("Connecting via websocket"); 
			websocket = c.prepareGet(wsUrl).execute(handler).get();
			logger.info("Connection completed"); 
		}
		
		@Override 
		public NextAction handleAccept(FilterChainContext ctx) {
			try {				
				logger.info("Connection on " + ctx.getConnection());
                connectViaWebSocket(ctx.getConnection(), ctx.getMemoryManager());
			} catch (Exception e) { 
				ctx.getConnection().close(); 
				logger.error("error connecting, closing connection", e); 
			}
			return ctx.getStopAction(); 
		}

	}

	WebSocket socket;

	public void go() throws InterruptedException, ExecutionException,
			IOException, NoSuchAlgorithmException, KeyManagementException {

		doTcp(); 
		System.out.println("Press Enter To Quit"); 
		System.in.read(); 
	}

	public static void main(String[] args) throws Exception {
        Options options = new Options();

        options.addOption("proxy", true, "proxy");
        options.addOption("host", true, "host");

        CommandLineParser commandLineParser = new GnuParser();
        CommandLine commandLine = commandLineParser.parse(options, args);

        if (commandLine.hasOption("proxy")) {
            proxyServer = commandLine.getOptionValue("proxy").split(":")[0];
            proxyPort = Integer.parseInt(commandLine.getOptionValue("proxy").split(":")[1]);
        }

        websocketUrl = commandLine.getOptionValue("host");

		new Grizzly().go();
	}
}
