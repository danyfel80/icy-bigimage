package plugins.protocols.danyfel80.utils;

import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;

import icy.plugin.abstract_.PluginActionable;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.Var;

/**
 * Create a rectangle from a 2D point and a 2D dimension.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class Rectangle2D extends PluginActionable implements Block {
	private Var<Point2D> pt;
	private Var<Dimension2D> dim;
	private Var<java.awt.geom.Rectangle2D> rect;

	@Override
	public void declareInput(VarList inputMap) {
		pt = new Var<>("Position", new Point2D.Double());
		dim = new Var<>("Dimension", new icy.type.dimension.Dimension2D.Double());
		inputMap.add(pt.getName(), pt);
		inputMap.add(dim.getName(), dim);
	}

	@Override
	public void declareOutput(VarList outputMap) {
		rect = new Var<>("Rectangle", new java.awt.geom.Rectangle2D.Double());
	}

	@Override
	public void run() {
		Point2D pos = pt.getValue(true);
		Dimension2D size = dim.getValue(true);
		rect.setValue(new java.awt.geom.Rectangle2D.Double(pos.getX(), pos.getY(), size.getWidth(), size.getHeight()));
	}
}
