/*
	Copyright (c) 2014, Battelle Memorial Institute
    All rights reserved.
    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions are met:
    1. Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.
    2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.
    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
    ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
    WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
     
    DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
    ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
    (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
    LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
    ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
    (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
    The views and conclusions contained in the software and documentation are those
    of the authors and should not be interpreted as representing official policies,
    either expressed or implied, of the FreeBSD Project.
    This material was prepared as an account of work sponsored by an
    agency of the United States Government. Neither the United States
    Government nor the United States Department of Energy, nor Battelle,
    nor any of their employees, nor any jurisdiction or organization
    that has cooperated in the development of these materials, makes
    any warranty, express or implied, or assumes any legal liability
    or responsibility for the accuracy, completeness, or usefulness or
    any information, apparatus, product, software, or process disclosed,
    or represents that its use would not infringe privately owned rights.
    Reference herein to any specific commercial product, process, or
    service by trade name, trademark, manufacturer, or otherwise does
    not necessarily constitute or imply its endorsement, recommendation,
    or favoring by the United States Government or any agency thereof,
    or Battelle Memorial Institute. The views and opinions of authors
    expressed herein do not necessarily state or reflect those of the
    United States Government or any agency thereof.
    PACIFIC NORTHWEST NATIONAL LABORATORY
    operated by BATTELLE for the UNITED STATES DEPARTMENT OF ENERGY
    under Contract DE-AC05-76RL01830
*/
package pnnl.goss.dsa.impl;

import javax.servlet.http.HttpSession;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;

import org.apache.cxf.jaxws.context.WebServiceContextImpl;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.log4j.Logger;

import pnnl.goss.core.client.GossClient;
import pnnl.goss.dsa.security.GossLoginInterceptor;

public class GossServiceHelper {
	private static final String GOSS_CLIENT_PARAM = "GOSS_CLIENT";
	protected Logger log = Logger.getLogger(GossServiceHelper.class);

	
	public GossClient getGOSSClient(){
		GossClient client = null;
		WebServiceContext ws = new WebServiceContextImpl();
		MessageContext mc = ws.getMessageContext();
		HttpSession session = ((javax.servlet.http.HttpServletRequest)mc.get(MessageContext.SERVLET_REQUEST)).getSession();
		if(session!=null) {
			Object clientObj = session.getAttribute(GOSS_CLIENT_PARAM);
	        if(clientObj!=null){
	        	client = (GossClient)clientObj;
	        	log.info("Session: "+session.getId()+"  Client already exists");
	        }  else {
	        	log.info("Session: "+session.getId()+"  No client, creating new");
	        	client = new GossClient(getMessageCredentials());
	        	session.setAttribute(GOSS_CLIENT_PARAM, client);
	        }
		}
        
        
       
        return client;
	}
	
	public UsernamePasswordCredentials getMessageCredentials(){
		try{
			
			WebServiceContext ws = new WebServiceContextImpl();
			MessageContext mc = ws.getMessageContext();
	        
			Object name = mc.get(GossLoginInterceptor.CONST_NAME);
			Object pw = mc.get(GossLoginInterceptor.CONST_PW);
			if(name!=null && pw!=null)
				return new UsernamePasswordCredentials(name.toString(), pw.toString());
	        
	        
		}catch(Exception e){
			e.printStackTrace();
			log.error(e.getMessage(), e);
		}
		log.warn("WARNING: NO USERNAME/PW FOUND IN GOSS SERVICE HELPER");
		return null;
	}
	
	
}
