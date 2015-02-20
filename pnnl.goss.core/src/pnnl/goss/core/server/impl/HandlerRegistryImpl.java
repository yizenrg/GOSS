package pnnl.goss.core.server.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.felix.dm.annotation.api.Component;
import org.apache.felix.dm.annotation.api.ServiceDependency;
import org.osgi.framework.ServiceReference;

import pnnl.goss.core.Request;
import pnnl.goss.core.RequestAsync;
import pnnl.goss.core.Response;
import pnnl.goss.core.ResponseError;
import pnnl.goss.core.UploadRequest;
import pnnl.goss.core.server.HandlerNotFoundException;
import pnnl.goss.core.server.RequestHandler;
import pnnl.goss.core.server.RequestHandlerRegistry;

@Component
public class HandlerRegistryImpl implements RequestHandlerRegistry {
	
	private final Map<ServiceReference<RequestHandler>, RequestHandler> registered = new ConcurrentHashMap<>();
	private final Map<String, RequestHandler> handlers = new ConcurrentHashMap<>();
		
	@ServiceDependency(removed="handlerRemoved")
	public void handlerAdded(ServiceReference<RequestHandler> ref, RequestHandler handler){
		System.out.println("Registering Service");
		registered.put(ref, handler);
		handler.getHandles().forEach(p->handlers.put(p, handler));
	}
	
	public void handlerRemoved(ServiceReference<RequestHandler> ref){
		System.out.println("Un-Registering Service");
		registered.get(ref).getHandles().forEach(p->handlers.remove(p));
		registered.remove(ref);
	}

	@Override
	public RequestHandler getHandler(Class<? extends Request> request) throws HandlerNotFoundException {
		
		Optional<RequestHandler> maybeHandler = Optional.ofNullable(handlers.get(request.getName()));	
		return maybeHandler.orElseThrow(HandlerNotFoundException::new);
		
	}

	@Override
	public List<RequestHandler> list() {
		ArrayList<RequestHandler> items = new ArrayList<>();
		registered.values().forEach(p->items.add(p));
		return items;
	}

	@Override
	public Response handle(Request request) throws HandlerNotFoundException {
		
		RequestHandler handler = getHandler(request.getClass());
		return handler.handle(request);
	}

	@Override
	public Response handle(UploadRequest request, String datatype) throws HandlerNotFoundException {
		RequestHandler handler = getHandler(request.getClass());
		return handler.handle(request);
	}

	@Override
	public Response handle(RequestAsync request) throws HandlerNotFoundException {
		RequestHandler handler = getHandler(request.getClass());
		return handler.handle(request);
	}

}
