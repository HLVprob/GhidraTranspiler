package ghidra.transpiler;

import docking.ActionContext;
import docking.action.DockingAction;
import docking.action.MenuData;
import ghidra.app.events.*;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.framework.plugintool.*;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.program.model.listing.*;
import ghidra.program.util.ProgramLocation;
import ghidra.transpiler.ui.TranspilerProvider;
import ghidra.util.Msg;
import ghidra.util.task.SwingUpdateManager;

@PluginInfo(
    status = PluginStatus.RELEASED,
    packageName = "Ghidra Core",
    category = PluginCategoryNames.ANALYSIS,
    shortDescription = "PCode -> Pseudo-C++ Transpiler",
    description = "Mirrors the Decompiler: auto-transpiles whichever function the Decompiler is showing.",
    servicesRequired = {},
    eventsConsumed = {
        ProgramActivatedPluginEvent.class,
        ProgramOpenedPluginEvent.class,
        ProgramLocationPluginEvent.class,
        ProgramClosedPluginEvent.class
    }
)
public class GhidraTranspilerPlugin extends Plugin {

    private TranspilerProvider provider;

    private Program         currentProgram;
    private ProgramLocation currentLocation;
    private Function        lastFunction;

    private final SwingUpdateManager updateMgr =
        new SwingUpdateManager(200, 200, this::doAutoTranspile);

    public GhidraTranspilerPlugin(PluginTool tool) {
        super(tool);
        provider = new TranspilerProvider(this);
        tool.addComponentProvider(provider, true);
        createActions();
        Msg.info(this, "GhidraTranspilerPlugin loaded.");
    }

    @Override
    public void processEvent(PluginEvent event) {

        if (event instanceof ProgramClosedPluginEvent e) {
            if (e.getProgram() == currentProgram) {
                currentProgram  = null;
                currentLocation = null;
                lastFunction    = null;
            }

        } else if (event instanceof ProgramActivatedPluginEvent e) {
            currentProgram  = e.getActiveProgram();
            currentLocation = null;
            lastFunction    = null;
            updateMgr.updateLater();

        } else if (event instanceof ProgramLocationPluginEvent e) {
            ProgramLocation loc = e.getLocation();
            if (loc == null || currentProgram == null) return;
            if (loc.getAddress() == null) return;
            if (loc.getAddress().isExternalAddress()) return;

            currentLocation = loc;
            updateMgr.updateLater();
        }
    }

    private void doAutoTranspile() {
        if (currentProgram == null || currentLocation == null) return;

        FunctionManager fm = currentProgram.getFunctionManager();
        Function fn = fm.getFunctionContaining(currentLocation.getAddress());
        if (fn == null || fn.equals(lastFunction)) return;

        lastFunction = fn;
        provider.transpile(currentProgram, fn);
    }

    private void createActions() {
        DockingAction action = new DockingAction("Transpile Function", getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                lastFunction = null;
                doAutoTranspile();
            }
            @Override
            public boolean isEnabledForContext(ActionContext context) {
                return currentProgram != null;
            }
        };
        action.setMenuBarData(new MenuData(
            new String[]{"Tools", "GhidraTranspiler", "Transpile Current Function"},
            null, "GhidraTranspiler"
        ));
        tool.addAction(action);
    }

    public void transpileCurrentFunction() {
        lastFunction = null;
        doAutoTranspile();
    }

    @Override
    public void dispose() {
        updateMgr.dispose();
        if (provider != null) {
            tool.removeComponentProvider(provider);
            provider = null;
        }
        super.dispose();
    }
}
