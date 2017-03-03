package plugins.danyfel80.bigimage.io;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;

import algorithms.danyfel80.bigimage.io.BigImageReader;
import icy.common.exception.UnsupportedFormatException;
import icy.common.listener.RichProgressListener;
import icy.gui.frame.progress.FailedAnnounceFrame;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzGroup;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;
import plugins.adufour.ezplug.EzVar;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarFile;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarListener;
import plugins.adufour.ezplug.EzVarSequence;

/**
 * This plugin uses the BigImageReader class to load big images and show the
 * loaded sequence.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class LoadBigImage extends EzPlug implements Block, EzStoppable {

	private EzVarFile inFile = new EzVarFile("Image path", "");
	// Downsampling
	private EzVarInteger inMaxWidth = new EzVarInteger("Maximum width");
	private EzVarInteger inMaxHeight = new EzVarInteger("Maximum height");

	// Tiling
	private EzVarBoolean inIsTiled = new EzVarBoolean("Load tile", false);
	private EzVarInteger inTileX = new EzVarInteger("Tile x");
	private EzVarInteger inTileY = new EzVarInteger("Tile y");
	private EzVarInteger inTileW = new EzVarInteger("Tile width");
	private EzVarInteger inTileH = new EzVarInteger("Tile height");

	BigImageReader loader;
	Thread loaderThread;
	
	// Result
	private EzVarSequence outSequence = new EzVarSequence("Sequence");

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * plugins.adufour.blocks.lang.Block#declareInput(plugins.adufour.blocks.util.
	 * VarList)
	 */
	@Override
	public void declareInput(VarList inputMap) {
		inputMap.add(inFile.name, inFile.getVariable());
		inputMap.add(inMaxWidth.name, inMaxWidth.getVariable());
		inputMap.add(inMaxHeight.name, inMaxHeight.getVariable());
		inputMap.add(inIsTiled.name, inIsTiled.getVariable());
		inputMap.add(inTileX.name, inTileX.getVariable());
		inputMap.add(inTileY.name, inTileY.getVariable());
		inputMap.add(inTileW.name, inTileW.getVariable());
		inputMap.add(inTileH.name, inTileH.getVariable());
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
		outputMap.add(outSequence.name, outSequence.getVariable());
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
		// Read input
		File path = inFile.getValue();
		int maxWidth = inMaxWidth.getValue();
		int maxHeight = inMaxHeight.getValue();
		boolean isTiled = inIsTiled.getValue();
		int tileX = inTileX.getValue();
		int tileY = inTileY.getValue();
		int tileW = inTileW.getValue();
		int tileH = inTileH.getValue();

		// Process
		long startTime = System.nanoTime();
		try {
			this.loader = new BigImageReader(path, isTiled ? new Rectangle(tileX, tileY, tileW, tileH) : null, maxWidth,
			    maxHeight, new RichProgressListener() {
						@Override
						public boolean notifyProgress(double position, double length, String message, Object data) {
							if (LoadBigImage.this.getUI() != null) {
								LoadBigImage.this.getUI().setProgressBarValue(position/length);
								LoadBigImage.this.getUI().setProgressBarMessage(message + "... (tile " + (int)position + "/" + (int)length + ")");
							}
							return true;
						}
					});
			
			loaderThread = new Thread(loader);
			loaderThread.start();
			loaderThread.join();
		} catch (IllegalArgumentException | UnsupportedFormatException | IOException e) {
			e.printStackTrace();
			return;
		} catch (InterruptedException e1) {
			System.out.println("Plugin execution interrupted.");
		}
		
		long endTime = System.nanoTime();
		System.out.println("Loaded in " + ((endTime - startTime) / 1000000) + "msecs.");
		
		// Set output
		if (this.getUI() != null) {
			this.getUI().setProgressBarValue(0);
			addSequence(loader.getSequence());
		} else {
			outSequence.setValue(loader.getSequence());
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.ezplug.EzPlug#initialize()
	 */
	@Override
	protected void initialize() {
		addEzComponent(inFile);

		EzGroup downsamplingGroup = new EzGroup("Downsampling", inMaxWidth, inMaxHeight);
		addEzComponent(downsamplingGroup);

		inMaxWidth.setValue(2000);
		inMaxHeight.setValue(2000);

		inIsTiled.addVarChangeListener(new EzVarListener<Boolean>() {
			@Override
			public void variableChanged(EzVar<Boolean> source, Boolean newValue) {
				inTileX.setVisible(newValue);
				inTileY.setVisible(newValue);
				inTileW.setVisible(newValue);
				inTileH.setVisible(newValue);
			}
		});
		EzGroup tileGroup = new EzGroup("Tiles", inIsTiled, inTileX, inTileY, inTileW, inTileH);
		addEzComponent(tileGroup);
		inIsTiled.setValue(false);
	}

	/* (non-Javadoc)
	 * @see plugins.adufour.ezplug.EzPlug#stopExecution()
	 */
	@Override
	public void stopExecution() {
		if (this.loaderThread.isAlive()) {
			this.loader.interrupt();
			try {
				this.loaderThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			new FailedAnnounceFrame("The image " + FilenameUtils.getBaseName(inFile.getValue().getAbsolutePath()) + " loading has been interrupted.");
			this.getUI().setProgressBarValue(0);
		}
		
		super.stopExecution();
	}
	
}
