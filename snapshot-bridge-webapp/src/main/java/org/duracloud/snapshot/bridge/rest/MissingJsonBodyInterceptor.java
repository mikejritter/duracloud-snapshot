/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import java.io.EOFException;
import java.io.IOException;

import javax.annotation.Priority;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.ReaderInterceptorContext;

import org.apache.commons.httpclient.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class ensures that improperly formatted request bodies will return the a 400 http code.
 * @author Daniel Bernstein
 *         Date: Oct 12, 2015
 */
@Priority(0)
public class MissingJsonBodyInterceptor implements ReaderInterceptor{
    
    private static Logger log = LoggerFactory.getLogger(MissingJsonBodyInterceptor.class);
    
    /* (non-Javadoc)
     * @see javax.ws.rs.ext.ReaderInterceptor#aroundReadFrom(javax.ws.rs.ext.ReaderInterceptorContext)
     */
    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
        try {
            return context.proceed();
        }catch(Exception ex){
            log.error("request failed: " + ex.getCause().getMessage(), ex.getCause());
            if(ex.getCause() instanceof EOFException){
                return new WebApplicationException("JSON body expected.", HttpStatus.SC_BAD_REQUEST);
            }else{
                throw ex;
            }
        }
    }
}