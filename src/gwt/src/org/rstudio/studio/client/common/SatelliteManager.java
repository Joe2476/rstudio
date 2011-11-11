package org.rstudio.studio.client.common;

import java.util.ArrayList;

import org.rstudio.core.client.dom.WindowEx;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class SatelliteManager implements CloseHandler<Window>
{
   @Inject
   public SatelliteManager()
   {
      
   }
   
   // the main window should call this method during startup to set itself
   // up to manage and communicate with the satellite windows
   public void initMainWindow()
   {
      // export the registration hook used by satellites
      exportSatelliteRegistrationCallback();
      
      // handle onClosed to automatically close all satellites
      Window.addCloseHandler(this);
   }
   
   // satellite windows should call this during startup to setup a 
   // communication channel with the main window
   public native void initSatelliteWindow() /*-{
      $wnd.isRStudioSatellite = true;
      $wnd.opener.registerAsRStudioSatellite($wnd);
   }-*/;
   
   
   // check whether the current window is a satellite
   public native boolean isCurrentWindowSatellite() /*-{
      return !!$wnd.isRStudioSatellite;
   }-*/;
   
   
   // close all satellite windows
   public void closeAllSatellites()
   {
      for (int i=0; i<satellites_.size(); i++)
      {
         try
         {
            WindowEx satelliteWnd = satellites_.get(i);
            satelliteWnd.close();
         }
         catch(Throwable e)
         {
         }
      } 
      satellites_.clear();  
   }
   
   // close all satellites when we are closed
   @Override
   public void onClose(CloseEvent<Window> event)
   {
      closeAllSatellites();
   }
   
   // called by satellites to connect themselves with the main window
   private void registerAsSatellite(JavaScriptObject wnd)
   {
      WindowEx satelliteWnd = wnd.<WindowEx>cast();
      satellites_.add(satelliteWnd);
   }
   
   // export the global function requried for satellites to register
   private native void exportSatelliteRegistrationCallback() /*-{
      var manager = this;     
      $wnd.registerAsRStudioSatellite = $entry(
         function(satelliteWnd) {
            manager.@org.rstudio.studio.client.common.SatelliteManager::registerAsSatellite(Lcom/google/gwt/core/client/JavaScriptObject;)(satelliteWnd);
         }
      ); 
   }-*/;
   
   private final ArrayList<WindowEx> satellites_ = new ArrayList<WindowEx>();

   
}


