/*
 *  Kontalk Java client
 *  Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.view;

import com.alee.extended.statusbar.WebStatusBar;
import com.alee.extended.statusbar.WebStatusLabel;
import com.alee.laf.StyleConstants;
import com.alee.laf.button.WebButton;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.managers.hotkey.Hotkey;
import com.alee.managers.notification.NotificationListener;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.NotificationOption;
import com.alee.managers.notification.WebNotificationPopup;
import com.alee.managers.popup.PopupStyle;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JTextField;
import javax.swing.ToolTipManager;
import org.kontalk.KonConf;
import org.kontalk.KonException;
import org.kontalk.Kontalk;
import org.kontalk.model.KonThread;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class View {
    private final static Logger LOGGER = Logger.getLogger(View.class.getName());

    final static Color BLUE = new Color(130, 170, 240);
    final static Color LIGHT_BLUE = new Color(220, 220, 250);

    private final static Icon NOTIFICATION_ICON = getIcon("ic_msg_pending.png");
    private final static String ICON_PATH = "org/kontalk/res/";

    private final Kontalk mModel;
    private final UserListView mUserListView;
    private final ThreadListView mThreadListView;
    private final ThreadView mThreadView;
    private final JTextField mSendTextField;
    private final WebButton mSendButton;
    private final WebStatusLabel mStatusBarLabel;
    private final MainFrame mMainFrame;
    private TrayIcon mTrayIcon;

    public View(Kontalk model) {
        mModel = model;

        ToolTipManager.sharedInstance().setInitialDelay(200);

        mUserListView = new UserListView(this, UserList.getInstance());
        mThreadListView = new ThreadListView(this, ThreadList.getInstance());
        // notify threadlist of changes in user list
        UserList.getInstance().addObserver(mThreadListView);

        mThreadView = new ThreadView();

        // text field
        mSendTextField = new JTextField();
        //this.textField.setColumns(25);
        mSendTextField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                View.this.sendText();
            }
        });

        // send button
        mSendButton = new WebButton("Send");
        mSendButton.addHotkey(Hotkey.CTRL_S);
        mSendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                View.this.sendText();
            }
        });

        // status bar
        WebStatusBar statusBar = new WebStatusBar();
        mStatusBarLabel = new WebStatusLabel(" ");
        statusBar.add(mStatusBarLabel);

        // main frame
        mMainFrame = new MainFrame(this, mUserListView, mThreadListView,
                mThreadView, mSendTextField, mSendButton, statusBar);
        mMainFrame.setVisible(true);

        // tray
        this.setTray();

        // TODO: always disconnected?
        this.statusChanged(Kontalk.Status.DISCONNECTED);
    }

    /**
     * Setup view on startup.
     */
    public void init() {
        mThreadListView.selectLastThread();
    }

    public final void statusChanged(Kontalk.Status status) {
        switch (status) {
            case CONNECTING:
                mStatusBarLabel.setText("Connecting...");
                break;
            case CONNECTED:
                mThreadView.setColor(Color.white);
                mStatusBarLabel.setText("Connected");
                break;
            case DISCONNECTING:
                mStatusBarLabel.setText("Disconnecting...");
                break;
            case DISCONNECTED:
                mThreadView.setColor(Color.lightGray);
                mStatusBarLabel.setText("Not connected");
                //if (mTrayIcon != null)
                //    trayIcon.setImage(updatedImage);
                break;
            case SHUTTING_DOWN:
                mStatusBarLabel.setText("Shutting down...");
                mMainFrame.save();
                mThreadListView.save();
                break;
            case FAILED:
                mStatusBarLabel.setText("Connecting failed");
                break;
            }

        mMainFrame.statusChanged(status);
    }

    public void showConfig() {
        this.showConfig("Default text here");
    }

    public void connectionProblem(KonException ex) {
        this.showConfig("Help Message here");
    }

    final void setTray() {
        if (!KonConf.getInstance().getBoolean(KonConf.MAIN_TRAY)) {
            if (mTrayIcon != null) {
                // remove tray icon
                SystemTray tray = SystemTray.getSystemTray();
                tray.remove(mTrayIcon);
                mTrayIcon = null;
            }
            return;
        }

        if (!SystemTray.isSupported()) {
            LOGGER.info("tray icon not supported");
            return;
        }

        if (mTrayIcon != null)
            // already set
            return;

        // load image
        Image image = getImage("kontalk.png");
        //image = image.getScaledInstance(22, 22, Image.SCALE_SMOOTH);

        // TODO popup menu
        final WebPopupMenu popup = new WebPopupMenu("Kontalk");
        WebMenuItem quitItem = new WebMenuItem("Quit");
        quitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                View.this.shutDown();
            }
        });
        popup.add(quitItem);

        //final Frame frame = new Frame("");
        //frame.setVisible(true);

        // create a action listener to listen for default action executed on the tray icon
        MouseListener listener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                check(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1)
                    mMainFrame.toggleState();
                else
                    check(e);
            }
            private void check(MouseEvent e) {
                if (!e.isPopupTrigger())
                    return;

                // TODO ugly
                popup.setLocation(e.getX() - 20, e.getY() - 40);
                popup.setInvoker(popup);
                popup.setVisible(true);
            }
        };

        mTrayIcon = new TrayIcon(image, "Kontalk" /*, popup*/);
        mTrayIcon.setImageAutoSize(true);
        mTrayIcon.addMouseListener(listener);

        SystemTray tray = SystemTray.getSystemTray();
        try {
            tray.add(mTrayIcon);
        } catch (AWTException ex) {
            LOGGER.log(Level.WARNING, "can't add tray icon", ex);
        }
    }

    private void showConfig(String helpText) {
        JDialog configFrame = new ConfigurationDialog(mMainFrame, this, helpText);
        configFrame.setVisible(true);
    }

    void shutDown() {
        mModel.shutDown();
    }

    void connect() {
        mModel.connect();
    }

    void disconnect() {
        mModel.disconnect();
    }

    void selectThreadByUser(User user) {
        if (user == null)
            return;

        KonThread thread = ThreadList.getInstance().getThreadByUser(user);
        mThreadListView.selectThread(thread.getID());
        mMainFrame.selectTab(MainFrame.Tab.THREADS);
        mThreadView.showThread(thread);
    }

    void selectedThreadChanged(KonThread thread) {
        if (thread == null)
            return;

        thread.setRead();
        mThreadView.showThread(thread);
    }

    private void sendText() {
       KonThread thread = mThreadListView.getSelectedThread();
       if (thread == null) {
           // TODO
           // nothing selected
           return;
       }
       mModel.sendText(thread, mSendTextField.getText());
       mSendTextField.setText("");
    }

    void showNotification() {

        final WebDialog dialog = new WebDialog();
        dialog.setUndecorated(true);
        dialog.setBackground(Color.BLACK);
        dialog.setBackground(StyleConstants.transparent);

        WebNotificationPopup popup = new WebNotificationPopup(PopupStyle.dark);
        popup.setIcon(getIcon("kontalk_small.png"));
        popup.setMargin(10);
        popup.setDisplayTime(6000);
        popup.addNotificationListener(new NotificationListener() {
            @Override
            public void optionSelected(NotificationOption option) {
            }
            @Override
            public void accepted() {
            }
            @Override
            public void closed() {
                dialog.dispose();
            }
        });

        // content
        WebPanel panel = new WebPanel();
        panel.setMargin(10);
        panel.setOpaque(false);
        WebLabel title = new WebLabel("A new Message!");
        title.setFontSize(14);
        title.setForeground(Color.WHITE);
        panel.add(title, BorderLayout.NORTH);
        String text = "this is some message, and some longer text was added";
        WebLabel message = new WebLabel(text);
        message.setForeground(Color.WHITE);
        panel.add(message, BorderLayout.CENTER);
        popup.setContent(panel);

        //popup.packPopup();
        dialog.setSize(popup.getPreferredSize());

        // set position on screen
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        Rectangle screenBounds = gc.getBounds();
        // get height of the task bar
        // doesn't work on all environments
        //Insets toolHeight = toolkit.getScreenInsets(popup.getGraphicsConfiguration());
        int toolHeight  = 40;
        dialog.setLocation(screenBounds.width - dialog.getWidth() - 10,
                screenBounds.height - toolHeight - dialog.getHeight());

        dialog.setVisible(true);
        NotificationManager.showNotification(dialog, popup);
    }

    static Icon getIcon(String fileName) {
        return new ImageIcon(getImage(fileName));
    }

    static Image getImage(String fileName) {
        URL imageUrl = ClassLoader.getSystemResource(ICON_PATH + fileName);
        if (imageUrl == null) {
            LOGGER.warning("can't find icon image resource");;
            return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        }
        return Toolkit.getDefaultToolkit().createImage(imageUrl);
    }
}
