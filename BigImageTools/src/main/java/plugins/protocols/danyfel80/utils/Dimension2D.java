package plugins.protocols.danyfel80.utils;

import icy.plugin.abstract_.PluginActionable;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarInteger;

/**
 * Creates a dimension of 2 components.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class Dimension2D extends PluginActionable implements Block {

	private VarInteger w;
	private VarInteger h;

	private Var<java.awt.geom.Dimension2D> dim;

	@Override
	public void declareInput(VarList inputMap) {
		w = new VarInteger("Width", 0);
		h = new VarInteger("Height", 0);
		inputMap.add(w.getName(), w);
		inputMap.add(h.getName(), h);
	}

	@Override
	public void declareOutput(VarList outputMap) {
		dim = new Var<java.awt.geom.Dimension2D>("Dimension", new icy.type.dimension.Dimension2D.Double());
		outputMap.add(dim.getName(), dim);
	}

	@Override
	public void run() {
		dim.setValue(new icy.type.dimension.Dimension2D.Double(w.getValue(), h.getValue()));
	}

}
