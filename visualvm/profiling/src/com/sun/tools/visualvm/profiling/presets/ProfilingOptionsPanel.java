/*
 * Copyright 2007-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.visualvm.profiling.presets;

import com.sun.tools.visualvm.core.ui.components.SectionSeparator;
import com.sun.tools.visualvm.core.datasupport.Utils;
import com.sun.tools.visualvm.core.options.UISupport;
import com.sun.tools.visualvm.profiling.presets.ProfilerPresets.PresetsModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.io.File;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.netbeans.lib.profiler.global.Platform;
import org.openide.awt.Mnemonics;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Jiri Sedlacek
 */
final class ProfilingOptionsPanel extends JPanel {

    final private static Logger LOGGER =
            Logger.getLogger("com.sun.tools.visualvm.profiling.options"); // NOI18N
    private final ProfilingOptionsPanelController controller;

    private final SamplerCPUSettings samplerCpuSettings;
    private final SamplerMemorySettings samplerMemorySettings;
    private final ProfilerCPUSettings profilerCpuSettings;
    private final ProfilerMemorySettings profilerMemorySettings;

    private PresetsModel listModel;
    private ListDataListener listModelListener;

    private boolean internalChange;

    private boolean nameValid = true;


    ProfilingOptionsPanel(ProfilingOptionsPanelController controller) {
        this.controller = controller;

        Runnable validator = new Runnable() {
            public void run() {
                ProfilerPreset preset = (ProfilerPreset)list.getSelectedValue();
                if (preset == null) return;
                preset.setValid(samplerCpuSettings.valid() &&
                                profilerCpuSettings.valid());
                ProfilingOptionsPanel.this.controller.changed();
            }
        };

        samplerCpuSettings = new SamplerCPUSettings(validator);
        samplerMemorySettings = new SamplerMemorySettings();
        profilerCpuSettings = new ProfilerCPUSettings(validator);
        profilerMemorySettings = new ProfilerMemorySettings();

        listModelListener = new ListDataListener() {
            public void intervalAdded(ListDataEvent e) {
                updateComponents();
            }
            public void intervalRemoved(ListDataEvent e) {
                updateComponents();
            }
            public void contentsChanged(ListDataEvent e) {}
        };

        initComponents();
    }


    private void updateComponents() {
        int selectedIndex = listModel.isEmpty() ? -1 : list.getSelectedIndex();
        if (selectedIndex == listModel.getSize()) return; // isAdjusting
        
        removeButton.setEnabled(selectedIndex != -1);
        upButton.setEnabled(selectedIndex > 0);
        downButton.setEnabled(selectedIndex < listModel.getSize() - 1);

        refreshPreset(selectedIndex);
    }

    private String createPresetName() {
        Set<String> names = new HashSet();
        Enumeration presetsE = listModel.elements();
        while (presetsE.hasMoreElements())
            names.add(presetsE.nextElement().toString());

        int presetIndex = 1;
        String name = "Preset ";

        while (names.contains(name + presetIndex)) presetIndex++;

        return name + presetIndex;
    }

    private void createPreset() {
        ProfilerPreset preset = new ProfilerPreset(createPresetName(), "");
        listModel.addPreset(preset);
        list.setSelectedIndex(listModel.getSize() - 1);
        preselectNameField();
    }

    private void preselectNameField() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                nameField.requestFocusInWindow();
                nameField.selectAll();
            }
        });
    }

    private void deletePreset() {
        int selectedIndex = list.getSelectedIndex();
        listModel.removePreset(selectedIndex);
        if (listModel.getSize() > 0)
            list.setSelectedIndex(selectedIndex == listModel.getSize() ?
                                  selectedIndex - 1 : selectedIndex);
    }

    private void movePresetUp() {
        int selectedIndex = list.getSelectedIndex();
        listModel.movePresetUp(selectedIndex);
        list.setSelectedIndex(selectedIndex - 1);
    }

    private void movePresetDown() {
        int selectedIndex = list.getSelectedIndex();
        listModel.movePresetDown(selectedIndex);
        list.setSelectedIndex(selectedIndex + 1);
    }

    private void refreshPreset(int presetIndex) {
        ProfilerPreset preset = presetIndex == -1 ? new ProfilerPreset("", "") : // NOI18N
                                (ProfilerPreset)listModel.get(presetIndex);

        internalChange = true;
        nameField.setText(preset.getName());
        targetField.setText(preset.getSelector());
        internalChange = false;

        samplerCpuSettings.setPreset(preset);
        samplerMemorySettings.setPreset(preset);
        profilerCpuSettings.setPreset(preset);
        profilerMemorySettings.setPreset(preset);

        presetsPanel.setEnabled(presetIndex != -1);
    }

    private void updatePreset() {
        if (internalChange) return;
        ProfilerPreset preset = (ProfilerPreset)listModel.get(list.getSelectedIndex());

        preset.setName(nameField.getText());
        preset.setSelector(targetField.getText());

        nameValid = !nameField.getText().isEmpty();

        controller.changed();
    }

    private void initComponents() {
        final boolean nimbusLaF =
                com.sun.tools.visualvm.uisupport.UISupport.isNimbusLookAndFeel();

        GridBagConstraints c;

        setLayout(new GridBagLayout());

        // --- Presets ---------------------------------------------------------
        SectionSeparator presetsSection = UISupport.createSectionSeparator("Presets");
        c = new GridBagConstraints();
        c.gridy = 0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 5, 0);
        add(presetsSection, c);

        JPanel listPanel = new JPanel(new BorderLayout());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.weighty = 0.5;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(3, 15, 3, 0);
        add(listPanel, c);

        list = new JList();
        list.setSelectionModel(new DefaultListSelectionModel() {
            public void setSelectionInterval(int index0, int index1) {
                super.setSelectionInterval(index0, index1);
                updateComponents();
            }
            public void removeSelectionInterval(int i1, int i2) {}
        });
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        final Dimension oneDim = new Dimension(1, 1);
        final JLabel noPresetsLabel = new JLabel("<No Presets Defined>", JLabel.CENTER);
        noPresetsLabel.setEnabled(false);
        noPresetsLabel.setSize(noPresetsLabel.getPreferredSize());
        JScrollPane listScroll = new JScrollPane(list) {
            public Dimension getPreferredSize() {
                return oneDim;
            }
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }
            protected void paintChildren(Graphics g) {
                super.paintChildren(g);
                if (listModel == null || listModel.getSize() == 0) {
                    int x = (getWidth() - noPresetsLabel.getWidth()) / 2;
                    int y = (getHeight() - noPresetsLabel.getHeight()) / 2;
                    g.translate(x, y);
                    noPresetsLabel.paint(g);
                    g.translate(-x, -y);
                }
            }
        };
        listPanel.add(listScroll, BorderLayout.CENTER);
        
        addButton = new JButton() {
            protected void fireActionPerformed(ActionEvent e) {
                createPreset();
            }
        };
        addButton.setIcon(new ImageIcon(ImageUtilities.loadImage(
                "com/sun/tools/visualvm/profiler/resources/add.png", true)));   // NOI18N
        addButton.setToolTipText("Create new preset");
        Insets margin = addButton.getMargin();
        int mar = nimbusLaF ? 0 : 8;
        margin.left = mar;
        margin.right = mar;
        addButton.setMargin(margin);
        removeButton = new JButton() {
            protected void fireActionPerformed(ActionEvent e) {
                deletePreset();
            }
        };
        removeButton.setIcon(new ImageIcon(ImageUtilities.loadImage(
                "com/sun/tools/visualvm/profiler/resources/remove.png", true)));   // NOI18N
        removeButton.setToolTipText("Delete selected preset");
        removeButton.setMargin(margin);
        upButton = new JButton() {
            protected void fireActionPerformed(ActionEvent e) {
                movePresetUp();
            }
        };
        upButton.setIcon(new ImageIcon(ImageUtilities.loadImage(
                "com/sun/tools/visualvm/profiler/resources/up.png", true)));   // NOI18N
        upButton.setToolTipText("Move selected preset up");
        upButton.setMargin(margin);
        downButton = new JButton() {
            protected void fireActionPerformed(ActionEvent e) {
                movePresetDown();
            }
        };
        downButton.setIcon(new ImageIcon(ImageUtilities.loadImage(
                "com/sun/tools/visualvm/profiler/resources/down.png", true)));   // NOI18N
        downButton.setToolTipText("Move selected preset down");
        downButton.setMargin(margin);

        JPanel controlsPanel = new JPanel(new GridLayout(1, 4, 5, 0)) {
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                for (Component c : getComponents())
                    c.setEnabled(enabled);
            }
        };
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        controlsPanel.add(addButton);
        controlsPanel.add(removeButton);
        controlsPanel.add(upButton);
        controlsPanel.add(downButton);
        listPanel.add(controlsPanel, BorderLayout.SOUTH);

        JPanel headerPanel = new JPanel(new GridBagLayout()) {
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                for (Component c : getComponents())
                    c.setEnabled(enabled);
            }
        };

        JLabel nameLabel = new JLabel("Preset Name:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(3, 3, 3, 0);
        headerPanel.add(nameLabel, c);

        nameField = new JTextField();
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updatePreset(); listModel.fireItemChanged(list.getSelectedIndex()); }
            public void removeUpdate(DocumentEvent e) { updatePreset(); listModel.fireItemChanged(list.getSelectedIndex()); }
            public void changedUpdate(DocumentEvent e) { updatePreset(); listModel.fireItemChanged(list.getSelectedIndex()); }
        });
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1;
        c.weighty = 1;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 5, 3, 0);
        headerPanel.add(nameField, c);

        JLabel targetLabel = new JLabel("Preselect For:");
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(3, 3, 13, 0);
        headerPanel.add(targetLabel, c);

        final JLabel noTargetLabel = new JLabel("[Optional Main Class or Display Name]", JLabel.CENTER);
        noTargetLabel.setEnabled(false);
        noTargetLabel.setSize(noTargetLabel.getPreferredSize());
        targetField = new JTextField() {
            protected void paintChildren(Graphics g) {
                super.paintChildren(g);
                String text = getText();
                if (!isFocusOwner() && (text == null || text.isEmpty())) {
                    int x = nimbusLaF ? 6 : 2;
                    int y = (getHeight() - noTargetLabel.getHeight()) / 2;
                    g.translate(x, y);
                    noTargetLabel.paint(g);
                    g.translate(-x, -y);
                }
            }
            protected void processFocusEvent(FocusEvent e) {
                super.processFocusEvent(e);
                repaint();
            }
        };
        targetField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updatePreset(); }
            public void removeUpdate(DocumentEvent e) { updatePreset(); }
            public void changedUpdate(DocumentEvent e) { updatePreset(); }
        });
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 5, 13, 0);
        headerPanel.add(targetField, c);

        JTabbedPane settingsPanel = new JTabbedPane() {
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                for (Component c : getComponents())
                    c.setEnabled(enabled);
            }
        };
        settingsPanel.addTab("Sampler CPU", new ImageIcon(ImageUtilities.loadImage(
                "com/sun/tools/visualvm/profiling/resources/sampler.png", true)), // NOI18N
                samplerCpuSettings);
        settingsPanel.addTab("Sampler Memory", new ImageIcon(ImageUtilities.loadImage(
                "com/sun/tools/visualvm/profiling/resources/sampler.png", true)), // NOI18N
                samplerMemorySettings);
        settingsPanel.addTab("Profiler CPU", new ImageIcon(ImageUtilities.loadImage(
                "com/sun/tools/visualvm/profiling/resources/profiler.png", true)), // NOI18N
                profilerCpuSettings);
        settingsPanel.addTab("Profiler Memory", new ImageIcon(ImageUtilities.loadImage(
                "com/sun/tools/visualvm/profiling/resources/profiler.png", true)), // NOI18N
                profilerMemorySettings);

        presetsPanel = new JPanel(new BorderLayout()) {
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                for (Component c : getComponents())
                    c.setEnabled(enabled);
            }
        };
        presetsPanel.add(headerPanel, BorderLayout.NORTH);
        presetsPanel.add(settingsPanel, BorderLayout.CENTER);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 1;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(3, 8, 3, 0);
        add(presetsPanel, c);


        // --- Misellaneous ----------------------------------------------------
        SectionSeparator miscellaneousSection = UISupport.createSectionSeparator("Miscellaneous");
        c = new GridBagConstraints();
        c.gridy = 50;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(15, 0, 5, 0);
        add(miscellaneousSection, c);

        JPanel resetCalibrationPanel = new JPanel(new BorderLayout());

        JLabel resetCalibrationLabel = new JLabel();
        Mnemonics.setLocalizedText(resetCalibrationLabel, NbBundle.getMessage(
                                   ProfilingOptionsPanel.class, "LBL_ResetData")); // NOI18N
        resetCalibrationPanel.add(resetCalibrationLabel, BorderLayout.CENTER);

        resetCalibrationButton = new JButton() {
            protected void fireActionPerformed(ActionEvent e) {
                resetCalibrationButtonAction();
            }
        };
        Mnemonics.setLocalizedText(resetCalibrationButton, NbBundle.getMessage(
                                   ProfilingOptionsPanel.class, "BTN_Reset2")); // NOI18N
        resetCalibrationPanel.add(resetCalibrationButton, BorderLayout.EAST);

        c = new GridBagConstraints();
        c.gridy = 52;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 15, 3, 0);
        add(resetCalibrationPanel, c);

    }

    private void resetCalibrationButtonAction() {
        resetCalibrationButton.setEnabled(false);
        RequestProcessor.getDefault().post(new Runnable() {
            public void run() {
                try {
                    File calibrationDirectory = new File(Platform.getProfilerUserDir());
                    if (calibrationDirectory.isDirectory()) {
                        File[] calibrationFiles = calibrationDirectory.listFiles();
                        for (File calibrationFile : calibrationFiles) {
                            if (calibrationFile.isFile() && calibrationFile.getName().startsWith("machinedata.")) // NOI18N
                                Utils.delete(calibrationFile, false);

                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Error resetting calibration data", e);  // NOI18N
                }
            }
        });
    }

    void load() {
        listModel = ProfilerPresets.getInstance().getPresets();
        listModel.addListDataListener(listModelListener);
        list.setModel(listModel);
        int items = listModel.getSize();

        ProfilerPreset toCreate = ProfilerPresets.getInstance().presetToCreate();
        if (toCreate != null) {
            toCreate.setName(createPresetName());
            listModel.addElement(toCreate);
            list.setSelectedIndex(items);
        } else if (listModel.size() > 0) {
            ProfilerPreset select = ProfilerPresets.getInstance().presetToSelect();
            String toSelect = select == null ? null : select.getName();
            int indexToSelect = 0;
            if (toSelect != null) {
                for (int i = 0; i < items; i++) {
                    ProfilerPreset preset = (ProfilerPreset)listModel.get(i);
                    if (preset.getName().equals(toSelect)) {
                        indexToSelect = i;
                        break;
                    }
                }
            }  
            list.setSelectedIndex(indexToSelect);
        }

        resetCalibrationButton.setEnabled(true);

        updateComponents();

        if (toCreate != null) preselectNameField();
    }

    void store() {
        ProfilerPresets.getInstance().savePresets(listModel);
        ProfilerPreset selected = (ProfilerPreset)list.getSelectedValue();
        ProfilerPresets.getInstance().optionsSubmitted(selected);
    }

    void closed() {
        if (listModel != null) listModel.removeListDataListener(listModelListener);
        list.setModel(new DefaultListModel());
    }

    boolean valid() {
        return nameValid && presetsValid();
    }
    
    private boolean presetsValid() {
        Enumeration presets = listModel.elements();
        while (presets.hasMoreElements()) {
            ProfilerPreset preset = (ProfilerPreset)presets.nextElement();
            if (!preset.isValid()) return false;
        }
        return true;
    }


    private JPanel presetsPanel;
    private JList list;
    private JButton addButton;
    private JButton removeButton;
    private JButton upButton;
    private JButton downButton;
    private JTextField nameField;
    private JTextField targetField;
    private JButton resetCalibrationButton;
    
}