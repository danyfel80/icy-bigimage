package plugins.danyfel80.bigimage.blocks;

import java.awt.Point;

import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarInteger;

/**
 * Creates a rectangle block.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class Rectangle extends EzPlug implements Block {
	private VarInteger inX;
	private VarInteger inY;
	private VarInteger inWidth;
	private VarInteger inHeight;

	private Var<java.awt.Rectangle> outRectangle;
	private Var<Point> outPoint;
	private Var<java.awt.Dimension> outDimension;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * plugins.adufour.blocks.lang.Block#declareInput(plugins.adufour.blocks.util.
	 * VarList)
	 */
	@Override
	public void declareInput(VarList inputMap) {
		inputMap.add("X", inX = new VarInteger("X", 0));
		inputMap.add("Y", inY = new VarInteger("Y", 0));
		inputMap.add("Width", inWidth = new VarInteger("Width", 0));
		inputMap.add("Height", inHeight = new VarInteger("Height", 0));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * plugins.adufour.blocks.lang.Block#declareOutput(plugins.adufour.blocks.util
	 * .VarList)
	 */
	@Override
	public void declareOutput(VarList outputMap) {
		outputMap.add("Rectangle", outRectangle = new Var<java.awt.Rectangle>("Rectangle", java.awt.Rectangle.class));
		outputMap.add("Location", outPoint = new Var<Point>("Location", Point.class));
		outputMap.add("Dimension", outDimension = new Var<java.awt.Dimension>("Dimension", java.awt.Dimension.class));
	}

	@Override
	public void clean() {
	}

	@Override
	protected void execute() {
		outPoint.setValue(new Point(inX.getValue(), inY.getValue()));
		outDimension.setValue(new java.awt.Dimension(inWidth.getValue(), inHeight.getValue()));
		outRectangle.setValue(new java.awt.Rectangle(outPoint.getValue(), outDimension.getValue()));

	}

	@Override
	protected void initialize() {
	}

}
