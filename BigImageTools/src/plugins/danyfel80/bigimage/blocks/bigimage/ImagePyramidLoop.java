package plugins.danyfel80.bigimage.blocks.bigimage;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.List;

import javax.vecmath.Point3d;

import algorithms.danyfel80.bigimage.io.BigImageReader;
import algorithms.danyfel80.bigimage.io.BigImageUtil;
import algorithms.danyfel80.bigimage.io.BigImageWriter;
import icy.common.exception.UnsupportedFormatException;
import icy.sequence.Sequence;
import icy.type.DataType;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import ome.units.UNITS;
import ome.units.quantity.Length;
import plugins.adufour.blocks.lang.Loop;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarEnum;
import plugins.adufour.vars.lang.VarFile;
import plugins.adufour.vars.lang.VarInteger;
import plugins.adufour.vars.lang.VarSequence;

/**
 * Particular kind of loop that will go through all image scales until a desired
 * scale. Scales are defined by s from 0 to n and the input image is tiled using
 * the formula scalingFactor = 1 / pow(2, s). If necessary the tiles can be
 * downsampled to a fixed maximum size to handle memory limitations.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class ImagePyramidLoop extends Loop {

	// ---- Private members ----

	// scale limits
	private VarInteger	startScale;
	private VarInteger	endScale;

	// scale indexes
	private VarInteger			currentScale;
	private VarInteger			currentScaleTileNum;
	private Dimension				currentScaleTileDimension;
	private Var<Point>			currentTilePosition;
	private Var<Dimension>	currentTileDimension;
	private VarInteger			currentTile;
	private VarSequence			currentTileSequence;

	// down-sampling variables
	private VarInteger	maxImageWidth;
	private VarInteger	maxImageHeight;

	// working file and images
	private VarFile							workingFile;
	private VarFile							resultFile;
	private VarEnum<DataType>		resultDataType;
	private VarInteger					resultChannels;
	private Var<Dimension>			imageDimension;
	private Var<Point3d>				pixelSize;
	private BigImageWriter			resultWriter;
	private Var<BigImageWriter>	loopWriter;

	// ---- Getter Methods ----

	// ---- Overridden methods ----

	/*
	 * (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.Loop#initializeLoop()
	 */
	@Override
	public void initializeLoop() {
		super.initializeLoop();
		int start = Math.min(startScale.getValue(), endScale.getValue());
		int end = Math.max(startScale.getValue(), endScale.getValue());

		// Initialize current scale
		this.startScale.setValue(start);
		this.endScale.setValue(end);
		this.currentScale.setValue(start);
		// Initialize current tile
		this.currentTile.setValue(0);

		// Initialize image size
		this.imageDimension = new Var<>("Image size", Dimension.class);
		try {
			this.imageDimension.setValue(BigImageUtil.getImageDimension(workingFile.getValue()));
		} catch (IOException | UnsupportedFormatException e) {
			e.printStackTrace();
		}

		// Initialize pixel size
		this.pixelSize = new Var<>("Image size", new Point3d(1, 1, 1));
		try {
			Length[] pxS = BigImageUtil.getImagePixelSize(workingFile.getValue());
			this.pixelSize.setValue(new Point3d(pxS[0].value(UNITS.MICROM).doubleValue(),
					pxS[1].value(UNITS.MICROM).doubleValue(), pxS[2].value(UNITS.MICROM).doubleValue()));
		} catch (IOException | UnsupportedFormatException e) {
			e.printStackTrace();
		}

		// Initialize current scale width and height
		this.currentTilePosition.setValue(new Point(0, 0));
		this.currentScaleTileDimension = new Dimension(imageDimension.getValue());
		this.currentTileDimension.setValue(new Dimension(currentScaleTileDimension));
		this.currentScaleTileNum = new VarInteger("Current scale tile amount", 1);
		for (int i = 1; i <= startScale.getValue(); i++) {
			this.currentScaleTileNum.setValue(currentScaleTileNum.getValue() * 4);
			this.currentScaleTileDimension.width = (int) Math.ceil(currentScaleTileDimension.getWidth() / 2.0);
			this.currentScaleTileDimension.height = (int) Math.ceil(currentScaleTileDimension.getHeight() / 2.0);
		}
		// Initialize current sequence variable: Done in loop var init
		// Initialize image writer
		try {
			this.resultWriter = new BigImageWriter(resultFile.getValue(), imageDimension.getValue(), pixelSize.getValue(),
					resultChannels.getValue(), resultDataType.getValue(), currentScaleTileDimension);
			loopWriter.setValue(resultWriter);
		} catch (ServiceException | FormatException | IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.Loop#beforeIteration()
	 */
	@Override
	public void beforeIteration() {
		super.beforeIteration();

		this.currentTileDimension.getValue().width = currentScaleTileDimension.width;
		this.currentTileDimension.getValue().height = currentScaleTileDimension.height;

		System.out.println(
				String.format("Working on tile %d at scale %d", (int) currentTile.getValue(), (int) currentScale.getValue()));
		System.out.println(
				String.format("Tile position (%d, %d)", currentTilePosition.getValue().x, currentTilePosition.getValue().y));
		System.out.println(String.format("Tile size (%d, %d)", currentTileDimension.getValue().width,
				currentTileDimension.getValue().height));
		// Load current tile at current scale
		Rectangle tileRect = new Rectangle(currentTilePosition.getValue(), currentTileDimension.getValue());
		try {
			BigImageReader biReader = new BigImageReader(workingFile.getValue(), tileRect, maxImageWidth.getValue(),
					maxImageHeight.getValue(), null);
			Thread biReaderThr = new Thread(biReader);
			biReaderThr.start();
			biReaderThr.join();
			this.currentTileSequence.setValue(biReader.getSequence());
		} catch (IllegalArgumentException | UnsupportedFormatException | IOException | InterruptedException e) {
			e.printStackTrace();
		}

		// Icy.getMainInterface().addSequence(currentTileSequence.getValue());
	}

	/*
	 * (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.Loop#afterIteration()
	 */
	@Override
	public void afterIteration() {
		// Save current tile at current scale: using SaveLoopTile block.

		// Increment current tile
		this.currentTilePosition.getValue().x += currentTileDimension.getValue().width;
		if (currentTilePosition.getValue().x >= imageDimension.getValue().width) {
			this.currentTilePosition.getValue().x = 0;
			this.currentTilePosition.getValue().y += currentTileDimension.getValue().height;
		}

		if (currentTilePosition.getValue().x + currentTileDimension.getValue().width > imageDimension.getValue().width) {
			this.currentTileDimension.getValue().width = imageDimension.getValue().width - currentTilePosition.getValue().x;
		}
		if (currentTilePosition.getValue().y + currentTileDimension.getValue().height > imageDimension.getValue().height) {
			this.currentTileDimension.getValue().height = imageDimension.getValue().height - currentTilePosition.getValue().y;
		}

		this.currentTile.setValue(currentTile.getValue() + 1);
		// If necessary increment scale
		if (currentTile.getValue() >= currentScaleTileNum.getValue()) {
			try {
				this.resultWriter.closeWriter();
			} catch (IOException e) {
				e.printStackTrace();
			}

			this.currentTile.setValue(0);
			this.currentScale.setValue(currentScale.getValue() + 1);

			// If there are still scales to process
			if (currentScale.getValue() <= endScale.getValue()) {
				this.currentScaleTileDimension.width = (int) Math.ceil(currentScaleTileDimension.width / 2.0);
				this.currentScaleTileDimension.height = (int) Math.ceil(currentScaleTileDimension.height / 2.0);
				this.currentTileDimension.getValue().width = currentScaleTileDimension.width;
				this.currentTileDimension.getValue().height = currentScaleTileDimension.height;
				this.currentScaleTileNum.setValue(currentScaleTileNum.getValue() * 4);
				this.currentTilePosition.getValue().x = 0;
				this.currentTilePosition.getValue().y = 0;
			}
		}

		super.afterIteration();
	}

	/*
	 * (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.Loop#isStopConditionReached()
	 */
	@Override
	public boolean isStopConditionReached() {
		if (super.isStopConditionReached()) {
			return true;
		}

		// If it is past the last tile of the last scale
		return this.currentScale.getValue() > this.endScale.getValue();
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * plugins.adufour.blocks.lang.WorkFlow#declareInput(plugins.adufour.blocks.
	 * util.VarList)
	 */
	@Override
	public void declareInput(VarList inputMap) {
		super.declareInput(inputMap);

		inputMap.add("First scale ", startScale = new VarInteger("First scale", 0));
		inputMap.add("Last scale", endScale = new VarInteger("Last scale", 0));
		inputMap.add("Max image width", maxImageWidth = new VarInteger("Max image W", 1000));
		inputMap.add("Max image height", maxImageHeight = new VarInteger("Max image H", 1000));
		inputMap.add("Image file", workingFile = new VarFile("Image file", null));
		inputMap.add("Result file", resultFile = new VarFile("Result image", null));
		inputMap.add("Result type", resultDataType = new VarEnum<DataType>("Result type", DataType.UBYTE));
		inputMap.add("Result channels", resultChannels = new VarInteger("Result channels", 3));

	}

	/*
	 * (non-Javadoc)
	 * @see
	 * plugins.adufour.blocks.lang.Loop#declareOutput(plugins.adufour.blocks.util.
	 * VarList)
	 */
	@Override
	public void declareOutput(VarList outputMap) {
		super.declareOutput(outputMap);
		// inputMap.add("Image file", workingFile = new VarFile("Image file",
		// null));
	}

	/*
	 * (non-Javadoc)
	 * @see plugins.adufour.blocks.lang.Loop#declareLoopVariables(java.util.List)
	 */
	@Override
	public void declareLoopVariables(List<Var<?>> loopVariables) {
		loopVariables.add(workingFile);
		loopVariables.add(resultFile);
		loopVariables.add(startScale);
		loopVariables.add(endScale);
		loopVariables.add(maxImageWidth);
		loopVariables.add(maxImageHeight);
		loopVariables.add(resultDataType);
		loopVariables.add(resultChannels);

		loopVariables.add(currentScale = new VarInteger("Current Scale", 0));
		loopVariables.add(currentTile = new VarInteger("Current tile", 0));
		loopVariables.add(currentTileSequence = new VarSequence("Current sequence", new Sequence()));

		loopVariables.add(loopWriter = new Var<BigImageWriter>("Writer", BigImageWriter.class));
		loopVariables.add(currentTilePosition = new Var<Point>("Current position", Point.class));
		loopVariables.add(currentTileDimension = new Var<Dimension>("Tile size", Dimension.class));

		super.declareLoopVariables(loopVariables);
	}

}
