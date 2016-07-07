/**
 * 
 */
package plugins.danyfel80.bigimage.blocks.bigimage;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.List;

import algorithms.danyfel80.bigimage.io.BigImageReader;
import algorithms.danyfel80.bigimage.io.BigImageUtil;
import icy.common.exception.UnsupportedFormatException;
import plugins.adufour.blocks.lang.Loop;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarFile;
import plugins.adufour.vars.lang.VarInteger;
import plugins.adufour.vars.lang.VarSequence;

/**
 * @author Daniel Felipe Gonzalez Obando
 */
public class ProcessImageFileByTiles extends Loop {

	private VarFile inFile;
	private Var<Rectangle> inFileRectangle;
	private Var<Dimension> inTileSize;

	private VarSequence loopTile;
	private VarInteger loopTileIndexX;
	private VarInteger loopTileIndexY;
	private Var<Point> loopTilePosition;

	private Dimension imageSize;
	private Point tileCount;

	private Point currentTilePosition;
	private Dimension currentTileDimension;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * plugins.adufour.blocks.lang.WorkFlow#declareInput(plugins.adufour.blocks.
	 * util.VarList)
	 */
	@Override
	public void declareInput(VarList inputMap) {
		super.declareInput(inputMap);

		inputMap.add("File", inFile = new VarFile("Image file", null));
		inputMap.add("Working area", inFileRectangle = new Var<Rectangle>("Working area", Rectangle.class));
		inputMap.add("Tile size", inTileSize = new Var<Dimension>("Tile size", Dimension.class));
		inputMap.add("CurrentTile", loopTile = new VarSequence("Current tile", null));
		inputMap.add("Tile position", loopTilePosition = new Var<Point>("Tile position", Point.class));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.blocks.lang.Loop#declareLoopVariables(java.util.List)
	 */
	@Override
	public void declareLoopVariables(List<Var<?>> loopVariables) {
		loopVariables.add(inFile);
		loopVariables.add(inFileRectangle);
		loopVariables.add(inTileSize);

		loopVariables.add(loopTileIndexX = new VarInteger("X index", 0));
		loopVariables.add(loopTileIndexY = new VarInteger("Y index", 0));
		loopVariables.add(loopTilePosition); 
		loopVariables.add(loopTile);
		super.declareLoopVariables(loopVariables);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * plugins.adufour.blocks.lang.Loop#declareOutput(plugins.adufour.blocks.util.
	 * VarList)
	 */
	@Override
	public void declareOutput(VarList outputMap) {
		super.declareOutput(outputMap);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.blocks.lang.Loop#initializeLoop()
	 */
	@Override
	public void initializeLoop() {
		
		try {
			this.imageSize = BigImageUtil.getImageDimension(inFile.getValue());
		} catch (IOException | UnsupportedFormatException e) {
			e.printStackTrace();
			interrupt();
		}
		
		if (inTileSize.getValue() == null) {
			inTileSize.setValue(new Dimension(2000, 2000));
		}

		if (inFileRectangle.getValue() == null) {
			inFileRectangle.setValue(new Rectangle(new Point(0, 0), imageSize));
		}

		if (inFileRectangle.getValue().x < 0) {
			inFileRectangle.getValue().width -= inFileRectangle.getValue().x;
			inFileRectangle.getValue().x = 0;
		}
		if (inFileRectangle.getValue().y < 0) {
			inFileRectangle.getValue().height -= inFileRectangle.getValue().y;
			inFileRectangle.getValue().y = 0;
		}
		if (inFileRectangle.getValue().x + inFileRectangle.getValue().width > imageSize.width) {
			inFileRectangle.getValue().width = imageSize.width - inFileRectangle.getValue().x;
		}
		if (inFileRectangle.getValue().y + inFileRectangle.getValue().height > imageSize.height) {
			inFileRectangle.getValue().height = imageSize.height - inFileRectangle.getValue().y;
		}

		if (inFileRectangle.getValue().isEmpty()) {
			inFileRectangle.setValue(new Rectangle(new Point(0, 0), imageSize));
		}

		this.tileCount = new Point((int) Math.ceil(inFileRectangle.getValue().getWidth() / inTileSize.getValue().getWidth()),
		    (int) Math.ceil(inFileRectangle.getValue().getHeight() / inTileSize.getValue().getHeight()));
		
		loopTileIndexX.setValue(0);
		loopTileIndexY.setValue(0);

		currentTileDimension = new Dimension(inTileSize.getValue());
		currentTilePosition = new Point(inFileRectangle.getValue().getLocation());
		this.loopTilePosition.setValue(new Point());
		super.initializeLoop();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.blocks.lang.Loop#beforeIteration()
	 */
	@Override
	public void beforeIteration() {
		super.beforeIteration();
		if (currentTilePosition.x + currentTileDimension.width > inFileRectangle.getValue().x
		    + inFileRectangle.getValue().width) {
			currentTileDimension.width = inFileRectangle.getValue().x + inFileRectangle.getValue().width
			    - currentTilePosition.x;
		}
		if (currentTilePosition.y + currentTileDimension.height > inFileRectangle.getValue().y
		    + inFileRectangle.getValue().height) {
			currentTileDimension.height = inFileRectangle.getValue().y + inFileRectangle.getValue().height
			    - currentTilePosition.y;
		}
		
		this.loopTilePosition.getValue().setLocation(currentTilePosition.x - inFileRectangle.getValue().x, currentTilePosition.y - inFileRectangle.getValue().y);
		
		try {
			Rectangle extractedArea = new Rectangle(currentTilePosition, currentTileDimension);
			BigImageReader reader = new BigImageReader(inFile.getValue(),
			    extractedArea, currentTileDimension.width,
			    currentTileDimension.height, null);
			Thread readThread = new Thread(reader);
			readThread.start();
			readThread.join();
			readThread = null;
			this.loopTile.setValue(reader.getSequence());
			reader = null;
		} catch (IllegalArgumentException | UnsupportedFormatException | IOException | InterruptedException e) {
			e.printStackTrace();
			interrupt();
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.blocks.lang.Loop#afterIteration()
	 */
	@Override
	public void afterIteration() {
		loopTileIndexX.setValue(loopTileIndexX.getValue() + 1);
		currentTilePosition.x += inTileSize.getValue().width;
		if (loopTileIndexX.getValue() >= tileCount.x) {
			loopTileIndexX.setValue(0);
			loopTileIndexY.setValue(loopTileIndexY.getValue() + 1);
			currentTilePosition.x = inFileRectangle.getValue().x;
			currentTilePosition.y += inTileSize.getValue().height;
		}
		currentTileDimension.setSize(inTileSize.getValue());
		
		super.afterIteration();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.blocks.lang.Loop#isStopConditionReached()
	 */
	@Override
	public boolean isStopConditionReached() {
		if (super.isStopConditionReached() || loopTileIndexY.getValue() >= tileCount.y) {
			//this.inFileRectangle.setValue(null);
			return true;
		}
		return false;
	}

}
