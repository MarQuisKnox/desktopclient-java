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

import com.alee.extended.filechooser.WebFileChooserField;
import com.alee.extended.filefilter.ImageFilesFilter;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.GroupingType;
import com.alee.laf.button.WebButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebFormattedTextField;
import com.alee.laf.text.WebTextField;
import com.alee.managers.tooltip.TooltipManager;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import javax.swing.JFrame;
import javax.swing.text.NumberFormatter;
import org.kontalk.system.KonConf;
import org.kontalk.misc.KonException;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.model.Account;

/**
 * Dialog for showing and changing all application options.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final class ConfigurationDialog extends WebDialog {

    private static enum ConfPage {MAIN, ACCOUNT};

    private final KonConf mConf = KonConf.getInstance();
    private final View mView;

    ConfigurationDialog(JFrame owner, final View view) {
        super(owner);

        mView = view;
        this.setTitle("Preferences");
        this.setSize(550, 500);
        this.setResizable(false);
        this.setModal(true);
        this.setLayout(new BorderLayout(5, 5));

        WebTabbedPane tabbedPane = new WebTabbedPane(WebTabbedPane.LEFT);
        final MainPanel mainPanel = new MainPanel();
        final AccountPanel accountPanel = new AccountPanel();
        final PrivacyPanel privacyPanel = new PrivacyPanel();
        tabbedPane.addTab("Main", mainPanel);
        tabbedPane.addTab("Account", accountPanel);
        tabbedPane.addTab("Privacy", privacyPanel);

        this.add(tabbedPane, BorderLayout.CENTER);

        // buttons
        WebButton cancelButton = new WebButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ConfigurationDialog.this.dispose();
            }
        });
        WebButton saveButton = new WebButton("Save");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mainPanel.saveConfiguration();
                accountPanel.saveConfiguration();
                privacyPanel.saveConfiguration();
                ConfigurationDialog.this.dispose();
            }
        });

        GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        this.add(buttonPanel, BorderLayout.SOUTH);
    }

    private class MainPanel extends WebPanel {

        private final WebCheckBox mConnectStartupBox;
        private final WebCheckBox mTrayBox;
        private final WebCheckBox mCloseTrayBox;
        private final WebCheckBox mEnterSendsBox;
        private final WebCheckBox mBGBox;
        private final WebFileChooserField mBGChooser;

        MainPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel("Main Settings").setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            mConnectStartupBox = new WebCheckBox("Connect on startup");
            mConnectStartupBox.setAnimated(false);
            mConnectStartupBox.setSelected(mConf.getBoolean(KonConf.MAIN_CONNECT_STARTUP));
            groupPanel.add(mConnectStartupBox);

            mTrayBox = new WebCheckBox("Show tray icon");
            mTrayBox.setAnimated(false);
            mTrayBox.setSelected(mConf.getBoolean(KonConf.MAIN_TRAY));
            mTrayBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    mCloseTrayBox.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
                }
            });
            mCloseTrayBox = new WebCheckBox("Close to tray");
            mCloseTrayBox.setAnimated(false);
            mCloseTrayBox.setSelected(mConf.getBoolean(KonConf.MAIN_TRAY_CLOSE));
            mCloseTrayBox.setEnabled(mTrayBox.isSelected());
            groupPanel.add(new GroupPanel(10, mTrayBox, mCloseTrayBox));

            mEnterSendsBox = new WebCheckBox("Enter key sends");
            mEnterSendsBox.setAnimated(false);
            mEnterSendsBox.setSelected(mConf.getBoolean(KonConf.MAIN_ENTER_SENDS));
            String enterSendsToolText = "Enter key sends text, Control+Enter adds new line "
                    + "- or vice versa";
            TooltipManager.addTooltip(mEnterSendsBox, enterSendsToolText);
            groupPanel.add(new GroupPanel(mEnterSendsBox, new WebSeparator()));

            mBGBox = new WebCheckBox("Custom background: ");
            mBGBox.setAnimated(false);
            String bgPath = mConf.getString(KonConf.VIEW_THREAD_BG);
            mBGBox.setSelected(!bgPath.isEmpty());
            mBGBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    mBGChooser.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
                    mBGChooser.getChooseButton().setEnabled(e.getStateChange() == ItemEvent.SELECTED);
                }
            });

            mBGChooser = new WebFileChooserField();
            mBGChooser.setEnabled(mBGBox.isSelected());
            mBGChooser.getChooseButton().setEnabled(mBGBox.isSelected());
            if (!bgPath.isEmpty())
                mBGChooser.setSelectedFile(new File(bgPath));
            mBGChooser.setMultiSelectionEnabled(false);
            mBGChooser.setShowRemoveButton(true);
            mBGChooser.getWebFileChooser().setFileFilter(new ImageFilesFilter());
            File file = new File(bgPath);
            if (file.exists()) {
                mBGChooser.setSelectedFile(file);
            }

            if (file.getParentFile() != null && file.getParentFile().exists())
                mBGChooser.getWebFileChooser().setCurrentDirectory(file.getParentFile());

            groupPanel.add(new GroupPanel(GroupingType.fillLast, mBGBox, mBGChooser));

            this.add(groupPanel);
        }

        private void saveConfiguration() {
            mConf.setProperty(KonConf.MAIN_CONNECT_STARTUP, mConnectStartupBox.isSelected());
            mConf.setProperty(KonConf.MAIN_TRAY, mTrayBox.isSelected());
            mConf.setProperty(KonConf.MAIN_TRAY_CLOSE, mCloseTrayBox.isSelected());
            mView.setTray();
            mConf.setProperty(KonConf.MAIN_ENTER_SENDS, mEnterSendsBox.isSelected());
            mView.setHotkeys();
            String bgPath;
            if (mBGBox.isSelected() && !mBGChooser.getSelectedFiles().isEmpty()) {
                bgPath = mBGChooser.getSelectedFiles().get(0).getAbsolutePath();
            } else {
                bgPath = "";
            }
            String oldBGPath = mConf.getString(KonConf.VIEW_THREAD_BG);
            if (!bgPath.equals(oldBGPath)) {
                mConf.setProperty(KonConf.VIEW_THREAD_BG, bgPath);
                mView.reloadThreadBG();
            }
        }
    }

    private class AccountPanel extends WebPanel {

        private final WebTextField mServerField;
        private final WebFormattedTextField mPortField;
        private final WebCheckBox mDisableCertBox;
        private final WebLabel mFingerprintLabel;

        AccountPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel("Account Configuration").setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            // server text field
            groupPanel.add(new WebLabel("Server address:"));
            WebPanel serverPanel = new WebPanel(false);
            mServerField = new WebTextField(mConf.getString(KonConf.SERV_HOST));
            mServerField.setInputPrompt(KonConf.DEFAULT_SERV_HOST);
            mServerField.setInputPromptFont(mServerField.getFont().deriveFont(Font.ITALIC));
            mServerField.setHideInputPromptOnFocus(false);
            serverPanel.add(mServerField);
            int port = mConf.getInt(KonConf.SERV_PORT, KonConf.DEFAULT_SERV_PORT);
            NumberFormat format = new DecimalFormat("#####");
            NumberFormatter formatter = new NumberFormatter(format);
            formatter.setMinimum(1);
            formatter.setMaximum(65535);
            mPortField = new WebFormattedTextField(formatter);
            mPortField.setColumns(4);
            mPortField.setValue(port);
            serverPanel.add(new GroupPanel(new WebLabel("  Port:"), mPortField),
                    BorderLayout.EAST);
            groupPanel.add(serverPanel);
            mDisableCertBox = new WebCheckBox("Disable certificate validation");
            mDisableCertBox.setAnimated(false);
            mDisableCertBox.setSelected(!mConf.getBoolean(KonConf.SERV_CERT_VALIDATION));
            String disableCertText = "Disable SSL certificate server validation";
            TooltipManager.addTooltip(mDisableCertBox, disableCertText);
            groupPanel.add(new GroupPanel(mDisableCertBox, new WebSeparator()));

            groupPanel.add(new WebSeparator(true, true));
            mFingerprintLabel = new WebLabel();
            this.updateFingerprint();
            groupPanel.add(mFingerprintLabel);

            WebButton importButton = new WebButton("Import new Account");
            importButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    mView.showImportWizard(false);
                    AccountPanel.this.updateFingerprint();
                }
            });
            groupPanel.add(importButton);

            this.add(groupPanel, BorderLayout.CENTER);


            WebButton okButton = new WebButton("Save & Connect");
            okButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    AccountPanel.this.saveConfiguration();
                    ConfigurationDialog.this.dispose();
                    mView.callConnect();
                }
            });

            GroupPanel buttonPanel = new GroupPanel(okButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);
        }

        private void updateFingerprint() {
            PersonalKey personalKey = null;
            try {
                personalKey = Account.getInstance().getPersonalKey();
            } catch (KonException ex) {
                // ignore
            }
            String fingerprint = "- no key loaded -";
            if (personalKey != null)
                fingerprint = personalKey.getFingerprint();
            mFingerprintLabel.setText("Key fingerprint: "+fingerprint);
        }

        private void saveConfiguration() {
            mConf.setProperty(KonConf.SERV_HOST, mServerField.getText());
            int port = Integer.parseInt(mPortField.getText());
            mConf.setProperty(KonConf.SERV_PORT, port);
            mConf.setProperty(KonConf.SERV_CERT_VALIDATION, !mDisableCertBox.isSelected());
        }

    }

    private class PrivacyPanel extends WebPanel {

        private final WebCheckBox mChatStateBox;

        PrivacyPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel("Privacy Settings").setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            mChatStateBox = new WebCheckBox("Send chatstate notification");
            mChatStateBox.setAnimated(false);
            mChatStateBox.setSelected(mConf.getBoolean(KonConf.NET_SEND_CHAT_STATE));

            groupPanel.add(mChatStateBox);

            this.add(groupPanel);
        }

        private void saveConfiguration() {
            mConf.setProperty(KonConf.NET_SEND_CHAT_STATE, mChatStateBox.isSelected());
        }
    }
}
