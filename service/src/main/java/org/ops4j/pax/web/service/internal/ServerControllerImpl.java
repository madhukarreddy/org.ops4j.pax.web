/* Copyright 2007 Alin Dreghiciu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.web.service.internal;

import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.Servlet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.jetty.Handler;
import org.ops4j.pax.web.service.HttpServiceConfiguration;

public class ServerControllerImpl implements ServerController
{

    private static final Log m_logger = LogFactory.getLog( ServerControllerImpl.class );

    private HttpServiceConfiguration m_configuration;
    private State m_state;
    private JettyFactory m_jettyFactory;
    private JettyServer m_jettyServer;
    private Set<ServerListener> m_listeners;
    private Handler m_handler;

    public ServerControllerImpl( final JettyFactory jettyFactory, final Handler handler )
    {
        m_jettyFactory = jettyFactory;
        m_configuration = null;
        m_state = new Unconfigured();
        m_listeners = new HashSet<ServerListener>();
        m_handler = handler;
    }

    public synchronized void start()
    {
        if( m_logger.isInfoEnabled() )
        {
            m_logger.info( "starting server: " + this );
        }
        m_state.start();
    }

    public synchronized void stop()
    {
        if( m_logger.isInfoEnabled() )
        {
            m_logger.info( "stopping server: " + this );
        }
        m_state.stop();
    }

    public synchronized void configure( final HttpServiceConfiguration configuration )
    {
        if( m_logger.isInfoEnabled() )
        {
            m_logger.info( "configuring server: " + this + " -> " + configuration );
        }
        if( configuration == null )
        {
            throw new IllegalArgumentException( "configuration == null" );
        }
        m_configuration = configuration;
        m_state.configure();
    }

    public HttpServiceConfiguration getConfiguration()
    {
        return m_configuration;
    }

    public void addListener( ServerListener listener )
    {
        if( listener == null )
        {
            throw new IllegalArgumentException( "listener == null" );
        }
        m_listeners.add( listener );
    }

    public String addServlet( final String alias, final Servlet servlet, Map<String, String> initParams )
    {
        Assert.notNull( "alias == null", alias );
        Assert.notEmpty( "alias is empty", alias );
        Assert.notNull( "servlet == null", servlet );
        return m_state.addServlet( alias, servlet, initParams );
    }

    public void removeServlet( String name )
    {
        Assert.notNull( "name == null", name );
        Assert.notEmpty( "name is empty", name );
        m_state.removeServlet( name );
    }

    public boolean isStarted()
    {
        return m_state instanceof Started;
    }

    public void addEventListener( final EventListener listener )
    {
        m_state.addEventListener( listener );
    }

    public void removeEventListener( final EventListener listener )
    {
        m_state.removeEventListener( listener );
    }

    void notifyListeners( ServerEvent event )
    {
        for( ServerListener listener : m_listeners )
        {
            listener.stateChanged( event );
        }
    }

    @Override
    public String toString()
    {
        return new StringBuilder()
            .append( ServerControllerImpl.class.getSimpleName() )
            .append( "{" )
            .append( "state=" )
            .append( m_state )
            .append( "}" )
            .toString();
    }

    private interface State
    {

        void start();

        void stop();

        void configure();

        String addServlet( String alias, Servlet servlet, Map<String, String> initParams );

        void removeServlet( String alias );

        void addEventListener( EventListener listener );

        void removeEventListener( EventListener listener );
    }

    private class Started implements State
    {

        public void start()
        {
            throw new IllegalStateException( "server is already started. must be stopped first." );
        }

        public void stop()
        {
            m_jettyServer.stop();
            m_state = new Stopped();
            notifyListeners( ServerEvent.STOPPED );
        }

        public void configure()
        {
            ServerControllerImpl.this.stop();
            ServerControllerImpl.this.start();
        }

        public String addServlet( final String alias, final Servlet servlet, Map<String, String> initParams )
        {
            return m_jettyServer.addServlet( alias, servlet, initParams );
        }

        public void removeServlet( final String name )
        {
            m_jettyServer.removeServlet( name );
        }

        public void addEventListener( EventListener listener )
        {
            m_jettyServer.addEventListener( listener );
        }

        public void removeEventListener( EventListener listener )
        {
            m_jettyServer.removeEventListener( listener );
        }

        @Override
        public String toString()
        {
            return "STARTED";
        }
    }

    private class Stopped implements State
    {

        public void start()
        {
            m_jettyServer = m_jettyFactory.createServer();
            if( m_configuration.isHttpEnabled() )
            {
                m_jettyServer.addConnector( m_jettyFactory.createConnector( m_configuration.getHttpPort() ) );
            }
            if( m_configuration.isHttpSecureEnabled() )
            {
                m_jettyServer.addConnector(
                    m_jettyFactory.createSecureConnector(
                        m_configuration.getHttpSecurePort(),
                        m_configuration.getSslKeystore(),
                        m_configuration.getSslPassword(),
                        m_configuration.getSslKeyPassword()
                    )
                );
            }
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put( "javax.servlet.context.tempdir", m_configuration.getTemporaryDirectory() );
            m_jettyServer.addContext( m_handler, attributes, m_configuration.getSessionTimeout() );
            m_jettyServer.start();
            m_state = new Started();
            notifyListeners( ServerEvent.STARTED );
        }

        public void stop()
        {
            // do nothing. already stopped
        }

        public void configure()
        {
            notifyListeners( ServerEvent.CONFIGURED );
        }

        public String addServlet( String alias, Servlet servlet, Map<String, String> initParams )
        {
            // do nothing if server is not started
            return null;
        }

        public void removeServlet( String name )
        {
            // do nothing if server is not started
        }

        public void addEventListener( EventListener listener )
        {
            // do nothing if server is not started
        }

        public void removeEventListener( EventListener listener )
        {
            // do nothing if server is not started
        }

        @Override
        public String toString()
        {
            return "STOPPED";
        }
    }

    private class Unconfigured extends Stopped
    {

        public void start()
        {
            throw new IllegalStateException( "server is not yet configured." );
        }

        public void configure()
        {
            m_state = new Stopped();
            notifyListeners( ServerEvent.CONFIGURED );
        }

        @Override
        public String toString()
        {
            return "UNCONFIGURED";
        }
    }

    // TODO verify synchronization 

}
