package goss.pnnl.fusiondb.launchers;

import goss.pnnl.fusiondb.handlers.RequestActualTotalHandler;
import goss.pnnl.fusiondb.handlers.RequestCapacityRequirementHandler;
import goss.pnnl.fusiondb.handlers.RequestForecastTotalHandler;
import goss.pnnl.fusiondb.handlers.RequestRTEDScheduleHandler;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import pnnl.goss.core.DataResponse;
import pnnl.goss.core.Request;
import pnnl.goss.core.Request.RESPONSE_FORMAT;
import pnnl.goss.core.Response;
import pnnl.goss.core.client.Client;
import pnnl.goss.core.client.GossClient;
import pnnl.goss.core.client.GossClient.PROTOCOL;
import pnnl.goss.core.client.GossResponseEvent;
import pnnl.goss.fusiondb.datamodel.ActualTotal;
import pnnl.goss.fusiondb.datamodel.ActualTotalData;
import pnnl.goss.fusiondb.datamodel.CapacityRequirement;
import pnnl.goss.fusiondb.datamodel.VizRequest;
import pnnl.goss.fusiondb.requests.RequestActualTotal;
import pnnl.goss.fusiondb.requests.RequestActualTotal.Type;
import pnnl.goss.fusiondb.requests.RequestCapacityRequirement;
import pnnl.goss.fusiondb.requests.RequestForecastTotal;
import pnnl.goss.fusiondb.requests.RequestRTEDSchedule;
import pnnl.goss.server.core.GossRequestHandler;
import scala.annotation.meta.setter;

import com.google.gson.Gson;

public class DataStreamLauncher {

	private volatile boolean isRunning = false;

	Client client = new GossClient(PROTOCOL.STOMP);
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	String controlTopic = "goss/fusion/viz/control";
	String errorTopic = "goss/fusion/viz/error";

	String historicTopic = "goss/fusion/viz/historic";
	String historicCapaReqTopic  = historicTopic+"/capareq";
	String historicInterchangeTotalTopic = historicTopic+"/inter_total";
	String historicInterchangeScheduleTopic = historicTopic+"/inter_sched";
	String historicActualLoadTopic = historicTopic+"/actual_load";
	String historicActualSolarTopic = historicTopic+"/actual_solar";
	String historicActualWindTopic = historicTopic+"/actual_wind";
	String historicForecastLoadTopic = historicTopic+"/forecast_load";
	String historicForecastSolarTopic = historicTopic+"/forecast_solar";
	String historicForecastWindTopic = historicTopic+"/forecast_wind";

	String currentTopic = "goss/fusion/viz/current";
	String currentCapaReqTopic  = currentTopic+"/capareq";
	String currentInterchangeTotalTopic = currentTopic+"/inter_total";
	String currentInterchangeScheduleTopic = currentTopic+"/inter_sched";
	String currentActualLoadTopic = currentTopic+"/actual/load";
	String currentActualSolarTopic = currentTopic+"/actual/solar";
	String currentActualWindTopic = currentTopic+"/actual/wind";
	String currentForecastLoadTopic = currentTopic+"/forecast/load";
	String currentForecastSolarTopic = currentTopic+"/forecast/solar";
	String currentForecastWindTopic = currentTopic+"/forecast/wind";

	/**
	 * Receives request from Fusion project's web based visualization on controlTopic.
	 * Published data stream for historic and current data.
	 * 
	 * Historic Request in the form:
	 * 	 {	type:historic,
	 * 		timestamp:"MM/dd/yyyy HH:mm:ss", 
	 * 		range:2
	 * 		unit:hour 	}
	 * 
	 * Current Request in the form: 
	 * 	{ 	type:current, 
	 * 		timestamp:"MM/dd/yyyy HH:mm:ss a", 
	 * 		range:5
	 * 		unit:minute	}
	 * 
	 * To stop current data stream:
	 * "stop stream"
	 * 
	 */

	public void launch() {
		GossResponseEvent event =  new GossResponseEvent() {
			@Override
			public void onMessage(Response response) {
				try{
					String message = (String)((DataResponse)response).getData();
					if(message.contains("stop stream"))
						isRunning= false;
					else{
						Gson gson = new Gson();
						final VizRequest vizRequest = gson.fromJson(message, VizRequest.class);
						if(vizRequest.getType().toLowerCase().equals("historic")){
							String endTimestamp = vizRequest.getTimestamp();
							Date date = dateFormat.parse(endTimestamp);
							date = new Date(date.getTime()-(vizRequest.getRange()*60*60*1000));
							String timestamp = dateFormat.format(date);
							publishHistoricData(timestamp, endTimestamp);
						}
						if(vizRequest.getType().toLowerCase().equals("current")){
							Thread thread = new Thread(new Runnable() {

								@Override
								public void run() {
									try{
										SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
										isRunning = true;
										String timestamp = vizRequest.getTimestamp();
										Date date = dateFormat.parse(timestamp);
										date = new Date(date.getTime()+(vizRequest.getRange()*60*1000));
										String endTimestamp = dateFormat.format(date);
										publishHistoricData(timestamp, endTimestamp);
										while(isRunning){
											publishCurrentData(timestamp,endTimestamp);
											timestamp = endTimestamp;
											date = dateFormat.parse(timestamp);
											date = new Date(date.getTime()+(vizRequest.getRange()*60*1000));
											endTimestamp = dateFormat.format(date);
										}
									}catch(ParseException p){
										client.publish(controlTopic, "timestamp is not in correct format mm/dd/yyyy HH:mm:ss");
										p.printStackTrace();
									}
								}
							});
							thread.start();
						}
					}
				}catch(ParseException e){
					client.publish(controlTopic, "timestamp is not in correct format mm/dd/yyyy HH:mm:ss");
					e.printStackTrace();
				}catch(Exception e){
					client.publish(controlTopic, e.getMessage());
					e.printStackTrace();
				}
			}
		};

		client.subscribeTo("/topic/goss/fusion/viz/control", event);
	}

	/**
	 * queries and publishes historical data
	 */
	private void publishHistoricData(String timeStamp, String endTimestamp){

		// capacity requirement
		Request request = new RequestCapacityRequirement(timeStamp,endTimestamp);
		RequestCapacityRequirementHandler handler = new RequestCapacityRequirementHandler();
		handler.viz = true;
		DataResponse response = (DataResponse)handler.handle(request);
		client.publish(historicCapaReqTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);
		Gson gson = new Gson();
		System.out.println(gson.toJson(response.getData()));

		// total rted
		request = new RequestRTEDSchedule(timeStamp, endTimestamp);
		RequestRTEDScheduleHandler rtedhandler = new RequestRTEDScheduleHandler();
		rtedhandler.viz = true;
		response = (DataResponse)rtedhandler.handle(request);
		client.publish(historicInterchangeScheduleTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);
		System.out.println(gson.toJson(response.getData()));

		// total interchange
		request =  new RequestActualTotal(Type.INTERHCHANGE, timeStamp, endTimestamp);
		RequestActualTotalHandler actualtotalhandler = new RequestActualTotalHandler();
		actualtotalhandler.viz = true;
		response = (DataResponse)actualtotalhandler.handle(request);
		client.publish(historicInterchangeTotalTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);
		System.out.println(gson.toJson(response.getData()));

		// actual load
		request =  new RequestActualTotal(Type.LOAD, timeStamp, endTimestamp);
		actualtotalhandler = new RequestActualTotalHandler();
		actualtotalhandler.viz = true;
		response = (DataResponse)actualtotalhandler.handle(request);
		client.publish(historicActualLoadTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);
		System.out.println(gson.toJson(response.getData()));

		// actual wind
		request =  new RequestActualTotal(Type.WIND, timeStamp, endTimestamp);
		actualtotalhandler = new RequestActualTotalHandler();
		actualtotalhandler.viz = true;
		response = (DataResponse)actualtotalhandler.handle(request);
		client.publish(historicActualWindTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);
		System.out.println(gson.toJson(response.getData()));

		// actual solar
		request =  new RequestActualTotal(Type.SOLAR, timeStamp, endTimestamp);
		actualtotalhandler = new RequestActualTotalHandler();
		actualtotalhandler.viz = true;
		response = (DataResponse)actualtotalhandler.handle(request);
		client.publish(historicActualSolarTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);
		System.out.println(gson.toJson(response.getData()));

		//forecast load
		request =  new RequestForecastTotal(pnnl.goss.fusiondb.requests.RequestForecastTotal.Type.LOAD, timeStamp, endTimestamp);
		RequestForecastTotalHandler forecasthandler = new RequestForecastTotalHandler();
		forecasthandler.viz = true;
		response = (DataResponse)forecasthandler.handle(request);
		client.publish(historicForecastLoadTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);
		System.out.println(gson.toJson(response.getData()));

		//forecast solar
		request =  new RequestForecastTotal(pnnl.goss.fusiondb.requests.RequestForecastTotal.Type.SOLAR, timeStamp, endTimestamp);
		forecasthandler = new RequestForecastTotalHandler();
		forecasthandler.viz = true;
		response = (DataResponse)forecasthandler.handle(request);
		client.publish(historicForecastSolarTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);
		System.out.println(gson.toJson(response.getData()));

		//forecast wind
		request =  new RequestForecastTotal(pnnl.goss.fusiondb.requests.RequestForecastTotal.Type.WIND, timeStamp, endTimestamp);
		forecasthandler = new RequestForecastTotalHandler();
		forecasthandler.viz = true;
		response = (DataResponse)forecasthandler.handle(request);
		client.publish(historicForecastWindTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);
		System.out.println(gson.toJson(response.getData()));
	}

	private void publishCurrentData(String timeStamp, String endTimestamp){

		// capacity requirement
		Request request = new RequestCapacityRequirement(timeStamp,endTimestamp);
		GossRequestHandler handler = new RequestCapacityRequirementHandler();
		((RequestCapacityRequirementHandler)handler).viz = true;
		DataResponse response = (DataResponse)handler.handle(request);
		client.publish(currentCapaReqTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);

		// total rted
		request = new RequestRTEDSchedule(timeStamp, endTimestamp);
		handler = new RequestRTEDScheduleHandler();
		((RequestRTEDScheduleHandler)handler).viz = true;
		response = (DataResponse)handler.handle(request);
		client.publish(currentInterchangeScheduleTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);

		// total interchange
		request =  new RequestActualTotal(Type.INTERHCHANGE, timeStamp, endTimestamp);
		handler = new RequestActualTotalHandler();
		((RequestActualTotalHandler)handler).viz = true;
		response = (DataResponse)handler.handle(request);
		client.publish(currentInterchangeTotalTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);

		// actual load
		request =  new RequestActualTotal(Type.LOAD, timeStamp, endTimestamp);
		handler = new RequestActualTotalHandler();
		((RequestActualTotalHandler)handler).viz = true;
		response = (DataResponse)handler.handle(request);
		client.publish(currentActualLoadTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);

		// actual wind
		request =  new RequestActualTotal(Type.WIND, timeStamp, endTimestamp);
		handler = new RequestActualTotalHandler();
		((RequestActualTotalHandler)handler).viz = true;
		response = (DataResponse)handler.handle(request);
		client.publish(currentActualWindTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);

		// actual solar
		request =  new RequestActualTotal(Type.SOLAR, timeStamp, endTimestamp);
		handler = new RequestActualTotalHandler();
		((RequestActualTotalHandler)handler).viz = true;
		response = (DataResponse)handler.handle(request);
		client.publish(currentActualSolarTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);

		//forecast load
		request =  new RequestForecastTotal(pnnl.goss.fusiondb.requests.RequestForecastTotal.Type.LOAD, timeStamp, endTimestamp);
		handler = new RequestForecastTotalHandler();
		((RequestForecastTotalHandler)handler).viz = true;
		response = (DataResponse)handler.handle(request);
		client.publish(currentForecastLoadTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);

		//forecast solar
		request =  new RequestForecastTotal(pnnl.goss.fusiondb.requests.RequestForecastTotal.Type.SOLAR, timeStamp, endTimestamp);
		handler = new RequestForecastTotalHandler();
		((RequestForecastTotalHandler)handler).viz = true;
		response = (DataResponse)handler.handle(request);
		client.publish(currentForecastSolarTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);

		//forecast wind
		request =  new RequestForecastTotal(pnnl.goss.fusiondb.requests.RequestForecastTotal.Type.WIND, timeStamp, endTimestamp);
		handler = new RequestForecastTotalHandler();
		((RequestForecastTotalHandler)handler).viz = true;
		response = (DataResponse)handler.handle(request);
		client.publish(currentForecastWindTopic, (Serializable)response.getData(),  RESPONSE_FORMAT.JSON);

	}

}