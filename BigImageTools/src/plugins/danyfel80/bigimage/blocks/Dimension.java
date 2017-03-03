package plugins.danyfel80.bigimage.blocks;

import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarInteger;

/**
 * Creates a Dimension block
 * @author Daniel Felipe Gonzalez Obando
 */
public class Dimension extends EzPlug implements Block {

	private VarInteger inWidth;
	private VarInteger inHeight;

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
		outputMap.add("Dimension", outDimension = new Var<java.awt.Dimension>("Dimension", java.awt.Dimension.class));
	}

	@Override
	public void clean() {
	}

	@Override
	protected void execute() {
		outDimension.setValue(new java.awt.Dimension(inWidth.getValue(), inHeight.getValue()));
	}

	@Override
	protected void initialize() {
	}

}
