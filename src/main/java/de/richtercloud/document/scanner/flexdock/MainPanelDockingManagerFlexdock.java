/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.richtercloud.document.scanner.flexdock;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import javax.swing.GroupLayout;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.commons.lang3.tuple.Pair;
import org.flexdock.docking.Dockable;
import org.flexdock.docking.DockingConstants;
import org.flexdock.docking.DockingManager;
import org.flexdock.docking.DockingPort;
import org.flexdock.docking.defaults.DefaultDockingPort;
import org.flexdock.docking.defaults.DockableComponentWrapper;
import de.richtercloud.document.scanner.ifaces.Constants;
import de.richtercloud.document.scanner.ifaces.EntityPanel;
import de.richtercloud.document.scanner.ifaces.MainPanel;
import de.richtercloud.document.scanner.ifaces.MainPanelDockingManager;
import de.richtercloud.document.scanner.ifaces.OCRPanel;
import de.richtercloud.document.scanner.ifaces.OCRSelectComponent;

/**
 *
 * @author richter
 */
public class MainPanelDockingManagerFlexdock implements MainPanelDockingManager {
    static {
        DockingManager.setFloatingEnabled(true);
    }
    private DefaultDockingPort port = new DefaultDockingPort();
    private MainPanel mainPanel;

    @Override
    public void init(JFrame dockingControlFrame,
            MainPanel mainPanel) {
        this.mainPanel = mainPanel;
    }

    @Override
    public void addDocumentDockable(OCRSelectComponent old,
            OCRSelectComponent aNew) {
        Dockable aNewDockable;
        boolean success;
        if(old != null) {
            success = DockingManager.dock(aNew, old);
        }else {
            success = DockingManager.dock(aNew, (DockingPort)port, DockingConstants.NORTH_REGION);
            GroupLayout mainPanelLayout = mainPanel.getLayout();
            mainPanelLayout.setHorizontalGroup(mainPanelLayout.createSequentialGroup().addComponent(port));
            mainPanelLayout.setVerticalGroup(mainPanelLayout.createSequentialGroup().addComponent(port));
            //mainPanel.validate(); isn't necessary and thus a waste of
            //resources (very slow)
        }
        assert success;
            //separate assertion in order to ensure that statement is
            //executed if assertions are off
        //Since there's no way to listen to changes of the selected tab in a
        //dockable with multiple components (and there's no mailing list to ask
        //questions (requested one at https://github.com/opencollab/flexdock/issues/10))
        //add a PropertyChangeListener (which only works if one clicks on the
        //tab component after changing the tab) in which the listener can be
        //added lazily, i.e. if the ((DockableComponentWrapper)PropertyEvent.getSource).getComponent.getParent instanceof JTabbedPane,
        //ActiveDockableListener might do what this does, but is undocumented.
        ChangeListener tabbedPaneChangeListener = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                assert e.getSource() instanceof JTabbedPane;
                JTabbedPane eventSourceCast = (JTabbedPane) e.getSource();
                Component eventSourceCastSelectedComponent = eventSourceCast.getSelectedComponent();
                if(eventSourceCastSelectedComponent == null) {
                    //is null after document has been removed
                    return;
                }
                assert eventSourceCastSelectedComponent instanceof OCRSelectComponent;
                OCRSelectComponent newOCRSelectComponent = (OCRSelectComponent) eventSourceCastSelectedComponent;
                if(!newOCRSelectComponent.equals(mainPanel.getoCRSelectComponent())) {
                    switchDocument(mainPanel.getoCRSelectComponent(),
                            newOCRSelectComponent);
                    mainPanel.setoCRSelectComponent(newOCRSelectComponent);
                }
            }
        };
        aNewDockable = DockingManager.getDockable(aNew);
        aNewDockable.getDockingProperties().setDockableDesc(aNew.getFile() != null
                ? aNew.getFile().getName()
                : Constants.UNSAVED_NAME);
        aNewDockable.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                assert evt.getSource() instanceof DockableComponentWrapper;
                DockableComponentWrapper dockableComponentWrapper = (DockableComponentWrapper) evt.getSource();
                Component dockableComponentWrapperComponent = dockableComponentWrapper.getComponent();
                assert dockableComponentWrapperComponent instanceof OCRSelectComponent;
                if(dockableComponentWrapperComponent.getParent() instanceof JTabbedPane) {
                    JTabbedPane dockableComponentWrapperTabbedPane = (JTabbedPane) dockableComponentWrapperComponent.getParent();
                    if(!new ArrayList<>(Arrays.asList(dockableComponentWrapperTabbedPane.getChangeListeners())).contains(tabbedPaneChangeListener)) {
                        dockableComponentWrapperTabbedPane.addChangeListener(tabbedPaneChangeListener);
                    }
                    //work around bug that causes the tab title of the firstly
                    //added dockable to be "null" although
                    //Dockable.getDockableDesc differs (by simple refreshing
                    //all titles)
                    for(int tabIndex = 0; tabIndex<dockableComponentWrapperTabbedPane.getTabCount(); tabIndex++) {
                        Component tabComponent = dockableComponentWrapperTabbedPane.getComponentAt(tabIndex);
                            //JTabbedPane.getTabComponentAt(int) returns null
                            //because the tab component is different from the
                            //component inside the tab<ref>http://stackoverflow.com/questions/988734/jtabbedpane-weird-behaviour</ref>
                        assert tabComponent != null; //returns null for unknown reasons
                        assert tabComponent instanceof OCRSelectComponent;
                        OCRSelectComponent tabComponentCast = (OCRSelectComponent) tabComponent;
                        dockableComponentWrapperTabbedPane.setTitleAt(tabIndex, tabComponentCast.getFile() != null
                                ? tabComponentCast.getFile().getName()
                                : Constants.UNSAVED_NAME);
                    }
                }
            }
        });
        switchDocument(old,
                aNew);
    }

    @Override
    public void switchDocument(OCRSelectComponent old,
            OCRSelectComponent aNew) {
        Pair<OCRPanel, EntityPanel> newPair = mainPanel.getDocumentSwitchingMap().get(aNew);
        assert newPair != null;
        OCRPanel oCRPanelNew = newPair.getKey();
        EntityPanel entityPanelNew = newPair.getValue();
        assert oCRPanelNew != null;
        assert entityPanelNew != null;
        if(old != null) {
            Pair<OCRPanel, EntityPanel> oldPair = mainPanel.getDocumentSwitchingMap().get(old);
            assert oldPair != null;
            OCRPanel oCRPanelOld = oldPair.getKey();
            EntityPanel entityPanelOld = oldPair.getValue();
            assert oCRPanelOld != null;
            assert entityPanelOld != null;
            //in order to simulate replacement (which isn't directly supported
            //by flexdock) add and remove
            boolean success = DockingManager.dock(oCRPanelNew, oCRPanelOld);
            assert success;
            success = DockingManager.undock(oCRPanelOld);
            assert success;
            success = DockingManager.dock(entityPanelNew, entityPanelOld);
            assert success;
            success = DockingManager.undock(entityPanelOld);
            assert success;
        }else {
            boolean success = DockingManager.dock(oCRPanelNew, (DockingPort)port, DockingConstants.EAST_REGION);
            assert success;
            success = DockingManager.dock(entityPanelNew, (DockingPort)port, DockingConstants.SOUTH_REGION);
            assert success;
        }
        mainPanel.setoCRSelectComponent(aNew);
        //mainPanel.validate(); isn't necessary and thus a waste of resources
        //(very slow)
    }

    @Override
    public void removeDocument(OCRSelectComponent oCRSelectComponent) {
        assert oCRSelectComponent != null;
        Pair<OCRPanel, EntityPanel> newPair = mainPanel.getDocumentSwitchingMap().get(oCRSelectComponent);
        assert newPair != null;
        OCRPanel oCRPanelNew = newPair.getKey();
        EntityPanel entityPanelNew = newPair.getValue();
        assert oCRPanelNew != null;
        assert entityPanelNew != null;
        if(mainPanel.getDocumentSwitchingMap().size() > 1) {
            //if mainPanel.getDocumentSwitchingMap.size > 1, then at least one
            //document will remain
            //Get the any new document which isn't the one to be removed
            Iterator<OCRSelectComponent> documentSwitchingMapItr = mainPanel.getDocumentSwitchingMap().keySet().iterator();
            OCRSelectComponent aNew = documentSwitchingMapItr.next();
            if(aNew.equals(oCRSelectComponent)) {
                aNew = documentSwitchingMapItr.next();
            }
            switchDocument(oCRSelectComponent, //old
                    aNew);
            boolean success = DockingManager.undock(oCRSelectComponent);
            assert success;
        }else {
            boolean success = DockingManager.undock(oCRPanelNew);
            assert success;
            success = DockingManager.undock(entityPanelNew);
            assert success;
            success = DockingManager.undock(oCRSelectComponent);
            assert success;
            mainPanel.setoCRSelectComponent(null);
        }
        mainPanel.getDocumentSwitchingMap().remove(oCRSelectComponent);
            //only remove after switchDocument, i.e. the complete if-else
            //block above
    }
}
