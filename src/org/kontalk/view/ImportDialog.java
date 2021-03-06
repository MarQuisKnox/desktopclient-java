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

import com.alee.extended.filechooser.FilesSelectionListener;
import com.alee.extended.filechooser.WebFileChooserField;
import com.alee.extended.panel.GroupPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebPasswordField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.kontalk.misc.KonException;
import org.kontalk.model.Account;
import org.kontalk.util.Tr;

/**
 * Wizard-like dialog for importing new key files.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final class ImportDialog extends WebDialog {
    private final static Logger LOGGER = Logger.getLogger(ImportDialog.class.getName());

    private static enum ImportPage {INTRO, SETTINGS, RESULT};
    private static enum Direction {BACK, FORTH};

    private final EnumMap<ImportPage, WebPanel> mPanels;
    private final WebButton mBackButton;
    private final WebButton mNextButton;
    private final WebButton mCancelButton;
    private final WebButton mFinishButton;

    private final WebFileChooserField mZipFileChooser;
    private final WebPasswordField mPassField;

    private final WebLabel mResultLabel;
    private final WebLabel mErrorLabel;

    private ImportPage mCurrentPage;

    ImportDialog(final View view, final boolean connect) {
        this.setTitle(Tr.tr("Import Wizard"));
        this.setSize(420, 260);

        this.setResizable(false);
        this.setModal(true);

        mZipFileChooser = createFileChooser(".kontalk-keys.zip");
        mPassField = new WebPasswordField(42);

        mResultLabel = new WebLabel();
        mErrorLabel = new WebLabel();

        // panels
        mPanels = new EnumMap<>(ImportPage.class);
        mPanels.put(ImportPage.INTRO, new IntroPanel());
        mPanels.put(ImportPage.SETTINGS, new SettingsPanel());
        mPanels.put(ImportPage.RESULT, new ResultPanel());

        // buttons
        mBackButton = new WebButton(Tr.tr("Back"));
        mBackButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ImportDialog.this.switchPage(Direction.BACK);
            }
        });
        mNextButton = new WebButton(Tr.tr("Next"));
        mNextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ImportDialog.this.switchPage(Direction.FORTH);
            }
        });
        mCancelButton = new WebButton(Tr.tr("Cancel"));
        mCancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ImportDialog.this.dispose();
            }
        });
        mFinishButton = new WebButton(Tr.tr("Finish"));
        mFinishButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ImportDialog.this.dispose();
                if (connect)
                    view.callConnect();
            }
        });
        mFinishButton.setVisible(false);

        GroupPanel buttonPanel = new GroupPanel(2, mBackButton, mNextButton, mCancelButton, mFinishButton);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        this.add(buttonPanel, BorderLayout.SOUTH);

        this.updatePage(ImportPage.INTRO);
    }

    private void switchPage(Direction dir) {
        int step = dir == Direction.BACK ? -1 : +1;
        ImportPage[] pages = ImportPage.values();
        ImportPage newPage = pages[mCurrentPage.ordinal() + step];
        this.remove(mPanels.get(mCurrentPage));
        this.updatePage(newPage);
    }

    private void updatePage(ImportPage newPage) {
        mCurrentPage = newPage;
        switch (mCurrentPage) {
            case INTRO :
                mBackButton.setVisible(false);
                mNextButton.setEnabled(true);
                break;
            case SETTINGS :
                mBackButton.setVisible(true);
                mNextButton.setVisible(true);
                mCancelButton.setVisible(true);
                mFinishButton.setVisible(false);
                this.checkNextButton();
                break;
            case RESULT :
                boolean success = ImportDialog.this.importAccount();
                mNextButton.setVisible(false);
                if (success) {
                    mCancelButton.setVisible(false);
                    mFinishButton.setVisible(true);
                }
                break;
        }
        this.add(mPanels.get(mCurrentPage), BorderLayout.CENTER);
        // swing is messy again
        this.repaint();
    }

    private void checkNextButton() {
        mNextButton.setEnabled(!mZipFileChooser.getSelectedFiles().isEmpty() &&
                        !String.valueOf(mPassField.getPassword()).isEmpty());
    }

    private boolean importAccount() {
        if (mZipFileChooser.getSelectedFiles().isEmpty()) {
            LOGGER.warning("no zip file selected");
            return false;
        }
        String zipPath = mZipFileChooser.getSelectedFiles().get(0).getAbsolutePath();
        String password = new String(mPassField.getPassword());

        String errorText = null;
        try {
            Account.getInstance().importAccount(zipPath, password);
        } catch (KonException ex) {
            errorText = View.getErrorText(ex);
        }

        String result = errorText == null ? Tr.tr("Success!") : Tr.tr("Error");
        mResultLabel.setText(Tr.tr("Import process finished with:")+" "+result);
        mErrorLabel.setText(errorText == null ?
                "" :
                "<html>"+Tr.tr("Error description:")+" \n\n"+errorText+"</html>");
        return errorText == null;
    }

    private class IntroPanel extends WebPanel {

        IntroPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel(Tr.tr("Get Started")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            // html tag for word wrap
            String text = "<html>"+
                    Tr.tr("Welcome to the import wizard.")+" "
                    +Tr.tr("To use the Kontalk desktop client you need an existing account.")+" "
                    +Tr.tr("Please export the key files from your Android device and select them on the next page.")
                    +"</html>";
            groupPanel.add(new WebLabel(text));

            this.add(groupPanel);
        }

    }

    private class SettingsPanel extends WebPanel {

         SettingsPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel(Tr.tr("Setup")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            // file chooser for key files
            groupPanel.add(new WebLabel(Tr.tr("Zip archive containing personal key:")));

            mZipFileChooser.addSelectedFilesListener(new FilesSelectionListener() {
                @Override
                public void selectionChanged(List<File> files) {
                        ImportDialog.this.checkNextButton();
                }
            });
            groupPanel.add(mZipFileChooser);
            groupPanel.add(new WebSeparator(true, true));

            // text field for passphrase
            groupPanel.add(new WebLabel(Tr.tr("Decryption password for key:")));
            mPassField.setInputPrompt(Tr.tr("Enter password..."));
            mPassField.setHideInputPromptOnFocus(false);
            mPassField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    ImportDialog.this.checkNextButton();
                }
                @Override
                public void removeUpdate(DocumentEvent e) {
                    ImportDialog.this.checkNextButton();
                }
                @Override
                public void changedUpdate(DocumentEvent e) {
                    ImportDialog.this.checkNextButton();
                }
            });
            groupPanel.add(mPassField);

            WebCheckBox showPasswordBox = new WebCheckBox(Tr.tr("Show password"));
            showPasswordBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    boolean selected = e.getStateChange() == ItemEvent.SELECTED;
                    mPassField.setEchoChar(selected ? (char)0 : '*');
                }
            });
            groupPanel.add(showPasswordBox);

            this.add(groupPanel);
        }

    }

    private static WebFileChooserField createFileChooser(String path) {
        final WebFileChooserField fileChooser = new WebFileChooserField();
        fileChooser.setPreferredWidth(100);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setShowFileShortName(false);
        fileChooser.setShowRemoveButton(false);
        fileChooser.getWebFileChooser().setFileFilter(new FileNameExtensionFilter(Tr.tr("Zip archive"), "zip"));
        File file = new File(path);
        if (file.exists()) {
            fileChooser.setSelectedFile(file);
        } else {
            fileChooser.setBorderColor(Color.RED);
        }

        if (file.getParentFile() != null && file.getParentFile().exists())
            fileChooser.getWebFileChooser().setCurrentDirectory(file.getParentFile());

        fileChooser.addSelectedFilesListener(new FilesSelectionListener() {
            @Override
            public void selectionChanged(List<File> files) {
                for (File file : files) {
                    if (file.exists()) {
                        fileChooser.setBorderColor(Color.BLACK);
                    }
                }
            }
        });

        return fileChooser;
    }

    private class ResultPanel extends WebPanel {

        ResultPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel(Tr.tr("Import results")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            groupPanel.add(mResultLabel);
            groupPanel.add(mErrorLabel);

            this.add(groupPanel);
        }

    }

}
