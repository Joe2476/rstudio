/*
 * ModalDialogBase.java
 *
 * Copyright (C) 2009-11 by RStudio, Inc.
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.core.client.widget;


import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.ui.HasHorizontalAlignment.HorizontalAlignmentConstant;
import org.rstudio.core.client.command.ShortcutManager;
import org.rstudio.core.client.dom.DomUtils;
import org.rstudio.core.client.dom.NativeWindow;
import org.rstudio.core.client.theme.res.ThemeStyles;
import org.rstudio.studio.client.RStudioGinjector;

import java.util.ArrayList;


public abstract class ModalDialogBase extends DialogBox
{   
   protected ModalDialogBase()
   {
      this(null);
   }
   
   protected ModalDialogBase(SimplePanel containerPanel)
   {
      // core initialization. passing false for modal works around
      // modal PopupPanel supressing global keyboard accelerators (like
      // Ctrl-N or Ctrl-T). modality is achieved via setGlassEnabled(true)
      super(false, false);
      setGlassEnabled(true);
      addStyleDependentName("ModalDialog");  
    
      // main panel used to host UI
      mainPanel_ = new VerticalPanel();
      bottomPanel_ = new HorizontalPanel();
      bottomPanel_.setStyleName(ThemeStyles.INSTANCE.dialogBottomPanel());
      bottomPanel_.setWidth("100%");
      buttonPanel_ = new HorizontalPanel();
      leftButtonPanel_ = new HorizontalPanel();
      bottomPanel_.add(leftButtonPanel_);
      bottomPanel_.add(buttonPanel_);
      setButtonAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
      mainPanel_.add(bottomPanel_);
    
      // embed main panel in a custom container if specified
      containerPanel_ = containerPanel;
      if (containerPanel_ != null)
      {
         containerPanel_.setWidget(mainPanel_);
         setWidget(containerPanel_);
      }
      else
      {
         setWidget(mainPanel_);
      }

      addDomHandler(new KeyDownHandler()
      {
         public void onKeyDown(KeyDownEvent event)
         {
            // Is this too aggressive? Alternatively we could only filter out
            // keycodes that are known to be problematic (pgup/pgdown)
            event.stopPropagation();
         }
      }, KeyDownEvent.getType());
   }

   @Override
   protected void onLoad()
   {
      // 728: Focus remains in Source view when message dialog pops up over it
      NativeWindow.get().focus();
      
      super.onLoad();
      allActiveDialogs_.add(this);
      ShortcutManager.INSTANCE.setEnabled(false);
   }

   @Override
   protected void onUnload()
   {
      boolean removed = allActiveDialogs_.remove(this);
      if (allActiveDialogs_.size() == 0)
         ShortcutManager.INSTANCE.setEnabled(true);
      assert removed;
      
      super.onUnload();
   }

   public void showModal()
   {
      if (mainWidget_ == null)
      {
         mainWidget_ = createMainWidget();
         
         // get the main widget to line up with the right edge of the buttons. 
         mainWidget_.getElement().getStyle().setMarginRight(3, Unit.PX);
         
         mainPanel_.insert(mainWidget_, 0);
      }

      originallyActiveElement_ = DomUtils.getActiveElement();

      // position the dialog
      positionAndShowDialog();

      // defer shown notification to allow all elements to render
      // before attempting to interact w/ them programatically (e.g. setFocus)
      Timer timer = new Timer() {
         public void run() {
            onDialogShown();
         }
      };
      timer.schedule(100); 
   }


   protected abstract Widget createMainWidget() ;
   
   protected void positionAndShowDialog()
   {
      super.center();
   }
   
   protected void onDialogShown()
   {
   }
   
   protected void addOkButton(ThemedButton okButton)
   {
      okButton_ = okButton;
      okButton_.addStyleDependentName("DialogAction");
      okButton_.setDefault(true);
      addButton(okButton_);
   }
   
   protected void setOkButtonCaption(String caption)
   {
      okButton_.setText(caption);
   }
   
   
   protected void addCancelButton()
   {
      addCancelButton(createCancelButton(null));
   }
   
   protected ThemedButton createCancelButton(final Operation cancelOperation)
   {
      return new ThemedButton("Cancel", new ClickHandler() {
         public void onClick(ClickEvent event) {
            if (cancelOperation != null)
               cancelOperation.execute();
            closeDialog();
         }
      });
   }
   
   protected void addCancelButton(ThemedButton cancelButton)
   {
      cancelButton_ = cancelButton;
      cancelButton_.addStyleDependentName("DialogAction");
      addButton(cancelButton_);
   }
    
   protected void addLeftButton(ThemedButton button)
   {
      button.addStyleDependentName("DialogAction");
      button.addStyleDependentName("DialogActionLeft");
      leftButtonPanel_.add(button);
      allButtons_.add(button);
   }

   protected void addLeftWidget(Widget widget)
   {
      leftButtonPanel_.add(widget);
   }


   protected void addButton(ThemedButton button)
   {
      button.addStyleDependentName("DialogAction");
      buttonPanel_.add(button);
      allButtons_.add(button);
   }
   
   protected void setButtonAlignment(HorizontalAlignmentConstant alignment)
   {
      bottomPanel_.setCellHorizontalAlignment(buttonPanel_, alignment);
   }
   
   
   protected ProgressIndicator addProgressIndicator()
   {
      final SlideLabel label = new SlideLabel(true);
      Element labelEl = label.getElement();
      Style labelStyle = labelEl.getStyle();
      labelStyle.setPosition(Style.Position.ABSOLUTE);
      labelStyle.setLeft(0, Style.Unit.PX);
      labelStyle.setRight(0, Style.Unit.PX);
      labelStyle.setTop(-12, Style.Unit.PX);
      getWidget().getElement().getParentElement().appendChild(labelEl);

      return new ProgressIndicator()
      {
         public void onProgress(String message)
         {
            if (message == null)
            {
               label.setText("", true);
               if (showing_)
                  endProgress();
            }
            else
            {
               label.setText(message, false);
               if (!showing_)
               {
                  enableControls(false);
                  label.show();
                  showing_ = true;
               }
            }
         }

         public void onCompleted()
         {
            endProgress();
            closeDialog();
         }

         public void onError(String message)
         {
            endProgress();
            RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                  "Error", message);
         }

         private void endProgress()
         {
            if (showing_)
            {
               enableControls(true);
               label.hide();
               showing_ = false;
            }
         }

         private boolean showing_;
      };
   }
   
   protected void closeDialog()
   {
      hide();
      removeFromParent();

      if (originallyActiveElement_ != null
          && !originallyActiveElement_.getTagName().equalsIgnoreCase("body"))
      {
         Document doc = originallyActiveElement_.getOwnerDocument();
         if (doc != null)
         {
            originallyActiveElement_.focus();
         }
      }
      originallyActiveElement_ = null;
   }
   
   protected SimplePanel getContainerPanel()
   {
      return containerPanel_;
   }
   
   protected void enableControls(boolean enabled)
   {
      enableButtons(enabled);
      onEnableControls(enabled);
   }
      
   protected void onEnableControls(boolean enabled)
   {
      
   }
   
   @Override
   public void onPreviewNativeEvent(Event.NativePreviewEvent event)
   {
      if (allActiveDialogs_.get(allActiveDialogs_.size() - 1) != this)
         return;

      if (event.getTypeInt() == Event.ONKEYDOWN)
      {
         NativeEvent nativeEvent = event.getNativeEvent();
         switch (nativeEvent.getKeyCode())
         {
            case KeyCodes.KEY_ENTER:
               if ((okButton_ != null) && okButton_.isEnabled())
               {
                  nativeEvent.preventDefault();
                  nativeEvent.stopPropagation();
                  event.cancel();
                  okButton_.click();
               }
               break;
            case KeyCodes.KEY_ESCAPE:
               if (cancelButton_ == null)
               {
                  if ((okButton_ != null) && okButton_.isEnabled())
                  {
                     nativeEvent.preventDefault();
                     nativeEvent.stopPropagation();
                     event.cancel();
                     okButton_.click();
                  }
               }
               else if (cancelButton_.isEnabled())
               {
                  nativeEvent.preventDefault();
                  nativeEvent.stopPropagation();
                  event.cancel();
                  cancelButton_.click();
               }
               break;
         } 
      }
   }
   
  
   private void enableButtons(boolean enabled)
   {
      for (int i=0; i<allButtons_.size(); i++)
         allButtons_.get(i).setEnabled(enabled);
   }

   private static final ArrayList<ModalDialogBase> allActiveDialogs_ =
                                               new ArrayList<ModalDialogBase>();
  
   private SimplePanel containerPanel_;
   private VerticalPanel mainPanel_ ;
   private HorizontalPanel bottomPanel_;
   private HorizontalPanel buttonPanel_;
   private HorizontalPanel leftButtonPanel_;
   private ThemedButton okButton_;
   private ThemedButton cancelButton_;
   private ArrayList<ThemedButton> allButtons_ = new ArrayList<ThemedButton>();
   private Widget mainWidget_ ;
   private com.google.gwt.dom.client.Element originallyActiveElement_;
}
