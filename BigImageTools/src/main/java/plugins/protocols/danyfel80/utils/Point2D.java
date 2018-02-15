package plugins.protocols.danyfel80.utils;

import icy.plugin.abstract_.PluginActionable;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarDouble;

/**
 * Creates a 2-dimensional point.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class Point2D extends PluginActionable implements Block {

	private VarDouble x;
	private VarDouble y;
	private Var<java.awt.geom.Point2D> pt;

	@Override
	public void declareInput(VarList inputMap) {
		x = new VarDouble("X", 0d);
		y = new VarDouble("Y", 0d);
		inputMap.add(x.getName(), x);
		inputMap.add(y.getName(), y);
	}

	@Override
	public void declareOutput(VarList outputMap) {
		pt = new Var<>("Point", new java.awt.geom.Point2D.Double());
		outputMap.add(pt.getName(), pt);
	}

	@Override
	public void run() {
		pt.setValue(new java.awt.geom.Point2D.Double(x.getValue(), y.getValue()));
	}

}
