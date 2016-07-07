package plugins.danyfel80.bigimage.blocks.bigimage;

import java.awt.Dimension;
import java.io.IOException;

import algorithms.danyfel80.bigimage.io.BigImageWriter;
import icy.type.DataType;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarEnum;
import plugins.adufour.vars.lang.VarFile;
import plugins.adufour.vars.lang.VarInteger;

/**
 * Block to create an image writer. This writer can be closed with
 * CloseImageWriter block.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class CreateImageWriter extends EzPlug implements Block {

	private VarFile inFile;
	private Var<Dimension> inImageSize;
	private Var<Dimension> inTileSize;
	private VarInteger inChannelSize;
	private VarEnum<DataType> inDataType;

	private Var<BigImageWriter> outImageWriter;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * plugins.adufour.blocks.lang.Block#declareInput(plugins.adufour.blocks.util.
	 * VarList)
	 */
	@Override
	public void declareInput(VarList inputMap) {
		inputMap.add("File", inFile = new VarFile("File", null));
		inputMap.add("Image size", inImageSize = new Var<>("Image size", Dimension.class));
		inputMap.add("Tile size", inTileSize = new Var<>("Tile size", Dimension.class));
		inputMap.add("Channels", inChannelSize = new VarInteger("Channels", 3));
		inputMap.add("Data type", inDataType = new VarEnum<DataType>("Data type", DataType.UBYTE));
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
		outputMap.add("Writer", outImageWriter = new Var<BigImageWriter>("Writer", BigImageWriter.class));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.ezplug.EzPlug#clean()
	 */
	@Override
	public void clean() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.ezplug.EzPlug#execute()
	 */
	@Override
	protected void execute() {
		try {
			BigImageWriter writer;
			writer = new BigImageWriter(inFile.getValue(), inImageSize.getValue(), inChannelSize.getValue(),
			    inDataType.getValue(), inTileSize.getValue());
			this.outImageWriter.setValue(writer);
		} catch (ServiceException | FormatException | IOException e) {
			e.printStackTrace();
			this.stopExecution();
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
