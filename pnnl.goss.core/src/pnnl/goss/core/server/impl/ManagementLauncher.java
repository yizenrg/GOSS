package pnnl.goss.core.server.impl;

import java.io.Serializable;

import org.apache.felix.dm.annotation.api.Component;
import org.apache.felix.dm.annotation.api.ServiceDependency;
import org.apache.felix.dm.annotation.api.Start;
import org.apache.felix.dm.annotation.api.Stop;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.hamcrest.core.IsInstanceOf;

import com.google.gson.Gson;

import pnnl.goss.core.Client;
import pnnl.goss.core.Client.PROTOCOL;
import pnnl.goss.core.ClientFactory;
import pnnl.goss.core.DataResponse;
import pnnl.goss.core.GossResponseEvent;
import pnnl.goss.core.server.DataSourceRegistry;
import pnnl.goss.core.server.RequestHandlerRegistry;
import pnnl.goss.core.server.ServerControl;

@Component
public class ManagementLauncher {

	@ServiceDependency
	private volatile ClientFactory clientFactory;

	@ServiceDependency
	private volatile ServerControl serverControl;

	@ServiceDependency
	private volatile RequestHandlerRegistry handlerRegistry;

	@ServiceDependency
	private volatile DataSourceRegistry datasourceRegistry;

	class ResponseEvent implements GossResponseEvent{
		private final Client client;
		private Gson gson = new Gson();

		public ResponseEvent(Client client){
			this.client = client;
		}

		@Override
		public void onMessage(Serializable response) {
			String responseData = "{}";
			if (response instanceof DataResponse){
				String request = (String)((DataResponse) response).getData();
				if (request.trim().equals("list_handlers")){
					responseData = gson.toJson(handlerRegistry.list());
				}
				else if (request.trim().equals("list_datasources")){
					responseData = gson.toJson(datasourceRegistry.getAvailable());
				}
			}


			System.out.println("On message: "+response.toString());
			client.publish("goss/management/response", responseData);
		}

	}

	@Start
	public void start(){
		try {
			Client client = clientFactory.create(PROTOCOL.STOMP,
					new UsernamePasswordCredentials("system", "manager"));
			client.subscribeTo("/topic/goss/management/request", new ResponseEvent(client));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Stop
	public void stop(){
		System.out.println("Stopping ManagementLauncher");
	}
}
