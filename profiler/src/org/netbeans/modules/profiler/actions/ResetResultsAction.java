/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */

package org.netbeans.modules.profiler.actions;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.netbeans.lib.profiler.TargetAppRunner;
import org.netbeans.lib.profiler.client.ClientUtils;
import org.netbeans.lib.profiler.common.Profiler;
import org.netbeans.lib.profiler.common.event.ProfilingStateEvent;
import org.netbeans.lib.profiler.common.event.ProfilingStateListener;
import org.netbeans.modules.profiler.utilities.Delegate;
import org.netbeans.modules.profiler.ResultsListener;
import org.netbeans.modules.profiler.ResultsManager;
import org.netbeans.modules.profiler.api.icons.Icons;
import org.netbeans.modules.profiler.api.icons.ProfilerIcons;
import org.netbeans.modules.profiler.utilities.ProfilerUtils;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;


/**
 * Reset Collected Results for the profiled application (= Reset Collectors)
 *
 * @author Ian Formanek
 */
@NbBundle.Messages({
    "LBL_ResetResultsAction=R&eset Collected Results",
    "HINT_ResetResultsAction=Reset Collected Results Buffer"
})
public final class ResetResultsAction extends AbstractAction implements ProfilingStateListener {
    
    Listener resultListener;
    
    //~ Constructors -------------------------------------------------------------------------------------------------------------
    
    /* 
     * The following code is an externalization of various listeners registered
     * in the global lookup and needing access to an enclosing instance of
     * ResetResultsAction. 
     * The enclosing instance will use the FQN registration to obtain the shared instance
     * of the listener implementation and inject itself as a delegate into the listener.
     */
    @ServiceProvider(service=ResultsListener.class)
    public static final class Listener extends Delegate<ResetResultsAction> implements ResultsListener {
        @Override
        public void resultsAvailable() {
            if (getDelegate() != null) getDelegate().updateEnabledState();
        }

        @Override
        public void resultsReset() { 
            if (getDelegate() != null) getDelegate().updateEnabledState();
        }
    }
    
    private static ResetResultsAction instance;
    
    private ResetResultsAction() {
        putValue(Action.NAME, Bundle.LBL_ResetResultsAction());
        putValue(Action.SHORT_DESCRIPTION, Bundle.HINT_ResetResultsAction());
        putValue(Action.SMALL_ICON, Icons.getIcon(ProfilerIcons.RESET_RESULTS));
        putValue("iconBase", Icons.getResource(ProfilerIcons.RESET_RESULTS)); // NOI18N
        
        updateEnabledState();
        
        resultListener = Lookup.getDefault().lookup(Listener.class);
        resultListener.setDelegate(this);        
        Profiler.getDefault().addProfilingStateListener(this);
    }
    
    public static synchronized ResetResultsAction getInstance() {
        if (instance == null) {
            instance = new ResetResultsAction();
        }
        return instance;
    }

    //~ Methods ------------------------------------------------------------------------------------------------------------------

    public void instrumentationChanged(final int oldInstrType, final int currentInstrType) {
    } // ignore

    public void profilingStateChanged(final ProfilingStateEvent e) {
        updateEnabledState();
    }

    public void threadsMonitoringChanged() {
    } // ignore
    
    /**
     * Invoked when an action occurs.
     */
    @Override
    public void actionPerformed(final ActionEvent e) {
        
        ProfilerUtils.runInProfilerRequestProcessor(new Runnable() {
            @Override
            public void run() {
                ResultsManager.getDefault().reset();
        
                try {
                    TargetAppRunner runner = Profiler.getDefault().getTargetAppRunner();

                    if (runner.targetJVMIsAlive()) {
                        runner.resetTimers();
                    } else {
                        runner.getProfilerClient().resetClientData();

                        // TODO 
                        //        CPUCallGraphBuilder.resetCollectors();
                    }
                } catch (ClientUtils.TargetAppOrVMTerminated targetAppOrVMTerminated) {} // ignore
            }
        });
    }
    
    private void updateEnabledState() {
        setEnabled(ResultsManager.getDefault().resultsAvailable());
    }
}
