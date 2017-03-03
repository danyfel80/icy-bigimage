/**
 * 
 */
package plugins.danyfel80.bigimage.blocks.bigimage;

import java.awt.Point;
import java.io.IOException;

import algorithms.danyfel80.bigimage.io.BigImageWriter;
import icy.image.IcyBufferedImage;
import loci.formats.FormatException;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarSequence;

/**
 * @author Daniel Felipe Gonzalez Obando
 *
 */
public class SaveLoopTile extends EzPlug implements Block {

	private VarSequence inSequence = new VarSequence("Tile sequence", null);
	private Var<BigImageWriter> inWriter = new Var<BigImageWriter>("Writer", BigImageWriter.class);
	private Var<Point> inPosition = new Var<Point>("Position", Point.class);

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * plugins.adufour.blocks.lang.Block#declareInput(plugins.adufour.blocks.util.
	 * VarList)
	 */
	@Override
	public void declareInput(VarList inputMap) {
		inputMap.add("Sequence", inSequence);
		inputMap.add("Writer", inWriter);
		inputMap.add("Position", inPosition);
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
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.ezplug.EzPlug#clean()
	 */
	@Override
	public void clean() {
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.ezplug.EzPlug#execute()
	 */
	@Override
	protected void execute() {
		System.out.println("saving to" + inPosition.getValue());
		IcyBufferedImage im = inSequence.getValue().getFirstImage();
		try {
			inWriter.getValue().saveTile(im, inPosition.getValue());
		} catch (IOException | FormatException e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.ezplug.EzPlug#initialize()
	 */
	@Override
	protected void initialize() {

	}

}
